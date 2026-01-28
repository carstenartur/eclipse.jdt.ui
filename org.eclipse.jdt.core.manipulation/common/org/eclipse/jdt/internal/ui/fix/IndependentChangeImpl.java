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
import java.util.Collections;
import java.util.List;

import org.eclipse.ltk.core.refactoring.Change;

import org.eclipse.jdt.ui.cleanup.IndependentChange;

/**
 * Default implementation of {@link IndependentChange} that wraps a {@link Change} object
 * and tracks dependencies with other changes.
 * <p>
 * This implementation provides:
 * </p>
 * <ul>
 * <li>Wrapping of any LTK Change into an IndependentChange</li>
 * <li>Tracking of changes that depend on this change</li>
 * <li>Independence flag to indicate if the change can be safely rejected</li>
 * </ul>
 *
 * @since 1.22
 */
public class IndependentChangeImpl implements IndependentChange {

	private final Change change;
	private final boolean independent;
	private final List<IndependentChange> dependentChanges;

	/**
	 * Creates a new independent change wrapping the given change.
	 *
	 * @param change the underlying change to wrap; must not be {@code null}
	 * @param independent {@code true} if this change can be safely rejected without affecting other
	 *            changes
	 */
	public IndependentChangeImpl(Change change, boolean independent) {
		if (change == null) {
			throw new IllegalArgumentException("Change must not be null"); //$NON-NLS-1$
		}
		this.change= change;
		this.independent= independent;
		this.dependentChanges= new ArrayList<>();
	}

	@Override
	public boolean isIndependent() {
		return independent;
	}

	@Override
	public List<IndependentChange> getDependentChanges() {
		return Collections.unmodifiableList(dependentChanges);
	}

	@Override
	public Change getChange() {
		return change;
	}

	/**
	 * Adds a change that depends on this change being applied.
	 * <p>
	 * This method should be called during cleanup construction to establish dependency relationships
	 * between changes. When a change is added as a dependent, it will be included in the list
	 * returned by {@link #getDependentChanges()}.
	 * </p>
	 *
	 * @param dependentChange the change that depends on this change; must not be {@code null}
	 */
	public void addDependentChange(IndependentChange dependentChange) {
		if (dependentChange == null) {
			throw new IllegalArgumentException("Dependent change must not be null"); //$NON-NLS-1$
		}
		if (!dependentChanges.contains(dependentChange)) {
			dependentChanges.add(dependentChange);
		}
	}

	/**
	 * Removes a dependent change from this change's dependency list.
	 *
	 * @param dependentChange the change to remove from the dependency list
	 * @return {@code true} if the change was removed, {@code false} if it was not in the list
	 */
	public boolean removeDependentChange(IndependentChange dependentChange) {
		return dependentChanges.remove(dependentChange);
	}

	@Override
	public String toString() {
		return "IndependentChange[independent=" + independent + //$NON-NLS-1$
				", dependents=" + dependentChanges.size() + //$NON-NLS-1$
				", change=" + change.getName() + "]"; //$NON-NLS-1$ //$NON-NLS-2$
	}
}
