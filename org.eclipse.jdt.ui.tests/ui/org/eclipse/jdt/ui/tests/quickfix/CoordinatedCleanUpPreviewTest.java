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
package org.eclipse.jdt.ui.tests.quickfix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.eclipse.jdt.testplugin.JavaProjectHelper;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextEditBasedChange;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;

import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.internal.corext.fix.CoordinatedCleanUpChange;

import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.jdt.ui.tests.core.rules.ProjectTestSetup;

import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;

/** Tests atomic presentation and execution of coordinated cleanup candidates. */
public class CoordinatedCleanUpPreviewTest extends QuickFixTest {

	@Rule
	public ProjectTestSetup projectSetup= new ProjectTestSetup();

	private IJavaProject fProject;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setUp() throws Exception {
		fProject= projectSetup.getProject();
		fSourceFolder= JavaProjectHelper.addSourceContainer(fProject, "src"); //$NON-NLS-1$
	}

	@After
	public void tearDown() throws Exception {
		JavaProjectHelper.clear(fProject, projectSetup.getDefaultClasspath());
	}

	@Test
	public void testCoordinatedFilesBecomeOneNodeWhileIndependentFileRemainsSelectable() throws Exception {
		Fixture fixture= createFixture();
		CoordinatedScopeCleanUp coordinated= new CoordinatedScopeCleanUp("candidate-a", //$NON-NLS-1$
				"Convert the coordinated API", "// coordinated\n", false, fixture.first(), fixture.second()); //$NON-NLS-1$ //$NON-NLS-2$
		IndependentFileCleanUp independent= new IndependentFileCleanUp(fixture.independent());

		CleanUpRefactoring refactoring= createRefactoring(List.of(fixture.first(), fixture.independent()),
				coordinated, independent);
		RefactoringStatus status= refactoring.checkAllConditions(new NullProgressMonitor());
		assertFalse(status.toString(), status.hasError());

		CompositeChange root= (CompositeChange) refactoring.createChange(null);
		assertEquals(2, root.getChildren().length);

		CoordinatedCleanUpChange atomic= findCoordinated(root);
		assertEquals(List.of("candidate-a"), atomic.getCandidateIds()); //$NON-NLS-1$
		assertEquals(Set.of(fixture.first(), fixture.second()), Set.copyOf(atomic.getCompilationUnits()));
		assertEquals(2, atomic.getChanges().length);
		assertEquals("Convert the coordinated API", atomic.getName()); //$NON-NLS-1$
		assertTrue(atomic.getDescription().contains("all affected source files")); //$NON-NLS-1$

		Change independentChange= findIndependent(root);
		assertTrue(independentChange instanceof TextEditBasedChange);
		assertEquals(fixture.independent(), independentChange.getAdapter(ICompilationUnit.class));

		atomic.setEnabled(false);
		for (Change child : atomic.getChanges()) {
			assertFalse(child.isEnabled());
		}
		assertTrue(independentChange.isEnabled());

		atomic.setEnabled(true);
		for (Change child : atomic.getChanges()) {
			assertTrue(child.isEnabled());
		}
	}

	@Test
	public void testPartialNestedSelectionFailsClosedBeforeResourceModification() throws Exception {
		Fixture fixture= createFixture();
		CoordinatedScopeCleanUp coordinated= new CoordinatedScopeCleanUp("candidate-a", //$NON-NLS-1$
				"Convert the coordinated API", "// coordinated\n", false, fixture.first(), fixture.second()); //$NON-NLS-1$ //$NON-NLS-2$
		CleanUpRefactoring refactoring= createRefactoring(List.of(fixture.first()), coordinated);
		assertFalse(refactoring.checkAllConditions(new NullProgressMonitor()).hasError());

		CoordinatedCleanUpChange atomic= findCoordinated((CompositeChange) refactoring.createChange(null));
		atomic.initializeValidationData(new NullProgressMonitor());
		atomic.getChanges()[0].setEnabled(false);

		RefactoringStatus validity= atomic.isValid(new NullProgressMonitor());
		assertTrue(validity.toString(), validity.hasFatalError());
		try {
			atomic.perform(new NullProgressMonitor());
			fail("A partially selected coordinated cleanup must not be performed"); //$NON-NLS-1$
		} catch (CoreException expected) {
			assertTrue(expected.getStatus().getMessage().contains("atomic unit")); //$NON-NLS-1$
		} finally {
			atomic.dispose();
		}

		assertEquals(fixture.firstSource(), fixture.first().getSource());
		assertEquals(fixture.secondSource(), fixture.second().getSource());
	}

	@Test
	public void testAtomicApplyAndUndoRestoresEveryFile() throws Exception {
		Fixture fixture= createFixture();
		CoordinatedScopeCleanUp coordinated= new CoordinatedScopeCleanUp("candidate-a", //$NON-NLS-1$
				"Convert the coordinated API", "// coordinated\n", false, fixture.first(), fixture.second()); //$NON-NLS-1$ //$NON-NLS-2$
		CleanUpRefactoring refactoring= createRefactoring(List.of(fixture.first()), coordinated);
		assertFalse(refactoring.checkAllConditions(new NullProgressMonitor()).hasError());

		CoordinatedCleanUpChange atomic= findCoordinated((CompositeChange) refactoring.createChange(null));
		atomic.initializeValidationData(new NullProgressMonitor());
		assertFalse(atomic.isValid(new NullProgressMonitor()).hasError());

		Change undo= atomic.perform(new NullProgressMonitor());
		assertNotNull(undo);
		assertEquals("// coordinated\n" + fixture.firstSource(), fixture.first().getSource()); //$NON-NLS-1$
		assertEquals("// coordinated\n" + fixture.secondSource(), fixture.second().getSource()); //$NON-NLS-1$

		undo.initializeValidationData(new NullProgressMonitor());
		assertFalse(undo.isValid(new NullProgressMonitor()).hasError());
		undo.perform(new NullProgressMonitor());
		undo.dispose();

		assertEquals(fixture.firstSource(), fixture.first().getSource());
		assertEquals(fixture.secondSource(), fixture.second().getSource());
	}

	@Test
	public void testDisjointCandidatesFromOneCleanupRemainIndependentlySelectable() throws Exception {
		Fixture fixture= createFixture();
		DisjointCoordinatedCleanUp cleanUp= new DisjointCoordinatedCleanUp(
				new Candidate("candidate-a", "First migration", fixture.first(), "// first\n"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				new Candidate("candidate-b", "Second migration", fixture.second(), "// second\n")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		CleanUpRefactoring refactoring= createRefactoring(List.of(fixture.first()), cleanUp);
		assertFalse(refactoring.checkAllConditions(new NullProgressMonitor()).hasError());

		CompositeChange root= (CompositeChange) refactoring.createChange(null);
		assertEquals(2, root.getChildren().length);
		Set<Set<String>> candidateIds= java.util.Arrays.stream(root.getChildren())
				.map(CoordinatedCleanUpChange.class::cast)
				.map(change -> Set.copyOf(change.getCandidateIds()))
				.collect(java.util.stream.Collectors.toSet());
		assertEquals(Set.of(Set.of("candidate-a"), Set.of("candidate-b")), candidateIds); //$NON-NLS-1$ //$NON-NLS-2$

		CoordinatedCleanUpChange first= (CoordinatedCleanUpChange)root.getChildren()[0];
		CoordinatedCleanUpChange second= (CoordinatedCleanUpChange)root.getChildren()[1];
		first.setEnabled(false);
		assertFalse(first.isEnabled());
		assertTrue(second.isEnabled());
	}

	@Test
	public void testOverlappingCandidatesAreMergedIntoOneSafeSelectionUnit() throws Exception {
		Fixture fixture= createFixture();
		CoordinatedScopeCleanUp firstCandidate= new CoordinatedScopeCleanUp("candidate-a", //$NON-NLS-1$
				"First coordinated migration", "// first\n", false, fixture.first(), fixture.second()); //$NON-NLS-1$ //$NON-NLS-2$
		CoordinatedScopeCleanUp secondCandidate= new CoordinatedScopeCleanUp("candidate-b", //$NON-NLS-1$
				"Second coordinated migration", "\n// second", true, fixture.first()); //$NON-NLS-1$ //$NON-NLS-2$

		CleanUpRefactoring refactoring= createRefactoring(List.of(fixture.first()), firstCandidate, secondCandidate);
		assertFalse(refactoring.checkAllConditions(new NullProgressMonitor()).hasError());

		CompositeChange root= (CompositeChange) refactoring.createChange(null);
		assertEquals(1, root.getChildren().length);
		CoordinatedCleanUpChange atomic= findCoordinated(root);
		assertEquals(Set.of("candidate-a", "candidate-b"), Set.copyOf(atomic.getCandidateIds())); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(Set.of(fixture.first(), fixture.second()), Set.copyOf(atomic.getCompilationUnits()));
	}

	private Fixture createFixture() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null); //$NON-NLS-1$
		String firstSource= "package test1;\npublic class First {}\n"; //$NON-NLS-1$
		String secondSource= "package test1;\npublic class Second {}\n"; //$NON-NLS-1$
		String independentSource= "package test1;\npublic class Independent {}\n"; //$NON-NLS-1$
		ICompilationUnit first= pack.createCompilationUnit("First.java", firstSource, false, null); //$NON-NLS-1$
		ICompilationUnit second= pack.createCompilationUnit("Second.java", secondSource, false, null); //$NON-NLS-1$
		ICompilationUnit independent= pack.createCompilationUnit("Independent.java", independentSource, false, null); //$NON-NLS-1$
		return new Fixture(first, second, independent, firstSource, secondSource);
	}

	private static CleanUpRefactoring createRefactoring(List<ICompilationUnit> initialUnits,
			AbstractCleanUp... cleanUps) {
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		for (ICompilationUnit unit : initialUnits) {
			refactoring.addCompilationUnit(unit);
		}
		for (AbstractCleanUp cleanUp : cleanUps) {
			refactoring.addCleanUp(cleanUp);
		}
		return refactoring;
	}

	private static CoordinatedCleanUpChange findCoordinated(CompositeChange root) {
		for (Change child : root.getChildren()) {
			if (child instanceof CoordinatedCleanUpChange coordinated) {
				return coordinated;
			}
		}
		throw new AssertionError("No coordinated cleanup change found"); //$NON-NLS-1$
	}

	private static Change findIndependent(CompositeChange root) {
		for (Change child : root.getChildren()) {
			if (!(child instanceof CoordinatedCleanUpChange)) {
				return child;
			}
		}
		throw new AssertionError("No independent cleanup change found"); //$NON-NLS-1$
	}

	private record Fixture(ICompilationUnit first, ICompilationUnit second, ICompilationUnit independent,
			String firstSource, String secondSource) {
	}

	public static final class CoordinatedScopeCleanUp extends AbstractCleanUp {
		private final String id;
		private final String name;
		private final String insertedText;
		private final boolean append;
		private final List<ICompilationUnit> affectedUnits;

		CoordinatedScopeCleanUp(String id, String name, String insertedText, boolean append,
				ICompilationUnit... affectedUnits) {
			this.id= id;
			this.name= name;
			this.insertedText= insertedText;
			this.append= append;
			this.affectedUnits= List.of(affectedUnits);
		}

		public Collection<ICompilationUnit> expandCleanUpScope(IJavaProject project,
				Collection<ICompilationUnit> currentScope, IProgressMonitor monitor) {
			return affectedUnits;
		}

		public Map<String, ?> getCoordinatedCleanUpPreview(IJavaProject project) {
			return Map.of(
					"id", id, //$NON-NLS-1$
					"name", name, //$NON-NLS-1$
					"description", "This migration is valid only when all affected source files are applied together.", //$NON-NLS-1$ //$NON-NLS-2$
					"compilationUnits", affectedUnits, //$NON-NLS-1$
					"details", List.of("All affected declarations and references were resolved.")); //$NON-NLS-1$ //$NON-NLS-2$
		}

		@Override
		public ICleanUpFix createFix(CleanUpContext context) throws CoreException {
			ICompilationUnit unit= context.getCompilationUnit().getPrimary();
			if (!affectedUnits.contains(unit)) {
				return null;
			}
			int offset= append ? unit.getSource().length() : 0;
			return progressMonitor -> {
				CompilationUnitChange change= new CompilationUnitChange(name, unit);
				MultiTextEdit root= new MultiTextEdit();
				root.addChild(new InsertEdit(offset, insertedText));
				change.setEdit(root);
				return change;
			};
		}
	}

	private record Candidate(String id, String name, ICompilationUnit unit, String text) {
	}

	public static final class DisjointCoordinatedCleanUp extends AbstractCleanUp {
		private final List<Candidate> candidates;

		DisjointCoordinatedCleanUp(Candidate... candidates) {
			this.candidates= List.of(candidates);
		}

		public Collection<ICompilationUnit> expandCleanUpScope(IJavaProject project,
				Collection<ICompilationUnit> currentScope, IProgressMonitor monitor) {
			return candidates.stream().map(Candidate::unit).toList();
		}

		public Collection<Map<String, ?>> getCoordinatedCleanUpPreview(IJavaProject project) {
			return candidates.stream().map(candidate -> Map.<String, Object>of(
					"id", candidate.id(), //$NON-NLS-1$
					"name", candidate.name(), //$NON-NLS-1$
					"description", "One independently selectable coordinated candidate.", //$NON-NLS-1$ //$NON-NLS-2$
					"compilationUnits", List.of(candidate.unit()), //$NON-NLS-1$
					"details", List.of("The candidate scope is closed."))) //$NON-NLS-1$ //$NON-NLS-2$
					.toList();
		}

		@Override
		public ICleanUpFix createFix(CleanUpContext context) {
			ICompilationUnit unit= context.getCompilationUnit().getPrimary();
			Candidate candidate= candidates.stream().filter(value -> value.unit().equals(unit)).findFirst().orElse(null);
			if (candidate == null) {
				return null;
			}
			return progressMonitor -> {
				CompilationUnitChange change= new CompilationUnitChange(candidate.name(), unit);
				MultiTextEdit root= new MultiTextEdit();
				root.addChild(new InsertEdit(0, candidate.text()));
				change.setEdit(root);
				return change;
			};
		}
	}

	private static final class IndependentFileCleanUp extends AbstractCleanUp {
		private final ICompilationUnit affectedUnit;

		IndependentFileCleanUp(ICompilationUnit affectedUnit) {
			this.affectedUnit= affectedUnit;
		}

		@Override
		public ICleanUpFix createFix(CleanUpContext context) {
			ICompilationUnit unit= context.getCompilationUnit().getPrimary();
			if (!affectedUnit.equals(unit)) {
				return null;
			}
			return progressMonitor -> {
				CompilationUnitChange change= new CompilationUnitChange("Independent local cleanup", unit); //$NON-NLS-1$
				MultiTextEdit root= new MultiTextEdit();
				root.addChild(new InsertEdit(0, "// independent\n")); //$NON-NLS-1$
				change.setEdit(root);
				return change;
			};
		}
	}
}
