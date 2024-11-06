/*******************************************************************************
 * Copyright (c) 2000, 2021 IBM Corporation and others.
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
package org.eclipse.jdt.ui.tests.refactoring.ccp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.widgets.Display;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;

import org.eclipse.jdt.ui.actions.SelectionDispatchAction;
import org.eclipse.jdt.ui.tests.refactoring.GenericRefactoringTest;
import org.eclipse.jdt.ui.tests.refactoring.infra.MockClipboard;
import org.eclipse.jdt.ui.tests.refactoring.infra.MockWorkbenchSite;
import org.eclipse.jdt.ui.tests.refactoring.rules.RefactoringTestSetup;

import org.eclipse.jdt.internal.ui.refactoring.reorg.CopyToClipboardAction;

public class CopyResourcesToClipboardActionTest extends GenericRefactoringTest{

	public CopyResourcesToClipboardActionTest() {
		rts= new RefactoringTestSetup();
	}

	private ICompilationUnit fCuA;
	private ICompilationUnit fCuB;
	private IPackageFragment fPackage_Q;
	private IPackageFragment fPackage_Q_R;
	private IPackageFragment fDefaultPackage;
	private static final String CU_A_NAME= "A";
	private static final String CU_B_NAME= "B";
	private IFile faTxt;

	private Clipboard fClipboard;

	private IFile createFile(IFolder folder, String fileName) throws Exception {
		IFile file= folder.getFile(fileName);
		file.create(getStream("aa"), true, null);
		return file;
	}

	@BeforeEach
	public void before() throws Exception {
		fClipboard= new MockClipboard(Display.getDefault());
		fDefaultPackage= rts.getDefaultSourceFolder().createPackageFragment("", true, null);

		fCuA= createCU(getPackageP(), CU_A_NAME + ".java", "package p; class A{}");

		fPackage_Q= rts.getDefaultSourceFolder().createPackageFragment("q", true, null);
		fCuB= createCU(fPackage_Q, CU_B_NAME + ".java", "package q; class B{}");

		fPackage_Q_R= rts.getDefaultSourceFolder().createPackageFragment("q.r", true, null);

		faTxt= createFile((IFolder)getPackageP().getUnderlyingResource(), "a.txt");

		assertTrue(fCuA.exists(), "A.java does not exist");
		assertTrue(fCuB.exists(), "B.java does not exist");
		assertTrue(fPackage_Q.exists(), "q does not exist");
		assertTrue(fPackage_Q_R.exists(), "q.r does not exist");
		assertTrue(faTxt.exists());
	}

	@AfterEach
	public void after() throws Exception {
		fClipboard.dispose();
	}

	private void checkEnabled(Object[] elements) {
		SelectionDispatchAction copyAction= new CopyToClipboardAction(new MockWorkbenchSite(elements), fClipboard);
		copyAction.update(copyAction.getSelection());
		assertTrue(copyAction.isEnabled(), "action should be enabled");
	}

	private void checkDisabled(Object[] elements) {
		SelectionDispatchAction copyAction= new CopyToClipboardAction(new MockWorkbenchSite(elements), fClipboard);
		copyAction.update(copyAction.getSelection());
		assertFalse(copyAction.isEnabled(), "action should not be enabled");
	}

	@Test
	public void testEnabled0() throws Exception{
		checkEnabled(new Object[]{fCuA});
	}

	@Test
	public void testEnabled1() throws Exception{
		checkEnabled(new Object[]{getRoot().getJavaProject()});
	}

	@Test
	public void testEnabled2() throws Exception{
		checkEnabled(new Object[]{getPackageP()});
	}

	@Test
	public void testEnabled3() throws Exception{
		checkEnabled(new Object[]{getPackageP(), fPackage_Q, fPackage_Q_R});
	}

	@Test
	public void testEnabled4() throws Exception{
		checkEnabled(new Object[]{faTxt});
	}

	@Test
	public void testEnabled5() throws Exception{
		checkEnabled(new Object[]{getRoot()});
	}

	@Test
	public void testDisabled0() throws Exception{
		checkDisabled(new Object[]{});
	}

	@Test
	public void testDisabled1() throws Exception{
		checkDisabled(new Object[]{getRoot().getJavaProject(), fCuA});
	}

	@Test
	public void testDisabled2() throws Exception{
		checkDisabled(new Object[]{getRoot().getJavaProject(), fPackage_Q});
	}

	@Test
	public void testDisabled3() throws Exception{
		checkDisabled(new Object[]{getRoot().getJavaProject(), faTxt});
	}

	@Test
	public void testDisabled4() throws Exception{
		checkDisabled(new Object[]{getPackageP(), fCuA});
	}

	@Test
	public void testDisabled5() throws Exception{
		checkDisabled(new Object[]{getRoot(), fCuA});
	}

	@Test
	public void testDisabled6() throws Exception{
		checkDisabled(new Object[]{getRoot(), fPackage_Q});
	}

	@Test
	public void testDisabled7() throws Exception{
		checkDisabled(new Object[]{getRoot(), faTxt});
	}

	@Test
	public void testDisabled8() throws Exception{
		checkDisabled(new Object[]{getRoot(), getRoot().getJavaProject()});
	}

	@Test
	public void testDisabled9() throws Exception{
		checkDisabled(new Object[]{rts.getProject().getPackageFragmentRoots()});
	}

	@Test
	public void testDisabled10() throws Exception{
		checkDisabled(new Object[]{fCuA, fCuB});
	}

	@Test
	public void testDisabled11() throws Exception{
		checkDisabled(new Object[]{fDefaultPackage});
	}
}
