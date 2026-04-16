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
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.junit.Messages;
import org.eclipse.jdt.internal.junit.model.TestCaseElement;
import org.eclipse.jdt.internal.junit.model.TestElement;

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
		fTestRunnerPart= testRunnerPart;
	}

	/**
	 * Update the action based on the current test case element selection.
	 *
	 * @param testElement the selected test element
	 */
	public void update(TestElement testElement) {
		fTestCaseElement= null;

		if (!(testElement instanceof TestCaseElement)) {
			setEnabled(false);
			return;
		}

		TestCaseElement testCase= (TestCaseElement) testElement;

		if (!testCase.isParameterizedTest() && testCase.getParameterSourceType() == null) {
			ParameterizedTestMetadataExtractor.populateMetadata(testCase);
		}

		if (testCase.isParameterizedTest() && "EnumSource".equals(testCase.getParameterSourceType())) { //$NON-NLS-1$
			fTestCaseElement= testCase;
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
			String displayName= fTestCaseElement.getDisplayName();
			String paramValue= extractParameterValue(displayName);
			if (paramValue == null) {
				return;
			}

			String className= fTestCaseElement.getTestClassName();
			String methodName= fTestCaseElement.getTestMethodName();

			IJavaProject javaProject= fTestCaseElement.getTestRunSession().getLaunchedProject();
			if (javaProject == null) {
				return;
			}

			IType type= javaProject.findType(className);
			if (type == null) {
				return;
			}

			IMethod method= findTestMethod(type, methodName);
			if (method == null) {
				return;
			}

			try {
				int remainingValues= EnumSourceValidator.calculateRemainingValues(method, paramValue);
				if (remainingValues >= 0 && remainingValues <= 1) {
					Shell shell= fTestRunnerPart.getViewSite().getShell();
					String message;
					if (remainingValues == 0) {
						message= Messages.format(JUnitMessages.ExcludeParameterValueAction_warning_no_values, paramValue);
					} else {
						message= Messages.format(JUnitMessages.ExcludeParameterValueAction_warning_one_value, paramValue);
					}

					boolean proceed= MessageDialog.openQuestion(
						shell,
						JUnitMessages.ExcludeParameterValueAction_warning_title,
						message
					);

					if (!proceed) {
						return;
					}
				}
			} catch (Exception e) {
				JUnitPlugin.log(e);
			}

			TestAnnotationModifier.excludeEnumValue(method, paramValue);

			try {
				EnumSourceValidator.removeInvalidEnumNames(method);
			} catch (Exception e) {
				JUnitPlugin.log(e);
			}

			try {
				JavaUI.openInEditor(method);
			} catch (Exception e) {
				JUnitPlugin.log(e);
			}
		} catch (Exception e) {
			JUnitPlugin.log(e);
		}
	}

	/**
	 * Extract the parameter value from a parameterized test display name.
	 * JUnit 5 display names for parameterized tests can have different formats:
	 * - "[2] VALUE2" (index in brackets followed by enum name)
	 * - "testWithEnum[VALUE2]" (enum name directly in brackets)
	 * - "1 ==> VALUE2" (index followed by arrow and enum name)
	 */
	private String extractParameterValue(String displayName) {
		// Try to find enum name after the brackets (format: "[2] VALUE2")
		int closeBracket= displayName.indexOf(']');
		if (closeBracket >= 0 && closeBracket < displayName.length() - 1) {
			String afterBracket= displayName.substring(closeBracket + 1).trim();
			if (!afterBracket.isEmpty()) {
				String[] tokens= afterBracket.split("[,\\s]"); //$NON-NLS-1$
				for (String token : tokens) {
					token= token.trim();
					if (!token.isEmpty() && !"==>".equals(token) && !"==".equals(token)) { //$NON-NLS-1$ //$NON-NLS-2$
						return token;
					}
				}
			}
		}

		// Fallback: try to get content inside brackets (format: "testWithEnum[VALUE2]")
		int start= displayName.indexOf('[');
		int end= displayName.indexOf(']');
		if (start >= 0 && end > start) {
			String inBracket= displayName.substring(start + 1, end).trim();
			if (!inBracket.matches("\\d+")) { //$NON-NLS-1$
				int commaIndex= inBracket.indexOf(',');
				if (commaIndex > 0) {
					return inBracket.substring(0, commaIndex).trim();
				}
				return inBracket;
			}
		}

		return null;
	}

	private IMethod findTestMethod(IType type, String methodName) throws JavaModelException {
		IMethod[] methods= type.getMethods();
		for (IMethod method : methods) {
			if (method.getElementName().equals(methodName)) {
				return method;
			}
		}
		return null;
	}
}
