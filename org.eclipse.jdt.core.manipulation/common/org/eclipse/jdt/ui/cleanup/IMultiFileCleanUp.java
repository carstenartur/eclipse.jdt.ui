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
package org.eclipse.jdt.ui.cleanup;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;

/**
 * A multi-file clean up can create coordinated fixes across multiple compilation units.
 * <p>
 * This interface extends the capabilities of {@link ICleanUp} to support cleanups that
 * need to make changes across multiple files atomically. For example, removing an unused
 * method declaration from both the implementing class and its interface definition.
 * </p>
 * <p>
 * Multi-file cleanups are invoked with a list of {@link CleanUpContext} objects, one for
 * each compilation unit that may need changes. The cleanup analyzes all contexts together
 * and returns a single {@link CompositeChange} that describes all coordinated edits.
 * </p>
 * <p>
 * Implementers must also implement the standard {@link ICleanUp} interface. The framework
 * will detect multi-file capable cleanups and invoke {@link #createFix(List)} instead of
 * the single-file {@link ICleanUp#createFix(CleanUpContext)} when appropriate.
 * </p>
 * <p>
 * <strong>Example usage:</strong>
 * </p>
 * <pre>
 * public class MyMultiFileCleanUp extends AbstractCleanUp implements IMultiFileCleanUp {
 *     
 *     public CompositeChange createFix(List&lt;CleanUpContext&gt; contexts) throws CoreException {
 *         CompositeChange composite = new CompositeChange("Remove unused methods");
 *         
 *         // Analyze all contexts to find related changes
 *         for (CleanUpContext context : contexts) {
 *             // Find problems and create fixes
 *             CompilationUnitChange change = ...;
 *             if (change != null) {
 *                 composite.add(change);
 *             }
 *         }
 *         
 *         return composite.getChildren().length > 0 ? composite : null;
 *     }
 *     
 *     public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
 *         // Fallback for single-file invocation
 *         return null;
 *     }
 * }
 * </pre>
 *
 * @since 1.22
 */
public interface IMultiFileCleanUp extends ICleanUp {

	/**
	 * Creates a fix across multiple compilation units.
	 * <p>
	 * This method is called instead of {@link ICleanUp#createFix(CleanUpContext)} when the
	 * cleanup framework detects that this cleanup implements {@link IMultiFileCleanUp}.
	 * </p>
	 * <p>
	 * The cleanup should analyze all provided contexts together to identify coordinated changes.
	 * For example, when removing an unused method, the cleanup might need to:
	 * <ul>
	 * <li>Remove the method implementation from the class (one context)</li>
	 * <li>Remove the method declaration from the interface (another context)</li>
	 * <li>Remove overriding methods from subclasses (additional contexts)</li>
	 * </ul>
	 * </p>
	 * <p>
	 * All changes should be bundled into a single {@link CompositeChange} so they can be
	 * previewed and applied (or undone) atomically.
	 * </p>
	 *
	 * @param contexts list of clean up contexts, one per compilation unit; guaranteed to be
	 *                 non-null and non-empty
	 * @return a composite change describing all coordinated edits, or {@code null} if no
	 *         changes are needed
	 * @throws CoreException if an unexpected error occurred while analyzing or creating the fix
	 */
	CompositeChange createFix(List<CleanUpContext> contexts) throws CoreException;

	/**
	 * Creates independent, atomic change units that can be individually accepted or rejected without
	 * breaking other changes.
	 * <p>
	 * This method is an alternative to {@link #createFix(List)} that provides fine-grained control
	 * over change acceptance. Instead of returning a single {@link CompositeChange}, it returns a
	 * list of {@link IndependentChange} objects, each of which can be independently accepted or
	 * rejected by the user.
	 * </p>
	 * <p>
	 * The default implementation wraps the result of {@link #createFix(List)} into a single
	 * independent change, maintaining backward compatibility with existing implementations.
	 * </p>
	 * <p>
	 * Implementations that want to support selective change acceptance should override this method
	 * to return multiple independent changes with appropriate dependency relationships.
	 * </p>
	 * <p>
	 * <strong>Example:</strong>
	 * </p>
	 * <pre>
	 * public List&lt;IndependentChange&gt; createIndependentFixes(List&lt;CleanUpContext&gt; contexts) {
	 *     List&lt;IndependentChange&gt; changes = new ArrayList&lt;&gt;();
	 *     
	 *     // Create each change independently
	 *     for (CleanUpContext context : contexts) {
	 *         Change change = createChangeFor(context);
	 *         if (change != null) {
	 *             changes.add(new IndependentChangeImpl(change, true));
	 *         }
	 *     }
	 *     
	 *     return changes;
	 * }
	 * </pre>
	 *
	 * @param contexts list of clean up contexts, one per compilation unit; guaranteed to be
	 *                 non-null and non-empty by the caller
	 * @return list of independent changes that can be individually accepted or rejected; may return
	 *         an empty list if no changes are needed
	 * @throws CoreException if an unexpected error occurred while analyzing or creating the fixes
	 * @since 1.22
	 */
	default List<IndependentChange> createIndependentFixes(List<CleanUpContext> contexts) throws CoreException {
		// Call the existing createFix method (which may return null)
		CompositeChange compositeChange= createFix(contexts);
		if (compositeChange == null || compositeChange.getChildren().length == 0) {
			return new ArrayList<>();
		}

		// Default implementation: wrap the entire composite change as a single independent change
		List<IndependentChange> result= new ArrayList<>();
		result.add(new IndependentChange() {
			@Override
			public boolean isIndependent() {
				return true;
			}

			@Override
			public Change getChange() {
				return compositeChange;
			}
		});
		return result;
	}

	/**
	 * Recomputes remaining changes after user selection with fresh AST contexts.
	 * <p>
	 * This method is called when {@link #requiresFreshASTAfterSelection()} returns {@code true} and
	 * the user has made a selection in the preview UI. It allows the cleanup to recompute the
	 * remaining changes based on the current state of the code after the selected changes have been
	 * conceptually applied.
	 * </p>
	 * <p>
	 * This is useful for cleanups that need to:
	 * </p>
	 * <ul>
	 * <li>Recalculate which changes are still valid after previous changes</li>
	 * <li>Update change descriptions based on the current code state</li>
	 * <li>Avoid conflicts between changes that were valid initially but become invalid after other
	 * changes are applied</li>
	 * </ul>
	 * <p>
	 * The default implementation simply calls {@link #createFix(List)} with the fresh contexts,
	 * which is appropriate for most cleanups.
	 * </p>
	 * <p>
	 * <strong>Note:</strong> This method is only called if {@link #requiresFreshASTAfterSelection()}
	 * returns {@code true}.
	 * </p>
	 *
	 * @param selectedChanges changes that the user has chosen to apply
	 * @param freshContexts updated contexts with fresh ASTs after previous changes have been applied
	 * @return recomputed changes based on the current state; may return {@code null} if no changes
	 *         are needed
	 * @throws CoreException if an unexpected error occurred while recomputing the changes
	 * @since 1.22
	 */
	default CompositeChange recomputeAfterSelection(List<IndependentChange> selectedChanges,
			List<CleanUpContext> freshContexts) throws CoreException {
		return createFix(freshContexts);
	}

	/**
	 * Returns whether this cleanup requires fresh AST recomputation after each change selection.
	 * <p>
	 * When this method returns {@code true}, the framework will:
	 * </p>
	 * <ol>
	 * <li>Show the user the initial set of changes in the preview UI</li>
	 * <li>Allow the user to select which changes to apply</li>
	 * <li>Apply the selected changes</li>
	 * <li>Regenerate fresh ASTs for all compilation units</li>
	 * <li>Call {@link #recomputeAfterSelection(List, List)} to get updated changes</li>
	 * <li>Repeat from step 2 if there are remaining changes</li>
	 * </ol>
	 * <p>
	 * This is useful for cleanups where the validity or applicability of later changes depends on
	 * earlier changes being applied. However, it comes with a performance cost, as AST parsing and
	 * change computation must be repeated.
	 * </p>
	 * <p>
	 * The default implementation returns {@code false}, meaning changes are computed once and all
	 * selected changes are applied together.
	 * </p>
	 *
	 * @return {@code true} if this cleanup requires fresh AST recomputation after user selection,
	 *         {@code false} otherwise
	 * @since 1.22
	 */
	default boolean requiresFreshASTAfterSelection() {
		return false;
	}

}
