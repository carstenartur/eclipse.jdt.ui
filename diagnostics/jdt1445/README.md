# Save participant issue 1445: JUnit diagnostics

This is a diagnostic branch, not a proposed production fix. It investigates
[eclipse.jdt.ui#1445](https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/1445)
and the related [#79](https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/79).

**Executed results and evidence: [RESULTS.md](RESULTS.md).**

The tests demonstrate a JDT Core options-cache publication race, including its
missing-cast-space consequence through the actual Java editor save action. They
do not reproduce all historical failure signatures collected in those issues.

## Scope

Six JUnit 4 tests execute inside a real headless Eclipse application against the
actual JDT Core option manager and formatter. Two further JUnit tests run inside
a workbench and exercise a real Java project, editor document and
`JavaEditor.doSave()`. No SWTBot is required for these API-driven tests.

The option-cache tests temporarily wrap real preference lookup nodes with
transparent delegating proxies, solely to delay one read using latches. They do
not replace `JavaModelManager`, the preference implementation, or the formatter
with mock implementations. The proxies return actual preference values. Tests
use disposable workspaces and restore global options and original lookup nodes.

Controls check defensive copies and sequential updates, plus 200 formatter calls
across four workers with fixed source/options. Three headless interleaving tests
assert that a completed option update cannot be overwritten by an older
in-flight read, that a completed preference invalidation cannot be lost, and that
a subsequent formatter invocation observes the completed update. The editor
integration pair compares consistent options with the same controlled Core race.

At tested revision `f8f5adaffbd80ab1365589b0851d7b157ace5d58`, four controls pass
and four regression assertions fail, with no ignored tests. A red consistency
assertion is diagnostic evidence, not a claim that the defect has been fixed.
Build or test-harness initialization errors must not be counted as such evidence.

## Run

Use JDK 25 on Linux, with an Eclipse SDK containing JDT and JUnit 4.
**Use a disposable SDK copy:** the script installs the diagnostic bundle into
that SDK and updates its `configuration/org.eclipse.equinox.simpleconfigurator/bundles.info`.
It does not modify production source code.

```sh
# Headless Core/formatter tests:
bash diagnostics/jdt1445/run.sh /path/to/disposable/eclipse

# Real editor-save integration tests (requires Xvfb):
xvfb-run -a bash diagnostics/jdt1445/run.sh /path/to/disposable/eclipse jdt1445.diagnostics.ui
```

The branch-specific GitHub Actions workflow obtains SDK build I20260826-2300,
compares the complete whitespace-normalized `JavaModelManager.getOptions()` and
`setOptions()` method bodies with inspected upstream Core commit
`8c40c7d2ae12c0a32ab3cca1ab31b53956c65d51`, then compiles and executes both test
applications. It records loaded bundle versions and uploads the test output and
workspace log. A source mismatch stops execution rather than silently claiming
that a different implementation was tested. This comparison covers those two
methods, not every SDK bundle or all upstream master sources.

## Interpretation limits

The tests establish a JDT Core defect and demonstrate a resulting formatting
error through the actual JDT UI save path. They do **not** establish that all
historical save-participant failures share this cause. The extra space before
`=` and malformed-edit offset exceptions have not been reproduced, and the
original full test suite has not been run to spontaneous failure.

The minimal UI workbench also logs operation-history null-context assertions in
both the passing control and failing race case. These are documented in
[RESULTS.md](RESULTS.md), are not the cache-regression assertion, and should be
cleaned up before submitting an upstream integration-test change. The independent
headless Core reproduction is unaffected.

No production fix or upstream pull request is included. All diagnostics are kept
outside the existing product/test modules.
