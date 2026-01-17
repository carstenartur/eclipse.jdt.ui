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

import java.util.List;

import org.eclipse.jface.action.Action;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.junit.model.TestElement;
import org.eclipse.jdt.internal.junit.model.TestSuiteElement;

/**
 * Action to re-include a single excluded enum value from @EnumSource annotation.
 * @since 3.15
 */
public class ReincludeEnumValueAction extends Action {
	private final TestRunnerViewPart fTestRunnerPart;
	private final String fEnumValue;
	private IMethod fMethod;

	public ReincludeEnumValueAction(TestRunnerViewPart testRunnerPart, String enumValue) {
		super(enumValue);
		fTestRunnerPart = testRunnerPart;
		fEnumValue = enumValue;
	}

	public void update(TestElement element) {
		// Find the test method
		fMethod = findMethod(element);
		setEnabled(fMethod != null);
	}

	@Override
	public void run() {
		if (fMethod == null) {
			return;
		}

		try {
			// Get current excluded names from @EnumSource
			List<String> excludedNames = EnumSourceValidator.getExcludedNames(fMethod);

			// Remove this enum value from the list
			excludedNames.remove(fEnumValue);

			if (excludedNames.isEmpty()) {
				// No more exclusions - remove mode and names entirely
				EnumSourceValidator.removeExcludeMode(fMethod);
			} else {
				// Update the names array
				EnumSourceValidator.updateExcludedNames(fMethod, excludedNames);
			}
		} catch (Exception e) {
			JUnitPlugin.log(e);
		}
	}

	private IMethod findMethod(TestElement element) {
		if (!(element instanceof TestSuiteElement)) {
			return null;
		}

		TestSuiteElement testSuite = (TestSuiteElement) element;
		String testName = testSuite.getTestName();

		// Extract method name from "testMethodName(ParameterType)"
		int index = testName.indexOf('(');
		if (index <= 0) {
			return null;
		}

		String methodName = testName.substring(0, index);
		String className = testSuite.getSuiteTypeName();

		if (className == null || className.isEmpty()) {
			return null;
		}

		IJavaProject javaProject = testSuite.getTestRunSession().getLaunchedProject();
		if (javaProject == null) {
			return null;
		}

		try {
			IType type = javaProject.findType(className);
			if (type == null) {
				return null;
			}

			// Find the method - for parameterized tests, method name is without parameters
			IMethod[] methods = type.getMethods();
			for (IMethod method : methods) {
				if (method.getElementName().equals(methodName)) {
					return method;
				}
			}
		} catch (JavaModelException e) {
			JUnitPlugin.log(e);
		}

		return null;
	}
}
