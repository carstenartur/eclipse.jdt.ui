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

import java.util.List;

import org.eclipse.core.runtime.CoreException;

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

}
