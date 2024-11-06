/*******************************************************************************
 * Copyright (c) 2000, 2020 IBM Corporation and others.
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
package org.eclipse.jdt.ui.tests.refactoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.testplugin.JavaProjectHelper;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.refactoring.changes.RenameSourceFolderChange;

import org.eclipse.jdt.ui.tests.refactoring.rules.RefactoringTestSetup;

public class RenameSourceFolderChangeTests extends GenericRefactoringTest {

	public RenameSourceFolderChangeTests() {
		rts= new RefactoringTestSetup();
	}

	@Test
	public void test0() throws Exception {
		String oldName= "oldName";
		String newName= "newName";

		try{
			IJavaProject testProject= rts.getProject();
			IPackageFragmentRoot oldRoot= JavaProjectHelper.addSourceContainer(rts.getProject(), oldName);

			assertTrue(oldRoot.exists(), "old folder should exist here");

			RenameSourceFolderChange change= new RenameSourceFolderChange(oldRoot, newName);
			change.initializeValidationData(new NullProgressMonitor());
			performChange(change);

			assertFalse(oldRoot.exists(), "old folder should not exist");
			assertEquals(3, testProject.getPackageFragmentRoots().length, "expected 3 pfr's");
			IPackageFragmentRoot[] newRoots= testProject.getPackageFragmentRoots();
			for (int i= 0; i < newRoots.length; i++){
				assertTrue(newRoots[i].exists(), "should exist " + i);
			}
		} finally{
			JavaProjectHelper.removeSourceContainer(rts.getProject(), newName);
		}
	}

	@Test
	public void test1() throws Exception {
		String oldName1= "oldName1";
		String oldName2= "oldName2";
		String newName1= "newName";

		try{

			IJavaProject testProject= rts.getProject();
			IPackageFragmentRoot oldRoot1= JavaProjectHelper.addSourceContainer(rts.getProject(), oldName1);
			IPackageFragmentRoot oldRoot2= JavaProjectHelper.addSourceContainer(rts.getProject(), oldName2);

			assertTrue(oldRoot1.exists(), "old folder should exist here");
			assertTrue(oldRoot2.exists(), "old folder 2 should exist here");

			RenameSourceFolderChange change= new RenameSourceFolderChange(oldRoot1, newName1);
			change.initializeValidationData(new NullProgressMonitor());
			performChange(change);

			assertFalse(oldRoot1.exists(), "old folder should not exist");
			assertEquals(4, testProject.getPackageFragmentRoots().length, "expected 4 pfr's");
			IPackageFragmentRoot[] newRoots= testProject.getPackageFragmentRoots();
			for (int i= 0; i < newRoots.length; i++){
				//DebugUtils.dump(newRoots[i].getElementName());
				assertTrue(newRoots[i].exists(), "should exist " + i);
				if (i == 2)
					assertEquals(newName1, newRoots[i].getElementName(), "3rd position should be:" + newName1);
			}
		}finally{
			JavaProjectHelper.removeSourceContainer(rts.getProject(), newName1);
			JavaProjectHelper.removeSourceContainer(rts.getProject(), oldName2);
		}
	}

	@Test
	public void testBug129991() throws Exception {
		IJavaProject project= JavaProjectHelper.createJavaProject("RenameSourceFolder", "bin");

		try {
			IPath projectPath= project.getPath();

			IPath[] exclusion= new IPath[] { new Path("src/") };
			JavaProjectHelper.addToClasspath(project, JavaCore.newSourceEntry(projectPath, exclusion));
			IPackageFragmentRoot src= JavaProjectHelper.addSourceContainer(project, "src");

			RenameSourceFolderChange change= new RenameSourceFolderChange(src, "src2");
			change.initializeValidationData(new NullProgressMonitor());
			performChange(change);

			assertFalse(src.exists(), "src should not exist");
			assertEquals(2, project.getPackageFragmentRoots().length, "expected 2 pfr's");

			IClasspathEntry[] rawClasspath= project.getRawClasspath();
			assertEquals(projectPath, rawClasspath[0].getPath());

			assertEquals("src2/", rawClasspath[0].getExclusionPatterns()[0].toString());
			assertEquals(projectPath.append("src2/"), rawClasspath[1].getPath());
		} finally {
			JavaProjectHelper.delete(project.getProject());
		}
	}
}
