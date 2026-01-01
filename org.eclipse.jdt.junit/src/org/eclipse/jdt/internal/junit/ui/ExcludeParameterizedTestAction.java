/*******************************************************************************
 * Copyright (c) 2025 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.junit.ui;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.junit.model.TestCaseElement;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.ui.IEditorPart;

/**
 * Action to exclude a specific parameterized test case by modifying the @EnumSource annotation
 * to add the test parameter value to the names exclusion list.
 * 
 * @since 3.15
 */
public class ExcludeParameterizedTestAction extends Action {

	private final TestRunnerViewPart fTestRunnerPart;

	public ExcludeParameterizedTestAction(TestRunnerViewPart testRunnerPart) {
		super(JUnitMessages.ExcludeParameterizedTestAction_label);
		fTestRunnerPart= testRunnerPart;
	}

	@Override
	public void run() {
		IStructuredSelection selection= (IStructuredSelection) fTestRunnerPart.getSelectionProvider().getSelection();
		if (selection == null || selection.isEmpty()) {
			return;
		}

		Object element= selection.getFirstElement();
		if (!(element instanceof TestCaseElement)) {
			return;
		}

		TestCaseElement testCase= (TestCaseElement) element;
		
		// Check if this is a parameterized test with @EnumSource
		if (!testCase.isParameterizedTest() || !"EnumSource".equals(testCase.getParameterSourceType())) {
			return;
		}

		try {
			excludeTestParameter(testCase);
		} catch (Exception e) {
			// Log error
			e.printStackTrace();
		}
	}

	private void excludeTestParameter(TestCaseElement testCase) throws JavaModelException {
		// Extract parameter value from test display name
		String displayName= testCase.getDisplayName();
		String paramValue= extractParameterValue(displayName);
		if (paramValue == null) {
			return;
		}

		// Find the test method
		String className= testCase.getTestClassName();
		String methodName= testCase.getTestMethodName();
		
		IJavaProject javaProject= testCase.getTestRunSession().getLaunchedProject();
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

		// Open the editor
		IEditorPart editor= JavaUI.openInEditor(method);
		if (editor == null) {
			return;
		}

		// Modify the @EnumSource annotation
		modifyEnumSourceAnnotation(method, paramValue);
	}

	/**
	 * Extract the parameter value from a parameterized test display name.
	 * E.g., "testWithEnum[VALUE2]" -> "VALUE2"
	 */
	private String extractParameterValue(String displayName) {
		int start= displayName.indexOf('[');
		int end= displayName.indexOf(']');
		if (start >= 0 && end > start) {
			return displayName.substring(start + 1, end);
		}
		return null;
	}

	private IMethod findTestMethod(IType type, String methodName) throws JavaModelException {
		// Find method with matching name
		IMethod[] methods= type.getMethods();
		for (IMethod method : methods) {
			if (method.getElementName().equals(methodName)) {
				return method;
			}
		}
		return null;
	}

	private void modifyEnumSourceAnnotation(IMethod method, String paramValue) throws JavaModelException {
		ICompilationUnit cu= method.getCompilationUnit();
		if (cu == null) {
			return;
		}

		// Parse the compilation unit
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(cu);
		parser.setResolveBindings(true);
		CompilationUnit astRoot= (CompilationUnit) parser.createAST(null);

		// Find the method declaration and modify @EnumSource
		astRoot.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				if (node.getName().getIdentifier().equals(method.getElementName())) {
					// TODO: Implement the actual AST modification
					// This would use the same logic as AdvancedQuickAssistProcessor.createAddNamesFilterProposal
					// to add the paramValue to the names exclusion list
				}
				return false;
			}
		});
	}

	/**
	 * Updates the enabled state based on the current selection.
	 */
	public void updateEnablement() {
		IStructuredSelection selection= (IStructuredSelection) fTestRunnerPart.getSelectionProvider().getSelection();
		boolean enabled= false;

		if (selection != null && !selection.isEmpty()) {
			Object element= selection.getFirstElement();
			if (element instanceof TestCaseElement) {
				TestCaseElement testCase= (TestCaseElement) element;
				// Enable only for parameterized tests with @EnumSource that have failed
				enabled= testCase.isParameterizedTest() 
						&& "EnumSource".equals(testCase.getParameterSourceType())
						&& testCase.getStatus().isErrorOrFailure();
			}
		}

		setEnabled(enabled);
	}
}
