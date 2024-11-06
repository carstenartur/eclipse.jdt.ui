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
package org.eclipse.jdt.ui.tests.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.jdt.testplugin.JavaTestPlugin;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.IncrementalProjectBuilder;

import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.tests.core.rules.ProjectTestSetup;

import org.eclipse.jdt.internal.ui.util.CoreUtility;
import org.eclipse.jdt.internal.ui.wizards.ClassPathDetector;

public class ClassPathDetectorTest {
	@RegisterExtension
	public ProjectTestSetup pts= new ProjectTestSetup();

	private IJavaProject fJProject1;

	private boolean fEnableAutoBuildAfterTesting;

	@BeforeEach
	public void setUp() throws Exception {

		IWorkspace workspace= JavaTestPlugin.getWorkspace();
		assertNotNull(workspace);

		// disable auto-build
		IWorkspaceDescription workspaceDesc= workspace.getDescription();
		if (workspaceDesc.isAutoBuilding()) {
			fEnableAutoBuildAfterTesting= true;
			CoreUtility.setAutoBuilding(false);
		}

		IPreferenceStore store= PreferenceConstants.getPreferenceStore();
		store.setValue(PreferenceConstants.SRCBIN_BINNAME, "bin");

		fJProject1= null;
	}

	@AfterEach
	public void tearDown() throws Exception {
		if (fJProject1 != null) {
			JavaProjectHelper.delete(fJProject1);
		}

		if (fEnableAutoBuildAfterTesting)
			CoreUtility.setAutoBuilding(true);
	}

	private boolean hasSamePaths(IPath[] a, IPath[] b) {
		if (a.length != b.length) {
			return false;
		}
		for (int i= 0; i < a.length; i++) {
			if (!a[i].equals(b[i])) {
				return false;
			}
		}
		return true;
	}

	private IClasspathEntry findEntry(IClasspathEntry entry, IClasspathEntry[] entries) {
		for (IClasspathEntry curr : entries) {
			if (curr.getPath().equals(entry.getPath()) && curr.getEntryKind() == entry.getEntryKind()) {
				if (curr.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
					if (hasSamePaths(curr.getExclusionPatterns(), entry.getExclusionPatterns())) {
						return curr;
					}
				} else {
					return curr;
				}
			}
		}
		return null;
	}



	private void assertSameClasspath(IClasspathEntry[] projectEntries, IClasspathEntry[] entries) throws Exception {
		assertEquals(projectEntries.length, entries.length, "Number of classpath entries");

		for (IClasspathEntry curr : projectEntries) {
			assertNotNull(findEntry(curr, entries), "entry not found: " + curr.getPath());
		}
	}

	private void clearClasspath() throws Exception {

		// see 29306
		IClasspathEntry other= JavaCore.newSourceEntry(fJProject1.getPath());
		fJProject1.setRawClasspath(new IClasspathEntry[] { other }, fJProject1.getPath().append("bin"), null);

		//fJProject1.setRawClasspath(new IClasspathEntry[0], projectOutput, null);
	}



	@Test
	public void sourceAndLibrary() throws Exception {
		fJProject1= JavaProjectHelper.createJavaProject("TestProject1", "bin");

		// source folder & internal JAR

		File junitSrcArchive= JavaTestPlugin.getDefault().getFileInPlugin(JavaProjectHelper.JUNIT_SRC_381);
		assertNotNull(junitSrcArchive, "junit src not found");
		assertTrue(junitSrcArchive.exists(), "junit src not found");

		JavaProjectHelper.addSourceContainerWithImport(fJProject1, "src", junitSrcArchive, JavaProjectHelper.JUNIT_SRC_ENCODING);

		File mylibJar= JavaTestPlugin.getDefault().getFileInPlugin(JavaProjectHelper.MYLIB);
		assertNotNull(junitSrcArchive, "lib not found");
		assertTrue(junitSrcArchive.exists(), "lib not found");

		JavaProjectHelper.addLibraryWithImport(fJProject1, Path.fromOSString(mylibJar.getPath()), null, null);

		for (IClasspathEntry jreEntry : PreferenceConstants.getDefaultJRELibrary()) {
			JavaProjectHelper.addToClasspath(fJProject1, jreEntry);
		}
		fJProject1.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);

		IClasspathEntry[] projectEntries= fJProject1.getRawClasspath();
		IPath projectOutput= fJProject1.getOutputLocation();

		clearClasspath();

		ClassPathDetector detector= new ClassPathDetector(fJProject1.getProject(), null);
		IPath outputLocation= detector.getOutputLocation();
		IClasspathEntry[] entries= detector.getClasspath();
		assertNotNull(entries, "No classpath detected");
		assertNotNull(outputLocation, "No outputLocation detected");

		assertSameClasspath(projectEntries, entries);

		assertTrue(outputLocation.equals(projectOutput), "Output folder");
	}

	@Test
	public void twoSourceFolders() throws Exception {
		fJProject1= JavaProjectHelper.createJavaProject("TestProject1", "bin");
		// 2 source folders

		File junitSrcArchive= JavaTestPlugin.getDefault().getFileInPlugin(JavaProjectHelper.JUNIT_SRC_381);
		assertNotNull(junitSrcArchive, "junit src not found");
		assertTrue(junitSrcArchive.exists(), "junit src not found");

		JavaProjectHelper.addSourceContainerWithImport(fJProject1, "src1", junitSrcArchive, JavaProjectHelper.JUNIT_SRC_ENCODING);

		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(fJProject1, "src2");
		IPackageFragment pack1= root.createPackageFragment("test1", false, null);
		String str= """
			package test1;
			public class E {
			    public void foo() {
			        getClass();
			    }
			}
			""";
		pack1.createCompilationUnit("E.java", str, false, null);

		for (IClasspathEntry jreEntry : PreferenceConstants.getDefaultJRELibrary()) {
			JavaProjectHelper.addToClasspath(fJProject1, jreEntry);
		}
		fJProject1.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);

		IClasspathEntry[] projectEntries= fJProject1.getRawClasspath();
		IPath projectOutput= fJProject1.getOutputLocation();

		clearClasspath();

		ClassPathDetector detector= new ClassPathDetector(fJProject1.getProject(), null);
		IPath outputLocation= detector.getOutputLocation();
		IClasspathEntry[] entries= detector.getClasspath();
		assertNotNull(entries, "No classpath detected");
		assertNotNull(outputLocation, "No outputLocation detected");

		assertSameClasspath(projectEntries, entries);

		assertTrue(outputLocation.equals(projectOutput), "Output folder");
	}

	@Test
	public void nestedSources() throws Exception {
		fJProject1= JavaProjectHelper.createJavaProject("TestProject1", "bin");
		// 2 nested source folders

		File junitSrcArchive= JavaTestPlugin.getDefault().getFileInPlugin(JavaProjectHelper.JUNIT_SRC_381);
		assertNotNull(junitSrcArchive, "junit src not found");
		assertTrue(junitSrcArchive.exists(), "junit src not found");

		IPath[] exclusionFilter= new IPath[] { new Path("src2/") };
		JavaProjectHelper.addSourceContainerWithImport(fJProject1, "src1", junitSrcArchive, JavaProjectHelper.JUNIT_SRC_ENCODING, exclusionFilter);

		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(fJProject1, "src1/src2");
		IPackageFragment pack1= root.createPackageFragment("test1", false, null);
		String str= """
			package test1;
			public class E {
			    public void foo() {
			        getClass();
			    }
			}
			""";
		pack1.createCompilationUnit("E.java", str, false, null);

		for (IClasspathEntry jreEntry : PreferenceConstants.getDefaultJRELibrary()) {
			JavaProjectHelper.addToClasspath(fJProject1, jreEntry);
		}
		fJProject1.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);

		IClasspathEntry[] projectEntries= fJProject1.getRawClasspath();
		IPath projectOutput= fJProject1.getOutputLocation();

		clearClasspath();

		ClassPathDetector detector= new ClassPathDetector(fJProject1.getProject(), null);
		IPath outputLocation= detector.getOutputLocation();
		IClasspathEntry[] entries= detector.getClasspath();
		assertNotNull(entries, "No classpath detected");
		assertNotNull(outputLocation, "No outputLocation detected");

		assertSameClasspath(projectEntries, entries);

		assertTrue(outputLocation.equals(projectOutput), "Output folder");
	}

	@Test
	public void sourceAndOutputOnProject() throws Exception {
		fJProject1= JavaProjectHelper.createJavaProject("TestProject1", "");

		// source folder & internal JAR

		File junitSrcArchive= JavaTestPlugin.getDefault().getFileInPlugin(JavaProjectHelper.JUNIT_SRC_381);
		assertNotNull(junitSrcArchive, "junit src not found");
		assertTrue(junitSrcArchive.exists(), "junit src not found");
		JavaProjectHelper.addSourceContainerWithImport(fJProject1, "", junitSrcArchive, JavaProjectHelper.JUNIT_SRC_ENCODING);

		for (IClasspathEntry jreEntry : PreferenceConstants.getDefaultJRELibrary()) {
			JavaProjectHelper.addToClasspath(fJProject1, jreEntry);
		}
		fJProject1.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);

		IClasspathEntry[] projectEntries= fJProject1.getRawClasspath();
		IPath projectOutput= fJProject1.getOutputLocation();

		clearClasspath();

		ClassPathDetector detector= new ClassPathDetector(fJProject1.getProject(), null);
		IPath outputLocation= detector.getOutputLocation();
		IClasspathEntry[] entries= detector.getClasspath();
		assertNotNull(entries, "No classpath detected");
		assertNotNull(outputLocation, "No outputLocation detected");

		assertSameClasspath(projectEntries, entries);

		assertTrue(outputLocation.equals(projectOutput), "Output folder");
	}

	@Test
	public void classFolder() throws Exception {
		fJProject1= JavaProjectHelper.createJavaProject("TestProject1", "bin");
		// class folder:

		IPackageFragmentRoot root= JavaProjectHelper.addSourceContainer(fJProject1, "src1");
		IPackageFragment pack1= root.createPackageFragment("test1", false, null);
		String str= """
			package test1;
			public class E {
			    public void foo() {
			        getClass();
			    }
			}
			""";
		pack1.createCompilationUnit("E.java", str, false, null);

		File lib= JavaTestPlugin.getDefault().getFileInPlugin(JavaProjectHelper.MYLIB);
		assertNotNull(lib, "lib not found");
		assertTrue(lib.exists(), "lib not found");

		IPackageFragmentRoot cfroot= JavaProjectHelper.addClassFolderWithImport(fJProject1, "cf", null, null, lib);

		for (IClasspathEntry jreEntry : PreferenceConstants.getDefaultJRELibrary()) {
			JavaProjectHelper.addToClasspath(fJProject1, jreEntry);
		}
		fJProject1.getProject().build(IncrementalProjectBuilder.FULL_BUILD, null);

		JavaProjectHelper.removeFromClasspath(fJProject1, cfroot.getPath()); // classfolder should not be detected

		IClasspathEntry[] projectEntries= fJProject1.getRawClasspath();
		IPath projectOutput= fJProject1.getOutputLocation();

		clearClasspath();

		ClassPathDetector detector= new ClassPathDetector(fJProject1.getProject(), null);
		IPath outputLocation= detector.getOutputLocation();
		IClasspathEntry[] entries= detector.getClasspath();
		assertNotNull(entries, "No classpath detected");
		assertNotNull(outputLocation, "No outputLocation detected");

		assertSameClasspath(projectEntries, entries);
		assertTrue(outputLocation.equals(projectOutput), "Output folder");
	}
}
