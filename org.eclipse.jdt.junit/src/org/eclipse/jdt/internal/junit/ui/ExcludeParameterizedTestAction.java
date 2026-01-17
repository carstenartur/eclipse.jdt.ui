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

import java.util.ArrayList;
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
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.eclipse.jdt.internal.junit.JUnitCorePlugin;
import org.eclipse.jdt.internal.junit.model.TestCaseElement;

import org.eclipse.jdt.ui.CodeStyleConfiguration;
import org.eclipse.jdt.ui.JavaUI;

/**
 * Action to exclude a specific parameterized test case by modifying the @EnumSource annotation
 * to add the test parameter value to the names exclusion list, or to disable a normal test
 * by adding @Disabled (JUnit 5) or @Ignore (JUnit 4) annotation.
 * 
 * @since 3.15
 */
public class ExcludeParameterizedTestAction extends Action {

	private static final String JUNIT4_IGNORE_ANNOTATION = "org.junit.Ignore"; //$NON-NLS-1$
	private static final String JUNIT5_DISABLED_ANNOTATION = "org.junit.jupiter.api.Disabled"; //$NON-NLS-1$
	private static final String JUNIT5_TEST_ANNOTATION = "org.junit.jupiter.api.Test"; //$NON-NLS-1$
	private static final String JUNIT5_PARAMETERIZED_TEST_ANNOTATION = "org.junit.jupiter.params.ParameterizedTest"; //$NON-NLS-1$
	private static final String JUNIT5_REPEATED_TEST_ANNOTATION = "org.junit.jupiter.api.RepeatedTest"; //$NON-NLS-1$
	private static final String JUNIT5_TEST_FACTORY_ANNOTATION = "org.junit.jupiter.api.TestFactory"; //$NON-NLS-1$
	private static final String JUNIT5_TEST_TEMPLATE_ANNOTATION = "org.junit.jupiter.api.TestTemplate"; //$NON-NLS-1$

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

		// Modify the @EnumSource annotation
		modifyEnumSourceAnnotation(method, paramValue);
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

		// Parse the compilation unit
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(cu);
		parser.setResolveBindings(true);
		CompilationUnit astRoot= (CompilationUnit) parser.createAST(null);

		// Find the method declaration and add annotation
		ASTRewrite rewrite= ASTRewrite.create(astRoot.getAST());
		final boolean[] modified= new boolean[] { false };
		final String[] annotationToAdd= new String[1];
		final String[] annotationSimpleName= new String[1];

		astRoot.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				if (node.getName().getIdentifier().equals(method.getElementName())) {
					// Determine which annotation to add based on JUnit version
					if (isJUnit5TestMethod(node)) {
						annotationToAdd[0]= JUNIT5_DISABLED_ANNOTATION;
						annotationSimpleName[0]= "Disabled"; //$NON-NLS-1$
					} else if (hasAnnotation(node, JUnitCorePlugin.JUNIT4_ANNOTATION_NAME)) {
						annotationToAdd[0]= JUNIT4_IGNORE_ANNOTATION;
						annotationSimpleName[0]= "Ignore"; //$NON-NLS-1$
					}
					
					if (annotationToAdd[0] != null) {
						addDisableAnnotation(node, annotationToAdd[0], annotationSimpleName[0], rewrite, astRoot);
						modified[0]= true;
					}
				}
				return false;
			}
		});

		// Apply the changes if modification was successful
		if (modified[0]) {
			try {
				// Add import
				ImportRewrite importRewrite= CodeStyleConfiguration.createImportRewrite(astRoot, true);
				importRewrite.addImport(annotationToAdd[0]);

				// Combine both edits using MultiTextEdit to avoid conflicts
				org.eclipse.text.edits.MultiTextEdit multiEdit= new org.eclipse.text.edits.MultiTextEdit();
				
				org.eclipse.text.edits.TextEdit importEdit= importRewrite.rewriteImports(null);
				if (importEdit.hasChildren() || importEdit.getLength() != 0) {
					multiEdit.addChild(importEdit);
				}
				
				org.eclipse.text.edits.TextEdit rewriteEdit= rewrite.rewriteAST();
				multiEdit.addChild(rewriteEdit);

				// Apply the combined edit
				cu.applyTextEdit(multiEdit, null);
				cu.save(null, true);

				// Open the editor
				JavaUI.openInEditor(method);
			} catch (Exception e) {
				JUnitPlugin.log(e);
			}
		}
	}

	/**
	 * Add @Disabled or @Ignore annotation to the method.
	 */
	private void addDisableAnnotation(MethodDeclaration methodDecl, String annotationQualifiedName, 
			String annotationSimpleName, ASTRewrite rewrite, CompilationUnit astRoot) {
		AST ast= astRoot.getAST();
		
		// Add the annotation
		org.eclipse.jdt.core.dom.MarkerAnnotation annotation= ast.newMarkerAnnotation();
		annotation.setTypeName(ast.newName(annotationSimpleName));

		ListRewrite listRewrite= rewrite.getListRewrite(methodDecl, MethodDeclaration.MODIFIERS2_PROPERTY);
		listRewrite.insertFirst(annotation, null);
	}

	/**
	 * Check if method has JUnit 5 test annotations.
	 */
	private boolean isJUnit5TestMethod(MethodDeclaration methodDecl) {
		IMethodBinding binding= methodDecl.resolveBinding();
		if (binding == null) {
			return false;
		}

		IAnnotationBinding[] annotations= binding.getAnnotations();
		for (IAnnotationBinding annotation : annotations) {
			ITypeBinding annotationType= annotation.getAnnotationType();
			if (annotationType != null) {
				String qualifiedName= annotationType.getQualifiedName();
				if (isJUnit5TestAnnotation(qualifiedName)) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Check if qualified name is a JUnit 5 test annotation.
	 */
	private boolean isJUnit5TestAnnotation(String qualifiedName) {
		return JUNIT5_TEST_ANNOTATION.equals(qualifiedName) ||
			   JUNIT5_PARAMETERIZED_TEST_ANNOTATION.equals(qualifiedName) ||
			   JUNIT5_REPEATED_TEST_ANNOTATION.equals(qualifiedName) ||
			   JUNIT5_TEST_FACTORY_ANNOTATION.equals(qualifiedName) ||
			   JUNIT5_TEST_TEMPLATE_ANNOTATION.equals(qualifiedName);
	}

	/**
	 * Check if method has specific annotation.
	 */
	private boolean hasAnnotation(MethodDeclaration methodDecl, String annotationQualifiedName) {
		IMethodBinding binding= methodDecl.resolveBinding();
		if (binding == null) {
			return false;
		}

		IAnnotationBinding[] annotations= binding.getAnnotations();
		for (IAnnotationBinding annotation : annotations) {
			ITypeBinding annotationType= annotation.getAnnotationType();
			if (annotationType != null && annotationQualifiedName.equals(annotationType.getQualifiedName())) {
				return true;
			}
		}

		return false;
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
		
		// Check if annotation already has names and mode attributes
		org.eclipse.jdt.core.dom.Expression valueExpr= null;
		org.eclipse.jdt.core.dom.MemberValuePair existingModePair= null;
		org.eclipse.jdt.core.dom.MemberValuePair existingNamesPair= null;
		List<String> existingNames= new ArrayList<>();
		boolean isExcludeMode= false;
		
		if (annotation instanceof org.eclipse.jdt.core.dom.NormalAnnotation) {
			org.eclipse.jdt.core.dom.NormalAnnotation normalAnnotation= (org.eclipse.jdt.core.dom.NormalAnnotation) annotation;
			List<?> values= normalAnnotation.values();
			for (Object obj : values) {
				if (obj instanceof org.eclipse.jdt.core.dom.MemberValuePair) {
					org.eclipse.jdt.core.dom.MemberValuePair pair= (org.eclipse.jdt.core.dom.MemberValuePair) obj;
					String name= pair.getName().getIdentifier();
					if ("value".equals(name)) { //$NON-NLS-1$
						valueExpr= pair.getValue();
					} else if ("mode".equals(name)) { //$NON-NLS-1$
						existingModePair= pair;
						// Check if it's EXCLUDE mode
						org.eclipse.jdt.core.dom.Expression modeValue= pair.getValue();
						if (modeValue instanceof org.eclipse.jdt.core.dom.QualifiedName) {
							org.eclipse.jdt.core.dom.QualifiedName qn= (org.eclipse.jdt.core.dom.QualifiedName) modeValue;
							isExcludeMode= "EXCLUDE".equals(qn.getName().getIdentifier()); //$NON-NLS-1$
						}
					} else if ("names".equals(name)) { //$NON-NLS-1$
						existingNamesPair= pair;
						// Extract existing names
						org.eclipse.jdt.core.dom.Expression namesValue= pair.getValue();
						if (namesValue instanceof org.eclipse.jdt.core.dom.ArrayInitializer) {
							org.eclipse.jdt.core.dom.ArrayInitializer array= (org.eclipse.jdt.core.dom.ArrayInitializer) namesValue;
							List<?> expressions= array.expressions();
							for (Object expr : expressions) {
								if (expr instanceof org.eclipse.jdt.core.dom.StringLiteral) {
									org.eclipse.jdt.core.dom.StringLiteral literal= (org.eclipse.jdt.core.dom.StringLiteral) expr;
									existingNames.add(literal.getLiteralValue());
								}
							}
						}
					}
				}
			}
		} else if (annotation instanceof org.eclipse.jdt.core.dom.SingleMemberAnnotation) {
			valueExpr= ((org.eclipse.jdt.core.dom.SingleMemberAnnotation) annotation).getValue();
		}
		
		// Create new NormalAnnotation
		org.eclipse.jdt.core.dom.NormalAnnotation newAnnotation= ast.newNormalAnnotation();
		newAnnotation.setTypeName(ast.newName(annotation.getTypeName().getFullyQualifiedName()));
		
		// Copy value attribute
		if (valueExpr != null) {
			org.eclipse.jdt.core.dom.MemberValuePair valuePair= ast.newMemberValuePair();
			valuePair.setName(ast.newSimpleName("value")); //$NON-NLS-1$
			valuePair.setValue((org.eclipse.jdt.core.dom.Expression) rewrite.createCopyTarget(valueExpr));
			newAnnotation.values().add(valuePair);
		}
		
		// Determine the new names list based on existing mode
		List<String> newNames= new ArrayList<>();
		
		if (existingNamesPair == null) {
			// No existing names attribute: Add mode=EXCLUDE and names={paramValue}
			newNames.add(paramValue);
			isExcludeMode= true;
		} else if (isExcludeMode) {
			// Existing EXCLUDE mode: Add paramValue to the list if not already present
			newNames.addAll(existingNames);
			if (!newNames.contains(paramValue)) {
				newNames.add(paramValue);
			}
		} else {
			// Existing INCLUDE mode: Remove paramValue from the list
			for (String name : existingNames) {
				if (!name.equals(paramValue)) {
					newNames.add(name);
				}
			}
			// If we removed the last value, switch to EXCLUDE mode with just this value
			if (newNames.isEmpty()) {
				newNames.add(paramValue);
				isExcludeMode= true;
			}
		}
		
		// Add mode parameter if EXCLUDE mode
		if (isExcludeMode) {
			org.eclipse.jdt.core.dom.MemberValuePair modePair= ast.newMemberValuePair();
			modePair.setName(ast.newSimpleName("mode")); //$NON-NLS-1$
			org.eclipse.jdt.core.dom.QualifiedName modeName= ast.newQualifiedName(
				ast.newName("org.junit.jupiter.params.provider.EnumSource.Mode"), //$NON-NLS-1$
				ast.newSimpleName("EXCLUDE") //$NON-NLS-1$
			);
			modePair.setValue(modeName);
			newAnnotation.values().add(modePair);
		}
		
		// Add names parameter with the new list
		org.eclipse.jdt.core.dom.MemberValuePair namesPair= ast.newMemberValuePair();
		namesPair.setName(ast.newSimpleName("names")); //$NON-NLS-1$
		org.eclipse.jdt.core.dom.ArrayInitializer arrayInit= ast.newArrayInitializer();
		for (String name : newNames) {
			org.eclipse.jdt.core.dom.StringLiteral literal= ast.newStringLiteral();
			literal.setLiteralValue(name);
			arrayInit.expressions().add(literal);
		}
		namesPair.setValue(arrayInit);
		newAnnotation.values().add(namesPair);
		
		// Replace old annotation with new one
		rewrite.replace(annotation, newAnnotation, null);
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
