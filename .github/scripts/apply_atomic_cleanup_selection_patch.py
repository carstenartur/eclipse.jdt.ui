#!/usr/bin/env python3
"""Apply the small fork-specific wiring for coordinated Cleanup selection."""

from pathlib import Path

PATH = Path(
    "org.eclipse.jdt.ui/core extension/org/eclipse/jdt/internal/corext/fix/"
    "CleanUpRefactoring.java"
)

text = PATH.read_text(encoding="utf-8")
original = text

for import_line in (
    "import org.eclipse.ltk.core.refactoring.GroupCategory;\n",
    "import org.eclipse.ltk.core.refactoring.GroupCategorySet;\n",
):
    if import_line not in text:
        raise SystemExit(f"Expected import not found: {import_line.strip()}")
    text = text.replace(import_line, "", 1)

old_change = (
    "\t\t\tDynamicValidationStateChange change= "
    "new DynamicValidationStateChange(getName());"
)
new_change = (
    "\t\t\tDynamicValidationStateChange change= "
    "new CoordinatedCleanUpSelectionChange(getName());"
)
if text.count(old_change) != 1:
    raise SystemExit(
        f"Expected exactly one top-level Cleanup change construction, found {text.count(old_change)}"
    )
text = text.replace(old_change, new_change, 1)

old_groups = """\t\tfor (TextEditBasedChangeGroup changeGroup : source.getChangeGroups()) {
\t\t\tTextEditGroup textEditGroup= changeGroup.getTextEditGroup();
\t\t\tTextEditGroup newGroup;
\t\t\tif (textEditGroup instanceof CategorizedTextEditGroup) {
\t\t\t\tString label= textEditGroup.getName();
\t\t\t\tnewGroup= new CategorizedTextEditGroup(label, new GroupCategorySet(new GroupCategory(label, label, label)));
\t\t\t} else {
\t\t\t\tnewGroup= new TextEditGroup(textEditGroup.getName());
\t\t\t}
"""
new_groups = """\t\tfor (TextEditBasedChangeGroup changeGroup : source.getChangeGroups()) {
\t\t\tTextEditGroup textEditGroup= changeGroup.getTextEditGroup();
\t\t\tTextEditGroup newGroup;
\t\t\tif (textEditGroup instanceof CategorizedTextEditGroup categorizedGroup) {
\t\t\t\tnewGroup= new CategorizedTextEditGroup(textEditGroup.getName(),
\t\t\t\t\t\tcategorizedGroup.getGroupCategorySet());
\t\t\t} else {
\t\t\t\tnewGroup= new TextEditGroup(textEditGroup.getName());
\t\t\t}
"""
if text.count(old_groups) != 1:
    raise SystemExit(
        f"Expected exactly one change-group copy block, found {text.count(old_groups)}"
    )
text = text.replace(old_groups, new_groups, 1)

if text == original:
    raise SystemExit("Patch made no changes")

PATH.write_text(text, encoding="utf-8")
