/*******************************************************************************
 * Copyright (c) 2019, 2020 IBM Corporation and others.
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
 *     Red Hat Inc. - copied and modified from RenameResourceChangeTests
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.refactoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.testplugin.JavaProjectHelper;

import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.resource.MoveRenameResourceChange;

import org.eclipse.jdt.ui.tests.refactoring.rules.RefactoringTestSetup;

public class MoveRenameResourceChangeTests extends GenericRefactoringTest {

	public MoveRenameResourceChangeTests() {
		rts= new RefactoringTestSetup();
	}

	@Test
	public void testFile0() throws Exception{
		IFolder folder= (IFolder)getPackageP().getCorrespondingResource();
		String newName= "b.txt";
		String oldName= "a.txt";
		IFile file= folder.getFile(oldName);
		assertFalse(file.exists(), "should not exist");
		String content= "aaaaaaaaa";
		file.create(getStream(content), true, new NullProgressMonitor());
		assertTrue(file.exists(), "should exist");

		Change change= new MoveRenameResourceChange(file, file.getParent(), newName);
		change.initializeValidationData(new NullProgressMonitor());
		performChange(change);
		assertTrue(folder.getFile(newName).exists(), "after: should exist");
		assertFalse(folder.getFile(oldName).exists(), "after: old should not exist");
	}

	@Test
	public void testFile1() throws Exception{

		IFolder folder= (IFolder)getPackageP().getCorrespondingResource();
		String newName= "b.txt";
		String oldName= "a.txt";
		IFile file= folder.getFile(oldName);
		assertFalse(file.exists(), "should not exist");
		String content= "";
		file.create(getStream(content), true, new NullProgressMonitor());
		assertTrue(file.exists(), "should exist");


		Change change= new MoveRenameResourceChange(file, file.getParent(), newName);
		change.initializeValidationData(new NullProgressMonitor());
		performChange(change);
		assertTrue(folder.getFile(newName).exists(), "after: should exist");
		assertFalse(folder.getFile(oldName).exists(), "after: old should not exist");
	}

	@Test
	public void testFile2() throws Exception{
		IFolder folder= (IFolder)getPackageP().getCorrespondingResource();
		String oldName= "a.txt";
		String newName= "b.txt";
		IFile file= folder.getFile(oldName);
		assertFalse(file.exists(), "should not exist");
		String content= "aaaaaaaaa";
		file.create(getStream(content), true, new NullProgressMonitor());
		assertTrue(file.exists(), "should exist");

		Change change= new MoveRenameResourceChange(file, file.getParent(), newName);
		change.initializeValidationData(new NullProgressMonitor());
		Change undo= performChange(change);
		assertTrue(folder.getFile(newName).exists(), "after: should exist");
		assertFalse(folder.getFile(oldName).exists(), "after: old should not exist");
		//------

		assertNotNull(undo, "should be undoable");
		undo.initializeValidationData(new NullProgressMonitor());
		performChange(undo);
		assertTrue(folder.getFile(oldName).exists(), "after undo: should exist");
		assertFalse(folder.getFile(newName).exists(), "after undo: old should not exist");
	}

	@Test
	public void testFile3() throws Exception{
		IFolder folder= (IFolder)getPackageP().getCorrespondingResource();
		IFolder folder2= (IFolder)getPackageQ().getCorrespondingResource();
		String oldName= "a.txt";
		String newName= "b.txt";
		IFile file= folder.getFile(oldName);
		assertFalse(file.exists(), "should not exist");
		String content= "aaaaaaaaa";
		file.create(getStream(content), true, new NullProgressMonitor());
		assertTrue(file.exists(), "should exist");

		Change change= new MoveRenameResourceChange(file, folder2, newName);
		change.initializeValidationData(new NullProgressMonitor());
		Change undo= performChange(change);
		assertTrue(folder2.getFile(newName).exists(), "after: should exist");
		assertFalse(folder.getFile(oldName).exists(), "after: old should not exist");
		//------

		assertNotNull(undo, "should be undoable");
		undo.initializeValidationData(new NullProgressMonitor());
		performChange(undo);
		assertTrue(folder.getFile(oldName).exists(), "after undo: should exist");
		assertFalse(folder2.getFile(newName).exists(), "after undo: old should not exist");
	}


	@Test
	public void testFolder0() throws Exception{
		IFolder folder= (IFolder)getPackageP().getCorrespondingResource();
		String newName= "b";
		String oldName= "a";
		IFolder subFolder= folder.getFolder(oldName);
		assertFalse(subFolder.exists(), "should not exist");
		subFolder.create(true, true, null);
		assertTrue(subFolder.exists(), "should exist");


		Change change= new MoveRenameResourceChange(subFolder, folder, newName);
		change.initializeValidationData(new NullProgressMonitor());
		performChange(change);
		assertTrue(folder.getFolder(newName).exists(), "after: should exist");
		assertFalse(folder.getFolder(oldName).exists(), "after: old should not exist");
	}

	@Test
	public void testFolder1() throws Exception{
		IFolder folder= (IFolder)getPackageP().getCorrespondingResource();
		String newName= "b";

		String oldName= "a";
		IFolder subFolder= folder.getFolder(oldName);
		assertFalse(subFolder.exists(), "should not exist");
		subFolder.create(true, true, null);
		IFile file1= subFolder.getFile("a.txt");
		IFile file2= subFolder.getFile("b.txt");
		file1.create(getStream("123"), true, null);
		file2.create(getStream("123345"), true, null);

		assertTrue(subFolder.exists(), "should exist");
		assertTrue(file1.exists(), "file1 should exist");
		assertTrue(file2.exists(), "file2 should exist");

		Change change= new MoveRenameResourceChange(subFolder, folder, newName);
		change.initializeValidationData(new NullProgressMonitor());
		performChange(change);
		assertTrue(folder.getFolder(newName).exists(), "after: should exist");
		assertFalse(folder.getFolder(oldName).exists(), "after: old should not exist");
		assertEquals(2, folder.getFolder(newName).members().length, "after: child count");
	}

	@Test
	public void testFolder2() throws Exception{
		IFolder folder= (IFolder)getPackageP().getCorrespondingResource();
		String oldName= "a";
		String newName= "b";
		IFolder subFolder= folder.getFolder(oldName);
		assertFalse(subFolder.exists(), "should not exist");
		subFolder.create(true, true, null);
		assertTrue(subFolder.exists(), "should exist");


		Change change= new MoveRenameResourceChange(subFolder, folder, newName);
		change.initializeValidationData(new NullProgressMonitor());
		Change undo= performChange(change);
		assertTrue(folder.getFolder(newName).exists(), "after: should exist");
		assertFalse(folder.getFolder(oldName).exists(), "after: old should not exist");

		//---
		assertNotNull(undo, "should be undoable");
		undo.initializeValidationData(new NullProgressMonitor());
		performChange(undo);
		assertTrue(folder.getFolder(oldName).exists(), "after undo: should exist");
		assertFalse(folder.getFolder(newName).exists(), "after undo: old should not exist");
	}

	@Test
	public void testFolder3() throws Exception{
		IFolder folder= (IFolder)getPackageP().getCorrespondingResource();
		IFolder folder2= (IFolder)getPackageQ().getCorrespondingResource();
		String oldName= "a";
		String newName= "b";
		IFolder subFolder= folder.getFolder(oldName);
		assertFalse(subFolder.exists(), "should not exist");
		subFolder.create(true, true, null);
		assertTrue(subFolder.exists(), "should exist");


		Change change= new MoveRenameResourceChange(subFolder, folder2, newName);
		change.initializeValidationData(new NullProgressMonitor());
		Change undo= performChange(change);
		assertTrue(folder2.getFolder(newName).exists(), "after: should exist");
		assertFalse(folder.getFolder(oldName).exists(), "after: old should not exist");

		//---
		assertNotNull(undo, "should be undoable");
		undo.initializeValidationData(new NullProgressMonitor());
		performChange(undo);
		assertTrue(folder.getFolder(oldName).exists(), "after undo: should exist");
		assertFalse(folder2.getFolder(newName).exists(), "after undo: old should not exist");
	}

	@Test
	public void testJavaProject01() throws Exception {
		String oldName= "RenameResourceChangeTest";
		String newName= "RenameResourceChangeTest2";
		String linkName= "link";

		IWorkspaceRoot workspaceRoot= ResourcesPlugin.getWorkspace().getRoot();
		IProject project= workspaceRoot.getProject(oldName);
		IProject project2= workspaceRoot.getProject(newName);
		try {
			getPackageP().createCompilationUnit("A.java", "package p;\nclass A{}\n", false, null);

			project.create(null);
			project.open(null);
			IFolder link= project.getFolder(linkName);
			link.createLink(getPackageP().getResource().getRawLocation(), IResource.NONE, null);
			assertTrue(link.exists());

			Change change= new MoveRenameResourceChange(project, project.getParent(), newName);
			change.initializeValidationData(new NullProgressMonitor());
			performChange(change);

			assertTrue(project2.getFolder(linkName).exists(), "after: linked folder should exist");
			assertTrue(project2.getFolder(linkName).isLinked(), "after: linked folder should be linked");
			assertTrue(project2.getFolder(linkName).getFile("A.java").exists(), "after: linked folder should contain cu");
		} finally {
			if (project.exists())
				JavaProjectHelper.delete(project);
			if (project2.exists())
				JavaProjectHelper.delete(project2);
		}
	}
}
