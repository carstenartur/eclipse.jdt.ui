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
import org.eclipse.jdt.core.dom.MethodDeclaration;

import org.eclipse.jdt.internal.junit.JUnitCorePlugin;
import org.eclipse.jdt.internal.junit.model.TestCaseElement;

import org.eclipse.jdt.ui.JavaUI;

/**
 * Action to exclude a specific parameterized test case by modifying the @EnumSource annotation
 * to add the test parameter value to the names exclusion list, or to disable a normal test
 * by adding @Disabled (JUnit 5) or @Ignore (JUnit 4) annotation.
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
		TestViewer testViewer= fTestRunnerPart.getTestViewer();
		if (testViewer == null) {
			return;
		}
		IStructuredSelection selection= (IStructuredSelection) testViewer.getActiveViewer().getSelection();
		if (selection == null || selection.isEmpty()) {
			return;
		}

		Object element= selection.getFirstElement();
		if (!(element instanceof TestCaseElement)) {
			return;
		}

		TestCaseElement testCase= (TestCaseElement) element;
		
		try {
			// Check if this is a parameterized test with @EnumSource
			if (testCase.isParameterizedTest() && "EnumSource".equals(testCase.getParameterSourceType())) { //$NON-NLS-1$
				excludeTestParameter(testCase);
			} else {
				// For normal tests, add @Disabled or @Ignore annotation
				disableTest(testCase);
			}
		} catch (Exception e) {
			// Log error
			JUnitPlugin.log(e);
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
		try {
			JavaUI.openInEditor(method);
		} catch (Exception e) {
			// Unable to open editor
			return;
		}

		// Modify the @EnumSource annotation using shared utility
		TestAnnotationModifier.excludeEnumValue(method, paramValue);
	}

	/**
	 * Extract the parameter value from a parameterized test display name.
	 * E.g., "testWithEnum[VALUE2]" -> "VALUE2"
	 * E.g., "testWithEnum[VALUE2, otherParam]" -> "VALUE2" (first parameter only for enum)
	 */
	private String extractParameterValue(String displayName) {
		int start= displayName.indexOf('[');
		int end= displayName.indexOf(']');
		if (start >= 0 && end > start) {
			String params= displayName.substring(start + 1, end);
			// Handle multiple parameters by taking the first one (enum value)
			int commaIndex= params.indexOf(',');
			if (commaIndex > 0) {
				return params.substring(0, commaIndex).trim();
			}
			return params.trim();
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
		ASTRewrite rewrite= ASTRewrite.create(astRoot.getAST());
		final boolean[] modified= new boolean[] { false };

		astRoot.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				if (node.getName().getIdentifier().equals(method.getElementName())) {
					modified[0]= modifyEnumSourceInMethod(node, paramValue, rewrite);
				}
				return false;
			}
		});

		// Apply the changes if modification was successful
		if (modified[0]) {
			try {
				org.eclipse.text.edits.TextEdit edits= rewrite.rewriteAST();
				cu.applyTextEdit(edits, null);
				cu.save(null, true);
			} catch (Exception e) {
				JUnitPlugin.log(e);
			}
		}
	}

	/**
	 * Disable a test by adding @Disabled (JUnit 5) or @Ignore (JUnit 4) annotation.
	 */
	private void disableTest(TestCaseElement testCase) throws JavaModelException {
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

		ICompilationUnit cu= method.getCompilationUnit();
		if (cu == null) {
			return;
		}

		// Parse the compilation unit to determine JUnit version
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(cu);
		parser.setResolveBindings(true);
		CompilationUnit astRoot= (CompilationUnit) parser.createAST(null);

		// Determine which annotation to add based on JUnit version
		final boolean[] isJUnit5= new boolean[] { false };
		astRoot.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				if (node.getName().getIdentifier().equals(method.getElementName())) {
					isJUnit5[0]= TestAnnotationModifier.isJUnit5TestMethod(node) || 
								 !TestAnnotationModifier.hasAnnotation(node, JUnitCorePlugin.JUNIT4_ANNOTATION_NAME);
				}
				return false;
			}
		});

		// Add the appropriate annotation using shared utility
		TestAnnotationModifier.addDisabledAnnotation(method, isJUnit5[0]);

		// Open the editor
		try {
			JavaUI.openInEditor(method);
		} catch (Exception e) {
			// Unable to open editor
		}
	}

	/**
	 * Updates the enabled state based on the current selection.
	 * Enables the action for:
	 * - Parameterized tests with @EnumSource that have failed
	 * - Normal tests that have failed
	 */
	public void updateEnablement() {
		TestViewer testViewer= fTestRunnerPart.getTestViewer();
		if (testViewer == null) {
			setEnabled(false);
			return;
		}
		IStructuredSelection selection= (IStructuredSelection) testViewer.getActiveViewer().getSelection();
		boolean enabled= false;
		String label= JUnitMessages.ExcludeParameterizedTestAction_label;

		if (selection != null && !selection.isEmpty()) {
			Object element= selection.getFirstElement();
			if (element instanceof TestCaseElement) {
				TestCaseElement testCase= (TestCaseElement) element;
				
				// Enable for parameterized tests with @EnumSource that have failed
				if (testCase.isParameterizedTest() 
						&& "EnumSource".equals(testCase.getParameterSourceType()) //$NON-NLS-1$
						&& testCase.getStatus().isErrorOrFailure()) {
					enabled= true;
					label= JUnitMessages.ExcludeParameterizedTestAction_label;
				} 
				// Enable for any failed test (normal tests)
				else if (testCase.getStatus().isErrorOrFailure()) {
					enabled= true;
					label= JUnitMessages.ExcludeParameterizedTestAction_disableTest_label;
				}
			}
		}

		setEnabled(enabled);
		setText(label);
	}
}
