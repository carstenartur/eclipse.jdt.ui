/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer using github copilot - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.junit.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.testplugin.JavaProjectHelper;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.junit.ui.TestAnnotationModifier;

import org.eclipse.jdt.ui.tests.core.rules.Java1d8ProjectTestSetup;
import org.eclipse.jdt.ui.tests.core.rules.ProjectTestSetup;

/**
 * Tests for {@link TestAnnotationModifier#excludeEnumValue(IMethod, String)}.
 */
public class ExcludeParameterValueDisplayNameTest {

	@RegisterExtension
	public ProjectTestSetup projectSetup= new Java1d8ProjectTestSetup();

	private IJavaProject fJProject;
	private IPackageFragmentRoot fSourceFolder;

	@BeforeEach
	public void setUp() throws Exception {
		fJProject= projectSetup.getProject();
		fSourceFolder= JavaProjectHelper.addSourceContainer(fJProject, "src"); //$NON-NLS-1$

		JavaProjectHelper.addRTJar(fJProject);
		IClasspathEntry cpe= JavaCore.newContainerEntry(JUnitCore.JUNIT5_CONTAINER_PATH);
		JavaProjectHelper.addToClasspath(fJProject, cpe);
		JavaProjectHelper.set18CompilerOptions(fJProject);
	}

	@AfterEach
	public void tearDown() throws Exception {
		JavaProjectHelper.clear(fJProject, projectSetup.getDefaultClasspath());
	}

	/**
	 * Test excluding a value adds Mode.EXCLUDE and names.
	 */
	@Test
	public void testExcludeEnumValue_AddsExcludeMode() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null); //$NON-NLS-1$

		String enumCode= """
			package test1;
			public enum TestEnum {
				VALUE1, VALUE2, VALUE3
			}
			""";
		pack1.createCompilationUnit("TestEnum.java", enumCode, false, null); //$NON-NLS-1$

		String testCode= """
			package test1;

			import org.junit.jupiter.params.ParameterizedTest;
			import org.junit.jupiter.params.provider.EnumSource;

			public class MyTest {
			    @ParameterizedTest
			    @EnumSource(TestEnum.class)
			    public void testWithEnum(TestEnum value) {
			    }
			}
			""";

		ICompilationUnit cu= pack1.createCompilationUnit("MyTest.java", testCode, false, null); //$NON-NLS-1$
		IType type= cu.getType("MyTest"); //$NON-NLS-1$
		IMethod method= type.getMethod("testWithEnum", new String[] { "QTestEnum;" }); //$NON-NLS-1$ //$NON-NLS-2$

		TestAnnotationModifier.excludeEnumValue(method, "VALUE2"); //$NON-NLS-1$

		String result= cu.getSource();
		assertTrue(result.contains("\"VALUE2\""), "Should exclude VALUE2"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(result.contains("Mode.EXCLUDE"), "Should have Mode.EXCLUDE"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Test that Mode import is added properly (not fully qualified).
	 */
	@Test
	public void testExcludeEnumValueUsesImport() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null); //$NON-NLS-1$

		String enumCode= """
			package test1;
			public enum TestEnum {
				VALUE1, VALUE2, VALUE3
			}
			""";
		pack1.createCompilationUnit("TestEnum.java", enumCode, false, null); //$NON-NLS-1$

		String testCode= """
			package test1;

			import org.junit.jupiter.params.ParameterizedTest;
			import org.junit.jupiter.params.provider.EnumSource;

			public class MyTest {
			    @ParameterizedTest
			    @EnumSource(TestEnum.class)
			    public void testWithEnum(TestEnum value) {
			    }
			}
			""";

		ICompilationUnit cu= pack1.createCompilationUnit("MyTest.java", testCode, false, null); //$NON-NLS-1$
		IType type= cu.getType("MyTest"); //$NON-NLS-1$
		IMethod method= type.getMethod("testWithEnum", new String[] { "QTestEnum;" }); //$NON-NLS-1$ //$NON-NLS-2$

		TestAnnotationModifier.excludeEnumValue(method, "VALUE2"); //$NON-NLS-1$

		String result= cu.getSource();

		assertTrue(result.contains("import org.junit.jupiter.params.provider.EnumSource.Mode;"), //$NON-NLS-1$
				"Should have Mode import"); //$NON-NLS-1$
		assertTrue(result.contains("mode = Mode.EXCLUDE"), "Should use Mode.EXCLUDE (not fully qualified)"); //$NON-NLS-1$ //$NON-NLS-2$
		assertFalse(result.contains("org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE"), //$NON-NLS-1$
				"Should not use fully qualified Mode"); //$NON-NLS-1$
	}

	/**
	 * Test excluding when annotation already has exclusions (appends to existing list).
	 */
	@Test
	public void testExcludeEnumValue_ExistingAnnotation() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null); //$NON-NLS-1$

		String enumCode= """
			package test1;
			public enum TestEnum {
				VALUE1, VALUE2, VALUE3
			}
			""";
		pack1.createCompilationUnit("TestEnum.java", enumCode, false, null); //$NON-NLS-1$

		String testCode= """
			package test1;

			import org.junit.jupiter.params.ParameterizedTest;
			import org.junit.jupiter.params.provider.EnumSource;
			import org.junit.jupiter.params.provider.EnumSource.Mode;

			public class MyTest {
			    @ParameterizedTest
			    @EnumSource(value = TestEnum.class, mode = Mode.EXCLUDE, names = {"VALUE1"})
			    public void testWithEnum(TestEnum value) {
			    }
			}
			""";

		ICompilationUnit cu= pack1.createCompilationUnit("MyTest.java", testCode, false, null); //$NON-NLS-1$
		IType type= cu.getType("MyTest"); //$NON-NLS-1$
		IMethod method= type.getMethod("testWithEnum", new String[] { "QTestEnum;" }); //$NON-NLS-1$ //$NON-NLS-2$

		TestAnnotationModifier.excludeEnumValue(method, "VALUE2"); //$NON-NLS-1$

		String result= cu.getSource();

		assertTrue(result.contains("\"VALUE1\""), "Should still have VALUE1"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(result.contains("\"VALUE2\""), "Should have VALUE2"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(result.contains("Mode.EXCLUDE"), "Should have Mode.EXCLUDE"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Test multiple sequential exclusions work correctly.
	 */
	@Test
	public void testExcludeEnumValue_MultipleExclusions() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null); //$NON-NLS-1$

		String enumCode= """
			package test1;
			public enum TestEnum {
				VALUE1, VALUE2, VALUE3, VALUE4
			}
			""";
		pack1.createCompilationUnit("TestEnum.java", enumCode, false, null); //$NON-NLS-1$

		String testCode= """
			package test1;

			import org.junit.jupiter.params.ParameterizedTest;
			import org.junit.jupiter.params.provider.EnumSource;

			public class MyTest {
			    @ParameterizedTest
			    @EnumSource(TestEnum.class)
			    public void testWithEnum(TestEnum value) {
			    }
			}
			""";

		ICompilationUnit cu= pack1.createCompilationUnit("MyTest.java", testCode, false, null); //$NON-NLS-1$
		IType type= cu.getType("MyTest"); //$NON-NLS-1$
		IMethod method= type.getMethod("testWithEnum", new String[] { "QTestEnum;" }); //$NON-NLS-1$ //$NON-NLS-2$

		TestAnnotationModifier.excludeEnumValue(method, "VALUE2"); //$NON-NLS-1$
		TestAnnotationModifier.excludeEnumValue(method, "VALUE3"); //$NON-NLS-1$
		TestAnnotationModifier.excludeEnumValue(method, "VALUE4"); //$NON-NLS-1$

		String result= cu.getSource();

		assertTrue(result.contains("\"VALUE2\""), "Should have VALUE2"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(result.contains("\"VALUE3\""), "Should have VALUE3"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(result.contains("\"VALUE4\""), "Should have VALUE4"); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue(result.contains("Mode.EXCLUDE"), "Should have Mode.EXCLUDE"); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
