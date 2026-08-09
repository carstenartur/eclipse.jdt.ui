/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.fix;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.GroupCategory;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextEditBasedChange;
import org.eclipse.ltk.core.refactoring.TextEditBasedChangeGroup;

import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationStateChange;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * A Cleanup change that rejects a partially enabled coordinated migration
 * candidate before execution.
 * <p>
 * Ordinary text-edit groups are unaffected. A group participates only when one
 * of its categories has a description beginning with
 * {@link #CATEGORY_DESCRIPTION_PREFIX}; the suffix is the stable candidate id.
 * All marked groups of one candidate must be effectively enabled or all must be
 * disabled. The effective state includes disabled ancestor file changes.
 * </p>
 */
public final class CoordinatedCleanUpSelectionChange extends DynamicValidationStateChange {

	/** Marker prefix used in a {@link GroupCategory#getDescription()}. */
	public static final String CATEGORY_DESCRIPTION_PREFIX=
			"org.eclipse.jdt.ui.cleanup.atomic-selection:"; //$NON-NLS-1$

	private static final String PARTIAL_SELECTION_MESSAGE=
			"The coordinated cleanup candidate ''{0}'' is only partially selected. " //$NON-NLS-1$
			+ "Select all of its required changes or deselect the complete candidate."; //$NON-NLS-1$

	private static final class CandidateSelection {
		private final String label;
		private int total;
		private int enabled;

		CandidateSelection(String label) {
			this.label= label;
		}

		void add(boolean selected) {
			total++;
			if (selected) {
				enabled++;
			}
		}

		boolean isPartial() {
			return enabled > 0 && enabled < total;
		}
	}

	/** Creates an initially empty coordinated Cleanup change. */
	public CoordinatedCleanUpSelectionChange(String name) {
		super(name);
	}

	@Override
	public RefactoringStatus isValid(IProgressMonitor monitor) throws CoreException {
		RefactoringStatus result= new RefactoringStatus();
		result.merge(super.isValid(monitor));
		result.merge(validateSelection());
		return result;
	}

	@Override
	public Change perform(IProgressMonitor monitor) throws CoreException {
		RefactoringStatus selectionStatus= validateSelection();
		if (selectionStatus.hasFatalError()) {
			throw new CoreException(new Status(IStatus.ERROR, JavaPlugin.getPluginId(),
					selectionStatus.getMessageMatchingSeverity(RefactoringStatus.FATAL)));
		}
		return super.perform(monitor);
	}

	private RefactoringStatus validateSelection() {
		Map<String, CandidateSelection> candidates= new HashMap<>();
		collectSelections(this, true, candidates);
		RefactoringStatus result= new RefactoringStatus();
		for (CandidateSelection candidate : candidates.values()) {
			if (candidate.isPartial()) {
				result.addFatalError(java.text.MessageFormat.format(PARTIAL_SELECTION_MESSAGE, candidate.label));
			}
		}
		return result;
	}

	private static void collectSelections(Change change, boolean parentEnabled,
			Map<String, CandidateSelection> candidates) {
		boolean changeEnabled= parentEnabled && change.isEnabled();
		if (change instanceof TextEditBasedChange textChange) {
			for (TextEditBasedChangeGroup group : textChange.getChangeGroups()) {
				Set<String> countedCandidates= new HashSet<>();
				for (GroupCategory category : group.getGroupCategorySet().asList()) {
					String description= category.getDescription();
					if (!description.startsWith(CATEGORY_DESCRIPTION_PREFIX)) {
						continue;
					}
					String candidateId= description.substring(CATEGORY_DESCRIPTION_PREFIX.length());
					if (candidateId.isEmpty() || !countedCandidates.add(candidateId)) {
						continue;
					}
					candidates.computeIfAbsent(candidateId, ignored -> new CandidateSelection(category.getName()))
							.add(changeEnabled && group.isEnabled());
				}
			}
		}
		if (change instanceof CompositeChange compositeChange) {
			for (Change child : compositeChange.getChildren()) {
				collectSelections(child, changeEnabled, candidates);
			}
		}
	}
}
