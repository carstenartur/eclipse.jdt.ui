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

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.eclipse.jdt.testplugin.JavaProjectHelper;

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
		CleanUpRefactoring refactoring= new CleanUpRefactoring();
		refactoring.addCompilationUnit(first);
		refactoring.addCleanUp(cleanUp);

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

	public static final class ScopeExpandingCleanUp extends AbstractCleanUp {
		private final ICompilationUnit relatedUnit;
		private List<ICompilationUnit> plannedUnits= List.of();

		ScopeExpandingCleanUp(ICompilationUnit relatedUnit) {
			this.relatedUnit= relatedUnit;
		}

		public Collection<ICompilationUnit> expandCleanUpScope(IJavaProject project,
				Collection<ICompilationUnit> currentScope, IProgressMonitor monitor) {
			return List.of(relatedUnit);
		}

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
}
