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

import java.util.List;

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
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

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
		if (!testCase.isParameterizedTest() || !"EnumSource".equals(testCase.getParameterSourceType())) { //$NON-NLS-1$
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
				org.eclipse.jdt.core.dom.rewrite.ITrackedNodePosition trackedPosition= rewrite.track(astRoot);
				org.eclipse.text.edits.TextEdit edits= rewrite.rewriteAST();
				cu.applyTextEdit(edits, null);
				cu.save(null, true);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private boolean modifyEnumSourceInMethod(MethodDeclaration methodDecl, String paramValue, ASTRewrite rewrite) {
		// Find @EnumSource annotation
		List<?> modifiers= methodDecl.modifiers();
		for (Object modifier : modifiers) {
			if (modifier instanceof org.eclipse.jdt.core.dom.Annotation) {
				org.eclipse.jdt.core.dom.Annotation annotation= (org.eclipse.jdt.core.dom.Annotation) modifier;
				String annotationName= annotation.getTypeName().getFullyQualifiedName();

				if ("EnumSource".equals(annotationName) ||  //$NON-NLS-1$
					"org.junit.jupiter.params.provider.EnumSource".equals(annotationName)) { //$NON-NLS-1$
					// Modify this annotation to add the exclusion
					modifyAnnotationToExclude(annotation, paramValue, rewrite);
					return true;
				}
			}
		}
		return false;
	}

	private void modifyAnnotationToExclude(org.eclipse.jdt.core.dom.Annotation annotation, String paramValue, ASTRewrite rewrite) {
		AST ast= annotation.getAST();

		// Create new NormalAnnotation with value, mode=EXCLUDE, and names parameter
		org.eclipse.jdt.core.dom.NormalAnnotation newAnnotation= ast.newNormalAnnotation();
		newAnnotation.setTypeName(ast.newName(annotation.getTypeName().getFullyQualifiedName()));

		// Copy existing value attribute
		org.eclipse.jdt.core.dom.Expression valueExpr= null;
		if (annotation instanceof org.eclipse.jdt.core.dom.SingleMemberAnnotation) {
			valueExpr= ((org.eclipse.jdt.core.dom.SingleMemberAnnotation) annotation).getValue();
		} else if (annotation instanceof org.eclipse.jdt.core.dom.NormalAnnotation) {
			List<?> values= ((org.eclipse.jdt.core.dom.NormalAnnotation) annotation).values();
			for (Object obj : values) {
				if (obj instanceof org.eclipse.jdt.core.dom.MemberValuePair) {
					org.eclipse.jdt.core.dom.MemberValuePair pair= (org.eclipse.jdt.core.dom.MemberValuePair) obj;
					if ("value".equals(pair.getName().getIdentifier())) { //$NON-NLS-1$
						valueExpr= pair.getValue();
						break;
					}
				}
			}
		}

		if (valueExpr != null) {
			org.eclipse.jdt.core.dom.MemberValuePair valuePair= ast.newMemberValuePair();
			valuePair.setName(ast.newSimpleName("value")); //$NON-NLS-1$
			valuePair.setValue((org.eclipse.jdt.core.dom.Expression) rewrite.createCopyTarget(valueExpr));
			newAnnotation.values().add(valuePair);
		}

		// Add mode parameter with EXCLUDE mode
		org.eclipse.jdt.core.dom.MemberValuePair modePair= ast.newMemberValuePair();
		modePair.setName(ast.newSimpleName("mode")); //$NON-NLS-1$
		org.eclipse.jdt.core.dom.QualifiedName modeName= ast.newQualifiedName(
			ast.newName("org.junit.jupiter.params.provider.EnumSource.Mode"), //$NON-NLS-1$
			ast.newSimpleName("EXCLUDE") //$NON-NLS-1$
		);
		modePair.setValue(modeName);
		newAnnotation.values().add(modePair);

		// Add names parameter with the single excluded value
		org.eclipse.jdt.core.dom.MemberValuePair namesPair= ast.newMemberValuePair();
		namesPair.setName(ast.newSimpleName("names")); //$NON-NLS-1$
		org.eclipse.jdt.core.dom.ArrayInitializer arrayInit= ast.newArrayInitializer();
		org.eclipse.jdt.core.dom.StringLiteral literal= ast.newStringLiteral();
		literal.setLiteralValue(paramValue);
		arrayInit.expressions().add(literal);
		namesPair.setValue(arrayInit);
		newAnnotation.values().add(namesPair);

		// Replace old annotation with new one
		rewrite.replace(annotation, newAnnotation, null);
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
						&& "EnumSource".equals(testCase.getParameterSourceType()) //$NON-NLS-1$
						&& testCase.getStatus().isErrorOrFailure();
			}
		}

		setEnabled(enabled);
	}
}
