/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Contributors to the Eclipse Foundation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.fix;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.jdt.ui.cleanup.IMultiFileCleanUp;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.TextEdit;

/**
 * Multi-file cleanup that removes unused methods along with their interface declarations.
 * <p>
 * This cleanup demonstrates coordinated changes across multiple files:
 * <ul>
 * <li>Identifies methods that are never called in the codebase</li>
 * <li>Removes the method implementation from classes</li>
 * <li>Removes the corresponding method declaration from interfaces</li>
 * </ul>
 * </p>
 *
 * @since 1.22
 */
public class RemoveUnusedMethodCleanUp extends AbstractCleanUp implements IMultiFileCleanUp {

	/**
	 * Cleanup identifier for enabling this cleanup in preferences.
	 */
	public static final String CLEANUP_REMOVE_UNUSED_METHOD= "cleanup.remove_unused_method"; //$NON-NLS-1$

	public RemoveUnusedMethodCleanUp() {
		super();
	}

	@Override
	public CleanUpRequirements getRequirements() {
		// This cleanup requires AST to analyze method usage
		return new CleanUpRequirements(true, false, false, null);
	}

	@Override
	public RefactoringStatus checkPreConditions(IJavaProject project, ICompilationUnit[] compilationUnits,
			IProgressMonitor monitor) throws CoreException {
		return new RefactoringStatus();
	}

	@Override
	public RefactoringStatus checkPostConditions(IProgressMonitor monitor) throws CoreException {
		return new RefactoringStatus();
	}

	@Override
	public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
		// Single-file fallback - not used when IMultiFileCleanUp is detected
		return null;
	}

	@Override
	public CompositeChange createFix(List<CleanUpContext> contexts) throws CoreException {
		if (contexts == null || contexts.isEmpty()) {
			return null;
		}

		// Check if cleanup is enabled via options
		if (!isEnabled(CLEANUP_REMOVE_UNUSED_METHOD)) {
			return null;
		}

		// Step 1: Collect all method declarations and their usage
		Map<String, MethodInfo> declaredMethods= new HashMap<>();
		Set<String> invokedMethods= new HashSet<>();

		for (CleanUpContext context : contexts) {
			CompilationUnit ast= context.getAST();
			if (ast == null) {
				continue;
			}

			// Collect declared methods
			ast.accept(new ASTVisitor() {
				@Override
				public boolean visit(MethodDeclaration node) {
					IMethodBinding binding= node.resolveBinding();
					if (binding != null && !node.isConstructor()) {
						String key= getMethodKey(binding);
						MethodInfo info= new MethodInfo();
						info.declaration= node;
						info.compilationUnit= context.getCompilationUnit();
						info.ast= ast;
						info.binding= binding;
						declaredMethods.put(key, info);
					}
					return true;
				}
			});

			// Collect invoked methods
			ast.accept(new ASTVisitor() {
				@Override
				public boolean visit(MethodInvocation node) {
					IMethodBinding binding= node.resolveMethodBinding();
					if (binding != null) {
						invokedMethods.add(getMethodKey(binding));
					}
					return true;
				}
			});
		}

		// Step 2: Find unused methods
		List<MethodInfo> unusedMethods= new ArrayList<>();
		for (Map.Entry<String, MethodInfo> entry : declaredMethods.entrySet()) {
			if (!invokedMethods.contains(entry.getKey())) {
				unusedMethods.add(entry.getValue());
			}
		}

		if (unusedMethods.isEmpty()) {
			return null;
		}

		// Step 3: Create changes to remove unused methods
		CompositeChange compositeChange= new CompositeChange("Remove unused methods"); //$NON-NLS-1$
		Map<ICompilationUnit, ASTRewrite> rewrites= new HashMap<>();

		for (MethodInfo methodInfo : unusedMethods) {
			ICompilationUnit cu= methodInfo.compilationUnit;
			ASTRewrite rewrite= rewrites.get(cu);
			if (rewrite == null) {
				rewrite= ASTRewrite.create(methodInfo.ast.getAST());
				rewrites.put(cu, rewrite);
			}

			// Remove the method declaration
			rewrite.remove(methodInfo.declaration, null);

			// Also remove from interfaces if the method overrides an interface method
			IMethodBinding binding= methodInfo.binding;
			ITypeBinding declaringClass= binding.getDeclaringClass();
			if (declaringClass != null) {
				for (ITypeBinding interfaceType : declaringClass.getInterfaces()) {
					removeFromInterface(interfaceType, binding, contexts, rewrites);
				}
			}
		}

		// Convert rewrites to changes
		for (Map.Entry<ICompilationUnit, ASTRewrite> entry : rewrites.entrySet()) {
			ICompilationUnit cu= entry.getKey();
			ASTRewrite rewrite= entry.getValue();
			TextEdit edits= rewrite.rewriteAST();

			CompilationUnitChange change= new CompilationUnitChange("Remove unused method from " + cu.getElementName(), cu); //$NON-NLS-1$
			change.setEdit(edits);
			compositeChange.add(change);
		}

		return compositeChange.getChildren().length > 0 ? compositeChange : null;
	}

	/**
	 * Remove matching method declarations from interface.
	 */
	private void removeFromInterface(ITypeBinding interfaceType, IMethodBinding methodToRemove,
			List<CleanUpContext> contexts, Map<ICompilationUnit, ASTRewrite> rewrites) {
		for (IMethodBinding interfaceMethod : interfaceType.getDeclaredMethods()) {
			if (interfaceMethod.overrides(methodToRemove) || methodToRemove.overrides(interfaceMethod)
					|| isSameMethod(interfaceMethod, methodToRemove)) {
				// Find the interface declaration in our contexts
				for (CleanUpContext context : contexts) {
					CompilationUnit ast= context.getAST();
					if (ast == null) {
						continue;
					}

					MethodDeclaration methodDecl= findMethodDeclaration(ast, interfaceMethod);
					if (methodDecl != null) {
						ICompilationUnit cu= context.getCompilationUnit();
						ASTRewrite rewrite= rewrites.get(cu);
						if (rewrite == null) {
							rewrite= ASTRewrite.create(ast.getAST());
							rewrites.put(cu, rewrite);
						}
						rewrite.remove(methodDecl, null);
					}
				}
			}
		}
	}

	/**
	 * Find method declaration in AST by binding.
	 */
	private MethodDeclaration findMethodDeclaration(CompilationUnit ast, IMethodBinding binding) {
		MethodDeclaration[] result= new MethodDeclaration[1];
		ast.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				IMethodBinding nodeBinding= node.resolveBinding();
				if (nodeBinding != null && nodeBinding.getKey().equals(binding.getKey())) {
					result[0]= node;
					return false;
				}
				return true;
			}
		});
		return result[0];
	}

	/**
	 * Check if two methods are the same by comparing signature.
	 */
	private boolean isSameMethod(IMethodBinding m1, IMethodBinding m2) {
		if (!m1.getName().equals(m2.getName())) {
			return false;
		}
		ITypeBinding[] params1= m1.getParameterTypes();
		ITypeBinding[] params2= m2.getParameterTypes();
		if (params1.length != params2.length) {
			return false;
		}
		for (int i= 0; i < params1.length; i++) {
			if (!params1[i].getErasure().getQualifiedName().equals(params2[i].getErasure().getQualifiedName())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Create a unique key for a method.
	 */
	private String getMethodKey(IMethodBinding binding) {
		StringBuilder key= new StringBuilder();
		ITypeBinding declaringClass= binding.getDeclaringClass();
		if (declaringClass != null) {
			key.append(declaringClass.getQualifiedName());
			key.append('.'); //$NON-NLS-1$
		}
		key.append(binding.getName());
		key.append('('); //$NON-NLS-1$
		ITypeBinding[] params= binding.getParameterTypes();
		for (int i= 0; i < params.length; i++) {
			if (i > 0) {
				key.append(','); //$NON-NLS-1$
			}
			key.append(params[i].getErasure().getQualifiedName());
		}
		key.append(')'); //$NON-NLS-1$
		return key.toString();
	}

	@Override
	public String[] getStepDescriptions() {
		if (isEnabled(CLEANUP_REMOVE_UNUSED_METHOD)) {
			return new String[] { "Remove unused methods and their interface declarations" }; //$NON-NLS-1$
		}
		return new String[0];
	}

	/**
	 * Helper class to store method information.
	 */
	private static class MethodInfo {
		MethodDeclaration declaration;
		ICompilationUnit compilationUnit;
		CompilationUnit ast;
		IMethodBinding binding;
	}
}
