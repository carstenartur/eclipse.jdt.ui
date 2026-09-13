/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0, available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.screenshots;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.junit.TestRunListener;
import org.eclipse.jdt.junit.model.ITestRunSession;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.internal.junit.JUnitCorePlugin;
import org.eclipse.jdt.internal.junit.launcher.JUnitLaunchConfigurationConstants;
import org.eclipse.jdt.internal.junit.model.TestRunSession;
import org.eclipse.jdt.internal.junit.ui.TestRunnerViewPart;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.utils.SWTUtils;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

/**
 * Opt-in documentation test. Runs outside the UI thread and captures real JUnit
 * widgets only after checking the corresponding result/model state.
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class JUnitNewAndNoteworthyScreenshotTest {
	private final SWTWorkbenchBot bot = new SWTWorkbenchBot();
	private final List<TestRunSession> sessions = new ArrayList<>();
	private final List<ILaunchConfiguration> configurations = new ArrayList<>();
	private final List<ILaunch> launches = new ArrayList<>();
	private IJavaProject project;
	private SWTBotView view;
	private Path output;

	@Test
	public void captureJUnitNewAndNoteworthy() throws Throwable {
		output = Path.of(System.getProperty("screenshots.dir", "target/screenshots")).toAbsolutePath();
		Files.createDirectories(output);
		try {
			prepareWorkbench();
			createExampleProject();
			writeProvenance();

			TestRunSession timing = runExample("ExecutionTimingExampleTest", false);
			assertEquals(0, timing.getFailureCount());
			assertEquals(0, timing.getErrorCount());
			expandTests();
			assertFalse(view.viewMenu("Show Execution Time Details").isChecked());
			view.viewMenu("Show Execution Time Details").click();
			await("CPU and non-CPU details from the launched test JVM", () ->
					treeText().contains("[CPU ") && treeText().contains("non-CPU"));
			assertTrue(treeText().contains("cpuBoundWork"));
			assertTrue(treeText().contains("waitingForResponse"));
			capture("junit-execution-time-details.png");

			// Exercise the independent presentation switches, rather than changing data.
			view.viewMenu("Show Execution Time").click();
			assertFalse(view.viewMenu("Show Execution Time").isChecked());
			assertTrue(view.viewMenu("Show Execution Time Details").isChecked());
			await("CPU details with elapsed-time labels hidden", () -> treeText().contains("[CPU "));
			view.viewMenu("Show Execution Time").click();
			view.viewMenu("Show Execution Time Details").click();

			// Both XML reports are exports of real runs, not hand-written result data.
			File source = output.resolve("imported-results.xml").toFile();
			File updated = output.resolve("updated-results.xml").toFile();
			TestRunSession failing = runExample("ImportedResultsExampleTest", false);
			assertEquals(1, failing.getFailureCount());
			JUnitCore.exportTestRunSession(failing, source);
			TestRunSession passing = runExample("ImportedResultsExampleTest", true);
			assertEquals(0, passing.getFailureCount());
			assertEquals(0, passing.getErrorCount());
			JUnitCore.exportTestRunSession(passing, updated);

			TestRunSession imported = (TestRunSession) JUnitCore.importTestRunSession(source);
			sessions.add(imported);
			await("the imported report in the JUnit view", () -> activeSession() == imported);
			expandTests();
			selectTest("addsNumbers");
			await("Reload Test Run enabled for the local-file import", () ->
					view.toolbarButton("Reload the imported test results from their source file").isEnabled());
			capture("junit-imported-results-before-reload.png");

			List<TestRunSession> before = JUnitCorePlugin.getModel().getTestRunSessions();
			int position = before.indexOf(imported);
			assertTrue(position >= 0);
			Files.copy(updated.toPath(), source.toPath(), StandardCopyOption.REPLACE_EXISTING);
			assertSame("Updating the file must not automatically replace the run", imported, activeSession());
			view.toolbarButton("Reload the imported test results from their source file").click();
			await("the reloaded report at the original history position", () -> {
				List<TestRunSession> current = JUnitCorePlugin.getModel().getTestRunSessions();
				return current.size() == before.size() && current.get(position) != imported
						&& activeSession() == current.get(position);
			});
			TestRunSession reloaded = activeSession();
			sessions.add(reloaded);
			assertEquals(0, reloaded.getFailureCount());
			assertEquals(0, reloaded.getErrorCount());
			assertEquals(2, reloaded.getTotalCount());
			assertEquals(before.size(), JUnitCorePlugin.getModel().getTestRunSessions().size());
			assertEquals(source, JUnitCorePlugin.getModel().getImportedTestRunSource(reloaded));
			assertFalse(JUnitCorePlugin.getModel().getTestRunSessions().contains(imported));
			expandTests();
			selectTest("addsNumbers");
			capture("junit-imported-results-after-reload.png");
			Files.writeString(output.resolve("verification.txt"),
					"One SWTBot test completed. Three real JUnit launches.\n"
					+ "Timing details displayed from the test JVM; display options are independent.\n"
					+ "Reload invoked through the toolbar: failures 1 -> 0; tests 2.\n"
					+ "History size and replacement position unchanged; source file retained.\n");
		} catch (Throwable failure) {
			bot.captureScreenshot(output.resolve("capture-failure.png").toString());
			throw failure;
		}
	}

	private void prepareWorkbench() {
		ui(() -> {
			var workbench = PlatformUI.getWorkbench();
			var intro = workbench.getIntroManager().getIntro();
			if (intro != null)
				workbench.getIntroManager().closeIntro(intro);
			var window = workbench.getActiveWorkbenchWindow();
			window.getShell().setMaximized(false);
			window.getShell().setBounds(20, 20, 1180, 520);
			try {
				window.getActivePage().showView("org.eclipse.jdt.junit.ResultView");
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		});
		view = bot.viewById("org.eclipse.jdt.junit.ResultView");
		view.show();
		ui(() -> {
			var ref = view.getViewReference();
			if (ref.getPage().getPartState(ref) != IWorkbenchPage.STATE_MAXIMIZED)
				ref.getPage().toggleZoom(ref);
		});
		view.viewMenu("Layout").menu("Vertical").click();
	}

	private void createExampleProject() throws Exception {
		IProject resource = ResourcesPlugin.getWorkspace().getRoot().getProject("JUnitNnExamples");
		assertFalse("Use a fresh screenshot workspace", resource.exists());
		resource.create(null);
		resource.open(null);
		project = JavaCore.create(resource);
		IProjectDescription description = resource.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		ICommand builder = description.newCommand();
		builder.setBuilderName(JavaCore.BUILDER_ID);
		description.setBuildSpec(new ICommand[] { builder });
		resource.setDescription(description, null);
		IFolder src = resource.getFolder("src");
		src.create(true, true, null);
		IFolder bin = resource.getFolder("bin");
		bin.create(true, true, null);
		assertNotNull("The screenshot runtime must have a default JDK", JavaRuntime.getDefaultVMInstall());
		project.setRawClasspath(new IClasspathEntry[] {
				JavaCore.newSourceEntry(src.getFullPath()),
				JavaRuntime.getDefaultJREContainerEntry(),
				JavaCore.newContainerEntry(JUnitCore.JUNIT4_CONTAINER_PATH)
		}, bin.getFullPath(), null);
		var options = new HashMap<String, String>();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		project.setOptions(options);
		IPackageFragment pack = project.getPackageFragmentRoot(src).createPackageFragment("examples", true, null);
		pack.createCompilationUnit("ExecutionTimingExampleTest.java", """
				package examples;
				import org.junit.Test;
				public class ExecutionTimingExampleTest {
				    private static volatile long result;
				    @Test public void cpuBoundWork() {
				        long end = System.nanoTime() + 450_000_000L;
				        long value = 1;
				        do {
				            for (int i = 0; i < 1000; i++) value = value * 1664525 + 1013904223;
				            result = value;
				        } while (System.nanoTime() - end < 0);
				    }
				    @Test public void waitingForResponse() throws InterruptedException {
				        Thread.sleep(350);
				    }
				}
				""", true, null);
		pack.createCompilationUnit("ImportedResultsExampleTest.java", """
				package examples;
				import static org.junit.Assert.*;
				import org.junit.Test;
				public class ImportedResultsExampleTest {
				    @Test public void addsNumbers() {
				        assertEquals(Boolean.getBoolean("example.fixed") ? 4 : 5, 2 + 2);
				    }
				    @Test public void acceptsEmptyInput() {
				        assertTrue("".isEmpty());
				    }
				}
				""", true, null);
		resource.build(IncrementalProjectBuilder.FULL_BUILD, null);
		for (IMarker marker : resource.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE))
			assertNotEquals(marker.getAttribute(IMarker.MESSAGE, "Build problem"),
					IMarker.SEVERITY_ERROR, marker.getAttribute(IMarker.SEVERITY, -1));
	}

	private TestRunSession runExample(String type, boolean fixed) throws Exception {
		var manager = DebugPlugin.getDefault().getLaunchManager();
		var config = manager.getLaunchConfigurationType(JUnitLaunchConfigurationConstants.ID_JUNIT_APPLICATION)
				.newInstance(null, manager.generateLaunchConfigurationName(type));
		config.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, project.getElementName());
		config.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, "examples." + type);
		config.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, "-Dexample.fixed=" + fixed);
		config.setAttribute(JUnitLaunchConfigurationConstants.ATTR_TEST_RUNNER_KIND, "org.eclipse.jdt.junit.loader.junit4");
		config.setAttribute(JUnitLaunchConfigurationConstants.ATTR_KEEPRUNNING, false);
		ILaunchConfiguration saved = config.doSave();
		configurations.add(saved);
		CountDownLatch finished = new CountDownLatch(1);
		AtomicReference<TestRunSession> result = new AtomicReference<>();
		TestRunListener listener = new TestRunListener() {
			@Override public void sessionFinished(ITestRunSession session) {
				result.set((TestRunSession) session);
				finished.countDown();
			}
		};
		JUnitCore.addTestRunListener(listener);
		try {
			launches.add(saved.launch(ILaunchManager.RUN_MODE, new NullProgressMonitor(), false));
			assertTrue("JUnit launch did not finish: " + type, finished.await(90, TimeUnit.SECONDS));
		} finally {
			JUnitCore.removeTestRunListener(listener);
		}
		TestRunSession session = result.get();
		assertNotNull(session);
		sessions.add(session);
		assertEquals(2, session.getTotalCount());
		await("completed " + type + " in the view", () -> activeSession() == session && !session.isRunning());
		return session;
	}

	private TestRunSession activeSession() {
		return uiValue(() -> ((TestRunnerViewPart) view.getViewReference().getView(false)).getTestRunSession());
	}

	private void expandTests() {
		view.show();
		await("test tree", () -> view.bot().tree().getAllItems().length > 0);
		for (SWTBotTreeItem item : view.bot().tree().getAllItems())
			item.expand();
	}

	private void selectTest(String name) {
		for (SWTBotTreeItem root : view.bot().tree().getAllItems()) {
			for (SWTBotTreeItem child : root.getItems()) {
				if (child.getText().startsWith(name)) {
					child.select();
					return;
				}
			}
		}
		fail("Missing visible test: " + name);
	}

	private String treeText() {
		var tree = view.bot().tree().widget;
		return uiValue(() -> {
			StringBuilder text = new StringBuilder();
			appendText(tree.getItems(), text);
			return text.toString();
		});
	}

	private static void appendText(TreeItem[] items, StringBuilder text) {
		for (TreeItem item : items) {
			text.append(item.getText()).append('\n');
			appendText(item.getItems(), text);
		}
	}

	private void capture(String filename) throws Exception {
		Control tree = view.bot().tree().widget;
		Control pane = uiValue(() -> {
			Control current = tree;
			while (current.getParent() != null && !(current instanceof CTabFolder))
				current = current.getParent();
			current.getShell().forceActive();
			current.getDisplay().update();
			return current;
		});
		// Logical tree labels can change before GTK paints the new native frame.
		// Observe a repaint, then allow a frame-clock turn before reading pixels.
		CountDownLatch painted = new CountDownLatch(1);
		Listener paintListener = event -> painted.countDown();
		ui(() -> {
			tree.addListener(SWT.Paint, paintListener);
			tree.redraw();
		});
		try {
			assertTrue("The JUnit tree did not repaint", painted.await(10, TimeUnit.SECONDS));
		} finally {
			ui(() -> tree.removeListener(SWT.Paint, paintListener));
		}
		CountDownLatch frameReady = new CountDownLatch(1);
		ui(() -> pane.getDisplay().timerExec(250, () -> {
			pane.getDisplay().update();
			frameReady.countDown();
		}));
		assertTrue("The native frame did not settle", frameReady.await(10, TimeUnit.SECONDS));
		Files.writeString(output.resolve(filename.replace(".png", ".txt")), treeText());
		Path file = output.resolve(filename);
		assertTrue("Cannot capture " + file, SWTUtils.captureScreenshot(file.toString(), pane));
		assertTrue("Empty screenshot: " + file, Files.size(file) > 1000);
	}

	private void writeProvenance() throws Exception {
		StringBuilder text = new StringBuilder("Real SWTBot capture of an Eclipse workbench.\n");
		text.append("Java: ").append(System.getProperty("java.version")).append('\n');
		for (String id : List.of("org.eclipse.jdt.junit", "org.eclipse.jdt.junit.core",
				"org.eclipse.jdt.junit.runtime", "org.eclipse.swtbot.eclipse.finder"))
			text.append(id).append(": ").append(Platform.getBundle(id).getVersion()).append('\n');
		Files.writeString(output.resolve("provenance.txt"), text);
	}

	private void await(String description, BooleanSupplier condition) {
		bot.waitUntil(new DefaultCondition() {
			@Override public boolean test() { return condition.getAsBoolean(); }
			@Override public String getFailureMessage() { return "Timed out waiting for " + description; }
		}, 60_000);
	}

	private static void ui(Runnable action) { Display.getDefault().syncExec(action); }

	private static <T> T uiValue(Supplier<T> action) {
		AtomicReference<T> result = new AtomicReference<>();
		ui(() -> result.set(action.get()));
		return result.get();
	}

	@After
	public void cleanUp() throws Exception {
		for (ILaunch launch : launches) {
			if (!launch.isTerminated() && launch.canTerminate())
				launch.terminate();
			DebugPlugin.getDefault().getLaunchManager().removeLaunch(launch);
		}
		for (TestRunSession session : sessions)
			JUnitCorePlugin.getModel().removeTestRunSession(session);
		for (ILaunchConfiguration configuration : configurations)
			configuration.delete();
		if (project != null && project.exists())
			project.getProject().delete(true, true, null);
	}
}
