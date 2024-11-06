/*******************************************************************************
 * Copyright (c) 2021 IBM Corporation and others.
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Hashtable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

import org.eclipse.jdt.internal.core.manipulation.CodeTemplateContextType;
import org.eclipse.jdt.internal.core.manipulation.StubUtility;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.structure.MoveInnerToTopRefactoring;

import org.eclipse.jdt.ui.tests.refactoring.rules.Java16Setup;

import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;

public class MoveInnerToTopLevelTests16 extends GenericRefactoringTest {
	private static final String FIELD_COMMENT= "/** Comment */";
	private static final String REFACTORING_PATH= "MoveInnerToTopLevel16/";

	private String fCompactPref;

	public MoveInnerToTopLevelTests16() {
		rts= new Java16Setup();
	}

	@Override
	protected String getRefactoringPath() {
		return REFACTORING_PATH;
	}

	@BeforeEach
	public void before() throws Exception {
		StubUtility.setCodeTemplate(CodeTemplateContextType.FIELDCOMMENT_ID, FIELD_COMMENT, null);
		StubUtility.setCodeTemplate(CodeTemplateContextType.NEWTYPE_ID,
			"${package_declaration}" +
			System.getProperty("line.separator", "\n") +
			"${type_declaration}", null);

		Hashtable<String, String> options= JavaCore.getOptions();

		String setting= DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_ASSIGNMENT_OPERATOR;
		fCompactPref= options.get(setting);
		options.put(setting, DefaultCodeFormatterConstants.TRUE);
		JavaCore.setOptions(options);
	}

	@AfterEach
	public void after() throws Exception {
		Hashtable<String, String> options= JavaCore.getOptions();
		options.put(DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_ASSIGNMENT_OPERATOR, fCompactPref);
		JavaCore.setOptions(options);
	}


	private IType getClassFromTestFile(IPackageFragment pack, String className) throws Exception{
		return getType(createCUfromTestFile(pack, className), className);
	}

	private void validatePassingTest(String parentClassName, String className, String packageName, String[] cuNames, String[] packageNames, String enclosingInstanceName, boolean makeFinal, boolean possible, boolean mandatory, boolean createFieldIfPossible) throws Exception {
		IType parentClas= getClassFromTestFile(getPackage(packageName), parentClassName);
		IType clas= parentClas.getType(className);
		validatePassingTest(className, clas, cuNames, packageNames, enclosingInstanceName, makeFinal, possible, mandatory, createFieldIfPossible);
	}

	private void validatePassingTest(String className, IType clas, String[] cuNames, String[] packageNames, String enclosingInstanceName, boolean makeFinal, boolean possible, boolean mandatory, boolean createFieldIfPossible) throws JavaModelException, CoreException, Exception, IOException {
		assertTrue(RefactoringAvailabilityTester.isMoveInnerAvailable(clas), "should be enabled");
		MoveInnerToTopRefactoring ref= ((RefactoringAvailabilityTester.isMoveInnerAvailable(clas)) ? new MoveInnerToTopRefactoring(clas, JavaPreferencesSettings.getCodeGenerationSettings(clas.getJavaProject())) : null);
		assertNotNull(ref, "Move to inner refactoring should be available");
		RefactoringStatus preconditionResult= ref.checkInitialConditions(new NullProgressMonitor());
		assertTrue(preconditionResult.isOK(), "activation was supposed to be successful" + preconditionResult.toString());

		assertEquals(possible, ref.isCreatingInstanceFieldPossible(), "reference creation possible");
		assertEquals(mandatory, ref.isCreatingInstanceFieldMandatory(), "reference creation mandatory");
		if (ref.isCreatingInstanceFieldPossible() && ! ref.isCreatingInstanceFieldMandatory())
			ref.setCreateInstanceField(createFieldIfPossible);
		if (enclosingInstanceName != null){
			ref.setEnclosingInstanceName(enclosingInstanceName);
			assertTrue(ref.checkEnclosingInstanceName(enclosingInstanceName).isOK(), "name should be ok ");
		}
		ref.setMarkInstanceFieldAsFinal(makeFinal);
		ICompilationUnit[] cus= new ICompilationUnit[cuNames.length];
		for (int i= 0; i < cuNames.length; i++) {
			if (cuNames[i].equals(clas.getCompilationUnit().findPrimaryType().getElementName()))
				cus[i]= clas.getCompilationUnit();
			else
				cus[i]= createCUfromTestFile(getPackage(packageNames[i]), cuNames[i]);
		}

		RefactoringStatus checkInputResult= ref.checkFinalConditions(new NullProgressMonitor());
		assertFalse(checkInputResult.hasError(), "precondition was supposed to pass");
		performChange(ref, false);

		for (int i= 0; i < cus.length; i++) {
			String actual= cus[i].getSource();
			String expected= getFileContents(getOutputTestFileName(cuNames[i]));
			assertEqualLines(cus[i].getElementName(), expected, actual);
		}
		ICompilationUnit newCu= clas.getPackageFragment().getCompilationUnit(className + ".java");
		String expected= getFileContents(getOutputTestFileName(className));
		String actual= newCu.getSource();
		assertEqualLines("new Cu:", expected, actual);
	}


	private IPackageFragment getPackage(String name) throws JavaModelException {
		if ("p".equals(name))
			return getPackageP();
		IPackageFragment pack= getRoot().getPackageFragment(name);
		if (pack.exists())
			return pack;
		return getRoot().createPackageFragment(name, false, new NullProgressMonitor());
	}


	//-- tests

	@Test
	public void test_Bug572639_0() throws Exception {
		validatePassingTest("A", "Layout", "p", new String[] { "A" }, new String[] { "p" }, "a", false, false, false, false);
	}

	@Test
	public void test_Bug572639_1() throws Exception {
		validatePassingTest("A", "Layout", "p", new String[] { "A" }, new String[] { "p" }, "a", false, false, false, false);
	}

	@Test
	public void test_Bug572639_2() throws Exception {
		validatePassingTest("A", "Layout", "p", new String[] { "A" }, new String[] { "p" }, "a", false, false, false, false);
	}
}
