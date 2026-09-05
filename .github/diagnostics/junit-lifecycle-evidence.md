# JUnit lifecycle investigation — 2026-09-05

## Scope and limits

The investigation started with the two `testTerminateLaunch` failures in upstream PR #3152, Jenkins build PR-3152/2. Fetching the original Jenkins console and reports returned HTTP 403, including from a GitHub-hosted runner. The precise interleaving in that historical Jenkins build is therefore not established.

A separate, deterministic experiment against the actual Eclipse JUnit UI product code proves a stale-session lifecycle defect which can stop the active session's UI update job. This is not a timeout-only diagnosis. The original PR branch was not changed by this investigation.

## Baseline experiment: defect reproduced

Source revision: `af8e9e9bd1162a87538a9fd7e6ea2d85a808de6c`.

Run: https://github.com/carstenartur/eclipse.jdt.ui/actions/runs/33969834062

Job: https://github.com/carstenartur/eclipse.jdt.ui/actions/runs/33969834062/job/101316260516

Bounded extraction of its log: https://github.com/carstenartur/eclipse.jdt.ui/actions/runs/33970381437/job/101317713266

The PDE test activates session A, retains its listener, activates session B, then delivers `sessionTerminated()` to A's retained listener on a worker thread while dispatching the UI event queue. The product code itself is unchanged.

Observed output:

```text
LIFECYCLE_PROBE activeSession=active-session ...
expectedListener=TestRunnerViewPart$TestSessionListener@1c3400df actualListener=null
expectedJob=Update JUnit(59) actualJob=null
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
A retired session must not detach the active session listener
PDE_PROBE_EXECUTED True
```

Object names above are shortened for readability. The baseline Maven invocation used `maven.test.failure.ignore=true` to preserve the report, so its overall workflow status is green despite the reproduced test failure. The result above comes from the actual testcase XML, not the workflow conclusion.

## Cause

`TestRunnerViewPart.TestSessionListener` used the view's current `fTestRunSession`, `fTestSessionListener`, and `fUpdateJob` even when invoked for a retired session. `deregisterTestSessionListener(...)` therefore removed the current listener and `stopUpdateJobs()` stopped the current job. An in-progress `ListenerList` iteration retains removed listeners, so unregistering the old listener does not cancel a notification already in progress.

Eclipse's iterator contract explicitly documents this behavior:
https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/runtime/ListenerList.html

## Fix and successful verification

Fix branch: `carstenartur/eclipse.jdt.ui:fix/junit-retired-session-events`.

Commit: https://github.com/carstenartur/eclipse.jdt.ui/commit/b52d3f8e49df81d7d0c205fc539c508ce603488b

Parent: upstream master revision `6312a1cc907ad0a5ac5dd42e998eb0cbac686205`.

Verification run: https://github.com/carstenartur/eclipse.jdt.ui/actions/runs/33970503946

Verification job: https://github.com/carstenartur/eclipse.jdt.ui/actions/runs/33970503946/job/101318047920

All four lifecycle callbacks now run on the UI thread and check `fTestSessionListener == this` there before changing state. This serializes the check and lifecycle changes with session switches. No existing test timeout or assertion was weakened.

Both complete classes ran under Eclipse PDE/Tycho, Xvfb, Linux and Java 21:

```text
TestRunListenerTest5: Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
TestRunListenerTest6: Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
Total: Tests run: 20, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The XML was checked explicitly for successful execution of:

- `TestRunListenerTest5.testTerminateLaunch`
- `TestRunListenerTest6.testTerminateLaunch`
- `testRetiredSessionTerminationDoesNotStopActiveSession`
- `testRetiredSessionStopDoesNotStopActiveSession`
- `testRetiredSessionEndDoesNotStopActiveSession`
- `testActiveSessionTerminationStopsUpdateJobs`

The last case is a positive control: the current session must still detach its own listener and stop its own update job. The fix commit was created and pushed only after this verification passed. It contains exactly two Java files; the diagnostic workflows are confined to the separate diagnostic branch.

The full approximately 9,600-test upstream PR suite was not rerun as part of this targeted verification. The evidence proves the reproduced product defect and its correction, not the exact event sequence in the inaccessible original Jenkins run.
