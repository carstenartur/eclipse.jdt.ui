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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;

/**
 * Validates @EnumSource annotations to ensure that excluded/included enum values
 * actually exist in the referenced enum class.
 * 
 * @since 3.15
 */
public class EnumSourceValidator {

	private static final String ENUM_SOURCE_ANNOTATION = "org.junit.jupiter.params.provider.EnumSource"; //$NON-NLS-1$

	/**
	 * Validates that all values in @EnumSource names array exist in the enum.
	 * 
	 * @param method the test method with @EnumSource annotation
	 * @return List of invalid names that don't exist in the enum
	 * @throws JavaModelException if there's an error accessing the Java model
	 */
	public static List<String> findInvalidEnumNames(IMethod method) throws JavaModelException {
		List<String> invalidNames = new ArrayList<>();
		ICompilationUnit cu = method.getCompilationUnit();
		if (cu == null) {
			return invalidNames;
		}

		// Parse the compilation unit
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(cu);
		parser.setResolveBindings(true);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

		final List<String> namesInAnnotation = new ArrayList<>();
		final IType[] enumType = new IType[1];

		astRoot.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				if (node.getName().getIdentifier().equals(method.getElementName())) {
					extractEnumSourceData(node, namesInAnnotation, enumType);
				}
				return false;
			}
		});

		// If we found names and enum type, validate them
		if (!namesInAnnotation.isEmpty() && enumType[0] != null) {
			Set<String> validEnumConstants = getEnumConstants(enumType[0]);
			for (String name : namesInAnnotation) {
				if (!validEnumConstants.contains(name)) {
					invalidNames.add(name);
				}
			}
		}

		return invalidNames;
	}

	/**
	 * Checks if the given method has any invalid enum names in its @EnumSource annotation.
	 * 
	 * @param method the test method to check
	 * @return true if there are invalid enum names
	 * @throws JavaModelException if there's an error accessing the Java model
	 */
	public static boolean hasInvalidEnumNames(IMethod method) throws JavaModelException {
		return !findInvalidEnumNames(method).isEmpty();
	}

	/**
	 * Extract @EnumSource annotation data: the names array and the enum type.
	 */
	private static void extractEnumSourceData(MethodDeclaration methodDecl, List<String> namesOut, IType[] enumTypeOut) {
		IMethodBinding methodBinding = methodDecl.resolveBinding();
		if (methodBinding == null) {
			return;
		}

		IAnnotationBinding[] annotations = methodBinding.getAnnotations();
		for (IAnnotationBinding annotationBinding : annotations) {
			ITypeBinding annotationType = annotationBinding.getAnnotationType();
			if (annotationType != null && ENUM_SOURCE_ANNOTATION.equals(annotationType.getQualifiedName())) {
				// Extract enum type from value attribute
				IType enumType = extractEnumType(annotationBinding, methodDecl);
				if (enumType != null) {
					enumTypeOut[0] = enumType;
				}

				// Extract names array
				extractNamesArray(annotationBinding, methodDecl, namesOut);
				break;
			}
		}
	}

	/**
	 * Extract the enum type from the @EnumSource value attribute.
	 */
	private static IType extractEnumType(IAnnotationBinding annotationBinding, MethodDeclaration methodDecl) {
		IMemberValuePairBinding[] memberValuePairs = annotationBinding.getAllMemberValuePairs();
		for (IMemberValuePairBinding pair : memberValuePairs) {
			if ("value".equals(pair.getName())) { //$NON-NLS-1$
				Object value = pair.getValue();
				if (value instanceof ITypeBinding) {
					ITypeBinding typeBinding = (ITypeBinding) value;
					String qualifiedName = typeBinding.getQualifiedName();
					if (qualifiedName != null) {
						try {
							IType type = methodDecl.resolveBinding().getDeclaringClass().getJavaElement().getJavaProject().findType(qualifiedName);
							if (type != null && type.isEnum()) {
								return type;
							}
						} catch (JavaModelException e) {
							JUnitPlugin.log(e);
						}
					}
				}
				break;
			}
		}
		return null;
	}

	/**
	 * Extract the names array from @EnumSource annotation.
	 */
	private static void extractNamesArray(IAnnotationBinding annotationBinding, MethodDeclaration methodDecl, List<String> namesOut) {
		IMemberValuePairBinding[] memberValuePairs = annotationBinding.getAllMemberValuePairs();
		for (IMemberValuePairBinding pair : memberValuePairs) {
			if ("names".equals(pair.getName())) { //$NON-NLS-1$
				Object value = pair.getValue();
				if (value instanceof Object[]) {
					Object[] values = (Object[]) value;
					for (Object val : values) {
						if (val instanceof String) {
							namesOut.add((String) val);
						}
					}
				}
				break;
			}
		}
	}

	/**
	 * Get all enum constants from an enum type.
	 */
	private static Set<String> getEnumConstants(IType enumType) {
		Set<String> constants = new HashSet<>();
		try {
			if (enumType != null && enumType.isEnum()) {
				IField[] fields = enumType.getFields();
				for (IField field : fields) {
					if (field.isEnumConstant()) {
						constants.add(field.getElementName());
					}
				}
			}
		} catch (JavaModelException e) {
			JUnitPlugin.log(e);
		}
		return constants;
	}

	/**
	 * Removes invalid enum names from @EnumSource annotation.
	 * This method modifies the source code to remove enum values from the names array
	 * that don't exist in the referenced enum class.
	 * 
	 * @param method the test method with @EnumSource annotation
	 * @throws JavaModelException if there's an error accessing the Java model
	 */
	public static void removeInvalidEnumNames(IMethod method) throws JavaModelException {
		List<String> invalidNames = findInvalidEnumNames(method);
		if (invalidNames.isEmpty()) {
			return; // Nothing to remove
		}

		ICompilationUnit cu = method.getCompilationUnit();
		if (cu == null) {
			return;
		}

		// Parse the compilation unit
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(cu);
		parser.setResolveBindings(true);
		CompilationUnit astRoot = (CompilationUnit) parser.createAST(null);

		org.eclipse.jdt.core.dom.rewrite.ASTRewrite rewrite = org.eclipse.jdt.core.dom.rewrite.ASTRewrite.create(astRoot.getAST());
		final boolean[] modified = new boolean[] { false };

		astRoot.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				if (node.getName().getIdentifier().equals(method.getElementName())) {
					modified[0] = removeInvalidNamesFromAnnotation(node, invalidNames, rewrite);
				}
				return false;
			}
		});

		if (modified[0]) {
			try {
				org.eclipse.text.edits.TextEdit edit = rewrite.rewriteAST();
				cu.applyTextEdit(edit, null);
				cu.save(null, true);
			} catch (Exception e) {
				JUnitPlugin.log(e);
			}
		}
	}

	/**
	 * Remove invalid names from the @EnumSource annotation on a method.
	 */
	private static boolean removeInvalidNamesFromAnnotation(MethodDeclaration methodDecl, List<String> invalidNames, org.eclipse.jdt.core.dom.rewrite.ASTRewrite rewrite) {
		List<?> modifiers = methodDecl.modifiers();
		for (Object modifier : modifiers) {
			if (modifier instanceof Annotation) {
				Annotation annotation = (Annotation) modifier;
				String annotationName = annotation.getTypeName().getFullyQualifiedName();

				if ("EnumSource".equals(annotationName) || ENUM_SOURCE_ANNOTATION.equals(annotationName)) { //$NON-NLS-1$
					return modifyAnnotationToRemoveInvalidNames(annotation, invalidNames, rewrite);
				}
			}
		}
		return false;
	}

	/**
	 * Modify the annotation to remove invalid enum names from the names array.
	 */
	private static boolean modifyAnnotationToRemoveInvalidNames(Annotation annotation, List<String> invalidNames, org.eclipse.jdt.core.dom.rewrite.ASTRewrite rewrite) {
		if (!(annotation instanceof org.eclipse.jdt.core.dom.NormalAnnotation)) {
			return false;
		}

		org.eclipse.jdt.core.dom.NormalAnnotation normalAnnotation = (org.eclipse.jdt.core.dom.NormalAnnotation) annotation;
		List<?> values = normalAnnotation.values();
		
		for (Object obj : values) {
			if (obj instanceof MemberValuePair) {
				MemberValuePair pair = (MemberValuePair) obj;
				if ("names".equals(pair.getName().getIdentifier())) { //$NON-NLS-1$
					Expression value = pair.getValue();
					if (value instanceof ArrayInitializer) {
						ArrayInitializer arrayInit = (ArrayInitializer) value;
						List<?> expressions = arrayInit.expressions();
						
						// Find and remove invalid names
						List<Expression> toRemove = new ArrayList<>();
						for (Object expr : expressions) {
							if (expr instanceof StringLiteral) {
								StringLiteral literal = (StringLiteral) expr;
								if (invalidNames.contains(literal.getLiteralValue())) {
									toRemove.add((Expression) expr);
								}
							}
						}
						
						if (!toRemove.isEmpty()) {
							org.eclipse.jdt.core.dom.rewrite.ListRewrite listRewrite = rewrite.getListRewrite(arrayInit, ArrayInitializer.EXPRESSIONS_PROPERTY);
							for (Expression expr : toRemove) {
								listRewrite.remove(expr, null);
							}
							return true;
						}
					}
				}
			}
		}
		
		return false;
	}
}
