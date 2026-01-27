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

import java.util.Collections;
import java.util.List;

import org.eclipse.ltk.core.refactoring.Change;

/**
 * Represents a change that can be independently accepted or rejected without affecting other changes.
 * <p>
 * This interface extends the cleanup infrastructure to support selective change acceptance, allowing
 * users to review and accept/reject individual changes in a sequence of multi-file cleanups.
 * </p>
 * <p>
 * When a cleanup produces multiple independent changes, each can be individually accepted or rejected.
 * However, when changes have dependencies (one change depends on another), this relationship is tracked
 * to warn users if they attempt to reject a change that others depend on.
 * </p>
 * <p>
 * <strong>Example usage:</strong>
 * </p>
 * <pre>
 * public class MyMultiFileCleanUp extends AbstractCleanUp implements IMultiFileCleanUp {
 *     
 *     public List&lt;IndependentChange&gt; createIndependentFixes(List&lt;CleanUpContext&gt; contexts) {
 *         List&lt;IndependentChange&gt; changes = new ArrayList&lt;&gt;();
 *         
 *         // Create independent changes
 *         Change changeA = createChangeA(contexts);
 *         changes.add(new IndependentChangeImpl(changeA, true)); // independent
 *         
 *         // Create dependent change
 *         Change changeB = createChangeB(contexts);
 *         IndependentChangeImpl depChange = new IndependentChangeImpl(changeB, false);
 *         depChange.addDependency(changes.get(0)); // depends on changeA
 *         changes.add(depChange);
 *         
 *         return changes;
 *     }
 * }
 * </pre>
 *
 * @since 1.22
 * @see IMultiFileCleanUp
 */
public interface IndependentChange {

	/**
	 * Returns whether this change can be safely rejected without affecting other changes.
	 * <p>
	 * A change is considered independent if:
	 * </p>
	 * <ul>
	 * <li>No other changes depend on it being applied</li>
	 * <li>It doesn't rely on the effects of other changes</li>
	 * <li>Rejecting it won't cause compilation errors or inconsistencies</li>
	 * </ul>
	 * <p>
	 * Changes that are not independent may still be rejected, but the framework will warn users
	 * about potential consequences.
	 * </p>
	 *
	 * @return {@code true} if this change can be safely rejected without affecting other changes,
	 *         {@code false} otherwise
	 */
	boolean isIndependent();

	/**
	 * Returns the list of changes that depend on this change being applied.
	 * <p>
	 * This method is used to build a dependency graph for visualizing relationships between changes
	 * in the preview UI and warning users when they attempt to reject a change that others depend on.
	 * </p>
	 * <p>
	 * For example, if change B requires change A to be applied first, then change A's
	 * {@code getDependentChanges()} would include change B.
	 * </p>
	 *
	 * @return unmodifiable list of changes that depend on this change; returns an empty list if no
	 *         changes depend on this one
	 */
	default List<IndependentChange> getDependentChanges() {
		return Collections.emptyList();
	}

	/**
	 * Returns the underlying LTK {@link Change} object that this independent change wraps.
	 * <p>
	 * This allows the framework to access the actual change implementation for preview, application,
	 * and undo operations.
	 * </p>
	 *
	 * @return the underlying change object; never {@code null}
	 */
	Change getChange();

	/**
	 * Returns a human-readable description of this change.
	 * <p>
	 * This description is displayed to users in the preview UI to help them understand what the
	 * change does.
	 * </p>
	 *
	 * @return description of the change; never {@code null}
	 */
	default String getDescription() {
		Change change= getChange();
		return change != null ? change.getName() : "";
	}
}
