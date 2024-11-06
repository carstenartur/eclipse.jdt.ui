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
package org.eclipse.jdt.ui.tests.refactoring.nls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.junit.Rule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.eclipse.jdt.testplugin.JavaProjectHelper;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.core.resources.IFile;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;

import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSMessages;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSSubstitution;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.tests.core.rules.ProjectTestSetup;

public class NlsRefactoringCheckFinalConditionsTest {
	@Rule
	public ProjectTestSetup pts= new ProjectTestSetup();

	//private IPath fPropertyFilePath;
	private IPackageFragment fAccessorPackage;
	private String fAccessorClassName;
	private String fSubstitutionPattern;
	private NlsRefactoringTestHelper fHelper;
	private IJavaProject javaProject;
	private IPackageFragment fResourceBundlePackage;
	private String fResourceBundleName;

	@BeforeEach
	public void setUp() throws Exception {
		javaProject= pts.getProject();
		fHelper= new NlsRefactoringTestHelper(javaProject);
	}

	@AfterEach
	public void tearDown() throws Exception {
		JavaProjectHelper.clear(javaProject, pts.getDefaultClasspath());
	}

	@Test
	public void checkInputWithoutExistingPropertiesFile() throws Exception {
		ICompilationUnit cu= fHelper.getCu("/TestSetupProject/src1/p/WithStrings.java");
		IFile propertyFile= fHelper.getFile("/TestSetupProject/src2/p/test.properties");
		propertyFile.delete(false, fHelper.fNpm);
		initDefaultValues();

		RefactoringStatus res= createCheckInputStatus(cu);

		assertFalse(res.isOK(), "should info about properties");

		assertEquals(1, res.getEntries().length, "one info");
		RefactoringStatusEntry help= res.getEntryAt(0);
		assertEquals(RefactoringStatus.INFO, help.getSeverity(), "info");
		assertEquals(Messages.format(NLSMessages.NLSRefactoring_will_be_created, BasicElementLabels.getPathLabel(propertyFile.getFullPath(), false)), help.getMessage());
	}

	/*
	 * no substitutions -> nothing to do
	 */
	@Test
	public void checkInputWithNoSubstitutions() throws Exception {
		ICompilationUnit cu= fHelper.getCu("/TestSetupProject/src1/p/WithoutStrings.java"); //$NON-NLS-1$
		initDefaultValues();

		checkNothingToDo(createCheckInputStatus(cu));
	}

	/*
	 * substitution checks
	 */
	@Test
	public void checkInputWithSubstitutionPatterns() throws Exception {
		ICompilationUnit cu= fHelper.getCu("/TestSetupProject/src1/p/WithStrings.java"); //$NON-NLS-1$
		initDefaultValues();

		fSubstitutionPattern= ""; //$NON-NLS-1$

		RefactoringStatus res= createCheckInputStatus(cu);

		RefactoringStatusEntry[] results= res.getEntries();

		assertEquals(2, results.length, "substitution pattern must be given"); //$NON-NLS-1$
		assertEquals(RefactoringStatus.ERROR, results[0].getSeverity(), "first is fatal"); //$NON-NLS-1$
		assertEquals(NLSMessages.NLSRefactoring_pattern_empty, //$NON-NLS-1$
				results[0].getMessage(),
				"right fatal message");

		assertEquals(RefactoringStatus.WARNING, results[1].getSeverity(), //$NON-NLS-1$
				"warning no key given");
		assertEquals(Messages.format(NLSMessages.NLSRefactoring_pattern_does_not_contain, "${key}"), //$NON-NLS-1$
				results[1].getMessage(), "right warning message"); //$NON-NLS-1$

		fSubstitutionPattern= "blabla${key}"; //$NON-NLS-1$
		res= createCheckInputStatus(cu);
		assertTrue(res.isOK(), "substitution pattern ok"); //$NON-NLS-1$

		fSubstitutionPattern= "${key}blabla${key}"; //$NON-NLS-1$
		res= createCheckInputStatus(cu);
		assertFalse(res.isOK(), "substitution pattern ko"); //$NON-NLS-1$

		results= res.getEntries();
		assertEquals(1, results.length, "one warning"); //$NON-NLS-1$
		assertEquals(RefactoringStatus.WARNING, results[0].getSeverity(), "warning"); //$NON-NLS-1$
		assertEquals(Messages.format(NLSMessages.NLSRefactoring_Only_the_first_occurrence_of, "${key}"), //$NON-NLS-1$
				results[0].getMessage(), "warning message"); //$NON-NLS-1$

		// check for duplicate keys????
		// check for keys already defined
		// check for keys
	}

	private RefactoringStatus createCheckInputStatus(ICompilationUnit cu) throws CoreException {
		NLSRefactoring refac= prepareRefac(cu);
		RefactoringStatus res= refac.checkFinalConditions(fHelper.fNpm);
		return res;
	}

	private void initDefaultValues() {
		//fPropertyFilePath= fHelper.getFile("/TestSetupProject/src2/p/test.properties").getFullPath(); //$NON-NLS-1$
		fResourceBundlePackage= fHelper.getPackageFragment("/TestSetupProject/src2/p");
		fResourceBundleName= "test.properties";
		fAccessorPackage= fHelper.getPackageFragment("/TestSetupProject/src1/p"); //$NON-NLS-1$
		fAccessorClassName= "Help"; //$NON-NLS-1$
		fSubstitutionPattern= "${key}"; //$NON-NLS-1$
	}

	private NLSRefactoring prepareRefac(ICompilationUnit cu) {
		NLSRefactoring refac= NLSRefactoring.create(cu);
		NLSSubstitution[] subs= refac.getSubstitutions();
		refac.setPrefix("");
		for (NLSSubstitution sub : subs) {
			sub.setState(NLSSubstitution.EXTERNALIZED);
			sub.generateKey(subs, new Properties());
		}
		fillInValues(refac);
		return refac;
	}

	private void checkNothingToDo(RefactoringStatus status) {
		assertEquals(1, status.getEntries().length, "fatal error expected"); //$NON-NLS-1$

		RefactoringStatusEntry fatalError= status.getEntryAt(0);
		assertEquals(RefactoringStatus.FATAL, fatalError.getSeverity(), "fatalerror"); //$NON-NLS-1$
		assertEquals(NLSMessages.NLSRefactoring_nothing_to_do, //$NON-NLS-1$
				fatalError.getMessage(),
				"errormessage");
	}

	private void fillInValues(NLSRefactoring refac) {
		refac.setAccessorClassPackage(fAccessorPackage);
		//refac.setPropertyFilePath(fPropertyFilePath);
		refac.setResourceBundleName(fResourceBundleName);
		refac.setResourceBundlePackage(fResourceBundlePackage);
		refac.setAccessorClassName(fAccessorClassName);
		refac.setSubstitutionPattern(fSubstitutionPattern);
	}

}
