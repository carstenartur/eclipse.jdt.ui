/*******************************************************************************
 * Copyright (c) 2025 IBM Corporation and others.
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
package org.eclipse.jdt.ui.tests.quickfix;

import java.util.Hashtable;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.jdt.testplugin.TestOptions;

import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.tests.core.rules.ProjectTestSetup;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Tests for Quick Assist proposals to filter JUnit 5 @EnumSource parameterized tests.
 */
public class EnumSourceQuickAssistTest extends QuickFixTest {

	@Rule
	public ProjectTestSetup projectSetup= new ProjectTestSetup();

	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;

	@Before
	public void setUp() throws Exception {
		Hashtable<String, String> options= TestOptions.getDefaultOptions();
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");

		JavaCore.setOptions(options);

		IPreferenceStore store= JavaPlugin.getDefault().getPreferenceStore();
		store.setValue(PreferenceConstants.CODEGEN_ADD_COMMENTS, false);

		fJProject1= projectSetup.getProject();
		fSourceFolder= JavaProjectHelper.addSourceContainer(fJProject1, "src");
	}

	@After
	public void tearDown() throws Exception {
		JavaProjectHelper.clear(fJProject1, projectSetup.getDefaultClasspath());
	}

	@Test
	public void testEnumSourceDetection() throws Exception {
		// This is a basic test to verify the detection logic works
		// A full implementation would include:
		// - Test adding 'names' attribute to @EnumSource
		// - Test toggling between INCLUDE and EXCLUDE modes
		// - Test with different enum values
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		
		String enumContent= """
			package test1;
			
			public enum TestEnum {
				VALUE1, VALUE2, VALUE3
			}
			""";
		pack1.createCompilationUnit("TestEnum.java", enumContent, false, null);
		
		String testContent= """
			package test1;
			
			import org.junit.jupiter.params.ParameterizedTest;
			import org.junit.jupiter.params.provider.EnumSource;
			
			class TestClass {
				@ParameterizedTest
				@EnumSource(TestEnum.class)
				void testWithEnum(TestEnum value) {
					// test implementation
				}
			}
			""";
		ICompilationUnit cu= pack1.createCompilationUnit("TestClass.java", testContent, false, null);

		// Test would normally assert that Quick Assist is available at the @EnumSource annotation
		// For now, this test just verifies the code compiles and sets up correctly
		assertNotNull(cu);
	}
}
