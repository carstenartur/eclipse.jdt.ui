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

/**
 * Interface for checking preconditions before transforming enhanced for statements.
 */
public interface PreconditionsChecker {
	/**
	 * Validates that the enhanced for statement can be safely transformed.
	 *
	 * @param forStmt the enhanced for statement to validate
	 * @throws IllegalArgumentException if the statement cannot be transformed
	 */
	void validate(EnhancedForStatement forStmt) throws IllegalArgumentException;
}
