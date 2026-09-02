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

package org.eclipse.jdt.internal.junit.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.eclipse.jdt.junit.model.ITestElement.Result;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.junit.BasicElementLabels;
import org.eclipse.jdt.internal.junit.JUnitCorePlugin;
import org.eclipse.jdt.internal.junit.Messages;
import org.eclipse.jdt.internal.junit.util.XmlProcessorFactoryJdtJunit;

public final class TestRunSessionHistory {

	private static final String XML_SUFFIX= ".xml"; //$NON-NLS-1$
	private static final String TEMP_SUFFIX= ".tmp"; //$NON-NLS-1$
	private static final String HISTORY_FILE_DATE_PATTERN= "yyyyMMdd-HHmmss.SSS"; //$NON-NLS-1$

	private static final ILog LOG= ILog.of(TestRunSessionHistory.class);

	private TestRunSessionHistory() {
	}

	public static List<TestRunSession> load(File historyDirectory, int maxCount) {
		deleteTemporaryFiles(historyDirectory);

		File[] historyFiles= historyDirectory.listFiles(file -> file.isFile() && file.getName().endsWith(XML_SUFFIX));
		if (historyFiles == null || historyFiles.length == 0)
			return List.of();

		Arrays.sort(historyFiles, Comparator.comparing(File::getName).reversed());
		int limit= Math.max(0, maxCount);
		List<TestRunSession> sessions= new ArrayList<>(Math.min(limit, historyFiles.length));
		for (File historyFile : historyFiles) {
			if (sessions.size() >= limit) {
				delete(historyFile);
				continue;
			}
			try {
				sessions.add(read(historyFile));
			} catch (CoreException e) {
				LOG.log(e.getStatus());
				delete(historyFile);
			}
		}
		return sessions;
	}

	public static void store(List<TestRunSession> sessions, File historyDirectory, int maxCount) {
		try {
			Files.createDirectories(historyDirectory.toPath());
		} catch (IOException e) {
			LOG.error("Could not create the JUnit history directory", e); //$NON-NLS-1$
			return;
		}

		deleteTemporaryFiles(historyDirectory);
		Set<String> retainedFileNames= new HashSet<>();
		int limit= Math.max(0, maxCount);
		for (TestRunSession session : sessions) {
			if (retainedFileNames.size() >= limit)
				break;
			if (session.getStartTime() == 0 || session.isRunning() || session.isStarting() || session.isKeptAlive())
				continue;

			File historyFile= historyFile(historyDirectory, session.getStartTime());
			String fileName= historyFile.getName();
			retainedFileNames.add(fileName);

			// Inactive sessions may already have been swapped to this file. Keeping an
			// existing file avoids loading a potentially large test tree during shutdown.
			if (historyFile.isFile())
				continue;

			File temporaryFile= new File(historyDirectory, fileName + TEMP_SUFFIX);
			try {
				Files.deleteIfExists(temporaryFile.toPath());
				JUnitModel.exportTestRunSession(session, temporaryFile);
				moveReplacing(temporaryFile, historyFile);
			} catch (CoreException | IOException e) {
				LOG.error("Could not persist JUnit test run history", e); //$NON-NLS-1$
				delete(temporaryFile);
				if (!historyFile.isFile())
					retainedFileNames.remove(fileName);
			}
		}

		File[] historyFiles= historyDirectory.listFiles(file -> file.isFile() && file.getName().endsWith(XML_SUFFIX));
		if (historyFiles != null) {
			for (File historyFile : historyFiles) {
				if (!retainedFileNames.contains(historyFile.getName()))
					delete(historyFile);
			}
		}
	}

	static TestRunSession read(File historyFile) throws CoreException {
		long startTime= readStartTime(historyFile);
		HeaderHandler handler= new HeaderHandler(historyFile, startTime);
		try {
			SAXParserFactory parserFactory= XmlProcessorFactoryJdtJunit.createSAXFactoryWithErrorOnDOCTYPE();
			SAXParser parser= parserFactory.newSAXParser();
			parser.parse(historyFile, handler);
		} catch (StopParsingException e) {
			return handler.getSession();
		} catch (ParserConfigurationException | SAXException | IOException | IllegalArgumentException e) {
			throw readError(historyFile, e);
		}
		throw readError(historyFile, new SAXException("JUnit history file has no test-run root element")); //$NON-NLS-1$
	}

	static File historyFile(File historyDirectory, long startTime) {
		String timestamp= new SimpleDateFormat(HISTORY_FILE_DATE_PATTERN).format(new Date(startTime));
		return new File(historyDirectory, timestamp + XML_SUFFIX);
	}

	private static long readStartTime(File historyFile) throws CoreException {
		String fileName= historyFile.getName();
		if (!fileName.endsWith(XML_SUFFIX))
			throw readError(historyFile, new IllegalArgumentException("Not a JUnit history XML file")); //$NON-NLS-1$

		String timestamp= fileName.substring(0, fileName.length() - XML_SUFFIX.length());
		SimpleDateFormat format= new SimpleDateFormat(HISTORY_FILE_DATE_PATTERN);
		format.setLenient(false);
		ParsePosition position= new ParsePosition(0);
		Date parsed= format.parse(timestamp, position);
		if (parsed == null || position.getIndex() != timestamp.length())
			throw readError(historyFile, new IllegalArgumentException("Invalid JUnit history file name")); //$NON-NLS-1$
		return parsed.getTime();
	}

	private static void deleteTemporaryFiles(File historyDirectory) {
		File[] temporaryFiles= historyDirectory.listFiles(file -> file.isFile() && file.getName().endsWith(TEMP_SUFFIX));
		if (temporaryFiles != null) {
			for (File temporaryFile : temporaryFiles)
				delete(temporaryFile);
		}
	}

	private static void moveReplacing(File source, File target) throws IOException {
		try {
			Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void delete(File file) {
		try {
			Files.deleteIfExists(file.toPath());
		} catch (IOException e) {
			LOG.error("Could not delete obsolete JUnit history file", e); //$NON-NLS-1$
		}
	}

	private static CoreException readError(File file, Exception e) {
		return new CoreException(new Status(IStatus.ERROR,
				JUnitCorePlugin.getPluginId(),
				Messages.format(ModelMessages.JUnitModel_could_not_read, BasicElementLabels.getPathLabel(file)),
				e));
	}

	private static IJavaProject resolveProject(String projectName) {
		if (projectName == null)
			return null;
		IJavaModel javaModel= JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
		IJavaProject project= javaModel.getJavaProject(projectName);
		return project.exists() ? project : null;
	}

	private static int readCount(Attributes attributes, String name) throws SAXException {
		String value= attributes.getValue(name);
		if (value == null)
			return 0;
		try {
			return Math.max(0, Integer.parseInt(value));
		} catch (NumberFormatException e) {
			throw new SAXException("Invalid JUnit history count: " + name, e); //$NON-NLS-1$
		}
	}

	private static final class HeaderHandler extends DefaultHandler {
		private final File fHistoryFile;
		private final long fStartTime;
		private TestRunSession fSession;

		HeaderHandler(File historyFile, long startTime) {
			fHistoryFile= historyFile;
			fStartTime= startTime;
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			if (!IXMLTags.NODE_TESTRUN.equals(qName))
				throw new SAXException("JUnit history file has an unexpected root element: " + qName); //$NON-NLS-1$

			String name= attributes.getValue(IXMLTags.ATTR_NAME);
			if (name == null)
				throw new SAXException("JUnit history file has no test-run name"); //$NON-NLS-1$

			int totalCount= readCount(attributes, IXMLTags.ATTR_TESTS);
			int startedCount= readCount(attributes, IXMLTags.ATTR_STARTED);
			int failureCount= readCount(attributes, IXMLTags.ATTR_FAILURES);
			int errorCount= readCount(attributes, IXMLTags.ATTR_ERRORS);
			int ignoredCount= readCount(attributes, IXMLTags.ATTR_IGNORED);
			boolean stopped= startedCount < totalCount && failureCount == 0 && errorCount == 0;
			Result result= result(errorCount, failureCount, stopped);

			fSession= new RestoredTestRunSession(
					fHistoryFile,
					name,
					resolveProject(attributes.getValue(IXMLTags.ATTR_PROJECT)),
					fStartTime,
					totalCount,
					startedCount,
					failureCount,
					errorCount,
					ignoredCount,
					stopped,
					result,
					attributes.getValue(IXMLTags.ATTR_INCLUDE_TAGS),
					attributes.getValue(IXMLTags.ATTR_EXCLUDE_TAGS));
			throw new StopParsingException();
		}

		TestRunSession getSession() {
			return fSession;
		}

		private static Result result(int errorCount, int failureCount, boolean stopped) {
			if (errorCount > 0)
				return Result.ERROR;
			if (failureCount > 0)
				return Result.FAILURE;
			if (stopped)
				return Result.UNDEFINED;
			return Result.OK;
		}
	}

	private static final class StopParsingException extends SAXException {
		private static final long serialVersionUID= 1L;
	}

	private static final class RestoredTestRunSession extends TestRunSession {
		private final File fHistoryFile;
		private final Result fHeaderResult;
		private boolean fLoadingContents;
		private boolean fContentsLoaded;

		RestoredTestRunSession(File historyFile, String testRunName, IJavaProject project, long startTime,
				int totalCount, int startedCount, int failureCount, int errorCount, int ignoredCount,
				boolean stopped, Result result, String includeTags, String excludeTags) {
			super(testRunName, project);
			fHistoryFile= historyFile;
			fHeaderResult= result;
			fStartTime= startTime;
			fTotalCount= totalCount;
			fStartedCount= startedCount;
			fFailureCount= failureCount;
			fErrorCount= errorCount;
			fIgnoredCount= ignoredCount;
			fIsStopped= stopped;
			setIncludeTags(includeTags);
			setExcludeTags(excludeTags);
		}

		@Override
		public Result getTestResult(boolean includeChildren) {
			return fContentsLoaded ? super.getTestResult(includeChildren) : fHeaderResult;
		}

		@Override
		public double getElapsedTimeInSeconds() {
			return fContentsLoaded ? super.getElapsedTimeInSeconds() : Double.NaN;
		}

		@Override
		public synchronized void swapIn() {
			if (fContentsLoaded) {
				super.swapIn();
				return;
			}
			if (fLoadingContents)
				return;

			fLoadingContents= true;
			try {
				JUnitModel.importIntoTestRunSession(fHistoryFile, this);
				fContentsLoaded= true;
			} catch (CoreException e) {
				LOG.log(e.getStatus());
				reset();
				fContentsLoaded= true;
				delete(fHistoryFile);
			} finally {
				fLoadingContents= false;
			}
		}

		@Override
		public synchronized void swapOut() {
			if (fContentsLoaded)
				super.swapOut();
		}

		@Override
		public void removeSwapFile() {
			delete(fHistoryFile);
		}

	}
}
