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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;

import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.junit.TestRunListener;
import org.eclipse.jdt.junit.model.ITestRunSession;
import org.eclipse.jdt.testplugin.JavaProjectHelper;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;

import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IPerspectiveFactory;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.junit.launcher.TestKindRegistry;
import org.eclipse.jdt.internal.junit.model.TestRunSession;
import org.eclipse.jdt.internal.junit.ui.JUnitPlugin;
import org.eclipse.jdt.internal.junit.ui.TestRunnerViewPart;

/** Documentation-only fixture. Registered exclusively by the fork capture workflow. */
public class EnumSourceNewAndNoteworthyTest extends AbstractTestRunListenerTest {

	private static final String PERSPECTIVE_ID= "org.eclipse.jdt.ui.tests.enumSourceScreenshot";
	private static final String SOURCE= """
			package pack;

			import org.junit.jupiter.params.ParameterizedTest;
			import org.junit.jupiter.params.provider.EnumSource;

			public class ColorTest {
			    enum Color { RED, GREEN, BLUE }

			    @ParameterizedTest(name = "{index}: {0}")
			    @EnumSource(Color.class)
			    void testColor(Color color) {
			    }
			}
			""";

	public static class CodeAndResultsPerspective implements IPerspectiveFactory {
		@Override
		public void createInitialLayout(IPageLayout layout) {
			layout.setEditorAreaVisible(true);
			layout.addView(TestRunnerViewPart.NAME, IPageLayout.BOTTOM, 0.42f, layout.getEditorArea());
		}
	}

	@Override
	@Before
	public void setUp() throws Exception {
		fProject= JavaProjectHelper.createJavaProject("EnumSourceExample", "bin");
		JavaProjectHelper.addToClasspath(fProject, JavaCore.newContainerEntry(JUnitCore.JUNIT5_CONTAINER_PATH));
		JavaProjectHelper.addRTJar18(fProject);
	}

	@Test
	public void testExcludeAndReincludeFromRealContextMenu() throws Exception {
		assertNotNull(Display.getCurrent());
		IWorkbenchPage page= JUnitPlugin.getActivePage();
		IPerspectiveDescriptor previous= page.getPerspective();
		IPerspectiveDescriptor perspective= PlatformUI.getWorkbench().getPerspectiveRegistry().findPerspectiveWithId(PERSPECTIVE_ID);
		assertNotNull(perspective);
		Shell shell= page.getWorkbenchWindow().getShell();
		Rectangle bounds= shell.getBounds();
		boolean maximized= shell.getMaximized();
		IEditorPart editor= null;
		Menu menu= null;
		Path output= Path.of(System.getProperty("screenshots.dir", "target/screenshots")).toAbsolutePath();
		Files.createDirectories(output);
		List<String> evidence= new ArrayList<>();
		try {
			page.setPerspective(perspective);
			shell.setMaximized(false);
			shell.setBounds(20, 20, 720, 760);
			IType type= createType(SOURCE, "pack", "ColorTest.java");
			assertRun(runExample(type), 3);
			TestRunnerViewPart part= (TestRunnerViewPart) page.showView(TestRunnerViewPart.NAME);
			TableViewer viewer= prepareResults(part);
			Table table= viewer.getTable();
			editor= JavaUI.openInEditor(type.getCompilationUnit(), true, false);
			assertNotNull(editor);
			ITextOperationTarget operations= editor.getAdapter(ITextOperationTarget.class);
			assertTrue(operations instanceof ISourceViewer);
			StyledText text= ((ISourceViewer) operations).getTextWidget();
			text.setWordWrap(true);
			shell.layout(true, true);
			shell.forceActive();
			positionSource(text);
			Control capture= commonControl(tabFolder(text), tabFolder(table));
			assertTrue(capture.getSize().x <= 720);
			assertEquals(3, table.getItemCount());
			assertSourceVisible(text, "@EnumSource(Color.class)");
			Files.writeString(output.resolve("ColorTest-before.java"), type.getCompilationUnit().getSource());
			menu= openMenu(viewer, "GREEN");
			MenuItem exclude= findItem(menu, "Exclude Enum Value");
			assertTrue(exclude.isEnabled());
			selectNativeMenuItem(menu, exclude, false);
			evidence.add("before: total=3, skipped=0; Exclude Enum Value is enabled for GREEN");
			captureScreen(capture, output.resolve("junit-enumsource-exclude.png"));
			Files.writeString(output.resolve("menu-before.txt"), menuTexts(menu));
			menu.setVisible(false);
			exclude.notifyListeners(SWT.Selection, new Event());
			pumpUi();
			editor.doSave(new NullProgressMonitor());
			String excluded= type.getCompilationUnit().getSource();
			assertTrue(excluded, excluded.contains("EXCLUDE") && excluded.contains("\"GREEN\""));
			assertFalse(excluded, excluded.contains("@Disabled"));
			assertRun(runExample(type), 2);
			viewer= prepareResults(part);
			table= viewer.getTable();
			assertEquals(2, table.getItemCount());
			assertRows(table, "RED", "BLUE");
			positionSource(text);
			assertSourceVisible(text, "EXCLUDE");
			assertSourceVisible(text, "\"GREEN\"");
			Files.writeString(output.resolve("ColorTest-after-exclude.java"), excluded);
			menu= openMenu(viewer, "RED");
			MenuItem reinclude= findItem(menu, "Re-include Excluded Enum Values");
			assertNotNull(reinclude.getMenu());
			selectNativeMenuItem(menu, reinclude, true);
			Menu submenu= reinclude.getMenu();
			MenuItem green= findItem(submenu, "Re-include 'GREEN'");
			assertTrue(green.isEnabled());
			findItem(submenu, "Re-include All Enum Values");
			assertTrue("The actual re-inclusion submenu must be open", submenu.isVisible());
			evidence.add("after exclusion: total=2, skipped=0; RED and BLUE remain; re-inclusion menu offers GREEN and all values");
			captureScreen(capture, output.resolve("junit-enumsource-reinclude.png"));
			captureControl(capture, output.resolve("junit-enumsource-after-exclude.png"));
			Files.writeString(output.resolve("menu-after.txt"), menuTexts(menu) + "\nSUBMENU\n" + menuTexts(submenu));
			submenu.setVisible(false);
			menu.setVisible(false);
			green.notifyListeners(SWT.Selection, new Event());
			pumpUi();
			editor.doSave(new NullProgressMonitor());
			assertFalse(type.getCompilationUnit().getSource().contains("EXCLUDE"));
			assertRun(runExample(type), 3);
			assertRows(prepareResults(part).getTable(), "RED", "GREEN", "BLUE");
			evidence.add("after re-including GREEN: total=3, skipped=0; RED, GREEN and BLUE restored");

			// Also exercise the real re-include-all action, not only the individual item.
			menu= openMenu(prepareResults(part), "GREEN");
			exclude= findItem(menu, "Exclude Enum Value");
			menu.setVisible(false);
			exclude.notifyListeners(SWT.Selection, new Event());
			pumpUi();
			menu= openMenu(prepareResults(part), "RED");
			reinclude= findItem(menu, "Re-include Excluded Enum Values");
			selectNativeMenuItem(menu, reinclude, true);
			MenuItem all= findItem(reinclude.getMenu(), "Re-include All Enum Values");
			reinclude.getMenu().setVisible(false);
			menu.setVisible(false);
			all.notifyListeners(SWT.Selection, new Event());
			pumpUi();
			editor.doSave(new NullProgressMonitor());
			assertFalse(type.getCompilationUnit().getSource().contains("EXCLUDE"));
			assertRun(runExample(type), 3);
			evidence.add("Re-include All Enum Values: total=3, skipped=0; filter removed");
			Files.writeString(output.resolve("ColorTest-restored.java"), type.getCompilationUnit().getSource());
			Files.writeString(output.resolve("enumsource-evidence.txt"), String.join("\n", evidence) + "\n");
		} finally {
			if (menu != null && !menu.isDisposed())
				menu.setVisible(false);
			if (editor != null)
				page.closeEditor(editor, false);
			page.setPerspective(previous);
			shell.setBounds(bounds);
			shell.setMaximized(maximized);
		}
	}

	private static TableViewer prepareResults(TestRunnerViewPart part) {
		part.setLayoutMode(TestRunnerViewPart.LAYOUT_FLAT);
		part.getTestViewer().setShowFailuresOrIgnoredOnly(false, false, TestRunnerViewPart.LAYOUT_FLAT);
		part.getTestViewer().processChangesInUI();
		TableViewer viewer= (TableViewer) part.getTestViewer().getActiveViewer();
		Control pane= viewer.getTable();
		while (pane.getParent() != null && !(pane.getParent() instanceof SashForm))
			pane= pane.getParent();
		assertTrue(pane.getParent() instanceof SashForm);
		((SashForm) pane.getParent()).setMaximizedControl(pane);
		return viewer;
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

	private static void assertRun(TestRunSession session, int total) {
		assertEquals(total, session.getTotalCount());
		assertEquals(total, session.getStartedCount());
		assertEquals(0, session.getIgnoredCount());
		assertEquals(0, session.getFailureCount());
		assertEquals(0, session.getErrorCount());
	}

	private static void assertRows(Table table, String... names) {
		assertEquals(names.length, table.getItemCount());
		for (String name : names) {
			boolean found= false;
			for (TableItem item : table.getItems())
				found |= item.getText().contains(name);
			assertTrue("Expected invocation " + name, found);
		}
	}

	private static Menu openMenu(TableViewer viewer, String value) throws Exception {
		Table table= viewer.getTable();
		TableItem selected= null;
		for (TableItem item : table.getItems()) {
			if (item.getText().contains(value))
				selected= item;
		}
		assertNotNull("Invocation " + value, selected);
		viewer.setSelection(new StructuredSelection(selected.getData()), true);
		table.setFocus();
		pumpUi();
		Menu menu= table.getMenu();
		assertNotNull(menu);
		var location= table.toDisplay(145, 20);
		menu.setLocation(location.x, location.y);
		menu.setVisible(true);
		pumpUi();
		assertTrue(menu.isVisible());
		return menu;
	}

	private static MenuItem findItem(Menu menu, String label) {
		for (MenuItem item : menu.getItems()) {
			if (item.getText().replace("&", "").equals(label))
				return item;
		}
		throw new AssertionError("Missing " + label + " in " + menuTexts(menu));
	}

	private static String menuTexts(Menu menu) {
		List<String> labels= new ArrayList<>();
		for (MenuItem item : menu.getItems())
			labels.add(item.getText());
		return String.join("\n", labels);
	}

	private static void selectNativeMenuItem(Menu menu, MenuItem target, boolean openSubmenu) throws Exception {
		key(SWT.HOME);
		for (MenuItem item : menu.getItems()) {
			if (item == target)
				break;
			if ((item.getStyle() & SWT.SEPARATOR) == 0 && item.isEnabled())
				key(SWT.ARROW_DOWN);
		}
		if (openSubmenu)
			key(SWT.ARROW_RIGHT);
		pumpUi();
	}

	private static void key(int code) throws Exception {
		Display display= Display.getCurrent();
		Event event= new Event();
		event.type= SWT.KeyDown;
		event.keyCode= code;
		assertTrue(display.post(event));
		event.type= SWT.KeyUp;
		assertTrue(display.post(event));
		pumpUi();
	}

	private static void positionSource(StyledText text) throws Exception {
		pumpUi();
		int start= text.getText().indexOf("public class ColorTest");
		assertTrue(start >= 0);
		text.setCaretOffset(start);
		text.setTopIndex(text.getLineAtOffset(start));
		pumpUi();
	}

	private static void assertSourceVisible(StyledText text, String needle) {
		int start= text.getText().indexOf(needle);
		assertTrue(start >= 0);
		var point= text.getLocationAtOffset(start);
		assertTrue("Visible source: " + needle, point.y >= 0 && point.y + text.getLineHeight() <= text.getClientArea().height);
	}

	private static Control tabFolder(Control control) {
		while (!(control instanceof CTabFolder) && control.getParent() != null)
			control= control.getParent();
		assertTrue(control instanceof CTabFolder);
		return control;
	}

	private static Control commonControl(Control editor, Control view) {
		for (Control candidate= editor; candidate != null; candidate= candidate.getParent()) {
			for (Control child= view; child != null; child= child.getParent()) {
				if (child == candidate)
					return candidate;
			}
		}
		throw new AssertionError("No shared editor/view control");
	}

	private static void captureScreen(Control control, Path path) throws Exception {
		pumpUi();
		var location= control.toDisplay(0, 0);
		Image image= new Image(control.getDisplay(), control.getSize().x, control.getSize().y);
		GC gc= new GC(control.getDisplay());
		try {
			gc.copyArea(image, location.x, location.y);
			saveImage(image, path);
		} finally {
			gc.dispose();
			image.dispose();
		}
	}

	private static void captureControl(Control control, Path path) {
		Image image= new Image(control.getDisplay(), control.getSize().x, control.getSize().y);
		GC gc= new GC(image);
		try {
			assertTrue(control.print(gc));
			saveImage(image, path);
		} finally {
			gc.dispose();
			image.dispose();
		}
	}

	private static void saveImage(Image image, Path path) {
		ImageLoader loader= new ImageLoader();
		loader.data= new ImageData[] { image.getImageData() };
		loader.save(path.toString(), SWT.IMAGE_PNG);
	}

	private static void pumpUi() throws InterruptedException {
		Display display= Display.getCurrent();
		long until= System.nanoTime() + 350_000_000L;
		while (System.nanoTime() < until) {
			if (!display.readAndDispatch())
				Thread.sleep(10);
		}
		display.update();
	}
}
