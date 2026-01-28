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
import java.util.Collections;

import org.eclipse.ltk.core.refactoring.Change;

/**
 * Represents an independent change that can be selectively accepted or rejected in a multi-file
 * cleanup operation. Each independent change may have dependencies on other changes.
 * <p>
 * This interface allows for fine-grained control over which cleanup changes are applied,
 * and provides dependency tracking to warn users when rejecting a change that others depend on.
 * </p>
 *
 * @since 1.21
 */
public interface IndependentChange {

	/**
	 * Returns the underlying LTK Change object.
	 *
	 * @return the change object, never null
	 */
	Change getChange();

	/**
	 * Returns a human-readable description of this change for display in the UI.
	 *
	 * @return the description, never null
	 */
	default String getDescription() {
		return getChange().getName();
	}

	/**
	 * Returns the changes that depend on this change. If this change is rejected,
	 * the dependent changes may need to be rejected or recomputed as well.
	 * <p>
	 * The default implementation returns an empty collection, indicating no dependencies.
	 * </p>
	 *
	 * @return a collection of dependent changes, never null but may be empty
	 */
	default Collection<IndependentChange> getDependentChanges() {
		return Collections.emptyList();
	}

	/**
	 * Returns whether this change is currently selected (enabled) for application.
	 *
	 * @return true if the change is selected, false otherwise
	 */
	boolean isSelected();

	/**
	 * Sets whether this change is selected (enabled) for application.
	 *
	 * @param selected true to select the change, false to deselect it
	 */
	void setSelected(boolean selected);
}
