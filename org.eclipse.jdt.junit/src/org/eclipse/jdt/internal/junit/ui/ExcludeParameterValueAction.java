/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer using github copilot - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.junit.ui;

import org.eclipse.jface.action.Action;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.junit.model.TestCaseElement;
import org.eclipse.jdt.internal.junit.model.TestElement;

import org.eclipse.jdt.ui.JavaUI;

/**
 * Action to exclude a specific parameter value from a parameterized test
 * by modifying the @EnumSource annotation to add the test parameter value 
 * to the names exclusion list.
 * 
 * Only works on TestCaseElement that is a child of a parameterized test with @EnumSource.
 * 
 * @since 3.15
 */
public class ExcludeParameterValueAction extends Action {

	private TestCaseElement fTestCaseElement;
	private final TestRunnerViewPart fTestRunnerPart;

	public ExcludeParameterValueAction(TestRunnerViewPart testRunnerPart) {
		super(JUnitMessages.ExcludeParameterValueAction_label);
		fTestRunnerPart = testRunnerPart;
	}

	/**
	 * Update the action based on the current test case element selection.
	 * 
	 * @param testElement the selected test element
	 */
	public void update(TestElement testElement) {
		fTestCaseElement = null;
		
		// Only enable for TestCaseElement
		if (!(testElement instanceof TestCaseElement)) {
			setEnabled(false);
			return;
		}

		TestCaseElement testCase = (TestCaseElement) testElement;
		
		// Only enable for parameterized tests with @EnumSource
		if (testCase.isParameterizedTest() && "EnumSource".equals(testCase.getParameterSourceType())) { //$NON-NLS-1$
			fTestCaseElement = testCase;
			setEnabled(true);
			return;
		}
		
		setEnabled(false);
	}

	@Override
	public void run() {
		if (fTestCaseElement == null) {
			return;
		}

		try {
			// Extract parameter value from test display name
			String displayName = fTestCaseElement.getDisplayName();
			String paramValue = extractParameterValue(displayName);
			if (paramValue == null) {
				return;
			}

			// Find the test method
			String className = fTestCaseElement.getTestClassName();
			String methodName = fTestCaseElement.getTestMethodName();
			
			IJavaProject javaProject = fTestCaseElement.getTestRunSession().getLaunchedProject();
			if (javaProject == null) {
				return;
			}

			IType type = javaProject.findType(className);
			if (type == null) {
				return;
			}

			IMethod method = findTestMethod(type, methodName);
			if (method == null) {
				return;
			}

			// Modify the @EnumSource annotation
			TestAnnotationModifier.excludeEnumValue(method, paramValue);

			// Open the editor
			try {
				JavaUI.openInEditor(method);
			} catch (Exception e) {
				// Unable to open editor
				JUnitPlugin.log(e);
			}
		} catch (Exception e) {
			JUnitPlugin.log(e);
		}
	}

	/**
	 * Extract the parameter value from a parameterized test display name.
	 * E.g., "testWithEnum[VALUE2]" -> "VALUE2"
	 * E.g., "testWithEnum[VALUE2, otherParam]" -> "VALUE2" (first parameter only for enum)
	 */
	private String extractParameterValue(String displayName) {
		int start = displayName.indexOf('[');
		int end = displayName.indexOf(']');
		if (start >= 0 && end > start) {
			String params = displayName.substring(start + 1, end);
			// Handle multiple parameters by taking the first one (enum value)
			int commaIndex = params.indexOf(',');
			if (commaIndex > 0) {
				return params.substring(0, commaIndex).trim();
			}
			return params.trim();
		}
		return null;
	}

	private IMethod findTestMethod(IType type, String methodName) throws JavaModelException {
		IMethod[] methods = type.getMethods();
		for (IMethod method : methods) {
			if (method.getElementName().equals(methodName)) {
				return method;
			}
		}
		return null;
	}
}
