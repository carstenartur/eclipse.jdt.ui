/*******************************************************************************
 * Copyright (c) 2024 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.fix.helper;

import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;

/**
 * Builder for constructing stream pipeline transformations from enhanced for statements.
 * This class uses a factory method pattern to prevent security vulnerabilities related
 * to constructor exceptions (CT_CONSTRUCTOR_THROW).
 *
 * <p>The private constructor ensures that the object cannot be left in a partially
 * initialized state if validation fails, preventing potential finalizer attacks.</p>
 */
public final class StreamPipelineBuilder {
	private final EnhancedForStatement forStatement;
	private final Expression collectionExpression;
	private final ITypeBinding elementType;

	/**
	 * Private constructor to prevent direct instantiation and ensure security.
	 * This constructor only performs simple field assignments that cannot throw exceptions.
	 *
	 * @param forStmt the validated enhanced for statement
	 * @param collExpr the collection expression
	 * @param elemType the element type binding
	 */
	private StreamPipelineBuilder(EnhancedForStatement forStmt, Expression collExpr, ITypeBinding elemType) {
		this.forStatement= forStmt;
		this.collectionExpression= collExpr;
		this.elementType= elemType;
	}

	/**
	 * Factory method to create a StreamPipelineBuilder instance.
	 * This method performs validation that may throw exceptions, ensuring that
	 * any failures occur before object construction begins.
	 *
	 * @param forStmt the enhanced for statement to transform
	 * @param checker the preconditions checker for validation
	 * @return a fully initialized StreamPipelineBuilder instance
	 * @throws IllegalArgumentException if the statement cannot be transformed
	 * @throws NullPointerException if forStmt or checker is null
	 */
	public static StreamPipelineBuilder create(EnhancedForStatement forStmt, PreconditionsChecker checker)
			throws IllegalArgumentException {
		// Validate parameters before construction
		if (forStmt == null) {
			throw new NullPointerException("EnhancedForStatement cannot be null"); //$NON-NLS-1$
		}
		if (checker == null) {
			throw new NullPointerException("PreconditionsChecker cannot be null"); //$NON-NLS-1$
		}

		// Perform validation that may throw exceptions
		checker.validate(forStmt);

		// Extract validated data
		Expression collExpr= forStmt.getExpression();
		if (collExpr == null) {
			throw new IllegalArgumentException("Enhanced for statement must have an expression"); //$NON-NLS-1$
		}

		ITypeBinding typeBinding= collExpr.resolveTypeBinding();
		if (typeBinding == null) {
			throw new IllegalArgumentException("Cannot resolve type binding for expression"); //$NON-NLS-1$
		}

		// Only construct the object after all validation passes
		return new StreamPipelineBuilder(forStmt, collExpr, typeBinding);
	}

	/**
	 * @return the enhanced for statement
	 */
	public EnhancedForStatement getForStatement() {
		return this.forStatement;
	}

	/**
	 * @return the collection expression
	 */
	public Expression getCollectionExpression() {
		return this.collectionExpression;
	}

	/**
	 * @return the element type binding
	 */
	public ITypeBinding getElementType() {
		return this.elementType;
	}
}
