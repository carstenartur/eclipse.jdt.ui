# Results: JDT Core option-cache race affecting editor save formatting

Date: 2026-09-05. This is a diagnostic result, not a production fix.

## Finding and scope

A cold `JavaModelManager.getOptions()` call can publish an obsolete options map
**after** a concurrent `JavaCore.setOptions()` has completed, overwriting the
newer cache. It can also republish obsolete data after a direct preference
change has invalidated that cache. A later, non-overlapping read then returns an
old formatter setting although the underlying preference has the new value.

The new JUnit tests reproduce both interleavings against the real JDT Core
implementation. A separate integration test reproduces the resulting missing
space after a cast through a real `JavaEditor.doSave()` call, with only
"format changed lines" enabled. The corresponding editor control passes.

This establishes a concrete **JDT Core defect with a demonstrated JDT UI save-action
consequence**. It does not establish that every failure collected in
[eclipse.jdt.ui#1445](https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/1445)
or [#79](https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/79) has this cause.
In particular, the additional space before `=` in the original report and the
`End position lies outside document range` exceptions have NOT been reproduced
by these tests. The historical test suite has not been run to spontaneous failure.

## Executed tests

Test revision: `f8f5adaffbd80ab1365589b0851d7b157ace5d58`.

[Complete CI run](https://github.com/carstenartur/eclipse.jdt.ui/actions/runs/33949505461)
— job `101261484795`.

[Original logs artifact](https://github.com/carstenartur/eclipse.jdt.ui/actions/runs/33949505461/artifacts/9964360332)
— SHA-256 `3199e90a825465b2d6561f13c9f2b3a78709bcf0f48a53307a82d5adbcdb5779`.

| Class | Test | Result |
| --- | --- | --- |
| OptionsCacheConsistencyTest | returnedOptionsAreDefensiveCopies | PASS |
| OptionsCacheConsistencyTest | sequentialUpdatesReachBothReadApis | PASS |
| OptionsCacheConsistencyTest | isolatedFormatterIsStableWithFixedSourceAndOptions | PASS: 200 formatter calls, four workers |
| OptionsCacheConsistencyTest | readerMustNotOverwriteCompletedSetOptions | FAIL: later cached value is obsolete |
| OptionsCacheConsistencyTest | readerMustNotUndoPreferenceInvalidation | FAIL: invalidated cache is republished with obsolete value |
| OptionsCacheConsistencyTest | completedOptionUpdateMustReachChangedLineFormatter | FAIL: missing cast space in real formatter output |
| SaveParticipantIntegrationTest | saveFormatsChangedLineWithConsistentOptions | PASS |
| SaveParticipantIntegrationTest | saveMustUseCompletedOptionUpdate | FAIL: missing cast space after actual editor save |

Totals: eight executed tests, four passing controls and four failing regression
assertions, zero ignored tests. The workflow is intentionally red while these
consistency assertions fail. This is not a claim that the bug is fixed.

The headless result was also reproduced in earlier runs
[33949136542](https://github.com/carstenartur/eclipse.jdt.ui/actions/runs/33949136542)
and [33949369948](https://github.com/carstenartur/eclipse.jdt.ui/actions/runs/33949369948).
Earlier harness revisions had a javac source-classpath problem and a missing UI
bundle dependency; neither is counted as evidence of a JDT defect.

## Actual output excerpts

Headless tests:

```text
OPTIONS_RACE writer=JavaCore.setOptions overlapping=do not insert persisted=insert subsequentCached=do not insert
OPTIONS_RACE writer=preferences.put overlapping=do not insert persisted=insert subsequentCached=do not insert
FORMATTER_CONTROL: 200 invocations, four workers, fixed source/options
DIAGNOSTIC_RESULT tests=6 failures=3 ignored=0
```

Editor control:

```text
SAVE_BEFORE race=false persisted=insert cache=insert project=insert bufferType=org.eclipse.jdt.internal.ui.javaeditor.DocumentAdapter length=107 stamp=12
SAVE_AFTER race=false length=105 stamp=14 text=package test1;\npublic class E1 {\n    public void foo( Object o ) {\n        String s = (String) o;\n    }\n}
```

Editor after controlled Core race:

```text
SAVE_BEFORE race=true persisted=insert cache=do not insert project=do not insert bufferType=org.eclipse.jdt.internal.ui.javaeditor.DocumentAdapter length=107 stamp=20
SAVE_AFTER race=true length=104 stamp=21 text=package test1;\npublic class E1 {\n    public void foo( Object o ) {\n        String s = (String)o;\n    }\n}
UI_DIAGNOSTIC_RESULT tests=2 failures=1 ignored=0
```

The before-save source text is identical in the two UI cases. Both tests also
check agreement between the editor document and compilation-unit buffer. The
observed effective project option follows the obsolete Core cache in the race
case, not the successfully updated underlying preference.

The minimal UI workbench additionally logs `OperationHistoryActionHandler`
null-context assertions in BOTH cases and a workspace-shutdown warning. These
are not the failures used for the cache diagnosis and are not the historical
malformed-edit exception. The editor control still produces the exact expected
text. These auxiliary UI-harness diagnostics should be cleaned up before
submitting the tests upstream; the independent headless reproduction is unaffected.

## Deterministic interleaving

The key under test is
`DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_CLOSING_PAREN_IN_CAST`.

1. Set the old preference to `do not insert`, then force a cold cache.
2. Start a real `JavaCore.getOptions()` call on a second thread.
3. Pause that reader immediately after the real preference node has returned the
   old value for this key, before `getOptions()` can publish its map.
4. On the writer thread, update the preference to `insert`. Wait for
   `JavaCore.setOptions()` (or the direct preference update) to complete.
5. Resume and join the reader.
6. Compare a NEW `JavaCore.getOptions().get(key)` call with
   `JavaCore.getOption(key)`, after both operations have completed.

The overlapping reader is allowed to return the old value; that is not the
assertion. The failure is the obsolete value returned by a subsequent call.

The scheduling barrier uses `CountDownLatch`, with timeouts, rather than sleeps.
Reflection is confined to forcing a cold cache and temporarily wrapping the
preference lookup nodes with transparent delegating proxies. The proxies return
actual preference values, not invented values. The option manager, preference
implementation and formatter are real Eclipse implementations. Global settings
and original lookup nodes are restored after the test.

## Source and runtime provenance

The branch started at JDT UI upstream commit
`6312a1cc907ad0a5ac5dd42e998eb0cbac686205`.

Runtime: Eclipse SDK `I20260826-2300`, Linux x86_64, Ubuntu 24.04.4,
Temurin JDK 25.0.4+1. Relevant loaded bundle versions:

```text
org.eclipse.jdt.core 3.47.0.v20260813-2102
org.eclipse.text 3.14.800.v20260815-0849
org.eclipse.core.runtime 3.35.0.v20260623-1631
```

SDK archive SHA-256:
`1a81564c817ba6016557f6b75e3c3a31e3d4532f42e8ab8883b74ebcc68ddbce`.

The workflow extracts `JavaModelManager.java` from the SDK source bundle and
compares complete whitespace-normalized `getOptions()` and `setOptions()` method
bodies against JDT Core upstream commit
`8c40c7d2ae12c0a32ab3cca1ab31b53956c65d51`. Both comparisons passed. This verifies
those methods, not the equivalence of every SDK bundle with all upstream master
sources.

[Inspected JavaModelManager source](https://github.com/eclipse-jdt/eclipse.jdt.core/blob/8c40c7d2ae12c0a32ab3cca1ab31b53956c65d51/org.eclipse.jdt.core/model/org/eclipse/jdt/internal/core/JavaModelManager.java)

The critical cold-reader publication in `getOptions()` is:

```java
this.optionsCache = new Hashtable<>(options);
```

It does not check whether the preferences or cache changed while the map was
being built. `setOptions()` publishes its newer cache separately; preference
listeners invalidate the cache separately. Defensive copies do not solve this
publication-order race.

## Fix direction, not implemented here

A production fix belongs in JDT Core. Cache publication must be coordinated with
both `setOptions()` and preference invalidation, for example using a generation
checked at publication time and retrying an obsolete computation. Any design must
also account for partially applied multi-key updates and reentrant preference
callbacks. Simply adding sleeps, more Hashtable copies, or a volatile reference
would not prevent the demonstrated late overwrite.

No production source was changed, no fix was benchmarked, no complete JDT suite
was run, and no upstream issue comment or pull request was posted. The tests are
kept under `diagnostics/jdt1445`, outside the existing product/test modules.
