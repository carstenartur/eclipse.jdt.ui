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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.NullChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextEditBasedChange;
import org.eclipse.ltk.core.refactoring.TextEditBasedChangeGroup;

import org.eclipse.jdt.core.ICompilationUnit;

import org.eclipse.jdt.internal.ui.IJavaStatusConstants;
import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Presents one coordinated cleanup migration as a single selectable LTK change
 * while delegating validation, execution, and undo to the ordinary per-file
 * changes produced by the cleanup refactoring.
 *
 * <p>The wrapped changes deliberately remain hidden from the standard preview
 * tree. A dedicated preview viewer can inspect them through {@link #getChanges()}
 * without exposing unsafe per-file or per-edit check boxes. Ordinary cleanup
 * changes that are not part of this wrapper retain the standard LTK preview.</p>
 */
public final class CoordinatedCleanUpChange extends Change {

	private final String fName;
	private final String fDescription;
	private final List<String> fCandidateIds;
	private final List<String> fSafetyDetails;
	private final List<ICompilationUnit> fCompilationUnits;
	private final CompositeChange fDelegate;

	/**
	 * Creates one atomic coordinated cleanup change.
	 *
	 * @param name user-facing candidate name
	 * @param description user-facing explanation
	 * @param candidateIds stable candidate identifiers
	 * @param safetyDetails safety and scope evidence
	 * @param compilationUnits compilation units changed by the candidate
	 * @param changes ordinary LTK changes to execute atomically
	 */
	public CoordinatedCleanUpChange(String name, String description, List<String> candidateIds,
			List<String> safetyDetails, List<ICompilationUnit> compilationUnits, Change[] changes) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("The coordinated cleanup name must not be blank"); //$NON-NLS-1$
		}
		if (candidateIds == null || candidateIds.isEmpty()) {
			throw new IllegalArgumentException("At least one coordinated cleanup candidate id is required"); //$NON-NLS-1$
		}
		if (changes == null || changes.length == 0) {
			throw new IllegalArgumentException("At least one coordinated cleanup change is required"); //$NON-NLS-1$
		}
		fName= name;
		fDescription= description == null ? "" : description; //$NON-NLS-1$
		fCandidateIds= List.copyOf(candidateIds);
		fSafetyDetails= safetyDetails == null ? List.of() : List.copyOf(safetyDetails);
		fCompilationUnits= compilationUnits == null ? List.of() : List.copyOf(compilationUnits);
		fDelegate= new CompositeChange(name, changes);
	}

	@Override
	public String getName() {
		return fName;
	}

	/**
	 * Returns the explanation shown by the coordinated cleanup preview viewer.
	 *
	 * @return immutable description
	 */
	public String getDescription() {
		return fDescription;
	}

	/**
	 * Returns the stable candidate identifiers represented by this change.
	 *
	 * @return immutable identifiers
	 */
	public List<String> getCandidateIds() {
		return fCandidateIds;
	}

	/**
	 * Returns user-facing safety and scope evidence.
	 *
	 * @return immutable detail lines
	 */
	public List<String> getSafetyDetails() {
		return fSafetyDetails;
	}

	/**
	 * Returns the affected compilation units.
	 *
	 * @return immutable compilation units
	 */
	public List<ICompilationUnit> getCompilationUnits() {
		return fCompilationUnits;
	}

	/**
	 * Returns the wrapped per-file changes for a read-only preview.
	 *
	 * @return a snapshot of the delegated changes
	 */
	public Change[] getChanges() {
		return fDelegate.getChildren();
	}

	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		fDelegate.setEnabled(enabled);
	}

	@Override
	public void initializeValidationData(IProgressMonitor pm) {
		fDelegate.initializeValidationData(pm);
	}

	@Override
	public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		if (!isEnabled()) {
			return new RefactoringStatus();
		}
		RefactoringStatus selectionStatus= validateAtomicSelection();
		if (selectionStatus.hasFatalError()) {
			return selectionStatus;
		}
		return fDelegate.isValid(pm == null ? new NullProgressMonitor() : pm);
	}

	@Override
	public Change perform(IProgressMonitor pm) throws CoreException {
		if (!isEnabled()) {
			return new NullChange();
		}
		RefactoringStatus selectionStatus= validateAtomicSelection();
		if (selectionStatus.hasFatalError()) {
			String message= selectionStatus.getMessageMatchingSeverity(RefactoringStatus.FATAL);
			throw new CoreException(new Status(IStatus.ERROR, JavaPlugin.getPluginId(),
					IJavaStatusConstants.INTERNAL_ERROR, message, null));
		}
		return fDelegate.perform(pm == null ? new NullProgressMonitor() : pm);
	}

	@Override
	public void dispose() {
		fDelegate.dispose();
	}

	@Override
	public Object getModifiedElement() {
		if (fCompilationUnits.isEmpty()) {
			return null;
		}
		if (fCompilationUnits.size() == 1) {
			return fCompilationUnits.get(0);
		}
		return fCompilationUnits.get(0).getJavaProject();
	}

	@Override
	public Object[] getAffectedObjects() {
		Object[] affected= fDelegate.getAffectedObjects();
		if (affected != null) {
			return affected;
		}
		return fCompilationUnits.toArray();
	}

	private RefactoringStatus validateAtomicSelection() {
		List<String> disabled= new ArrayList<>();
		for (Change child : fDelegate.getChildren()) {
			collectDisabledParts(child, disabled);
		}
		if (disabled.isEmpty()) {
			return new RefactoringStatus();
		}
		String message= "The coordinated cleanup candidate must be selected as one atomic unit. " //$NON-NLS-1$
				+ "Re-enable the complete candidate or deselect its single top-level entry. Disabled parts: " //$NON-NLS-1$
				+ String.join(", ", disabled); //$NON-NLS-1$
		return RefactoringStatus.createFatalErrorStatus(message);
	}

	private static void collectDisabledParts(Change change, List<String> disabled) {
		if (!change.isEnabled()) {
			disabled.add(change.getName());
			return;
		}
		if (change instanceof TextEditBasedChange textChange) {
			for (TextEditBasedChangeGroup group : textChange.getChangeGroups()) {
				if (!group.isEnabled()) {
					disabled.add(change.getName() + " / " + group.getName()); //$NON-NLS-1$
				}
			}
		}
		if (change instanceof CompositeChange composite) {
			for (Change child : composite.getChildren()) {
				collectDisabledParts(child, disabled);
			}
		}
	}
}
