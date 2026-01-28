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
package org.eclipse.jdt.internal.corext.fix;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.eclipse.ltk.core.refactoring.Change;

import org.eclipse.jdt.ui.cleanup.IndependentChange;

/**
 * Default implementation of {@link IndependentChange}.
 * Wraps an LTK Change object and provides support for selection tracking and dependency management.
 */
public class IndependentChangeImpl implements IndependentChange {

	private final Change fChange;
	private boolean fSelected;
	private List<IndependentChange> fDependentChanges;

	/**
	 * Creates a new independent change wrapper.
	 *
	 * @param change the underlying LTK change object, must not be null
	 */
	public IndependentChangeImpl(Change change) {
		this(change, true);
	}

	/**
	 * Creates a new independent change wrapper with specified selection state.
	 *
	 * @param change the underlying LTK change object, must not be null
	 * @param selected the initial selection state
	 */
	public IndependentChangeImpl(Change change, boolean selected) {
		if (change == null) {
			throw new IllegalArgumentException("Change cannot be null"); //$NON-NLS-1$
		}
		fChange= change;
		fSelected= selected;
		fDependentChanges= null;
	}

	@Override
	public Change getChange() {
		return fChange;
	}

	@Override
	public String getDescription() {
		return fChange.getName();
	}

	@Override
	public Collection<IndependentChange> getDependentChanges() {
		if (fDependentChanges == null) {
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(fDependentChanges);
	}

	/**
	 * Adds a dependent change to this change.
	 *
	 * @param dependentChange the change that depends on this change
	 */
	public void addDependentChange(IndependentChange dependentChange) {
		if (fDependentChanges == null) {
			fDependentChanges= new ArrayList<>();
		}
		if (!fDependentChanges.contains(dependentChange)) {
			fDependentChanges.add(dependentChange);
		}
	}

	@Override
	public boolean isSelected() {
		return fSelected;
	}

	@Override
	public void setSelected(boolean selected) {
		fSelected= selected;
	}

	@Override
	public String toString() {
		return "IndependentChange[selected=" + fSelected + ", description=" + getDescription() + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}
}
