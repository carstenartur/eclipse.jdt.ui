import pathlib
import sys

mode = sys.argv[1]


def replace_once(text, old, new):
    assert text.count(old) == 1, f'Expected exactly one edit location: {old[:80]!r}'
    return text.replace(old, new, 1)


if mode == 'junit':
    path = pathlib.Path('org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/junit/tests/TestRunListenerTest5.java')
    text = path.read_text()
    text = replace_once(text, 'import java.util.concurrent.CompletableFuture;', 'import java.util.concurrent.FutureTask;')
    text = replace_once(text, 'import org.eclipse.jdt.core.IJavaProject;', 'import org.eclipse.jdt.core.IElementChangedListener;\nimport org.eclipse.jdt.core.IJavaProject;')
    anchor = '\tprivate void assertSessionNotification(Consumer<ITestSessionListener> notification, boolean retired) throws Exception {'
    tests = '''	@Test
	public void testActiveSessionStopStopsUpdateJobs() throws Exception {
		assertSessionNotification(listener -> listener.sessionStopped(0), false);
	}

	@Test
	public void testActiveSessionEndStopsUpdateJobs() throws Exception {
		assertSessionNotification(listener -> listener.sessionEnded(0), false);
	}

'''
    text = replace_once(text, anchor, tests + anchor)
    text = replace_once(text, '\t\tField runningField= accessibleField(TestRunSession.class, "fIsRunning");', '\t\tField runningField= accessibleField(TestRunSession.class, "fIsRunning");\n\t\tField dirtyListenerField= accessibleField(TestRunnerViewPart.class, "fDirtyListener");\n\t\tObject previousDirtyListener= dirtyListenerField.get(view);')
    old = '''			CompletableFuture<Void> delivered= CompletableFuture.runAsync(() -> notification.accept(recipient));
			assertTrue("The session notification must complete", waitForCondition(delivered::isDone, 10000, 10));
			delivered.get();'''
    text = replace_once(text, old, '\t\t\tdeliverSessionNotification(notification, recipient);')
    text = replace_once(text, '\t\t\t\tassertSame("A retired session must not stop the active session\'s update job", currentJob, jobField.get(view));', '\t\t\t\tassertSame("A retired session must not stop the active session\'s update job", currentJob, jobField.get(view));\n\t\t\t\tassertTrue("The active update job must remain schedulable", ((Job) currentJob).shouldSchedule());')
    text = replace_once(text, '\t\t\t\tassertNull("The active session must still stop its own update job", jobField.get(view));', '\t\t\t\tassertNull("The active session must still stop its own update job", jobField.get(view));\n\t\t\t\tassertFalse("The stopped update job must not reschedule", ((Job) currentJob).shouldSchedule());')
    text = replace_once(text, '\t\t\trunningField.setBoolean(oldSession, false);', '''			Object dirtyListener= dirtyListenerField.get(view);
			if (dirtyListener != previousDirtyListener) {
				if (dirtyListener != null)
					JavaCore.removeElementChangedListener((IElementChangedListener) dirtyListener);
				dirtyListenerField.set(view, previousDirtyListener);
				if (previousDirtyListener != null)
					JavaCore.addElementChangedListener((IElementChangedListener) previousDirtyListener);
			}
			runningField.setBoolean(oldSession, false);''')
    anchor = '\tprivate static Field accessibleField(Class<?> declaringClass, String name) throws ReflectiveOperationException {'
    helper = '''	private static void deliverSessionNotification(Consumer<ITestSessionListener> notification, ITestSessionListener recipient) throws Exception {
		FutureTask<Void> delivered= new FutureTask<>(() -> {
			notification.accept(recipient);
			return null;
		});
		Thread notifier= new Thread(delivered, "JUnit view session notification");
		notifier.setDaemon(true);
		try {
			notifier.start();
			assertTrue("The session notification must complete", waitForCondition(delivered::isDone, 10000, 10));
			delivered.get();
		} finally {
			// Keep dispatching pending syncExec work even after an assertion fails.
			// The worker must finish before the previous view session is restored.
			assertTrue("The notification thread must finish before restoring the view",
					waitForCondition(() -> !notifier.isAlive(), 10000, 10));
		}
	}

'''
    text = replace_once(text, anchor, helper + anchor)
elif mode == 'occurrence':
    path = pathlib.Path('org.eclipse.jdt.text.tests/src/org/eclipse/jdt/text/tests/MarkOccurrenceTest.java')
    text = path.read_text()
    anchor = '\t\t\t// The AST callback arrives before the selection validator has seen this'
    text = replace_once(text, anchor, '''			Object previousAnnotations= editorAccessor.get("fOccurrenceAnnotations");
			Object previousTargetRegion= editorAccessor.get("fMarkOccurrenceTargetRegion");
			Object previousModificationStamp= editorAccessor.get("fMarkOccurrenceModificationStamp");
''' + anchor)
    anchor = '\t\t\tassertEquals(withExistingAnnotations ? 9 : 0, countOccurrenceAnnotations());'
    text = replace_once(text, anchor, anchor + '''
			assertSame(previousAnnotations, editorAccessor.get("fOccurrenceAnnotations"), "Cancellation must preserve the installed annotations");
			assertSame(previousTargetRegion, editorAccessor.get("fMarkOccurrenceTargetRegion"), "Cancellation must preserve the last successful target region");
			assertEquals(previousModificationStamp, editorAccessor.get("fMarkOccurrenceModificationStamp"), "Cancellation must preserve the last successful modification stamp");''')
else:
    raise ValueError(mode)
path.write_text(text)
print(path)
