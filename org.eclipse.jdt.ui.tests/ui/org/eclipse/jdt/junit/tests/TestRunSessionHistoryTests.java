/*******************************************************************************
 * Copyright (c) 2026 Eclipse Foundation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.jdt.junit.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.eclipse.jdt.junit.model.ITestElement.Result;

import org.eclipse.jdt.internal.junit.model.TestRunSession;
import org.eclipse.jdt.internal.junit.model.TestRunSessionHistory;

public class TestRunSessionHistoryTests {

	@Rule
	public TemporaryFolder fTemporaryFolder= new TemporaryFolder();

	@Test
	public void readsOnlyTheTestRunHeader() throws Exception {
		File historyFile= fTemporaryFolder.newFile("20260901-120000.000.xml"); //$NON-NLS-1$
		Files.writeString(historyFile.toPath(), """
				<?xml version="1.0" encoding="UTF-8"?>
				<testrun name="header" tests="3" started="3" failures="1" errors="0" ignored="0">
				  <malformed
				"""); //$NON-NLS-1$

		TestRunSession session= TestRunSessionHistory.load(historyFile.getParentFile(), 1).get(0);

		assertEquals("header", session.getTestRunName()); //$NON-NLS-1$
		assertEquals(3, session.getTotalCount());
		assertEquals(3, session.getStartedCount());
		assertEquals(1, session.getFailureCount());
		assertEquals(Result.FAILURE, session.getTestResult(true));
	}

	@Test
	public void loadsTheTestTreeOnDemand() throws Exception {
		File historyFile= fTemporaryFolder.newFile("20260901-120001.000.xml"); //$NON-NLS-1$
		Files.writeString(historyFile.toPath(), """
				<?xml version="1.0" encoding="UTF-8"?>
				<testrun name="lazy" tests="1" started="1" failures="0" errors="0" ignored="0">
				  <testcase name="testOne" classname="example.ExampleTest" time="0.1"/>
				</testrun>
				"""); //$NON-NLS-1$

		TestRunSession session= TestRunSessionHistory.load(historyFile.getParentFile(), 1).get(0);

		assertEquals(1, session.getChildren().length);
		assertEquals(Result.OK, session.getTestResult(true));
	}

	@Test
	public void restoresNewestEntriesAndRemovesOlderOnes() throws Exception {
		File historyDirectory= fTemporaryFolder.newFolder("history"); //$NON-NLS-1$
		File oldest= writeTestRun(historyDirectory, "20260901-120000.000.xml", "oldest"); //$NON-NLS-1$ //$NON-NLS-2$
		File middle= writeTestRun(historyDirectory, "20260901-120001.000.xml", "middle"); //$NON-NLS-1$ //$NON-NLS-2$
		File newest= writeTestRun(historyDirectory, "20260901-120002.000.xml", "newest"); //$NON-NLS-1$ //$NON-NLS-2$
		List<TestRunSession> sessions= TestRunSessionHistory.load(historyDirectory, 2);

		assertEquals(2, sessions.size());
		assertEquals("newest", sessions.get(0).getTestRunName()); //$NON-NLS-1$
		assertEquals("middle", sessions.get(1).getTestRunName()); //$NON-NLS-1$
		assertFalse(oldest.exists());
		assertTrue(middle.exists());
		assertTrue(newest.exists());
	}

	@Test
	public void persistsCompletedSessions() throws Exception {
		File historyDirectory= fTemporaryFolder.newFolder("history"); //$NON-NLS-1$
		File historyFile= writeTestRun(historyDirectory, "20260901-120004.000.xml", "persisted"); //$NON-NLS-1$ //$NON-NLS-2$
		TestRunSession session= TestRunSessionHistory.load(historyFile.getParentFile(), 1).get(0);
		session.getChildren();
		Files.delete(historyFile.toPath());

		TestRunSessionHistory.store(List.of(session), historyDirectory, 10);

		assertTrue(historyFile.isFile());
		TestRunSession restored= TestRunSessionHistory.load(historyDirectory, 1).get(0);
		assertEquals("persisted", restored.getTestRunName()); //$NON-NLS-1$
		assertEquals(0, restored.getTotalCount());
		assertEquals(Result.OK, restored.getTestResult(true));
	}

	private static File writeTestRun(File directory, String fileName, String testRunName) throws Exception {
		File file= new File(directory, fileName);
		Files.writeString(file.toPath(), "<testrun name=\"" + testRunName //$NON-NLS-1$
				+ "\" tests=\"0\" started=\"0\" failures=\"0\" errors=\"0\" ignored=\"0\"/>"); //$NON-NLS-1$
		return file;
	}
}
