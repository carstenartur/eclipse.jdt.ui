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

		CompositeChange compositeChange= new CompositeChange("Example Multi-File Cleanup");
		List<CompilationUnitChange> changes= new ArrayList<>();

		// Analyze all contexts together to demonstrate multi-file coordination
		for (CleanUpContext context : contexts) {
			ICompilationUnit cu= context.getCompilationUnit();

			try {
				// Example: Check if this is a Java file we want to process
				if (cu.getElementName().endsWith(".java")) {
					// In a real implementation, this would analyze the code
					// and create appropriate changes. For now, this is just
					// a placeholder to demonstrate the infrastructure.

					// Uncomment to create actual changes:
					// CompilationUnitChange change = createChangeForUnit(cu);
					// if (change != null) {
					//     changes.add(change);
					//     compositeChange.add(change);
					// }
				}
			} catch (CoreException e) {
				// Log and continue with other files
				// JavaPlugin.log(e);
			}
		}

		// Return the composite change only if there are actual changes
		return compositeChange.getChildren().length > 0 ? compositeChange : null;
	}

	@Override
	public String[] getStepDescriptions() {
		if (isEnabled(CLEANUP_MULTI_FILE_EXAMPLE)) {
			return new String[] { "Example multi-file cleanup (proof of concept)" };
		}
		return new String[0];
	}

	/**
	 * Helper method to check if a cleanup option is enabled.
	 */
	private boolean isEnabled(String key) {
		return getOptions() != null && getOptions().isEnabled(key);
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
}
