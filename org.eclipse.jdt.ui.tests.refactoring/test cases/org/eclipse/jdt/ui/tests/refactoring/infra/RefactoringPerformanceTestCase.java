/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.refactoring.infra;

import org.eclipse.test.performance.Performance;
import org.eclipse.test.performance.PerformanceMeter;

import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jdt.ui.tests.performance.JdtPerformanceTestCase;

import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.PerformRefactoringOperation;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringCore;

public class RefactoringPerformanceTestCase extends JdtPerformanceTestCase {

	protected void executeRefactoring(Refactoring refactoring, PerformanceMeter meter) throws Exception {
		PerformRefactoringOperation operation= new PerformRefactoringOperation(refactoring, CheckConditionsOperation.ALL_CONDITIONS);
		joinBackgroudJobs();
		System.gc();
		meter.start();
		ResourcesPlugin.getWorkspace().run(operation, null);
		meter.stop();
		meter.commit();
		assertEquals(true, operation.getConditionStatus().isOK());
		assertEquals(true, operation.getValidationStatus().isOK());
		assertNotNull(operation.getUndoChange());
		RefactoringCore.getUndoManager().flush();
		System.gc();
	}

	protected void executeRefactoring(Refactoring refactoring, String id) throws Exception {
		PerformanceMeter meter= Performance.getDefault().createPerformanceMeter(
			Performance.getDefault().getDefaultScenarioId(this, id));
		executeRefactoring(refactoring, meter);
		meter.dispose();
	}
}
