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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.junit.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.junit.TestRunListener;
import org.eclipse.jdt.junit.model.ITestRunSession;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;

import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.viewers.TableViewer;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IPerspectiveFactory;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IType;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.junit.launcher.TestKindRegistry;
import org.eclipse.jdt.internal.junit.model.TestRunSession;
import org.eclipse.jdt.internal.junit.model.TestSuiteElement;
import org.eclipse.jdt.internal.junit.ui.JUnitPlugin;
import org.eclipse.jdt.internal.junit.ui.TestRunnerViewPart;

/** Fork-only documentation fixture; inherits the five existing UI regressions. */
public class DisabledParameterizedCodeScreenshotTest extends DisabledParameterizedTestViewTest {

	private static final String PERSPECTIVE_ID= "org.eclipse.jdt.ui.tests.junitCodeScreenshot";

	private static final String SOURCE= """
			package pack;

			import org.junit.jupiter.api.Disabled;
			import org.junit.jupiter.api.Test;
			import org.junit.jupiter.params.ParameterizedTest;
			import org.junit.jupiter.params.provider.ValueSource;

			public class ATestCase {
			    @Test
			    public void enabledTest() {
			    }

			    @Disabled("Not applicable on this platform")
			    @ParameterizedTest
			    @ValueSource(strings = { "one", "two" })
			    public void disabledParameterizedTest(String value) {
			    }
			}
			""";

	/** Registered only in the documentation workflow, never in product bundles. */
	public static class CodeAndResultsPerspective implements IPerspectiveFactory {
		@Override
		public void createInitialLayout(IPageLayout layout) {
			layout.setEditorAreaVisible(true);
			layout.addView(TestRunnerViewPart.NAME, IPageLayout.BOTTOM, 0.64f, layout.getEditorArea());
		}
	}

	@Override
	@Test
	public void testNewAndNoteworthyScreenshot() throws Exception {
		assertNotNull("Run this fixture on the UI thread", Display.getCurrent());
		IWorkbenchPage page= JUnitPlugin.getActivePage();
		IPerspectiveDescriptor previousPerspective= page.getPerspective();
		IPerspectiveDescriptor perspective= PlatformUI.getWorkbench().getPerspectiveRegistry().findPerspectiveWithId(PERSPECTIVE_ID);
		assertNotNull("Documentation perspective must be registered", perspective);
		Shell shell= page.getWorkbenchWindow().getShell();
		Rectangle previousBounds= shell.getBounds();
		boolean wasMaximized= shell.getMaximized();
		IEditorPart editor= null;
		SashForm resultSash= null;
		Control previousMaximizedControl= null;
		try {
			page.setPerspective(perspective);
			shell.setMaximized(false);
			shell.setBounds(20, 20, 720, 650);
			IType type= createType(SOURCE, "pack", "ATestCase.java");
			TestRunSession session= runExample(type);
			assertEquals(2, session.getTotalCount());
			assertEquals(2, session.getStartedCount());
			assertEquals(1, session.getIgnoredCount());
			assertEquals(0, session.getFailureCount());
			assertEquals(0, session.getErrorCount());
			assertEquals(0, session.getAssumptionFailureCount());

			TestRunnerViewPart part= (TestRunnerViewPart) page.showView(TestRunnerViewPart.NAME);
			part.setLayoutMode(TestRunnerViewPart.LAYOUT_FLAT);
			part.getTestViewer().setShowFailuresOrIgnoredOnly(false, false, TestRunnerViewPart.LAYOUT_FLAT);
			part.getTestViewer().processChangesInUI();
			Table table= ((TableViewer) part.getTestViewer().getActiveViewer()).getTable();
			assertEquals(2, table.getItemCount());
			int ignoredRows= 0;
			for (var item : table.getItems()) {
				if (item.getData() instanceof TestSuiteElement suite && suite.isIgnored())
					ignoredRows++;
			}
			assertEquals(1, ignoredRows);

			// The empty failure pane adds nothing to a successful/disabled example.
			Control resultPane= table;
			while (resultPane.getParent() != null && !(resultPane.getParent() instanceof SashForm))
				resultPane= resultPane.getParent();
			assertTrue(resultPane.getParent() instanceof SashForm);
			resultSash= (SashForm) resultPane.getParent();
			previousMaximizedControl= resultSash.getMaximizedControl();
			resultSash.setMaximizedControl(resultPane);

			editor= JavaUI.openInEditor(type.getCompilationUnit(), true, false);
			assertNotNull(editor);
			ITextOperationTarget operations= editor.getAdapter(ITextOperationTarget.class);
			assertTrue("Expected the real Java source viewer", operations instanceof ISourceViewer);
			StyledText text= ((ISourceViewer) operations).getTextWidget();
			assertEquals(SOURCE, type.getCompilationUnit().getSource());
			shell.layout(true, true);
			shell.forceActive();
			pumpUi();
			int classOffset= text.getText().indexOf("public class ATestCase");
			assertTrue(classOffset >= 0);
			text.setCaretOffset(classOffset);
			text.setTopIndex(text.getLineAtOffset(classOffset));
			table.deselectAll();
			pumpUi();

			assertVisible(text, "public void enabledTest()");
			assertVisible(text, "@Disabled(\"Not applicable on this platform\")");
			assertVisible(text, "@ParameterizedTest");
			assertVisible(text, "@ValueSource(strings = { \"one\", \"two\" })");
			assertVisible(text, "public void disabledParameterizedTest(String value)");
			for (var item : table.getItems()) {
				Rectangle row= item.getBounds();
				assertTrue("Both result rows must fit vertically", row.y >= 0 && row.y + row.height <= table.getClientArea().height);
				GC measure= new GC(table);
				try {
					assertTrue("Do not truncate " + item.getText(), measure.textExtent(item.getText()).x + 28 < table.getClientArea().width);
				} finally {
					measure.dispose();
				}
			}
			Control junitPane= tabFolder(table);
			assertTrue("The visible counter must match the run", containsText(junitPane, "2/2 (1 skipped)"));
			Control capture= tabFolder(text);
			while (capture != null && !containsControl(capture, junitPane))
				capture= capture.getParent();
			assertNotNull(capture);
			assertTrue(text.isVisible() && table.isVisible());
			assertTrue("Keep the native screenshot compact", capture.getSize().x <= 720 && capture.getSize().y <= 650);

			Path output= Path.of(System.getProperty("screenshots.dir", "target/screenshots")).toAbsolutePath();
			Files.createDirectories(output);
			Path screenshot= output.resolve("junit-disabled-parameterized-test-with-code.png");
			Image image= new Image(capture.getDisplay(), capture.getSize().x, capture.getSize().y);
			GC gc= new GC(image);
			try {
				assertTrue("Print the actual common editor/view control", capture.print(gc));
				ImageLoader loader= new ImageLoader();
				loader.data= new ImageData[] { image.getImageData() };
				loader.save(screenshot.toString(), SWT.IMAGE_PNG);
			} finally {
				gc.dispose();
				image.dispose();
			}
			Files.writeString(output.resolve("ATestCase.java"), type.getCompilationUnit().getSource());
			Files.writeString(output.resolve("code-and-results-evidence.txt"),
					"total=2\nstarted=2\nskipped=1\nfailures=0\nerrors=0\nvisibleAnnotations=3\n"
					+ "width=" + capture.getSize().x + "\nheight=" + capture.getSize().y + "\n"
					+ table.getItem(0).getText() + "\n" + table.getItem(1).getText() + "\n");
			assertTrue(Files.size(screenshot) > 1000);
		} finally {
			if (editor != null)
				page.closeEditor(editor, false);
			if (resultSash != null && !resultSash.isDisposed())
				resultSash.setMaximizedControl(previousMaximizedControl);
			page.setPerspective(previousPerspective);
			shell.setBounds(previousBounds);
			shell.setMaximized(wasMaximized);
		}
	}

	private TestRunSession runExample(IType type) throws Exception {
		TestRunLog log= new TestRunLog();
		AtomicReference<TestRunSession> finished= new AtomicReference<>();
		TestRunListener listener= new TestRunListener() {
			@Override
			public void sessionFinished(ITestRunSession session) {
				finished.set((TestRunSession) session);
				log.setDone();
			}
		};
		JUnitCore.addTestRunListener(listener);
		try {
			launchJUnit(type, TestKindRegistry.JUNIT5_TEST_KIND_ID, log);
		} finally {
			JUnitCore.removeTestRunListener(listener);
		}
		assertNotNull(finished.get());
		return finished.get();
	}

	private static void pumpUi() throws InterruptedException {
		Display display= Display.getCurrent();
		long until= System.nanoTime() + 800_000_000L;
		while (System.nanoTime() < until) {
			if (!display.readAndDispatch())
				Thread.sleep(10);
		}
		display.update();
	}

	private static void assertVisible(StyledText text, String needle) {
		int offset= text.getText().indexOf(needle);
		assertTrue("Source contains " + needle, offset >= 0);
		var start= text.getLocationAtOffset(offset);
		var end= text.getLocationAtOffset(offset + needle.length());
		Rectangle area= text.getClientArea();
		assertTrue("Source line is visible: " + needle, start.y >= 0 && start.y + text.getLineHeight() <= area.height);
		assertTrue("Source line is not truncated: " + needle, start.x >= 0 && end.x < area.width);
	}

	private static Control tabFolder(Control control) {
		while (control.getParent() != null && !(control instanceof CTabFolder))
			control= control.getParent();
		assertTrue(control instanceof CTabFolder);
		return control;
	}

	private static boolean containsControl(Control ancestor, Control child) {
		for (Control control= child; control != null; control= control.getParent()) {
			if (control == ancestor)
				return true;
		}
		return false;
	}

	private static boolean containsText(Control control, String expected) {
		if (control instanceof Text text && text.getText().contains(expected))
			return true;
		if (control instanceof Label label && label.getText().contains(expected))
			return true;
		if (control instanceof Composite composite) {
			for (Control child : composite.getChildren()) {
				if (containsText(child, expected))
					return true;
			}
		}
		return false;
	}
}
