/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.StringTokenizer;

import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.Refactoring;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.ui.tests.refactoring.infra.AbstractJunit4CUTestCase;
import org.eclipse.jdt.ui.tests.refactoring.infra.RefactoringTestPlugin;
import org.eclipse.jdt.ui.tests.refactoring.infra.TextRangeUtil;

/**
 * Test Naming Convention:<p>
 * - testSimple_A() -> package 'simple', file 'A.java'; result in package 'simple.out'<br>
 * - testSuch_ALongName17() -> package 'such', file 'ALongName17.java'
 */
public abstract class LineColumnSelectionTestCase extends AbstractJunit4CUTestCase {
	@Override
	protected InputStream getFileInputStream(String fileName) throws IOException {
		return RefactoringTestPlugin.getDefault().getTestResourceStream(fileName);
	}

	/**
	 * @param name e.g. "testSuch_ALongName17"
	 * @return e.g. "ALongName17"
	 */
	@Override
	protected String adaptName(String name) {
		int separator= name.indexOf('_');
		assertNotEquals(-1, separator);
		assertTrue(separator >= 5);
		return name.substring(separator + 1) + ".java";
	}

	/**
	 * @param name e.g. "testSuch_ALongName17"
	 * @return e.g. "such"
	 */
	protected String adaptPackage(String name) {
		int separator= name.indexOf('_');
		assertNotEquals(-1, separator);
		assertTrue(separator >= 5);
		return Character.toLowerCase(name.charAt(4))
				+ name.substring(5, separator);
	}

	/*
	 * get selection from comment in source "//selection: line, col, line, col"
	 * <br>relies on tabwidth == 4
	 */
	protected ISourceRange getSelection(ICompilationUnit cu) throws Exception {
		String source= cu.getSource();
		String selection= "//selection:";
		int selStart= source.indexOf(selection);
		assertNotEquals(-1, selStart);

		int dataStart= selStart + selection.length();
		StringTokenizer tokenizer= new StringTokenizer(source.substring(dataStart), " ,\t\r\n");
		int line1= Integer.parseInt(tokenizer.nextToken());
		int col1= Integer.parseInt(tokenizer.nextToken());
		int line2= Integer.parseInt(tokenizer.nextToken());
		int col2= Integer.parseInt(tokenizer.nextToken());
		return TextRangeUtil.getSelection(cu, line1, col1, line2, col2);
	}

	/* @require refactoring.checkActivation().isOK() */
	protected void performTest(final ICompilationUnit unit, final Refactoring refactoring, final String out) throws Exception {
		JavaCore.run(monitor -> {
			String original= unit.getSource();
			final Change change= refactoring.createChange(monitor);
			assertNotNull(change);
			change.initializeValidationData(new NullProgressMonitor());
			assertFalse(change.isValid(new NullProgressMonitor()).hasFatalError());
			Change undo= change.perform(monitor);
			change.dispose();
			assertNotNull(undo);
			compareSource(unit.getSource(), out);
			undo.initializeValidationData(new NullProgressMonitor());
			assertFalse(undo.isValid(new NullProgressMonitor()).hasFatalError());
			undo.perform(monitor);
			undo.dispose();
			compareSource(unit.getSource(), original);
		}, new NullProgressMonitor());
	}
}
