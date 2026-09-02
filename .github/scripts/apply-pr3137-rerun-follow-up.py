from pathlib import Path


SESSION = Path("org.eclipse.jdt.junit.core/src/org/eclipse/jdt/internal/junit/model/TestRunSession.java")
HISTORY = Path("org.eclipse.jdt.junit.core/src/org/eclipse/jdt/internal/junit/model/TestRunSessionHistory.java")
VIEW = Path("org.eclipse.jdt.junit/src/org/eclipse/jdt/internal/junit/ui/TestRunnerViewPart.java")
TESTS = Path("org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/junit/tests/TestRunSessionHistoryTests.java")


def replace_exact(path: Path, old: str, new: str, expected: int = 1) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != expected:
        raise SystemExit(
            f"{path}: expected {expected} occurrence(s), found {count}: {old!r}"
        )
    path.write_text(text.replace(old, new), encoding="utf-8", newline="")


if "getPersistableRerunLaunchConfiguration" in SESSION.read_text(encoding="utf-8"):
    print("PR 3137 follow-up already applied")
    raise SystemExit(0)

replace_exact(
    SESSION,
    "import org.eclipse.debug.core.ILaunchConfiguration;\n"
    "import org.eclipse.debug.core.ILaunchManager;",
    "import org.eclipse.debug.core.ILaunchConfiguration;\n"
    "import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;\n"
    "import org.eclipse.debug.core.ILaunchManager;",
)
replace_exact(
    SESSION,
    "\t/**\n"
    "\t * @return <code>true</code> if this session has an existing launch\n"
    "\t *         configuration and a launch mode for rerunning it\n"
    "\t */\n"
    "\tpublic boolean canRerun() {\n"
    "\t\tILaunchConfiguration configuration= getRerunLaunchConfiguration();\n"
    "\t\treturn configuration != null && configuration.exists() && getRerunLaunchMode() != null;\n"
    "\t}\n",
    "\tstatic ILaunchConfiguration getPersistableRerunLaunchConfiguration(\n"
    "\t\t\tILaunchConfiguration configuration) {\n"
    "\t\twhile (configuration instanceof ILaunchConfigurationWorkingCopy workingCopy) {\n"
    "\t\t\tILaunchConfiguration original= workingCopy.getOriginal();\n"
    "\t\t\tif (original == null || original == configuration)\n"
    "\t\t\t\treturn null;\n"
    "\t\t\tconfiguration= original;\n"
    "\t\t}\n"
    "\t\treturn configuration;\n"
    "\t}\n\n"
    "\t/**\n"
    "\t * @return <code>true</code> if this session has a launch configuration\n"
    "\t *         and a launch mode for rerunning it\n"
    "\t */\n"
    "\tpublic boolean canRerun() {\n"
    "\t\tILaunchConfiguration configuration= getRerunLaunchConfiguration();\n"
    "\t\treturn configuration != null\n"
    "\t\t\t\t&& (configuration.exists() || configuration instanceof ILaunchConfigurationWorkingCopy)\n"
    "\t\t\t\t&& getRerunLaunchMode() != null;\n"
    "\t}\n",
)
replace_exact(
    HISTORY,
    "\t\t\tILaunchConfiguration launchConfiguration= session.getRerunLaunchConfiguration();",
    "\t\t\tILaunchConfiguration launchConfiguration= TestRunSession\n"
    "\t\t\t\t\t.getPersistableRerunLaunchConfiguration(session.getRerunLaunchConfiguration());",
)

replace_exact(
    VIEW,
    "if (launchConfiguration == null || !launchConfiguration.exists() || launchMode == null)",
    "if (launchConfiguration == null || launchMode == null || !fTestRunSession.canRerun())",
    expected=2,
)
replace_exact(
    VIEW,
    "\t\t\t\tILaunchConfigurationWorkingCopy tmp= configuration.copy(configName);",
    "\t\t\t\tILaunchConfigurationWorkingCopy tmp= configuration.getWorkingCopy();\n"
    "\t\t\t\ttmp.rename(configName);",
)
replace_exact(
    VIEW,
    "\t\t\tILaunchConfigurationWorkingCopy tmp= launchConfiguration.copy(configName);",
    "\t\t\tILaunchConfigurationWorkingCopy tmp= launchConfiguration.getWorkingCopy();\n"
    "\t\t\ttmp.rename(configName);",
)
replace_exact(
    VIEW,
    "\t\t\tif (launchConfiguration != null && launchConfiguration.exists()) {",
    "\t\t\tif (launchConfiguration != null && fTestRunSession.canRerun()) {",
)
replace_exact(
    VIEW,
    "\t\t\t\t\tILaunchConfigurationWorkingCopy tmp = launchConfiguration.copy(configName);",
    "\t\t\t\t\tILaunchConfigurationWorkingCopy tmp= launchConfiguration.getWorkingCopy();\n"
    "\t\t\t\t\ttmp.rename(configName);",
)
replace_exact(
    VIEW,
    "\t\t\tfRerunLastTestAction.setEnabled(true);",
    "\t\t\tupdateRerunActions();",
)
replace_exact(
    VIEW,
    "updateRerunFailedFirstAction();",
    "updateRerunActions();",
    expected=3,
)
replace_exact(
    VIEW,
    "\n\t\t\tfRerunLastTestAction.setEnabled(fTestRunSession.canRerun());",
    "",
)
replace_exact(
    VIEW,
    "\tprivate void updateRerunFailedFirstAction() {\n"
    "\t\tboolean state= hasErrorsOrFailures() && fTestRunSession.canRerun();\n"
    "\t\tfRerunFailedFirstAction.setEnabled(state);\n"
    "\t}\n",
    "\tprivate void updateRerunActions() {\n"
    "\t\tboolean canRerun= fTestRunSession != null && fTestRunSession.canRerun();\n"
    "\t\tfRerunLastTestAction.setEnabled(canRerun);\n"
    "\t\tfRerunFailedFirstAction.setEnabled(canRerun && hasErrorsOrFailures());\n"
    "\t}\n",
)

test_method = """\t@Test
\tpublic void restoresRelaunchContextForFailuresFirstWorkingCopy() throws Exception {
\t\tFile historyDirectory= fTemporaryFolder.newFolder(\"history\"); //$NON-NLS-1$
\t\tILaunchConfiguration configuration= createLaunchConfiguration();
\t\ttry {
\t\t\tILaunchConfigurationWorkingCopy failuresFirst= configuration.getWorkingCopy();
\t\t\tfailuresFirst.rename(configuration.getName() + \" (failures first)\"); //$NON-NLS-1$
\t\t\tfailuresFirst.setAttribute(JUnitLaunchConfigurationConstants.ATTR_FAILURES_NAMES,
\t\t\t\t\t\"test-failures.txt\"); //$NON-NLS-1$
\t\t\tTestRunSession session= relaunchableSession(\"failures first\", failuresFirst, //$NON-NLS-1$
\t\t\t\t\tILaunchManager.RUN_MODE);

\t\t\tassertFalse(failuresFirst.exists());
\t\t\tassertTrue(session.canRerun());
\t\t\tTestRunSessionHistory.store(List.of(session), historyDirectory, 10);
\t\t\tTestRunSession restored= TestRunSessionHistory.load(historyDirectory, 10).get(0);

\t\t\tassertTrue(restored.canRerun());
\t\t\tassertEquals(configuration.getMemento(), restored.getRerunLaunchConfiguration().getMemento());
\t\t\tassertEquals(ILaunchManager.RUN_MODE, restored.getRerunLaunchMode());
\t\t} finally {
\t\t\tif (configuration.exists())
\t\t\t\tconfiguration.delete();
\t\t}
\t}

"""
replace_exact(
    TESTS,
    "\t@Test\n"
    "\tpublic void missingLaunchConfigurationDisablesRelaunchWithoutLosingHistory() throws Exception {",
    test_method
    + "\t@Test\n"
    + "\tpublic void missingLaunchConfigurationDisablesRelaunchWithoutLosingHistory() throws Exception {",
)

print("Applied PR 3137 follow-up")
