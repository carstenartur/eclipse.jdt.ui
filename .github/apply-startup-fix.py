from pathlib import Path

def replace(path, before, after):
    p = Path(path)
    s = p.read_text()
    if s.count(before) != 1:
        raise SystemExit(f'Expected exactly one patch context in {path}, found {s.count(before)}')
    p.write_text(s.replace(before, after))

replace('org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/StandardJavaElementContentProvider.java',
'''\tpublic Object getParent(Object element) {
\t\tif (!exists(element))
\t\t\treturn null;
\t\treturn internalGetParent(element);
\t}''',
'''\tpublic Object getParent(Object element) {
\t\t// Java element handles already describe their parent, even when the element
\t\t// does not exist. Checking existence here can open the project model and
\t\t// initialize classpath containers on the UI thread during editor restore.
\t\tif (!(element instanceof IJavaElement) && !exists(element))
\t\t\treturn null;
\t\treturn internalGetParent(element);
\t}''')
replace('org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/packageview/PackageExplorerContentProvider.java',
'''\t\tint elementType= element.getElementType();


\t\tif (elementType != IJavaElement.JAVA_MODEL''',
'''\t\tint elementType= element.getElementType();

\t\t// These changes are ignored at the compilation-unit level below. Avoid
\t\t// resolving the root's classpath container before reaching that leaf.
\t\tif (elementType == IJavaElement.PACKAGE_FRAGMENT_ROOT && isWorkingCopyOnlyDelta(delta))
\t\t\treturn false;

\t\tif (elementType != IJavaElement.JAVA_MODEL''')
replace('org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/packageview/PackageExplorerContentProvider.java',
'''\tprivate static boolean isStructuralCUChange(int flags) {''',
'''\tprivate static boolean isWorkingCopyOnlyDelta(IJavaElementDelta delta) {
\t\tif (delta.getKind() != IJavaElementDelta.CHANGED)
\t\t\treturn false;
\t\tIResourceDelta[] resources= delta.getResourceDeltas();
\t\tif (resources != null && resources.length != 0)
\t\t\treturn false;

\t\tint flags= delta.getFlags();
\t\tIJavaElementDelta[] children= delta.getAffectedChildren();
\t\tint type= delta.getElement().getElementType();
\t\tif (type == IJavaElement.COMPILATION_UNIT) {
\t\t\treturn children.length == 0
\t\t\t\t\t&& (flags & IJavaElementDelta.F_PRIMARY_WORKING_COPY) != 0
\t\t\t\t\t&& (flags & ~(IJavaElementDelta.F_PRIMARY_WORKING_COPY | IJavaElementDelta.F_FINE_GRAINED)) == 0;
\t\t}
\t\tif ((type != IJavaElement.PACKAGE_FRAGMENT_ROOT && type != IJavaElement.PACKAGE_FRAGMENT)
\t\t\t\t|| (flags & ~IJavaElementDelta.F_FINE_GRAINED) != IJavaElementDelta.F_CHILDREN
\t\t\t\t|| children.length == 0)
\t\t\treturn false;

\t\tfor (IJavaElementDelta child : children) {
\t\t\tif (!isWorkingCopyOnlyDelta(child))
\t\t\t\treturn false;
\t\t}
\t\treturn true;
\t}

\tprivate static boolean isStructuralCUChange(int flags) {''')
replace('org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/packageview/PackageExplorerTests.java',
'\tContentProviderTests7.class,',
'\tContentProviderTests7.class,\n\tStartupContentProviderTests.class,')
