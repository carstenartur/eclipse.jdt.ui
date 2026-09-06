#!/usr/bin/env python3
# Copyright (c) 2026 Carsten Hammer.
# SPDX-License-Identifier: EPL-2.0
"""Apply one diagnostic-only identity-isolation variant to WildcardBound."""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

TARGET = Path("org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/quickfix/AnnotateAssistTest1d8.java")
EXPECTED_BLOB = "d1fbf9f88dfe5ee1d427df95b1aaac17ebb33736"
START = "\t@Test\n\tpublic void testAnnotateParameter_WildcardBound() throws Exception {"


def git_blob_sha(data: bytes) -> str:
    return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()


def target_method(text: str) -> tuple[int, int, str]:
    start = text.index(START)
    end = text.index("\n\t/**", start + len(START))
    return start, end, text[start:end]


def replace_exact(text: str, old: str, new: str, count: int = 1) -> str:
    actual = text.count(old)
    if actual != count:
        raise AssertionError(f"Expected {count} occurrence(s), found {actual}: {old!r}")
    return text.replace(old, new, count)


def isolate_class(method: str) -> str:
    method = replace_exact(method, '\t\tString X_PATH= "pack/age/X";', '\t\tString X_PATH= "pack/age/XWildcard";')
    method = replace_exact(method, "public interface X {", "public interface XWildcard {")
    method = replace_exact(method, "class pack/age/X", "class pack/age/XWildcard", 2)
    return method


def isolate_archive(method: str) -> str:
    return replace_exact(
        method,
        'addLibrary(fJProject1, "lib.jar", "lib.zip", pathAndContents, ANNOTATION_PATH, JavaCore.VERSION_1_8, null);',
        'addLibrary(fJProject1, "wildcard-lib.jar", "wildcard-lib.zip", pathAndContents, ANNOTATION_PATH, JavaCore.VERSION_1_8, null);',
    )


def isolate_project(method: str) -> str:
    opening = START + "\n"
    setup = (
        opening
        + "\n"
        + '\t\tIJavaProject isolatedProject= JavaProjectHelper.createJavaProject("TestSetupProject1d8Wildcard", "bin");\n'
        + "\t\tisolatedProject.setRawClasspath(projectSetup.getDefaultClasspath(), null);\n"
        + "\t\tJavaProjectHelper.set18CompilerOptions(isolatedProject);\n"
        + "\t\tisolatedProject.setOption(JavaCore.COMPILER_ANNOTATION_NULL_ANALYSIS, JavaCore.ENABLED);\n"
        + "\t\tisolatedProject.getProject().getFolder(ANNOTATION_PATH).create(true, true, null);\n"
    )
    method = replace_exact(method, opening, setup)
    # Only the WildcardBound method is changed; its normal @Rule project remains the predecessor identity.
    method = method.replace("fJProject1", "isolatedProject")
    closing = (
        "\t\t} finally {\n"
        "\t\t\tJavaPlugin.getActivePage().closeAllEditors(false);\n"
        "\t\t}\n"
        "\t}"
    )
    replacement = (
        "\t\t} finally {\n"
        "\t\t\tJavaPlugin.getActivePage().closeAllEditors(false);\n"
        "\t\t\tJavaProjectHelper.delete(isolatedProject);\n"
        "\t\t}\n"
        "\t}"
    )
    method = replace_exact(method, closing, replacement)
    return method


def apply_variant(text: str, variant: str) -> str:
    start, end, method = target_method(text)
    original_method = method
    if variant in ("class", "all"):
        method = isolate_class(method)
    if variant in ("archive", "all"):
        method = isolate_archive(method)
    if variant in ("project", "all"):
        method = isolate_project(method)
    if variant == "control":
        assert method == original_method
    else:
        assert method != original_method
    result = text[:start] + method + text[end:]
    # Nothing outside the target method may change.
    r_start, r_end, _ = target_method(result)
    assert text[:start] == result[:r_start]
    assert text[end:] == result[r_end:]
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("variant", choices=("control", "class", "archive", "project", "all"))
    parser.add_argument("--path", type=Path, default=TARGET)
    args = parser.parse_args()

    data = args.path.read_bytes()
    actual = git_blob_sha(data)
    if actual != EXPECTED_BLOB:
        raise AssertionError(f"Unexpected input blob {actual}; expected {EXPECTED_BLOB}")
    text = data.decode("utf-8")
    result = apply_variant(text, args.variant)
    args.path.write_text(result, encoding="utf-8")

    _, _, method = target_method(result)
    checks = {
        "variant": args.variant,
        "input_blob": actual,
        "output_blob": git_blob_sha(result.encode()),
        "class_isolated": "XWildcard" in method,
        "archive_isolated": "wildcard-lib.jar" in method and "wildcard-lib.zip" in method,
        "project_isolated": "TestSetupProject1d8Wildcard" in method,
    }
    print(checks)


if __name__ == "__main__":
    main()
