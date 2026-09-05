# Save participant issue 1445: isolated JUnit diagnostics

This is a diagnostic branch, not a proposed production fix. It investigates
[eclipse.jdt.ui#1445](https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/1445)
and the related [#79](https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/79).

## Scope

The six JUnit 4 tests run inside a real headless Eclipse application against the
actual JDT Core option manager and formatter. No SWTBot is needed for this layer.
The option-cache tests replace preference lookup nodes temporarily with transparent
delegating proxies, solely to delay one read using latches. They do not replace
`JavaModelManager`, the preference implementation, or the formatter with mocks.
All tests use a throwaway workspace. Global options and lookup nodes are restored.

Controls check defensive copies and sequential updates, plus 200 formatter calls
across four workers with fixed source/options. Three interleaving tests assert that
a completed option update cannot be overwritten by an older in-flight read, that a
completed preference invalidation cannot be lost, and that a subsequent formatter
invocation observes the completed update. A red assertion is diagnostic evidence;
it must not be confused with a build failure or test-harness initialization error.

## Run

Use JDK 25 and an Eclipse SDK installation containing JUnit 4:

```sh
bash diagnostics/jdt1445/run.sh /path/to/eclipse
```

The branch-specific GitHub Actions workflow obtains SDK build I20260826-2300,
compares the source of `JavaModelManager.getOptions()` and `setOptions()` with
inspected upstream core commit `8c40c7d2ae12c0a32ab3cca1ab31b53956c65d51`, then
compiles and executes the tests. It records the actual bundle versions and output.
A source mismatch stops the workflow rather than silently claiming a reproduction
against current upstream code.

## Interpretation limits

A reproduced option-cache race establishes a JDT Core defect and a mechanism for
incorrect formatter settings. It does **not** establish that all historical save
participant failures (especially malformed edit offsets or unexpected spaces)
have the same cause. The editor save path, document snapshots and edit application
need their own PDE integration tests before closing either issue.

No production code is changed and no upstream pull request is opened by this
workflow. Results must be inspected before making a root-cause claim.
