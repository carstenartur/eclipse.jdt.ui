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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.List;
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

import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.jdt.ui.tests.core.rules.ProjectTestSetup;

import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;

/** Tests target-scope expansion before the established cleanup lifecycle. */
public class MultiFileCleanUpScopeExpansionTest extends QuickFixTest {

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
	public void testRelatedCompilationUnitParticipatesInPlanPreviewAndUndoTree() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null); //$NON-NLS-1$
		String firstSource= "package test1;\npublic class First {}\n"; //$NON-NLS-1$
		String secondSource= "package test1;\npublic class Second {}\n"; //$NON-NLS-1$
		ICompilationUnit first= pack.createCompilationUnit("First.java", firstSource, false, null); //$NON-NLS-1$
		ICompilationUnit second= pack.createCompilationUnit("Second.java", secondSource, false, null); //$NON-NLS-1$

		ScopeExpandingCleanUp cleanUp= new ScopeExpandingCleanUp(second);
		CleanUpRefactoring refactoring= createRefactoring(first, cleanUp);

		RefactoringStatus status= refactoring.checkAllConditions(new NullProgressMonitor());
		assertFalse(status.toString(), status.hasError());
		assertEquals(Set.of(first, second), Set.copyOf(cleanUp.plannedUnits));

		CompositeChange change= (CompositeChange) refactoring.createChange(null);
		Change[] children= change.getChildren();
		assertEquals(2, children.length);

		Set<String> previews= Set.of(
				((TextEditBasedChange) children[0]).getPreviewContent(new NullProgressMonitor()),
				((TextEditBasedChange) children[1]).getPreviewContent(new NullProgressMonitor()));
		assertEquals(Set.of("// cleanup\n" + firstSource, "// cleanup\n" + secondSource), previews); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testScopeExpansionRepeatsToFixedPointAndDeduplicatesTargets() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit first= pack.createCompilationUnit("First.java", "package test1;\nclass First {}\n", false, null); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit second= pack.createCompilationUnit("Second.java", "package test1;\nclass Second {}\n", false, null); //$NON-NLS-1$ //$NON-NLS-2$
		ICompilationUnit third= pack.createCompilationUnit("Third.java", "package test1;\nclass Third {}\n", false, null); //$NON-NLS-1$ //$NON-NLS-2$

		TransitiveScopeCleanUp cleanUp= new TransitiveScopeCleanUp(second, third);
		CleanUpRefactoring refactoring= createRefactoring(first, cleanUp);

		RefactoringStatus status= refactoring.checkAllConditions(new NullProgressMonitor());
		assertFalse(status.toString(), status.hasError());
		assertEquals(Set.of(first, second, third), Set.copyOf(cleanUp.plannedUnits));
		assertEquals(3, cleanUp.expansionInvocations);
		assertEquals(3, ((CompositeChange) refactoring.createChange(null)).getChildren().length);
	}

	@Test
	public void testInvalidProviderElementAbortsCleanup() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit first= pack.createCompilationUnit("First.java", "package test1;\nclass First {}\n", false, null); //$NON-NLS-1$ //$NON-NLS-2$
		CleanUpRefactoring refactoring= createRefactoring(first, new InvalidScopeCleanUp());

		try {
			refactoring.checkAllConditions(new NullProgressMonitor());
			fail("An invalid scope provider result must abort the cleanup"); //$NON-NLS-1$
		} catch (CoreException exception) {
			assertTrue(exception.getStatus().getMessage().contains("not an ICompilationUnit")); //$NON-NLS-1$
		}
	}

	private static CleanUpRefactoring createRefactoring(ICompilationUnit initialUnit, AbstractCleanUp cleanUp) {
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(initialUnit);
		refactoring.addCleanUp(cleanUp);
		return refactoring;
	}

	private abstract static class RecordingCleanUp extends AbstractCleanUp {
		List<ICompilationUnit> plannedUnits= List.of();

		@Override
		public RefactoringStatus checkPreConditions(IJavaProject project, ICompilationUnit[] compilationUnits,
				IProgressMonitor monitor) {
			plannedUnits= List.of(compilationUnits);
			return new RefactoringStatus();
		}

		@Override
		public ICleanUpFix createFix(CleanUpContext context) {
			return progressMonitor -> {
				CompilationUnitChange change= new CompilationUnitChange("Scope expansion test", //$NON-NLS-1$
						context.getCompilationUnit());
				MultiTextEdit root= new MultiTextEdit();
				root.addChild(new InsertEdit(0, "// cleanup\n")); //$NON-NLS-1$
				change.setEdit(root);
				return change;
			};
		}
	}

	public static final class ScopeExpandingCleanUp extends RecordingCleanUp {
		private final ICompilationUnit relatedUnit;

		ScopeExpandingCleanUp(ICompilationUnit relatedUnit) {
			this.relatedUnit= relatedUnit;
		}

		public Collection<ICompilationUnit> expandCleanUpScope(IJavaProject project,
				Collection<ICompilationUnit> currentScope, IProgressMonitor monitor) {
			return List.of(relatedUnit);
		}
	}

	public static final class TransitiveScopeCleanUp extends RecordingCleanUp {
		private final ICompilationUnit second;
		private final ICompilationUnit third;
		private int expansionInvocations;

		TransitiveScopeCleanUp(ICompilationUnit second, ICompilationUnit third) {
			this.second= second;
			this.third= third;
		}

		public Collection<ICompilationUnit> expandCleanUpScope(IJavaProject project,
				Collection<ICompilationUnit> currentScope, IProgressMonitor monitor) {
			expansionInvocations++;
			if (!currentScope.contains(second)) {
				return List.of(second, second);
			}
			if (!currentScope.contains(third)) {
				return List.of(second, third, third);
			}
			return List.of(second, third);
		}
	}

	public static final class InvalidScopeCleanUp extends AbstractCleanUp {
		public Collection<?> expandCleanUpScope(IJavaProject project,
				Collection<ICompilationUnit> currentScope, IProgressMonitor monitor) {
			return List.of("not a compilation unit"); //$NON-NLS-1$
		}

		@Override
		public ICleanUpFix createFix(CleanUpContext context) {
			return null;
		}
	}
}
