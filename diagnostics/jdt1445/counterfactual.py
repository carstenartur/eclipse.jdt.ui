#!/usr/bin/env python3
"""Causal control, NOT a production fix. Use only a disposable Eclipse SDK.

Arms: stock; rebuild the identical SDK JavaModelManager source; or rebuild it
with ONLY the cached-return fast path disabled. All JUnit tests stay unchanged.
No Eclipse/UI/formatter/text-store code is modified. Source and jar diffs are
saved so compiler/repackaging effects can be distinguished from the intervention.
"""
from __future__ import annotations

import argparse
import difflib
import hashlib
import os
from pathlib import Path
import re
import subprocess
import zipfile

SOURCE = "org/eclipse/jdt/internal/core/JavaModelManager.java"
PREFIX = "org/eclipse/jdt/internal/core/JavaModelManager"
EXPECTED = {
    "public Hashtable<String, String> getOptions()": "ae1d69fc7e8f91996c2ea9553c8a93861072cad1d768be9e606ed5f16cf94b06",
    "public void setOptions(Hashtable<String, String> newOptions)": "49636cb5d2d6ff5a848e422b318e826d715e96d99ee286c33c3a2f5d0cb8abc9",
}


def unique(paths: list[Path]) -> Path:
    if len(paths) != 1:
        raise RuntimeError(f"Expected exactly one bundle, got {paths}")
    return paths[0]


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepare(sdk: Path, out: Path, mode: str) -> None:
    out.mkdir(parents=True, exist_ok=True)
    jars = sorted((sdk / "plugins").glob("*.jar"))
    before = {p.name: sha(p) for p in jars}
    source_jar = unique(list((sdk / "plugins").glob("org.eclipse.jdt.core.source_*.jar")))
    core = unique(list((sdk / "plugins").glob("org.eclipse.jdt.core_*.jar")))
    with zipfile.ZipFile(source_jar) as archive:
        original = archive.read(SOURCE).decode("utf-8")
    for signature, expected in EXPECTED.items():
        start = original.index(signature)
        end = original.index("\n\t}", start) + 3
        normalized = re.sub(r"\s+", " ", original[start:end]).strip()
        actual = hashlib.sha256(normalized.encode()).hexdigest()
        if actual != expected:
            raise RuntimeError(f"Inspected-source mismatch for {signature}: {actual}")
        print("SOURCE_MATCH", signature, actual, flush=True)
    changed = original
    if mode == "uncached":
        needle = "if ((cachedOptions = this.optionsCache) != null) {"
        if original.count(needle) != 1:
            raise RuntimeError("Cache fast-path pattern is not unique")
        changed = original.replace(needle, "if (false && (cachedOptions = this.optionsCache) != null) {")
    (out / "counterfactual.patch").write_text("".join(difflib.unified_diff(
        original.splitlines(True), changed.splitlines(True),
        fromfile="SDK/JavaModelManager.java", tofile=f"{mode}/JavaModelManager.java")))
    if mode != "stock":
        src = out / "src" / SOURCE
        src.parent.mkdir(parents=True, exist_ok=True)
        src.write_text(changed)
        classes = out / "classes"
        classes.mkdir(exist_ok=True)
        empty = out / "empty-sourcepath"
        empty.mkdir(exist_ok=True)
        cp = os.pathsep.join(str(p) for p in jars if ".source_" not in p.name)
        subprocess.run(["javac", "--release", "21", "-proc:none", "-implicit:none",
                        "-sourcepath", str(empty), "-cp", cp, "-d", str(classes), str(src)], check=True)
        compiled = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob("*.class")}
        if PREFIX + ".class" not in compiled:
            raise RuntimeError("Missing compiled JavaModelManager")
        if any(not name.startswith(PREFIX) for name in compiled):
            raise RuntimeError("Unexpected compiled dependency")
        replacement = core.with_suffix(".replacement")
        removed_signatures = []
        with zipfile.ZipFile(core) as old, zipfile.ZipFile(replacement, "w", zipfile.ZIP_DEFLATED) as new:
            for entry in old.infolist():
                name = entry.filename
                if name.upper().startswith("META-INF/") and name.upper().endswith((".SF", ".RSA", ".DSA", ".EC")):
                    removed_signatures.append(name)
                    continue
                if name == PREFIX + ".class" or (name.startswith(PREFIX + "$") and name.endswith(".class")):
                    continue
                new.writestr(entry, old.read(name))
            for name, content in sorted(compiled.items()):
                new.writestr(name, content)
        replacement.replace(core)
        print("REBUILT_CLASSES", len(compiled), "REMOVED_SIGNATURES", removed_signatures, flush=True)
    after = {p.name: sha(p) for p in jars}
    changed_jars = [name for name in before if before[name] != after[name]]
    if changed_jars != ([] if mode == "stock" else [core.name]):
        raise RuntimeError(f"Unexpected changed bundles: {changed_jars}")
    (out / "bundle-hashes.txt").write_text("\n".join(
        f"{name} before={before[name]} after={after[name]}" for name in before) + "\n")
    print("COUNTERFACTUAL_ARM", mode, "CHANGED_BUNDLES", changed_jars, flush=True)


def verify(out: Path, mode: str) -> None:
    failures = {
        "headless": {
            "completedOptionUpdateMustReachChangedLineFormatter(diagnostics.OptionsCacheConsistencyTest)",
            "readerMustNotUndoPreferenceInvalidation(diagnostics.OptionsCacheConsistencyTest)",
            "readerMustNotOverwriteCompletedSetOptions(diagnostics.OptionsCacheConsistencyTest)",
        },
        "ui": {"saveMustUseCompletedOptionUpdate(diagnostics.SaveParticipantIntegrationTest)"},
    }
    report = [f"# Counterfactual arm: {mode}", "", "This checks a causal experiment, not production readiness.", ""]
    for app, count in [("headless", 6), ("ui", 2)]:
        text = (out / f"{app}-test-output.txt").read_text(errors="replace")
        expected = set() if mode == "uncached" else failures[app]
        prefix = "UI_DIAGNOSTIC" if app == "ui" else "DIAGNOSTIC"
        summaries = re.findall(rf"^{prefix}_RESULT tests=(\d+) failures=(\d+) ignored=(\d+)$", text, re.M)
        if summaries != [(str(count), str(len(expected)), "0")]:
            raise RuntimeError(f"Unexpected JUnit completion for {app}: {summaries}")
        actual = set(re.findall(rf"^{prefix}_FAILURE (.+)$", text, re.M))
        if actual != expected:
            raise RuntimeError(f"Unexpected failing tests for {app}: {actual}")
        status = int((out / f"{app}-exit-status.txt").read_text())
        if status != (1 if expected else 0):
            raise RuntimeError(f"Unexpected process status for {app}: {status}")
        errors = len(re.findall(r"^!ENTRY \S+ 4 ", text, re.M))
        report.append(f"{app}: {count} executed, {len(expected)} regression failures, 0 ignored; logged Eclipse errors={errors}")
        for line in text.splitlines():
            if line.startswith(("OPTIONS_RACE", "SAVE_BEFORE", "SAVE_AFTER", "FORMATTER_CONTROL")):
                report.append(line)
    result = "\n".join(report) + "\n"
    (out / "SUMMARY.md").write_text(result)
    print(result, flush=True)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as stream:
            stream.write(result)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["prepare", "verify"])
    parser.add_argument("mode", choices=["stock", "rebuilt", "uncached"])
    parser.add_argument("sdk", type=Path)
    parser.add_argument("out", type=Path)
    args = parser.parse_args()
    if args.action == "prepare":
        prepare(args.sdk.resolve(), args.out.resolve(), args.mode)
    else:
        verify(args.out.resolve(), args.mode)
