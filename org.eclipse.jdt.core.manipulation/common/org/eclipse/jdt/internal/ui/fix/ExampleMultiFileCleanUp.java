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

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.jdt.ui.cleanup.IMultiFileCleanUp;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

/**
 * Example multi-file cleanup implementation that demonstrates coordinated changes
 * across multiple compilation units.
 * <p>
 * This is a proof-of-concept cleanup that can be used as a template for more
 * complex multi-file operations like removing unused methods with their interface
 * declarations.
 * </p>
 *
 * @since 1.22
 */
public class ExampleMultiFileCleanUp extends AbstractCleanUp implements IMultiFileCleanUp {

	/**
	 * Cleanup identifier for enabling this cleanup in preferences.
	 */
	public static final String CLEANUP_MULTI_FILE_EXAMPLE= "cleanup.multi_file_example"; //$NON-NLS-1$

	public ExampleMultiFileCleanUp() {
		super();
	}

	@Override
	public CleanUpRequirements getRequirements() {
		// This example doesn't require AST parsing
		return new CleanUpRequirements(false, false, false, null);
	}

	@Override
	public RefactoringStatus checkPreConditions(IJavaProject project, ICompilationUnit[] compilationUnits,
			IProgressMonitor monitor) throws CoreException {
		// No preconditions needed for this example
		return new RefactoringStatus();
	}

	@Override
	public RefactoringStatus checkPostConditions(IProgressMonitor monitor) throws CoreException {
		// No postconditions needed for this example
		return new RefactoringStatus();
	}

	@Override
	public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
		// Single-file fallback - not typically used when IMultiFileCleanUp is detected
		// For compatibility, return null to indicate no single-file changes
		return null;
	}

	@Override
	public CompositeChange createFix(List<CleanUpContext> contexts) throws CoreException {
		if (contexts == null || contexts.isEmpty()) {
			return null;
		}

		// Check if cleanup is enabled via options
		if (!isEnabled(CLEANUP_MULTI_FILE_EXAMPLE)) {
			return null;
		}

		CompositeChange compositeChange= new CompositeChange("Example Multi-File Cleanup"); //$NON-NLS-1$

		// Analyze all contexts together to demonstrate multi-file coordination
		for (CleanUpContext context : contexts) {
			ICompilationUnit cu= context.getCompilationUnit();

			// Example: Check if this is a Java file we want to process
			if (cu.getElementName().endsWith(".java")) { //$NON-NLS-1$
				// In a real implementation, this would analyze the code
				// and create appropriate changes. For now, this is just
				// a placeholder to demonstrate the infrastructure.

				// Uncomment to create actual changes:
				// CompilationUnitChange change = createChangeForUnit(cu);
				// if (change != null) {
				//     compositeChange.add(change);
				// }
			}
		}

		// Return the composite change only if there are actual changes
		return compositeChange.getChildren().length > 0 ? compositeChange : null;
	}

	@Override
	public String[] getStepDescriptions() {
		if (isEnabled(CLEANUP_MULTI_FILE_EXAMPLE)) {
			return new String[] { "Example multi-file cleanup (proof of concept)" }; //$NON-NLS-1$
		}
		return new String[0];
	}

	/**
	 * Example method showing how to create a change for a compilation unit.
	 * This is commented out as it's just a template.
	 */
	@SuppressWarnings("unused")
	private CompilationUnitChange createChangeForUnit(ICompilationUnit cu) throws CoreException {
		// Example implementation would go here
		// This would typically:
		// 1. Analyze the compilation unit
		// 2. Find issues to fix
		// 3. Create TextEdits for the fixes
		// 4. Return a CompilationUnitChange with those edits

		return null;
	}

	/**
	 * Example demonstrating how to implement createIndependentFixes() to support
	 * selective change acceptance with dependency tracking.
	 * <p>
	 * This implementation shows:
	 * </p>
	 * <ul>
	 * <li>Creating multiple independent changes</li>
	 * <li>Marking changes as independent or dependent</li>
	 * <li>Establishing dependency relationships between changes</li>
	 * </ul>
	 * <p>
	 * Uncomment to enable this example.
	 * </p>
	 */
	// @Override
	// public List<IndependentChange> createIndependentFixes(List<CleanUpContext> contexts) throws CoreException {
	//     List<IndependentChange> changes = new ArrayList<>();
	//
	//     // Example: Create independent changes for each file
	//     for (CleanUpContext context : contexts) {
	//         ICompilationUnit cu = context.getCompilationUnit();
	//         
	//         // Create a change for this compilation unit
	//         CompilationUnitChange change = createChangeForUnit(cu);
	//         if (change != null) {
	//             // Wrap it as an independent change
	//             // true = this change can be rejected without affecting others
	//             IndependentChangeImpl indepChange = new IndependentChangeImpl(change, true);
	//             changes.add(indepChange);
	//         }
	//     }
	//
	//     // Example: Create a dependent change that relies on previous changes
	//     if (!changes.isEmpty()) {
	//         // Suppose we have a change that depends on the first change
	//         CompilationUnitChange dependentChange = createDependentChange(contexts);
	//         if (dependentChange != null) {
	//             // false = this change is not independent (has dependencies)
	//             IndependentChangeImpl depChange = new IndependentChangeImpl(dependentChange, false);
	//             
	//             // Mark the dependency: this change depends on the first change
	//             if (changes.get(0) instanceof IndependentChangeImpl) {
	//                 ((IndependentChangeImpl) changes.get(0)).addDependentChange(depChange);
	//             }
	//             
	//             changes.add(depChange);
	//         }
	//     }
	//
	//     return changes;
	// }

	/**
	 * Example demonstrating how to implement recomputeAfterSelection() to support
	 * fresh AST recomputation after user selection.
	 * <p>
	 * This is useful when the validity of later changes depends on earlier changes
	 * being applied. The framework will:
	 * </p>
	 * <ol>
	 * <li>Apply the selected changes</li>
	 * <li>Generate fresh ASTs</li>
	 * <li>Call this method to recompute remaining changes</li>
	 * </ol>
	 * <p>
	 * Uncomment to enable this example.
	 * </p>
	 */
	// @Override
	// public CompositeChange recomputeAfterSelection(List<IndependentChange> selectedChanges,
	//         List<CleanUpContext> freshContexts) throws CoreException {
	//     
	//     // Analyze the fresh contexts to recompute remaining changes
	//     CompositeChange recomputed = new CompositeChange("Recomputed Changes");
	//     
	//     for (CleanUpContext context : freshContexts) {
	//         // With fresh AST, determine what changes are still needed
	//         CompilationUnitChange change = createChangeForUnit(context.getCompilationUnit());
	//         if (change != null) {
	//             recomputed.add(change);
	//         }
	//     }
	//     
	//     return recomputed.getChildren().length > 0 ? recomputed : null;
	// }

	/**
	 * Example demonstrating when to return true for requiresFreshASTAfterSelection().
	 * <p>
	 * Return true if:
	 * </p>
	 * <ul>
	 * <li>Later changes depend on earlier changes being applied first</li>
	 * <li>Change validity needs to be rechecked after modifications</li>
	 * <li>You need to see the actual code state after changes</li>
	 * </ul>
	 * <p>
	 * Note: This has a performance cost as it requires re-parsing and recomputing.
	 * </p>
	 * <p>
	 * Uncomment to enable this example.
	 * </p>
	 */
	// @Override
	// public boolean requiresFreshASTAfterSelection() {
	//     // Return true if this cleanup needs to recompute changes after user selection
	//     // with fresh ASTs. This is useful when changes interact with each other.
	//     return false;
	// }

	@SuppressWarnings("unused")
	private CompilationUnitChange createDependentChange(List<CleanUpContext> contexts) throws CoreException {
		// Example placeholder for creating a change that depends on other changes
		return null;
	}
}
