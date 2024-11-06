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
package org.eclipse.jdt.ui.tests.refactoring.reorg;

import org.junit.FixMethodOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.runners.MethodSorters;

import org.eclipse.test.performance.Dimension;

import org.eclipse.jdt.ui.tests.refactoring.rules.RefactoringPerformanceTestSetup;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MoveStaticMembersPerfTests2 extends AbstractMoveStaticMemberPrefTest {

	@RegisterExtension
	public RefactoringPerformanceTestSetup rpts= new RefactoringPerformanceTestSetup();

	@Test
	public void testACold_10_10() throws Exception {
		executeRefactoring(10, 10, false, 3);
	}

	@Test
	public void testB_10_10() throws Exception {
		executeRefactoring(10, 10, true, 3);
	}

	@Test
	public void testC_10_100() throws Exception {
		executeRefactoring(10, 100, true, 1);
	}

	@Test
	public void testD_10_1000() throws Exception {
		tagAsSummary("Move static members - 10 CUs, 1000 Refs", Dimension.ELAPSED_PROCESS);
		executeRefactoring(10, 1000, true, 1);
	}
}
