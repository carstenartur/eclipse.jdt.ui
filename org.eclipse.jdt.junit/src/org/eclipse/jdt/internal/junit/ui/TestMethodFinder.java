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

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.internal.junit.model.TestSuiteElement;

/**
 * Resolves the Java method represented by a parameterized-test suite node.
 *
 * @since 3.15
 */
public final class TestMethodFinder {

	private static final char PARAM_START= '(';

	/**
	 * Finds the method represented by a parameterized-test suite.
	 *
	 * <p>When JUnit supplied parameter types, they are used to resolve overloads.
	 * Without parameter metadata, a method is returned only if its name is unique.
	 *
	 * @param testSuiteElement the test suite element
	 * @return the method, or <code>null</code> if it cannot be resolved unambiguously
	 */
	public static IMethod findMethodForParameterizedTest(TestSuiteElement testSuiteElement) {
		if (testSuiteElement == null) {
			return null;
		}

		String testName= testSuiteElement.getTestName();
		int index= testName.indexOf(PARAM_START);
		if (index < 0) {
			return null;
		}

		String className= testSuiteElement.getSuiteTypeName();
		if (className == null || className.isEmpty()) {
			return null;
		}

		IJavaProject javaProject= testSuiteElement.getTestRunSession().getLaunchedProject();
		if (javaProject == null) {
			return null;
		}

		try {
			IType type= javaProject.findType(className);
			if (type == null) {
				return null;
			}
			return findMethod(type, testName.substring(0, index), testSuiteElement.getParameterTypes());
		} catch (JavaModelException | IllegalArgumentException e) {
			JUnitPlugin.log(e);
			return null;
		}
	}

	/**
	 * Resolves a method by name and optional fully qualified parameter type names.
	 *
	 * @param type the declaring type
	 * @param methodName the method name
	 * @param parameterTypes parameter type names supplied by JUnit, or <code>null</code>
	 * @return the method, or <code>null</code> if resolution is ambiguous
	 * @throws JavaModelException if the Java model cannot be read
	 */
	public static IMethod findMethod(IType type, String methodName, String[] parameterTypes)
			throws JavaModelException {
		if (parameterTypes != null) {
			String[] signatures= new String[parameterTypes.length];
			for (int i= 0; i < parameterTypes.length; i++) {
				String parameterType= parameterTypes[i];
				if (parameterType.endsWith("...")) { //$NON-NLS-1$
					parameterType= parameterType.substring(0, parameterType.length() - 3) + "[]"; //$NON-NLS-1$
				}
				signatures[i]= Signature.createTypeSignature(parameterType, true);
			}
			return JavaModelUtil.findMethod(methodName, signatures, false, type);
		}

		IMethod result= null;
		for (IMethod method : type.getMethods()) {
			if (methodName.equals(method.getElementName())) {
				if (result != null) {
					return null;
				}
				result= method;
			}
		}
		return result;
	}

	private TestMethodFinder() {
		// Utility class - no instances
	}
}
