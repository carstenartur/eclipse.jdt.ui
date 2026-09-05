"""Prepare a narrowly scoped test correction; never change production code here."""
import argparse
import hashlib
import json
from pathlib import Path

PATH = Path('org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/junit/tests/TestRunListenerTest5.java')
ORIGINAL_BLOB = '63f2fc0df6cf60e7f4c51bb3d836b9ead065f6e0'


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise ValueError(f'Expected exactly one source match: {old[:100]!r}')
    return text.replace(old, new, 1)


def corrected(text):
    text = replace_once(text, 'import java.util.concurrent.atomic.AtomicLong;\n',
                        'import java.util.concurrent.atomic.AtomicLong;\nimport java.util.concurrent.atomic.AtomicReference;\n')
    text = replace_once(text, 'import org.eclipse.jdt.junit.model.ITestElement.Result;\n',
                        'import org.eclipse.jdt.junit.model.ITestElement.Result;\nimport org.eclipse.jdt.junit.model.ITestRunSession;\n')
    text = replace_once(text, 'import org.eclipse.jdt.internal.junit.JUnitCorePlugin;\n', '')
    text = replace_once(text, 'import org.eclipse.jdt.internal.junit.model.ITestRunSessionListener;\n', '')
    text = replace_once(text,
        '\t\tTestSessionListener sessionListener = new TestSessionListener();\n'
        '\t\tTestRunSessionListener runSessionListener = new TestRunSessionListener();\n'
        '\t\tJUnitCorePlugin.getModel().addTestRunSessionListener(runSessionListener);\n'
        '\t\ttry {\n'
        '\t\t\tconfiguration.launch(ILaunchManager.RUN_MODE, null);\n'
        '\t\t\twaitForCondition(launchesListener.fLaunchChanged::get, 30 * 1000, 1000);\n',
        '\t\tTestSessionListener sessionListener = new TestSessionListener();\n'
        '\t\tAtomicReference<TestRunSession> startedSession= new AtomicReference<>();\n'
        '\t\tTestRunListener runSessionListener= new TestRunListener() {\n'
        '\t\t\t@Override\n'
        '\t\t\tpublic void sessionStarted(ITestRunSession session) {\n'
        '\t\t\t\tif (session instanceof TestRunSession testRunSession && testRunSession.getLaunch() != null\n'
        '\t\t\t\t\t\t&& configuration.equals(testRunSession.getLaunch().getLaunchConfiguration())) {\n'
        '\t\t\t\t\tstartedSession.set(testRunSession);\n'
        '\t\t\t\t}\n'
        '\t\t\t}\n'
        '\t\t};\n'
        '\t\tJUnitCore.addTestRunListener(runSessionListener);\n'
        '\t\ttry {\n'
        '\t\t\t// This test needs a view, independently of preceding tests and show-on-error preferences.\n'
        '\t\t\tassertNotNull(JUnitPlugin.showTestRunnerViewPartInActivePage());\n'
        '\t\t\tILaunch launch= configuration.launch(ILaunchManager.RUN_MODE, null);\n'
        '\t\t\t// A launch change only announces the port; the remote VM may not have started JUnit yet.\n'
        '\t\t\t// Keep the job-scheduling timeout separate from this session-start prerequisite.\n'
        '\t\t\tassertTrue("Unexpected timeout on JUnit session start",\n'
        '\t\t\t\t\twaitForCondition(() -> startedSession.get() != null, 30 * 1000, 100));\n'
        '\t\t\tassertSame("Expected the session belonging to this launch", launch, startedSession.get().getLaunch());\n')
    text = replace_once(text, '\t\t\trunSessionListener.fTestRunSession.addTestSessionListener(sessionListener);',
                        '\t\t\tstartedSession.get().addTestSessionListener(sessionListener);')
    text = replace_once(text, '\t\t\tJUnitCorePlugin.getModel().removeTestRunSessionListener(runSessionListener);',
                        '\t\t\tJUnitCore.removeTestRunListener(runSessionListener);\n'
                        '\t\t\tTestRunSession session= startedSession.get();\n'
                        '\t\t\tif (session != null)\n'
                        '\t\t\t\tsession.removeTestSessionListener(sessionListener);')
    obsolete = '''\tprivate static class TestRunSessionListener implements ITestRunSessionListener  {

		private TestRunSession fTestRunSession;

		public TestRunSessionListener() {
		}

		@Override
		public void sessionAdded(TestRunSession testRunSession) {
			fTestRunSession= testRunSession;
		}

		@Override
		public void sessionRemoved(TestRunSession testRunSession) {
		}
	}

'''
    return replace_once(text, obsolete, '')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--original', action='store_true')
    parser.add_argument('--delay-agent')
    args = parser.parse_args()
    data = PATH.read_bytes()
    actual = hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
    if actual != ORIGINAL_BLOB:
        raise ValueError(f'Wrong input blob: {actual}')
    text = data.decode('utf-8')
    if not args.original:
        text = corrected(text)
    if args.delay_agent:
        declaration = '\t\tILaunchConfigurationWorkingCopy configuration= createLaunchConfiguration(aTestCase, testKindId, null, launchesListener);\n'
        # This injection belongs only to the diagnostic source, never to the PR correction.
        setting = '\t\tconfiguration.setAttribute(org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, ' + json.dumps('-javaagent:' + args.delay_agent) + ');\n'
        text = replace_once(text, declaration, declaration + setting)
    PATH.write_bytes(text.encode('utf-8'))


if __name__ == '__main__':
    main()
