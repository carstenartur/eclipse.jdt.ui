#!/usr/bin/env python3
from pathlib import Path
import sys

p = Path(sys.argv[1])
s = p.read_text()
replacements = {
    'import static org.junit.Assert.assertFalse;': 'import static org.junit.Assert.assertFalse;\nimport static org.junit.Assert.assertNotNull;\nimport static org.junit.Assert.assertNotSame;\nimport static org.junit.Assert.assertNull;\nimport static org.junit.Assert.assertSame;',
    'import java.nio.file.Files;': 'import java.lang.reflect.Field;\nimport java.lang.reflect.Method;\nimport java.nio.file.Files;',
    'import java.util.List;': 'import java.util.List;\nimport java.util.concurrent.CompletableFuture;',
    'import java.util.concurrent.atomic.AtomicLong;': 'import java.util.concurrent.atomic.AtomicLong;\nimport java.util.function.Consumer;',
    'import org.eclipse.jdt.testplugin.JavaProjectHelper;': 'import org.eclipse.jdt.testplugin.JavaProjectHelper;\nimport org.eclipse.jdt.testplugin.util.DisplayHelper;\n\nimport org.eclipse.swt.widgets.Display;',
    'import org.eclipse.jdt.internal.junit.ui.JUnitMessages;': 'import org.eclipse.jdt.internal.junit.ui.JUnitMessages;\nimport org.eclipse.jdt.internal.junit.ui.JUnitPlugin;\nimport org.eclipse.jdt.internal.junit.ui.TestRunnerViewPart;'
}
for before, after in replacements.items():
    assert s.count(before) == 1, before
    s = s.replace(before, after)
s = s.replace('Copyright (c) 2006, 2020', 'Copyright (c) 2006, 2026')
tests = '''
	@Test
	public void testRetiredSessionTerminationDoesNotStopActiveSession() throws Exception {
		assertSessionNotification(ITestSessionListener::sessionTerminated, true);
	}

	@Test
	public void testRetiredSessionStopDoesNotStopActiveSession() throws Exception {
		assertSessionNotification(listener -> listener.sessionStopped(0), true);
	}

	@Test
	public void testRetiredSessionEndDoesNotStopActiveSession() throws Exception {
		assertSessionNotification(listener -> listener.sessionEnded(0), true);
	}

	@Test
	public void testActiveSessionTerminationStopsUpdateJobs() throws Exception {
		assertSessionNotification(ITestSessionListener::sessionTerminated, false);
	}

	private void assertSessionNotification(Consumer<ITestSessionListener> notification, boolean retired) throws Exception {
		TestRunnerViewPart view= JUnitPlugin.showTestRunnerViewPartInActivePage();
		assertNotNull(view);
		DisplayHelper.driveEventQueue(Display.getCurrent());
		Field activeSessionField= accessibleField(TestRunnerViewPart.class, "fTestRunSession");
		Field listenerField= accessibleField(TestRunnerViewPart.class, "fTestSessionListener");
		Field jobField= accessibleField(TestRunnerViewPart.class, "fUpdateJob");
		Field runningField= accessibleField(TestRunSession.class, "fIsRunning");
		Method activate= TestRunnerViewPart.class.getDeclaredMethod("setActiveTestRunSession", TestRunSession.class);
		activate.setAccessible(true);
		Object previous= activeSessionField.get(view);
		TestRunSession oldSession= new TestRunSession("retired-session", fProject);
		TestRunSession currentSession= new TestRunSession("active-session", fProject);
		runningField.setBoolean(oldSession, true);
		runningField.setBoolean(currentSession, true);
		try {
			activate.invoke(view, oldSession);
			ITestSessionListener oldListener= (ITestSessionListener) listenerField.get(view);
			assertNotNull(oldListener);
			activate.invoke(view, currentSession);
			ITestSessionListener currentListener= (ITestSessionListener) listenerField.get(view);
			Object currentJob= jobField.get(view);
			assertNotNull(currentListener);
			assertNotNull(currentJob);
			assertNotSame(oldListener, currentListener);

			// A notifier may retain an old ListenerList snapshot while the UI
			// switches sessions. Deliver that event on a notification thread.
			ITestSessionListener recipient= retired ? oldListener : currentListener;
			CompletableFuture<Void> delivered= CompletableFuture.runAsync(() -> notification.accept(recipient));
			assertTrue("The session notification must complete", waitForCondition(delivered::isDone, 10000, 10));
			delivered.get();
			assertSame(currentSession, activeSessionField.get(view));
			if (retired) {
				assertSame("A retired session must not detach the active session listener", currentListener, listenerField.get(view));
				assertSame("A retired session must not stop the active session's update job", currentJob, jobField.get(view));
			} else {
				assertNull("The active session must still detach its own listener", listenerField.get(view));
				assertNull("The active session must still stop its own update job", jobField.get(view));
			}
		} finally {
			runningField.setBoolean(oldSession, false);
			runningField.setBoolean(currentSession, false);
			activate.invoke(view, previous);
		}
	}

	private static Field accessibleField(Class<?> declaringClass, String name) throws ReflectiveOperationException {
		Field field= declaringClass.getDeclaredField(name);
		field.setAccessible(true);
		return field;
	}
'''
end = s.rfind('}')
assert end >= 0
p.write_text(s[:end] + tests + s[end:])
