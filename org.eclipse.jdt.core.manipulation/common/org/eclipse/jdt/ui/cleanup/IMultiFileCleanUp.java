/*******************************************************************************
 * Copyright (c) 2024 IBM Corporation and others.
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
package org.eclipse.jdt.ui.cleanup;

import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;

/**
 * Extension of {@link ICleanUp} that supports multi-file cleanup operations with advanced features:
 * <ul>
 * <li>Selective acceptance of individual changes</li>
 * <li>Dependency tracking between changes</li>
 * <li>Fresh AST re-analysis after user selection</li>
 * <li>Iterative cleanup workflows</li>
 * </ul>
 * <p>
 * Clean ups implementing this interface can provide fine-grained control over which changes
 * are applied and support recomputation when user selections change.
 * </p>
 *
 * @since 1.21
 */
public interface IMultiFileCleanUp extends ICleanUp {

	/**
	 * Returns whether this cleanup requires fresh AST parsing after the user makes selections
	 * in the preview dialog. If true, the framework will trigger a fresh parse and call
	 * {@link #recomputeChangesAfterSelection(Collection, IProgressMonitor)} with the new ASTs.
	 * <p>
	 * The default implementation returns false, meaning no recomputation is needed.
	 * </p>
	 *
	 * @return true if fresh AST parsing is required after selection changes, false otherwise
	 */
	default boolean requiresFreshASTAfterSelection() {
		return false;
	}

	/**
	 * Creates a collection of independent changes that can be selectively accepted or rejected.
	 * Each change represents a logically independent unit of work that can be applied separately.
	 * <p>
	 * The default implementation returns an empty collection, indicating that the cleanup
	 * does not support selective acceptance.
	 * </p>
	 *
	 * @param contexts the cleanup contexts for all compilation units being cleaned
	 * @param monitor the progress monitor
	 * @return a collection of independent changes, never null but may be empty
	 * @throws CoreException if an error occurs while creating the changes
	 */
	default Collection<IndependentChange> createIndependentChanges(
			Collection<CleanUpContext> contexts, IProgressMonitor monitor) throws CoreException {
		return List.of();
	}

	/**
	 * Recomputes the changes after the user has made selections in the preview dialog.
	 * This method is called when {@link #requiresFreshASTAfterSelection()} returns true
	 * and the user has accepted or rejected some changes.
	 * <p>
	 * The method receives fresh AST contexts for all compilation units and should return
	 * updated independent changes based on the new analysis.
	 * </p>
	 * <p>
	 * The default implementation returns an empty collection.
	 * </p>
	 *
	 * @param contexts the fresh cleanup contexts with updated ASTs
	 * @param selectedChanges the changes that were previously selected by the user
	 * @param monitor the progress monitor
	 * @return a collection of recomputed independent changes, never null but may be empty
	 * @throws CoreException if an error occurs while recomputing the changes
	 */
	default Collection<IndependentChange> recomputeChangesAfterSelection(
			Collection<CleanUpContext> contexts,
			Collection<IndependentChange> selectedChanges,
			IProgressMonitor monitor) throws CoreException {
		return List.of();
	}
}
