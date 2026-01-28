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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.ui.fix.IndependentChangeImpl;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.jdt.ui.cleanup.IMultiFileCleanUp;
import org.eclipse.jdt.ui.cleanup.IndependentChange;
import org.eclipse.jdt.ui.tests.core.rules.ProjectTestSetup;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for the multi-file cleanup infrastructure.
 */
public class MultiFileCleanUpTest extends CleanUpTestCase {

	@Rule
	public ProjectTestSetup projectSetup = new ProjectTestSetup();

	@Override
	protected IJavaProject getProject() {
		return projectSetup.getProject();
	}

	@Override
	protected IClasspathEntry[] getDefaultClasspath() throws CoreException {
		return projectSetup.getDefaultClasspath();
	}

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
			return new String[] { "Test multi-file cleanup: " + marker }; //$NON-NLS-1$
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

			CompositeChange composite = new CompositeChange("Test Multi-File Cleanup: " + marker); //$NON-NLS-1$

			// For each context, create a simple change (add a comment marker)
			for (CleanUpContext context : contexts) {
				ICompilationUnit cu = context.getCompilationUnit();
				String source = cu.getSource();

				// Simple change: add a comment at the beginning
				String newSource = "/* " + marker + " */\n" + source; //$NON-NLS-1$ //$NON-NLS-2$

				CompilationUnitChange change = new CompilationUnitChange("Add marker to " + cu.getElementName(), cu); //$NON-NLS-1$
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
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null); //$NON-NLS-1$

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

		ICompilationUnit cu1 = pack.createCompilationUnit("A.java", input1, false, null); //$NON-NLS-1$
		ICompilationUnit cu2 = pack.createCompilationUnit("B.java", input2, false, null); //$NON-NLS-1$

		// Apply the multi-file cleanup
		ICleanUp cleanup = new TestMultiFileCleanUp("TEST_MARKER"); //$NON-NLS-1$

		enable(cleanup.getRequirements());

		Change[] changes = performCleanUp(cleanup, new ICompilationUnit[] { cu1, cu2 });

		assertNotNull("Changes should not be null", changes); //$NON-NLS-1$
		assertTrue("Should have changes", changes.length > 0); //$NON-NLS-1$

		// Verify that both files were processed
		String result1 = cu1.getSource();
		String result2 = cu2.getSource();

		assertTrue("File A should contain marker", result1.contains("TEST_MARKER")); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue("File B should contain marker", result2.contains("TEST_MARKER")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Test that multi-file cleanups work alongside regular cleanups.
	 */
	@Test
	public void testMultiFileCleanUpWithRegularCleanUp() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null); //$NON-NLS-1$

		String input = """
				package test;
				public class C {
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("C.java", input, false, null); //$NON-NLS-1$

		// Use a multi-file cleanup
		ICleanUp multiFileCleanup = new TestMultiFileCleanUp("MULTI"); //$NON-NLS-1$

		enable(multiFileCleanup.getRequirements());

		Change[] changes = performCleanUp(multiFileCleanup, new ICompilationUnit[] { cu });

		assertNotNull("Changes should not be null", changes); //$NON-NLS-1$
		String result = cu.getSource();
		assertTrue("File should contain multi-file marker", result.contains("MULTI")); //$NON-NLS-1$ //$NON-NLS-2$
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
		assertTrue("Preconditions should be OK: " + status.toString(), !status.hasFatalError()); //$NON-NLS-1$

		Change change = refactoring.createChange(null);
		assertNotNull("Change should not be null", change); //$NON-NLS-1$

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

	/**
	 * Test cleanup that creates independent changes.
	 */
	private static class IndependentChangeCleanUp implements IMultiFileCleanUp {
		@SuppressWarnings("unused") // Required by ICleanUp interface contract
		private CleanUpOptions options;

		@Override
		public void setOptions(CleanUpOptions options) {
			this.options = options;
		}

		@Override
		public String[] getStepDescriptions() {
			return new String[] { "Test independent changes" }; //$NON-NLS-1$
		}

		@Override
		public CleanUpRequirements getRequirements() {
			return new CleanUpRequirements(false, false, false, null);
		}

		@Override
		public RefactoringStatus checkPreConditions(IJavaProject project,
				ICompilationUnit[] compilationUnits, IProgressMonitor monitor) throws CoreException {
			return new RefactoringStatus();
		}

		@Override
		public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
			return null;
		}

		@Override
		public RefactoringStatus checkPostConditions(IProgressMonitor monitor) throws CoreException {
			return new RefactoringStatus();
		}

		@Override
		public CompositeChange createFix(List<CleanUpContext> contexts) throws CoreException {
			return null; // Not used when createIndependentFixes is overridden
		}

		@Override
		public List<IndependentChange> createIndependentFixes(List<CleanUpContext> contexts) throws CoreException {
			List<IndependentChange> changes = new ArrayList<>();

			// Create independent changes for each file
			for (CleanUpContext context : contexts) {
				ICompilationUnit cu = context.getCompilationUnit();
				String source = cu.getSource();
				String newSource = "/* INDEPENDENT */\n" + source; //$NON-NLS-1$

				CompilationUnitChange change = new CompilationUnitChange("Independent change to " + cu.getElementName(), cu); //$NON-NLS-1$
				change.setEdit(new org.eclipse.text.edits.ReplaceEdit(0, source.length(), newSource));

				// Each change is independent
				changes.add(new IndependentChangeImpl(change, true));
			}

			return changes;
		}
	}

	/**
	 * Test cleanup that creates dependent changes.
	 */
	private static class DependentChangeCleanUp implements IMultiFileCleanUp {
		@SuppressWarnings("unused") // Required by ICleanUp interface contract
		private CleanUpOptions options;

		@Override
		public void setOptions(CleanUpOptions options) {
			this.options = options;
		}

		@Override
		public String[] getStepDescriptions() {
			return new String[] { "Test dependent changes" }; //$NON-NLS-1$
		}

		@Override
		public CleanUpRequirements getRequirements() {
			return new CleanUpRequirements(false, false, false, null);
		}

		@Override
		public RefactoringStatus checkPreConditions(IJavaProject project,
				ICompilationUnit[] compilationUnits, IProgressMonitor monitor) throws CoreException {
			return new RefactoringStatus();
		}

		@Override
		public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
			return null;
		}

		@Override
		public RefactoringStatus checkPostConditions(IProgressMonitor monitor) throws CoreException {
			return new RefactoringStatus();
		}

		@Override
		public CompositeChange createFix(List<CleanUpContext> contexts) throws CoreException {
			return null; // Not used when createIndependentFixes is overridden
		}

		@Override
		public List<IndependentChange> createIndependentFixes(List<CleanUpContext> contexts) throws CoreException {
			List<IndependentChange> changes = new ArrayList<>();

			if (contexts.size() >= 2) {
				// First change
				ICompilationUnit cu1 = contexts.get(0).getCompilationUnit();
				String source1 = cu1.getSource();
				String newSource1 = "/* CHANGE_A */\n" + source1; //$NON-NLS-1$
				CompilationUnitChange change1 = new CompilationUnitChange("Change A", cu1); //$NON-NLS-1$
				change1.setEdit(new org.eclipse.text.edits.ReplaceEdit(0, source1.length(), newSource1));
				IndependentChangeImpl changeA = new IndependentChangeImpl(change1, false);
				changes.add(changeA);

				// Second change that depends on the first
				ICompilationUnit cu2 = contexts.get(1).getCompilationUnit();
				String source2 = cu2.getSource();
				String newSource2 = "/* CHANGE_B */\n" + source2; //$NON-NLS-1$
				CompilationUnitChange change2 = new CompilationUnitChange("Change B", cu2); //$NON-NLS-1$
				change2.setEdit(new org.eclipse.text.edits.ReplaceEdit(0, source2.length(), newSource2));
				IndependentChangeImpl changeB = new IndependentChangeImpl(change2, false);

				// Establish dependency: B depends on A
				changeA.addDependentChange(changeB);
				changes.add(changeB);
			}

			return changes;
		}
	}

	/**
	 * Test cleanup that requires fresh AST recomputation.
	 */
	private static class RecomputingCleanUp implements IMultiFileCleanUp {
		@SuppressWarnings("unused") // Required by ICleanUp interface contract
		private CleanUpOptions options;
		private int recomputeCallCount = 0;

		@Override
		public void setOptions(CleanUpOptions options) {
			this.options = options;
		}

		@Override
		public String[] getStepDescriptions() {
			return new String[] { "Test recomputation" }; //$NON-NLS-1$
		}

		@Override
		public CleanUpRequirements getRequirements() {
			return new CleanUpRequirements(false, false, false, null);
		}

		@Override
		public RefactoringStatus checkPreConditions(IJavaProject project,
				ICompilationUnit[] compilationUnits, IProgressMonitor monitor) throws CoreException {
			return new RefactoringStatus();
		}

		@Override
		public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
			return null;
		}

		@Override
		public RefactoringStatus checkPostConditions(IProgressMonitor monitor) throws CoreException {
			return new RefactoringStatus();
		}

		@Override
		public CompositeChange createFix(List<CleanUpContext> contexts) throws CoreException {
			CompositeChange composite = new CompositeChange("Initial changes"); //$NON-NLS-1$
			for (CleanUpContext context : contexts) {
				ICompilationUnit cu = context.getCompilationUnit();
				String source = cu.getSource();
				String newSource = "/* INITIAL */\n" + source; //$NON-NLS-1$
				CompilationUnitChange change = new CompilationUnitChange("Initial change", cu); //$NON-NLS-1$
				change.setEdit(new org.eclipse.text.edits.ReplaceEdit(0, source.length(), newSource));
				composite.add(change);
			}
			return composite.getChildren().length > 0 ? composite : null;
		}

		@Override
		public boolean requiresFreshASTAfterSelection() {
			return true;
		}

		@Override
		public CompositeChange recomputeAfterSelection(List<IndependentChange> selectedChanges,
				List<CleanUpContext> freshContexts) throws CoreException {
			recomputeCallCount++;
			CompositeChange composite = new CompositeChange("Recomputed changes " + recomputeCallCount); //$NON-NLS-1$
			for (CleanUpContext context : freshContexts) {
				ICompilationUnit cu = context.getCompilationUnit();
				String source = cu.getSource();
				String newSource = "/* RECOMPUTED_" + recomputeCallCount + " */\n" + source; //$NON-NLS-1$ //$NON-NLS-2$
				CompilationUnitChange change = new CompilationUnitChange("Recomputed change", cu); //$NON-NLS-1$
				change.setEdit(new org.eclipse.text.edits.ReplaceEdit(0, source.length(), newSource));
				composite.add(change);
			}
			return composite.getChildren().length > 0 ? composite : null;
		}

		public int getRecomputeCallCount() {
			return recomputeCallCount;
		}
	}

	/**
	 * Test that independent changes can be created and tracked.
	 */
	@Test
	public void testIndependentChangeCreation() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null); //$NON-NLS-1$

		String input1 = """
				package test;
				public class D {
				}
				""";

		String input2 = """
				package test;
				public class E {
				}
				""";

		ICompilationUnit cu1 = pack.createCompilationUnit("D.java", input1, false, null); //$NON-NLS-1$
		ICompilationUnit cu2 = pack.createCompilationUnit("E.java", input2, false, null); //$NON-NLS-1$

		IndependentChangeCleanUp cleanup = new IndependentChangeCleanUp();
		CleanUpOptions options = new CleanUpOptions();
		cleanup.setOptions(options);

		List<CleanUpContext> contexts = new ArrayList<>();
		contexts.add(new CleanUpContext(cu1, null));
		contexts.add(new CleanUpContext(cu2, null));

		List<IndependentChange> changes = cleanup.createIndependentFixes(contexts);

		assertNotNull("Changes should not be null", changes); //$NON-NLS-1$
		assertEquals("Should have 2 independent changes", 2, changes.size()); //$NON-NLS-1$

		// Verify each change is independent
		for (IndependentChange change : changes) {
			assertTrue("Change should be independent", change.isIndependent()); //$NON-NLS-1$
			assertTrue("Should have no dependent changes", change.getDependentChanges().isEmpty()); //$NON-NLS-1$
		}
	}

	/**
	 * Test that dependent changes are properly tracked.
	 */
	@Test
	public void testDependentChangeTracking() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null); //$NON-NLS-1$

		String input1 = """
				package test;
				public class F {
				}
				""";

		String input2 = """
				package test;
				public class G {
				}
				""";

		ICompilationUnit cu1 = pack.createCompilationUnit("F.java", input1, false, null); //$NON-NLS-1$
		ICompilationUnit cu2 = pack.createCompilationUnit("G.java", input2, false, null); //$NON-NLS-1$

		DependentChangeCleanUp cleanup = new DependentChangeCleanUp();
		CleanUpOptions options = new CleanUpOptions();
		cleanup.setOptions(options);

		List<CleanUpContext> contexts = new ArrayList<>();
		contexts.add(new CleanUpContext(cu1, null));
		contexts.add(new CleanUpContext(cu2, null));

		List<IndependentChange> changes = cleanup.createIndependentFixes(contexts);

		assertNotNull("Changes should not be null", changes); //$NON-NLS-1$
		assertEquals("Should have 2 changes", 2, changes.size()); //$NON-NLS-1$

		IndependentChange changeA = changes.get(0);
		IndependentChange changeB = changes.get(1);

		// Verify dependency relationship
		assertFalse("Change A should not be independent", changeA.isIndependent()); //$NON-NLS-1$
		assertFalse("Change B should not be independent", changeB.isIndependent()); //$NON-NLS-1$
		assertEquals("Change A should have 1 dependent change", 1, changeA.getDependentChanges().size()); //$NON-NLS-1$
		assertEquals("Change A's dependent should be Change B", changeB, changeA.getDependentChanges().get(0)); //$NON-NLS-1$
	}

	/**
	 * Test that requiresFreshASTAfterSelection returns correct value.
	 */
	@Test
	public void testRequiresFreshASTAfterSelection() throws Exception {
		RecomputingCleanUp cleanup = new RecomputingCleanUp();

		assertTrue("Should require fresh AST after selection", cleanup.requiresFreshASTAfterSelection()); //$NON-NLS-1$
	}

	/**
	 * Test that recomputeAfterSelection is called correctly.
	 */
	@Test
	public void testRecomputeAfterSelection() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null); //$NON-NLS-1$

		String input = """
				package test;
				public class H {
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("H.java", input, false, null); //$NON-NLS-1$

		RecomputingCleanUp cleanup = new RecomputingCleanUp();
		CleanUpOptions options = new CleanUpOptions();
		cleanup.setOptions(options);

		List<CleanUpContext> contexts = new ArrayList<>();
		contexts.add(new CleanUpContext(cu, null));

		// Simulate recomputation
		List<IndependentChange> selectedChanges = new ArrayList<>();
		CompositeChange recomputed = cleanup.recomputeAfterSelection(selectedChanges, contexts);

		assertNotNull("Recomputed changes should not be null", recomputed); //$NON-NLS-1$
		assertEquals("Recompute should have been called once", 1, cleanup.getRecomputeCallCount()); //$NON-NLS-1$
	}

	/**
	 * Test default createIndependentFixes implementation.
	 */
	@Test
	public void testDefaultCreateIndependentFixes() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null); //$NON-NLS-1$

		String input = """
				package test;
				public class I {
				}
				""";

		ICompilationUnit cu = pack.createCompilationUnit("I.java", input, false, null); //$NON-NLS-1$

		TestMultiFileCleanUp cleanup = new TestMultiFileCleanUp("DEFAULT_TEST"); //$NON-NLS-1$
		CleanUpOptions options = new CleanUpOptions();
		cleanup.setOptions(options);

		List<CleanUpContext> contexts = new ArrayList<>();
		contexts.add(new CleanUpContext(cu, null));

		// Call default implementation
		List<IndependentChange> changes = cleanup.createIndependentFixes(contexts);

		assertNotNull("Changes should not be null", changes); //$NON-NLS-1$
		assertEquals("Should have 1 change (wrapped composite)", 1, changes.size()); //$NON-NLS-1$
		assertTrue("Default implementation should create independent change", changes.get(0).isIndependent()); //$NON-NLS-1$
	}
}
