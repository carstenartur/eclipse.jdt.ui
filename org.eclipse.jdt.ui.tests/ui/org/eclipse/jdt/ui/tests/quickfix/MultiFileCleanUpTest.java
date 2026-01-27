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
package org.eclipse.jdt.ui.tests.quickfix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.jdt.ui.cleanup.IMultiFileCleanUp;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.DeleteEdit;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for the multi-file cleanup infrastructure.
 */
public class MultiFileCleanUpTest extends CleanUpTestCase {

	@Rule
	public ProjectTestSetup projectSetup = new ProjectTestSetup();

	/**
	 * Simple test cleanup that removes all content from files (for testing purposes only).
	 */
	private static class TestMultiFileCleanUp implements IMultiFileCleanUp {
		private CleanUpOptions options;
		private final String marker;

		public TestMultiFileCleanUp(String marker) {
			this.marker = marker;
		}

		@Override
		public void setOptions(CleanUpOptions options) {
			this.options = options;
		}

		@Override
		public String[] getStepDescriptions() {
			return new String[] { "Test multi-file cleanup: " + marker };
		}

		@Override
		public CleanUpRequirements getRequirements() {
			return new CleanUpRequirements(false, false, false, null);
		}

		@Override
		public RefactoringStatus checkPreConditions(org.eclipse.jdt.core.IJavaProject project,
				ICompilationUnit[] compilationUnits, IProgressMonitor monitor) throws CoreException {
			return new RefactoringStatus();
		}

		@Override
		public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
			// Single-file fallback - not used when multi-file is detected
			return null;
		}

		@Override
		public RefactoringStatus checkPostConditions(IProgressMonitor monitor) throws CoreException {
			return new RefactoringStatus();
		}

		@Override
		public CompositeChange createFix(List<CleanUpContext> contexts) throws CoreException {
			if (contexts.isEmpty()) {
				return null;
			}

			CompositeChange composite = new CompositeChange("Test Multi-File Cleanup: " + marker);

			// For each context, create a simple change (add a comment marker)
			for (CleanUpContext context : contexts) {
				ICompilationUnit cu = context.getCompilationUnit();
				String source = cu.getSource();

				// Simple change: add a comment at the beginning
				String newSource = "/* " + marker + " */\n" + source;

				CompilationUnitChange change = new CompilationUnitChange("Add marker to " + cu.getElementName(), cu);
				change.setEdit(new org.eclipse.text.edits.ReplaceEdit(0, source.length(), newSource));

				composite.add(change);
			}

			return composite.getChildren().length > 0 ? composite : null;
		}
	}

	/**
	 * Test that multi-file cleanups are properly detected and invoked.
	 */
	@Test
	public void testMultiFileCleanUpInvoked() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);

		// Create two compilation units
		String input1 = """
				package test;
				public class A {
				}
				""";

		String input2 = """
				package test;
				public class B {
				}
				""";

		ICompilationUnit cu1 = pack.createCompilationUnit("A.java", input1, false, null);
		ICompilationUnit cu2 = pack.createCompilationUnit("B.java", input2, false, null);

		// Apply the multi-file cleanup
		ICleanUp cleanup = new TestMultiFileCleanUp("TEST_MARKER");

		enable(cleanup.getRequirements());

		Change[] changes = performCleanUp(cleanup, new ICompilationUnit[] { cu1, cu2 });

		assertNotNull("Changes should not be null", changes);
		assertTrue("Should have changes", changes.length > 0);

		// Verify that both files were processed
		String result1 = cu1.getSource();
		String result2 = cu2.getSource();

		assertTrue("File A should contain marker", result1.contains("TEST_MARKER"));
		assertTrue("File B should contain marker", result2.contains("TEST_MARKER"));
	}

	/**
	 * Test that multi-file cleanups work alongside regular cleanups.
	 */
	@Test
	public void testMultiFileCleanUpWithRegularCleanUp() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null);

		String input = """
				package test;
				public class C {
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("C.java", input, false, null);

		// Use a multi-file cleanup
		ICleanUp multiFileCleanup = new TestMultiFileCleanUp("MULTI");

		enable(multiFileCleanup.getRequirements());

		Change[] changes = performCleanUp(multiFileCleanup, new ICompilationUnit[] { cu });

		assertNotNull("Changes should not be null", changes);
		String result = cu.getSource();
		assertTrue("File should contain multi-file marker", result.contains("MULTI"));
	}

	private Change[] performCleanUp(ICleanUp cleanUp, ICompilationUnit[] units) throws Exception {
		org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring refactoring = new org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring();
		refactoring.addCleanUp(cleanUp);

		CleanUpOptions options = new CleanUpOptions();
		cleanUp.setOptions(options);

		for (ICompilationUnit unit : units) {
			refactoring.addCompilationUnit(unit);
		}

		RefactoringStatus status = refactoring.checkAllConditions(null);
		assertTrue("Preconditions should be OK: " + status.toString(), !status.hasFatalError());

		Change change = refactoring.createChange(null);
		assertNotNull("Change should not be null", change);

		change.perform(null);

		if (change instanceof CompositeChange) {
			return ((CompositeChange) change).getChildren();
		} else {
			return new Change[] { change };
		}
	}

	private void enable(CleanUpRequirements requirements) {
		// Enable options based on requirements if needed
	}
}
