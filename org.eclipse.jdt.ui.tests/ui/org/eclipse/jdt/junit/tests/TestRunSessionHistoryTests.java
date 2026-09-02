/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.jdt.junit.tests;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;
import java.util.UUID;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.eclipse.jdt.junit.model.ITestElement.Result;

import org.eclipse.jdt.internal.junit.model.TestRunSession;
import org.eclipse.jdt.internal.junit.model.TestRunSessionHistory;

public class TestRunSessionHistoryTests {

	private static final String INDEX_FILE_NAME= "history.properties"; //$NON-NLS-1$
	private static final String HISTORY_FILE_PREFIX= "history-"; //$NON-NLS-1$
	private static final String SWAP_FILE_PREFIX= "swap-"; //$NON-NLS-1$
	private static final String XML_SUFFIX= ".xml"; //$NON-NLS-1$

	@Rule
	public TemporaryFolder fTemporaryFolder= new TemporaryFolder();

	@Test
	public void readsMetadataWithoutParsingTheTestTree() throws Exception {
		File historyDirectory= fTemporaryFolder.newFolder("history"); //$NON-NLS-1$
		HistoryEntry entry= entry("header", -1_788_336_000_000L, 1_788_336_000_000L, //$NON-NLS-1$
				"completed", 3, 3, 1, 0, 0, //$NON-NLS-1$
				"<testrun name=\"header\"><malformed"); //$NON-NLS-1$
		writeHistory(historyDirectory, entry);

		TestRunSession session= TestRunSessionHistory.load(historyDirectory, 1).get(0);

		assertEquals("header", session.getTestRunName()); //$NON-NLS-1$
		assertEquals(-1_788_336_000_000L, session.getStartTime());
		assertEquals(3, session.getTotalCount());
		assertEquals(3, session.getStartedCount());
		assertEquals(1, session.getFailureCount());
		assertEquals(Result.FAILURE, session.getTestResult(true));
	}

	@Test
	public void loadsTheTestTreeOnDemand() throws Exception {
		File historyDirectory= fTemporaryFolder.newFolder("history"); //$NON-NLS-1$
		HistoryEntry entry= entry("lazy", 1_788_336_001_000L, 1_788_336_001_000L, //$NON-NLS-1$
				"completed", 1, 1, 0, 0, 0, //$NON-NLS-1$
				"""
				<testrun name="lazy" tests="1" started="1" failures="0" errors="0" ignored="0">
				  <testcase name="testOne" classname="example.ExampleTest" time="0.1"/>
				</testrun>
				"""); //$NON-NLS-1$
		writeHistory(historyDirectory, entry);

		TestRunSession session= TestRunSessionHistory.load(historyDirectory, 1).get(0);

		assertEquals(1, session.getChildren().length);
		assertEquals(Result.OK, session.getTestResult(true));
	}

	@Test
	public void preservesUndefinedResultForAnEmptyCompletedRun() throws Exception {
		File historyDirectory= fTemporaryFolder.newFolder("history"); //$NON-NLS-1$
		HistoryEntry entry= emptyEntry("empty", 1_788_336_001_500L); //$NON-NLS-1$
		writeHistory(historyDirectory, entry);

		TestRunSession session= TestRunSessionHistory.load(historyDirectory, 1).get(0);

		assertEquals(Result.UNDEFINED, session.getTestResult(true));
		assertEquals(0, session.getChildren().length);
		assertEquals(Result.UNDEFINED, session.getTestResult(true));
	}

	@Test
	public void restoresConfiguredNumberWithoutDeletingValidExcessEntries() throws Exception {
		File historyDirectory= fTemporaryFolder.newFolder("history"); //$NON-NLS-1$
		HistoryEntry newest= emptyEntry("newest", 1_788_336_003_000L); //$NON-NLS-1$
		HistoryEntry middle= emptyEntry("middle", 1_788_336_002_000L); //$NON-NLS-1$
		HistoryEntry oldest= emptyEntry("oldest", 1_788_336_001_000L); //$NON-NLS-1$
		writeHistory(historyDirectory, newest, middle, oldest);

		List<TestRunSession> sessions= TestRunSessionHistory.load(historyDirectory, 2);

		assertEquals(2, sessions.size());
		assertEquals("newest", sessions.get(0).getTestRunName()); //$NON-NLS-1$
		assertEquals("middle", sessions.get(1).getTestRunName()); //$NON-NLS-1$
		assertTrue(oldest.file(historyDirectory).isFile());
	}

	@Test
	public void preservesImportedStartTimeAcrossLocaleAndTimeZoneChanges() throws Exception {
		File historyDirectory= fTemporaryFolder.newFolder("history"); //$NON-NLS-1$
		Locale originalLocale= Locale.getDefault();
		TimeZone originalTimeZone= TimeZone.getDefault();
		try {
			Locale.setDefault(Locale.forLanguageTag("th-TH")); //$NON-NLS-1$
			TimeZone.setDefault(TimeZone.getTimeZone("Asia/Bangkok")); //$NON-NLS-1$
			TestRunSession imported= new TestRunSession("imported", null); //$NON-NLS-1$
			long originalStartTime= imported.getStartTime();

			TestRunSessionHistory.store(List.of(imported), historyDirectory, 10);
			assertEquals(1, xmlFiles(historyDirectory).size());
			assertTrue(xmlFiles(historyDirectory).get(0).getName().matches(
					"history-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.xml")); //$NON-NLS-1$

			Locale.setDefault(Locale.GERMANY);
			TimeZone.setDefault(TimeZone.getTimeZone("UTC")); //$NON-NLS-1$
			TestRunSession restored= TestRunSessionHistory.load(historyDirectory, 10).get(0);

			assertTrue(originalStartTime < 0);
			assertEquals(originalStartTime, restored.getStartTime());
		} finally {
			Locale.setDefault(originalLocale);
			TimeZone.setDefault(originalTimeZone);
		}
	}

	@Test
	public void storesExplicitStoppedState() throws Exception {
		File historyDirectory= fTemporaryFolder.newFolder("history"); //$NON-NLS-1$
		TestRunSession stopped= new TestRunSession("stopped", null); //$NON-NLS-1$
		long startTime= stopped.getStartTime();
		stopped.stopTestRun();

		TestRunSessionHistory.store(List.of(stopped), historyDirectory, 10);
		TestRunSession restored= TestRunSessionHistory.load(historyDirectory, 10).get(0);

		assertTrue(restored.isStopped());
		assertEquals(startTime, restored.getStartTime());
	}

	@Test
	public void preservesStoppedStateWhenTheRunContainsAFailure() throws Exception {
		File historyDirectory= fTemporaryFolder.newFolder("history"); //$NON-NLS-1$
		HistoryEntry entry= entry("stoppedWithFailure", 1_788_336_004_000L, 1_788_336_004_000L, //$NON-NLS-1$
				"stopped", 10, 3, 1, 0, 0, //$NON-NLS-1$
				"""
				<testrun name="stoppedWithFailure" tests="10" started="3" failures="1" errors="0" ignored="0">
				  <testcase name="testOne" classname="example.ExampleTest" time="0.1">
				    <failure>failed</failure>
				  </testcase>
				</testrun>
				"""); //$NON-NLS-1$
		writeHistory(historyDirectory, entry);

		TestRunSession session= TestRunSessionHistory.load(historyDirectory, 1).get(0);

		assertTrue(session.isStopped());
		assertEquals(Result.FAILURE, session.getTestResult(true));
		assertEquals(1, session.getChildren().length);
		assertTrue(session.isStopped());
		assertEquals(Result.FAILURE, session.getTestResult(true));
	}

	@Test
	public void preservesStoppedStateWhenAllTestsWereStarted() throws Exception {
		File historyDirectory= fTemporaryFolder.newFolder("history"); //$NON-NLS-1$
		HistoryEntry entry= entry("stoppedAfterStart", 1_788_336_005_000L, 1_788_336_005_000L, //$NON-NLS-1$
				"stopped", 1, 1, 0, 0, 0, //$NON-NLS-1$
				"""
				<testrun name="stoppedAfterStart" tests="1" started="1" failures="0" errors="0" ignored="0">
				  <testcase name="testOne" classname="example.ExampleTest" incomplete="true"/>
				</testrun>
				"""); //$NON-NLS-1$
		writeHistory(historyDirectory, entry);

		TestRunSession session= TestRunSessionHistory.load(historyDirectory, 1).get(0);

		assertTrue(session.isStopped());
		assertEquals(Result.UNDEFINED, session.getTestResult(true));
		assertEquals(1, session.getChildren().length);
		assertTrue(session.isStopped());
	}

	@Test
	public void releasesAndReloadsTheSelectedTestTree() throws Exception {
		File historyDirectory= fTemporaryFolder.newFolder("history"); //$NON-NLS-1$
		HistoryEntry entry= entry("reload", 1_788_336_005_500L, 1_788_336_005_500L, //$NON-NLS-1$
				"completed", 1, 1, 0, 0, 0, //$NON-NLS-1$
				"""
				<testrun name="reload" tests="1" started="1" failures="0" errors="0" ignored="0">
				  <testcase name="testOne" classname="example.ExampleTest"/>
				</testrun>
				"""); //$NON-NLS-1$
		writeHistory(historyDirectory, entry);
		TestRunSession session= TestRunSessionHistory.load(historyDirectory, 1).get(0);
		assertEquals(1, session.getChildren().length);

		session.swapOut();
		Files.writeString(entry.file(historyDirectory).toPath(), """
				<testrun name="reload" tests="2" started="2" failures="0" errors="0" ignored="0">
				  <testcase name="testOne" classname="example.ExampleTest"/>
				  <testcase name="testTwo" classname="example.ExampleTest"/>
				</testrun>
				"""); //$NON-NLS-1$

		assertEquals(2, session.getChildren().length);
	}

	@Test
	public void keepsEqualHistoryTimestampsDistinct() throws Exception {
		File historyDirectory= fTemporaryFolder.newFolder("history"); //$NON-NLS-1$
		long timestamp= 1_788_336_006_000L;
		HistoryEntry first= emptyEntry("first", timestamp); //$NON-NLS-1$
		HistoryEntry second= emptyEntry("second", timestamp); //$NON-NLS-1$
		writeHistory(historyDirectory, first, second);

		List<TestRunSession> sessions= TestRunSessionHistory.load(historyDirectory, 10);
		TestRunSessionHistory.store(sessions, historyDirectory, 10);
		List<TestRunSession> restored= TestRunSessionHistory.load(historyDirectory, 10);

		assertEquals(2, xmlFiles(historyDirectory).size());
		assertEquals(2, restored.size());
		assertEquals("first", restored.get(0).getTestRunName()); //$NON-NLS-1$
		assertEquals("second", restored.get(1).getTestRunName()); //$NON-NLS-1$
	}

	@Test
	public void doesNotReuseAnUnownedStaleFile() throws Exception {
		File historyDirectory= fTemporaryFolder.newFolder("history"); //$NON-NLS-1$
		File staleFile= new File(historyDirectory, HISTORY_FILE_PREFIX + UUID.randomUUID() + XML_SUFFIX);
		Files.writeString(staleFile.toPath(), "<testrun name=\"stale\"/>"); //$NON-NLS-1$
		TestRunSession current= new TestRunSession("current", null); //$NON-NLS-1$

		TestRunSessionHistory.store(List.of(current), historyDirectory, 10);
		List<TestRunSession> restored= TestRunSessionHistory.load(historyDirectory, 10);

		assertFalse(staleFile.exists());
		assertEquals(1, xmlFiles(historyDirectory).size());
		assertEquals(1, restored.size());
		assertEquals("current", restored.get(0).getTestRunName()); //$NON-NLS-1$
	}

	@Test
	public void ignoresATruncatedNewerFileWithoutPruningAnOlderValidEntry() throws Exception {
		File historyDirectory= fTemporaryFolder.newFolder("history"); //$NON-NLS-1$
		HistoryEntry newer= emptyEntry("newer", 1_788_336_008_000L); //$NON-NLS-1$
		HistoryEntry older= emptyEntry("older", 1_788_336_007_000L); //$NON-NLS-1$
		writeHistory(historyDirectory, newer, older);
		Files.writeString(newer.file(historyDirectory).toPath(), "<testrun"); //$NON-NLS-1$

		List<TestRunSession> sessions= TestRunSessionHistory.load(historyDirectory, 1);

		assertEquals(1, sessions.size());
		assertEquals("older", sessions.get(0).getTestRunName()); //$NON-NLS-1$
		assertTrue(newer.file(historyDirectory).isFile());
		assertTrue(older.file(historyDirectory).isFile());

		TestRunSessionHistory.store(sessions, historyDirectory, 1);

		assertFalse(newer.file(historyDirectory).exists());
	}

	@Test
	public void rewritesALoadedSessionWhenItsPersistentFileDisappears() throws Exception {
		File historyDirectory= fTemporaryFolder.newFolder("history"); //$NON-NLS-1$
		HistoryEntry entry= entry("loaded", 1_788_336_009_000L, 1_788_336_009_000L, //$NON-NLS-1$
				"completed", 1, 1, 0, 0, 0, //$NON-NLS-1$
				"""
				<testrun name="loaded" tests="1" started="1" failures="0" errors="0" ignored="0">
				  <testcase name="testOne" classname="example.ExampleTest"/>
				</testrun>
				"""); //$NON-NLS-1$
		writeHistory(historyDirectory, entry);
		TestRunSession session= TestRunSessionHistory.load(historyDirectory, 1).get(0);
		session.getChildren();
		Files.delete(entry.file(historyDirectory).toPath());

		TestRunSessionHistory.store(List.of(session), historyDirectory, 10);
		List<TestRunSession> restored= TestRunSessionHistory.load(historyDirectory, 10);

		assertEquals(1, restored.size());
		assertEquals("loaded", restored.get(0).getTestRunName()); //$NON-NLS-1$
		assertEquals(1, restored.get(0).getChildren().length);
	}

	@Test
	public void doesNotRewriteAnUnreadableSessionAsAnEmptyRun() throws Exception {
		File historyDirectory= fTemporaryFolder.newFolder("history"); //$NON-NLS-1$
		HistoryEntry entry= emptyEntry("unreadable", 1_788_336_010_000L); //$NON-NLS-1$
		writeHistory(historyDirectory, entry);
		TestRunSession session= TestRunSessionHistory.load(historyDirectory, 1).get(0);
		Files.writeString(entry.file(historyDirectory).toPath(), "broken"); //$NON-NLS-1$

		TestRunSessionHistory.store(List.of(session), historyDirectory, 10);

		assertTrue(TestRunSessionHistory.load(historyDirectory, 10).isEmpty());
		assertTrue(entry.file(historyDirectory).isFile());
	}

	@Test
	public void removesUnpublishedXmlFilesWhenNoIndexExists() throws Exception {
		File historyDirectory= fTemporaryFolder.newFolder("history"); //$NON-NLS-1$
		File historyFile= new File(historyDirectory, HISTORY_FILE_PREFIX + UUID.randomUUID() + XML_SUFFIX);
		File swapFile= new File(historyDirectory, SWAP_FILE_PREFIX + UUID.randomUUID() + XML_SUFFIX);
		File legacyFile= new File(historyDirectory, "20260901-120000.000.xml"); //$NON-NLS-1$
		Files.writeString(historyFile.toPath(), "unpublished"); //$NON-NLS-1$
		Files.writeString(swapFile.toPath(), "transient"); //$NON-NLS-1$
		Files.writeString(legacyFile.toPath(), "legacy"); //$NON-NLS-1$

		assertTrue(TestRunSessionHistory.load(historyDirectory, 10).isEmpty());

		assertFalse(historyFile.exists());
		assertFalse(swapFile.exists());
		assertFalse(legacyFile.exists());
	}

	@Test
	public void keepsAnUnsupportedHistoryGenerationUntouched() throws Exception {
		File historyDirectory= fTemporaryFolder.newFolder("history"); //$NON-NLS-1$
		HistoryEntry entry= emptyEntry("future", 1_788_336_011_000L); //$NON-NLS-1$
		writeHistory(historyDirectory, entry);
		File indexFile= new File(historyDirectory, INDEX_FILE_NAME);
		String index= Files.readString(indexFile.toPath());
		Files.writeString(indexFile.toPath(), index.replace("formatVersion=2", "formatVersion=999")); //$NON-NLS-1$ //$NON-NLS-2$
		byte[] indexBefore= Files.readAllBytes(indexFile.toPath());
		byte[] xmlBefore= Files.readAllBytes(entry.file(historyDirectory).toPath());

		assertTrue(TestRunSessionHistory.load(historyDirectory, 10).isEmpty());
		TestRunSessionHistory.store(List.of(), historyDirectory, 10);

		assertArrayEquals(indexBefore, Files.readAllBytes(indexFile.toPath()));
		assertArrayEquals(xmlBefore, Files.readAllBytes(entry.file(historyDirectory).toPath()));
	}

	@Test
	public void keepsANonRegularHistoryIndexUntouched() throws Exception {
		File historyDirectory= fTemporaryFolder.newFolder("history"); //$NON-NLS-1$
		HistoryEntry entry= emptyEntry("unreadable-index", 1_788_336_012_000L); //$NON-NLS-1$
		writeHistory(historyDirectory, entry);
		File indexFile= new File(historyDirectory, INDEX_FILE_NAME);
		byte[] xmlBefore= Files.readAllBytes(entry.file(historyDirectory).toPath());
		Files.delete(indexFile.toPath());
		assertTrue(indexFile.mkdir());

		assertTrue(TestRunSessionHistory.load(historyDirectory, 10).isEmpty());
		TestRunSessionHistory.store(List.of(), historyDirectory, 10);

		assertTrue(indexFile.isDirectory());
		assertArrayEquals(xmlBefore, Files.readAllBytes(entry.file(historyDirectory).toPath()));
	}

	@Test
	public void removesAbandonedTemporaryFiles() throws Exception {
		File historyDirectory= fTemporaryFolder.newFolder("history"); //$NON-NLS-1$
		File temporaryFile= new File(historyDirectory, "abandoned.tmp"); //$NON-NLS-1$
		Files.writeString(temporaryFile.toPath(), "temporary"); //$NON-NLS-1$

		TestRunSessionHistory.load(historyDirectory, 10);

		assertFalse(temporaryFile.exists());
	}

	private static HistoryEntry emptyEntry(String name, long timestamp) {
		return entry(name, timestamp, timestamp, "completed", 0, 0, 0, 0, 0, //$NON-NLS-1$
				"<testrun name=\"" + name + "\" tests=\"0\" started=\"0\" failures=\"0\" errors=\"0\" ignored=\"0\"/>"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static HistoryEntry entry(String name, long startTime, long historyTimestamp, String progress,
			int totalCount, int startedCount, int failureCount, int errorCount, int ignoredCount, String xml) {
		Result result;
		if (errorCount > 0)
			result= Result.ERROR;
		else if (failureCount > 0)
			result= Result.FAILURE;
		else if ("stopped".equals(progress) || totalCount == 0) //$NON-NLS-1$
			result= Result.UNDEFINED;
		else
			result= Result.OK;
		return new HistoryEntry(UUID.randomUUID().toString(), name, startTime, historyTimestamp, progress, result,
				totalCount, startedCount, failureCount, errorCount, ignoredCount, 0, xml);
	}

	private static void writeHistory(File directory, HistoryEntry... entries) throws Exception {
		Properties properties= new Properties();
		properties.setProperty("formatVersion", "2"); //$NON-NLS-1$ //$NON-NLS-2$
		properties.setProperty("entryCount", Integer.toString(entries.length)); //$NON-NLS-1$
		for (int i= 0; i < entries.length; i++) {
			HistoryEntry entry= entries[i];
			File file= entry.file(directory);
			Files.writeString(file.toPath(), entry.fXml);
			String prefix= "entry." + i + '.'; //$NON-NLS-1$
			properties.setProperty(prefix + "id", entry.fId); //$NON-NLS-1$
			properties.setProperty(prefix + "name", entry.fName); //$NON-NLS-1$
			properties.setProperty(prefix + "startTime", Long.toString(entry.fStartTime)); //$NON-NLS-1$
			properties.setProperty(prefix + "historyTimestamp", Long.toString(entry.fHistoryTimestamp)); //$NON-NLS-1$
			properties.setProperty(prefix + "progress", entry.fProgress); //$NON-NLS-1$
			properties.setProperty(prefix + "result", entry.fResult.name()); //$NON-NLS-1$
			properties.setProperty(prefix + "totalCount", Integer.toString(entry.fTotalCount)); //$NON-NLS-1$
			properties.setProperty(prefix + "startedCount", Integer.toString(entry.fStartedCount)); //$NON-NLS-1$
			properties.setProperty(prefix + "failureCount", Integer.toString(entry.fFailureCount)); //$NON-NLS-1$
			properties.setProperty(prefix + "errorCount", Integer.toString(entry.fErrorCount)); //$NON-NLS-1$
			properties.setProperty(prefix + "ignoredCount", Integer.toString(entry.fIgnoredCount)); //$NON-NLS-1$
			properties.setProperty(prefix + "assumptionFailureCount", Integer.toString(entry.fAssumptionFailureCount)); //$NON-NLS-1$
			properties.setProperty(prefix + "fileLength", Long.toString(file.length())); //$NON-NLS-1$
		}
		try (BufferedOutputStream output= new BufferedOutputStream(
				new FileOutputStream(new File(directory, INDEX_FILE_NAME)))) {
			properties.store(output, null);
		}
	}

	private static List<File> xmlFiles(File directory) throws Exception {
		File[] files= directory.listFiles(file -> file.isFile() && file.getName().endsWith(XML_SUFFIX));
		List<File> result= new ArrayList<>();
		if (files != null)
			result.addAll(List.of(files));
		return result;
	}

	private static final class HistoryEntry {
		final String fId;
		final String fName;
		final long fStartTime;
		final long fHistoryTimestamp;
		final String fProgress;
		final Result fResult;
		final int fTotalCount;
		final int fStartedCount;
		final int fFailureCount;
		final int fErrorCount;
		final int fIgnoredCount;
		final int fAssumptionFailureCount;
		final String fXml;

		HistoryEntry(String id, String name, long startTime, long historyTimestamp, String progress, Result result,
				int totalCount, int startedCount, int failureCount, int errorCount, int ignoredCount,
				int assumptionFailureCount, String xml) {
			fId= id;
			fName= name;
			fStartTime= startTime;
			fHistoryTimestamp= historyTimestamp;
			fProgress= progress;
			fResult= result;
			fTotalCount= totalCount;
			fStartedCount= startedCount;
			fFailureCount= failureCount;
			fErrorCount= errorCount;
			fIgnoredCount= ignoredCount;
			fAssumptionFailureCount= assumptionFailureCount;
			fXml= xml;
		}

		File file(File directory) {
			return new File(directory, HISTORY_FILE_PREFIX + fId + XML_SUFFIX);
		}
	}
}
