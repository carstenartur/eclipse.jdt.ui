/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial tests
 *******************************************************************************/
package org.eclipse.jdt.junit.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.internal.junit.JUnitCorePlugin;
import org.eclipse.jdt.internal.junit.model.JUnitModel;
import org.eclipse.jdt.internal.junit.model.TestRunSession;
import org.eclipse.jdt.internal.junit.ui.ImportedTestRunFileWatcher;

public class ImportedTestRunReloadTest {

	private Path fTemporaryDirectory;
	private final List<TestRunSession> fSessionsToRemove= new ArrayList<>();

	@Before
	public void setUp() throws Exception {
		fTemporaryDirectory= Files.createTempDirectory("junit-reload-test"); //$NON-NLS-1$
	}

	@After
	public void tearDown() throws Exception {
		JUnitModel model= JUnitCorePlugin.getModel();
		for (TestRunSession session : fSessionsToRemove) {
			if (model.getTestRunSessions().contains(session)) {
				model.removeTestRunSession(session);
			}
		}
		if (fTemporaryDirectory != null) {
			try (Stream<Path> paths= Files.walk(fTemporaryDirectory)) {
				paths.sorted(Comparator.reverseOrder()).forEach(path -> {
					try {
						Files.deleteIfExists(path);
					} catch (Exception e) {
						// Best-effort cleanup.
					}
				});
			}
		}
	}

	@Test
	public void testReloadReplacesSessionAndRetainsSourceFile() throws Exception {
		Path resultFile= fTemporaryDirectory.resolve("results.xml"); //$NON-NLS-1$
		writeTestRun(resultFile, "first run", "firstTest"); //$NON-NLS-1$ //$NON-NLS-2$

		JUnitModel model= JUnitCorePlugin.getModel();
		TestRunSession imported= JUnitModel.importTestRunSession(resultFile.toFile());
		fSessionsToRemove.add(imported);

		assertEquals(resultFile.toAbsolutePath().normalize(), imported.getImportedFromFile());
		assertEquals("first run", imported.getTestRunName()); //$NON-NLS-1$

		writeTestRun(resultFile, "second run", "secondTest"); //$NON-NLS-1$ //$NON-NLS-2$
		TestRunSession reloaded= JUnitModel.reloadTestRunSession(imported);
		assertNotNull(reloaded);
		fSessionsToRemove.add(reloaded);

		assertNotSame(imported, reloaded);
		assertEquals(resultFile.toAbsolutePath().normalize(), reloaded.getImportedFromFile());
		assertEquals("second run", reloaded.getTestRunName()); //$NON-NLS-1$
		assertFalse(model.getTestRunSessions().contains(imported));
		assertTrue(model.getTestRunSessions().contains(reloaded));
	}

	@Test
	public void testFailedReloadKeepsPreviouslyImportedSession() throws Exception {
		Path resultFile= fTemporaryDirectory.resolve("results.xml"); //$NON-NLS-1$
		writeTestRun(resultFile, "valid run", "test"); //$NON-NLS-1$ //$NON-NLS-2$

		JUnitModel model= JUnitCorePlugin.getModel();
		TestRunSession imported= JUnitModel.importTestRunSession(resultFile.toFile());
		fSessionsToRemove.add(imported);

		Files.writeString(resultFile, "<testrun", StandardCharsets.UTF_8, //$NON-NLS-1$
				java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
		try {
			JUnitModel.reloadTestRunSession(imported);
			fail("Expected invalid XML to fail"); //$NON-NLS-1$
		} catch (CoreException expected) {
			// Expected.
		}

		assertTrue(model.getTestRunSessions().contains(imported));
		assertEquals("valid run", imported.getTestRunName()); //$NON-NLS-1$
	}

	@Test
	public void testFileWatcherReportsExternalModification() throws Exception {
		Path resultFile= fTemporaryDirectory.resolve("results.xml"); //$NON-NLS-1$
		writeTestRun(resultFile, "first run", "test"); //$NON-NLS-1$ //$NON-NLS-2$

		CountDownLatch changed= new CountDownLatch(1);
		AtomicReference<Path> changedFile= new AtomicReference<>();
		try (ImportedTestRunFileWatcher watcher= new ImportedTestRunFileWatcher(path -> {
			changedFile.set(path);
			changed.countDown();
		})) {
			watcher.watch(resultFile);
			writeTestRun(resultFile, "second run", "test"); //$NON-NLS-1$ //$NON-NLS-2$

			assertTrue("Expected a WatchService notification", changed.await(10, TimeUnit.SECONDS)); //$NON-NLS-1$
			assertEquals(resultFile.toAbsolutePath().normalize(), changedFile.get());
		}
	}

	private static void writeTestRun(Path file, String runName, String testName) throws Exception {
		String xml= "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" //$NON-NLS-1$
				+ "<testrun name=\"" + runName //$NON-NLS-1$
				+ "\" tests=\"1\" started=\"1\" failures=\"0\" errors=\"0\" ignored=\"0\">\n" //$NON-NLS-1$
				+ "  <testsuite name=\"example.Tests\" time=\"0.0\">\n" //$NON-NLS-1$
				+ "    <testcase name=\"" + testName //$NON-NLS-1$
				+ "\" classname=\"example.Tests\" time=\"0.0\"/>\n" //$NON-NLS-1$
				+ "  </testsuite>\n" //$NON-NLS-1$
				+ "</testrun>\n"; //$NON-NLS-1$
		Files.writeString(file, xml, StandardCharsets.UTF_8);
	}
}
