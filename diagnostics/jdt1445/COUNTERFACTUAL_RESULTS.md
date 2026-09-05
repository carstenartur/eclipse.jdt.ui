# Executed causal controls for JDT UI issue 1445

Recorded: 2026-09-05. This extends [RESULTS.md](RESULTS.md).

## Conclusion and scope

A deterministic JDT Core options-cache publication/invalidation race has been reproduced using the real Eclipse implementation. It causes a fresh options lookup, after both participating threads have finished, to disagree with the preferences value. An actual Java editor save action then uses the stale option and omits the space after a cast.

The causal intervention is confined to `JavaModelManager`: bypassing its cache-hit path makes all eight diagnostic JUnit tests pass. Recompiling the identical, unchanged class does not remove the failures. No formatter, editor, document, reconciler or text-store implementation was modified in either rebuild arm.

This establishes a Core defect and its actual save-action consequence. It does **not** establish that every historical failure collected in [eclipse.jdt.ui#1445](https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/1445) or [#79](https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/79) has this cause. In particular, the extra space before `=` in the original report and `MalformedTreeException: End position lies outside document range` have not been reproduced by these tests.

## Execution evidence

Executed test and experiment revision: `9cea8a527e4810e8c60d18215f1de122bac17120`.

[Completed GitHub Actions run 33950091797](https://github.com/carstenartur/eclipse.jdt.ui/actions/runs/33950091797), workflow **Save participant 1445 causal controls**. All three jobs completed on 2026-09-05.

| SDK / Core intervention | Headless JUnit | Real-editor JUnit | Total |
| --- | --- | --- | --- |
| Stock SDK | 6 executed, 3 failed | 2 executed, 1 failed | 8 executed, 4 failed |
| Identical `JavaModelManager` source rebuilt | 6 executed, 3 failed | 2 executed, 1 failed | 8 executed, 4 failed |
| Same rebuild, only options-cache hits bypassed | 6 executed, 0 failed | 2 executed, 0 failed | 8 executed, 0 failed |

No tests were ignored. The test sources and expectations are identical in all three arms.

**A green control job does not mean the stock tests passed.** `counterfactual.py verify` asserts the exact expected failure identities, test counts and nonzero test-process exit codes for the stock/rebuilt arms. For the uncached arm, it requires zero failures and successful process exits. Compilation failure, application startup failure, timeout, missing JUnit output or an unexpected failing test cannot satisfy these checks.

Jobs and evidence artifacts:

| Arm | Job | Artifact |
| --- | --- | --- |
| stock | [101263101906](https://github.com/carstenartur/eclipse.jdt.ui/actions/runs/33950091797/job/101263101906) | [jdt1445-causal-stock, 9964536230](https://github.com/carstenartur/eclipse.jdt.ui/actions/runs/33950091797/artifacts/9964536230) |
| rebuilt | [101263101663](https://github.com/carstenartur/eclipse.jdt.ui/actions/runs/33950091797/job/101263101663) | [jdt1445-causal-rebuilt, 9964536963](https://github.com/carstenartur/eclipse.jdt.ui/actions/runs/33950091797/artifacts/9964536963) |
| uncached | [101263101763](https://github.com/carstenartur/eclipse.jdt.ui/actions/runs/33950091797/job/101263101763) | [jdt1445-causal-uncached, 9964538452](https://github.com/carstenartur/eclipse.jdt.ui/actions/runs/33950091797/artifacts/9964538452) |

Artifact archive SHA-256 values returned by GitHub:

```text
stock     c42f416f1e8cf5d58a6d13c3dec809ea21f0eea4b055276a0b6cf44850e80000
rebuilt   dcc5803064924621d8975ac634527607215e62e079f83dca30055c4e664f1c3c
uncached  d021fd06c86fc735aebdcb9ea0b761459227fe4c609f6527532eed519df0b8ae
```

Each artifact contains the headless/UI outputs, exit statuses, summary, source intervention diff, SDK bundle hashes and workspace logs. These are temporary GitHub Actions artifacts, not indefinitely retained release assets.

## Exact failure mechanism

Affected class: `org.eclipse.jdt.internal.core.JavaModelManager` in **JDT Core**, not JDT UI.

The controlled setting is `DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_CLOSING_PAREN_IN_CAST`.

1. Reader A enters `JavaCore.getOptions()` with a cold options cache and starts collecting actual preferences. It reads the old cast-spacing value, `do not insert`.
2. Reader A pauses after the underlying preferences method has returned that old value. Writer B completes `JavaCore.setOptions(...)`, selecting `insert` and publishing the new cache. A second test instead uses a real preference `put(...)`, invalidating the cache.
3. Reader A resumes and reaches the unconditional `this.optionsCache = new Hashtable<>(options)` at the end of `getOptions()`. Its earlier snapshot overwrites the newly published cache, or resurrects the invalidated one.
4. Both operations complete. A **subsequent** `JavaCore.getOptions()` returns `do not insert`, whereas `JavaCore.getOption(KEY)` reads `insert` from preferences.

The overlapping reader is allowed to have observed the old value. The regression assertions concern a separate, later read after both operations have finished. That later discrepancy is the defect.

The tests use bounded `CountDownLatch` barriers, not sleep-based scheduling. A delegating preferences proxy delays a real read; it does not manufacture a fake preference value. Reflection is limited to installing/restoring this barrier and forcing a cold cache. Production cache logic and the preference writes remain real. This is a controlled interleaving, not a claim about the natural frequency of the original CI failure.

Representative stock/rebuilt output:

```text
OPTIONS_RACE writer=JavaCore.setOptions overlapping=do not insert persisted=insert subsequentCached=do not insert
OPTIONS_RACE writer=preferences.put overlapping=do not insert persisted=insert subsequentCached=do not insert
```

After bypassing cache hits, the overlapping read can still see `do not insert`, but `subsequentCached=insert` and the corresponding assertions pass.

The defensive-copy control passes on the stock implementation. Therefore this is not explained by a caller mutating the `Hashtable` returned by `getOptions()`: it is stale cache publication after an update/invalidation. More copying, synchronizing individual Hashtable operations, or making only the cache reference volatile would not by themselves prevent this demonstrated ordering.

## Tests and actual editor consequence

`OptionsCacheConsistencyTest` executes six tests:

- defensive-copy isolation;
- sequential writes visible through both read APIs;
- 200 independent formatter invocations across four worker threads with fixed source/options;
- a cold reader must not overwrite a completed `setOptions`;
- a cold reader must not undo preference invalidation;
- a fresh changed-line formatter must receive the completed option update.

The first three controls pass in every arm. The last three fail in stock/rebuilt and pass in uncached.

`SaveParticipantIntegrationTest` executes two JUnit tests inside a real SWT workbench. It creates a Java project and compilation unit, opens the real `JavaEditor`, changes its buffer and calls `editor.doSave(null)`, with formatting of changed lines enabled. It is API-driven Eclipse UI integration testing, not SWTBot click automation and not a mock save participant.

The baseline save passes. The save following the completed Core race fails in stock/rebuilt and passes with cache hits bypassed. Before saving, the test confirms that the intended source is in the editor and logs the persisted, cached and inherited project setting.

Representative rebuilt output, unchanged test source:

```text
SAVE_BEFORE race=false persisted=insert cache=insert project=insert bufferType=org.eclipse.jdt.internal.ui.javaeditor.DocumentAdapter length=107 stamp=12
SAVE_AFTER race=false length=105 stamp=14 text=package test1;\npublic class E1 {\n    public void foo( Object o ) {\n        String s = (String) o;\n    }\n}

SAVE_BEFORE race=true persisted=insert cache=do not insert project=do not insert bufferType=org.eclipse.jdt.internal.ui.javaeditor.DocumentAdapter length=107 stamp=20
SAVE_AFTER race=true length=104 stamp=21 text=package test1;\npublic class E1 {\n    public void foo( Object o ) {\n        String s = (String)o;\n    }\n}
```

The wrong inherited project option is already observable before `doSave()`. In the uncached arm, persisted/cache/project values agree on `insert`, and both saves produce the expected text with a space after `(String)`.

Thus this reproduction does not require concurrent corruption of the editor document to produce the demonstrated save failure. It does not exclude separate document/buffer races in other failures.

## Intervention and provenance

[Experiment implementation](counterfactual.py) and [workflow](../../.github/workflows/diagnose-save-participant-1445-counterfactual.yml).

The only source change in the uncached arm is:

```diff
-if ((cachedOptions = this.optionsCache) != null) {
+if (false && (cachedOptions = this.optionsCache) != null) {
```

Both rebuilt arms compile the same SDK `JavaModelManager.java` with `javac --release 21`; the unchanged-source rebuild controls for compiler/repackaging effects. The outer class and its 36 nested/generated classes are repackaged only into the disposable JDT Core bundle. The affected JAR signatures are removed from those disposable rebuilds. Other SDK bundle contents are verified unchanged by hashes.

This bypass deliberately sacrifices caching. It is a causal probe, **not an acceptable production fix or performance result**.

Test runtime:

```text
SDK             Eclipse I20260826-2300 / 4.41.0
OS              Ubuntu 24.04.4, Linux GTK x86_64
Java            Temurin 25.0.4+1 (runtime java.version 25.0.4.1)
JDT Core        3.47.0.v20260813-2102
Eclipse Text    3.14.800.v20260815-0849
Core Runtime    3.35.0.v20260623-1631
SDK SHA-256     1a81564c817ba6016557f6b75e3c3a31e3d4532f42e8ab8883b74ebcc68ddbce
```

The SDK archive checksum is checked in every arm. The normalized `getOptions()` and `setOptions(...)` source bodies are checked against hashes previously matched to inspected upstream JDT Core revision `8c40c7d2ae12c0a32ab3cca1ab31b53956c65d51`:

```text
getOptions  ae1d69fc7e8f91996c2ea9553c8a93861072cad1d768be9e606ed5f16cf94b06
setOptions  49636cb5d2d6ff5a848e422b318e826d715e96d99ee286c33c3a2f5d0cb8abc9
```

This verifies the relevant method implementations; it is not a claim that the entire Eclipse SDK or JDT UI binary is built from current master.

## Remaining limitations and next engineering step

The minimal UI harness logs null undo-context assertions from `OperationHistoryActionHandler` during document history notifications: 12 logged Eclipse errors in each arm, including the arm whose eight JUnit assertions all pass. These errors also occur in the passing baseline editor test. The headless runs report no logged Eclipse errors. These are not the historical out-of-range `MalformedTreeException`; they must not be conflated with it. The UI harness should be cleaned up before upstreaming the integration tests, and an all-green JUnit result here must not be advertised as an error-free workbench run.

No full upstream `SaveParticipantTest` or complete JDT regression suite has been run in this experiment. The original multi-symptom issue must remain open pending broader reproduction. Neither spontaneous failure rates nor production performance have been measured.

A production correction belongs in Core's cache lifecycle. It must prevent a snapshot computed before an update/invalidation from being published as current afterwards, while preserving safe preference-listener interaction, visibility, correct reset semantics and cache performance. Generation-checked publication or a carefully reviewed synchronization protocol are candidates, not implemented fixes. The deterministic interleaving tests should become Core regression tests; the real-editor test supplies a consumer-level check. Additional UI/Text investigation is still needed for the unmatched whitespace-before-assignment and out-of-range edit failures.
