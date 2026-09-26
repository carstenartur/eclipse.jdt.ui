from pathlib import Path

def edit(path, edits):
    p=Path(path)
    s=p.read_text()
    for old,new in edits:
        assert s.count(old)==1, old[:150]
        s=s.replace(old,new)
    p.write_text(s)

edit('org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/JavaEditorBreadcrumb.java', [
('''\t\t\t\tif (!project.equals(fInitializingProject)) {''', '''\t\t\t\tIStatus result= fInitializationJob == null ? null : fInitializationJob.getResult();
\t\t\t\t// A new request must not be discarded by a cancelled job's pending UI callback.
\t\t\t\tif (!project.equals(fInitializingProject) || result != null && !result.isOK()) {''')])

edit('org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/packageview/BreadcrumbStartupTests.java', [
('''\tprivate void exercise(Scenario scenario) throws Exception {''', '''\tprivate static void awaitInput(Viewer viewer, String expected) throws Exception {
\t\tlong deadline= System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
\t\twhile (!(viewer.getInput() instanceof IJavaElement element) || !expected.equals(element.getElementName())) {
\t\t\tassertTrue(System.nanoTime() < deadline, "Breadcrumb did not publish the latest input: " + expected);
\t\t\tdrainEvents();
\t\t\tThread.sleep(10);
\t\t}
\t}

\tprivate void exercise(Scenario scenario) throws Exception {'''),
('''\t\tAtomicReference<IStatus> cancellation= new AtomicReference<>();''', '''\t\tAtomicReference<IStatus> cancellation= new AtomicReference<>();
\t\tCountDownLatch cancelledDone= new CountDownLatch(1);'''),
('''\t\t\t\t\t\tcancellation.set(event.getResult());''', '''\t\t\t\t\t\tcancellation.set(event.getResult());
\t\t\t\t\t\tcancelledDone.countDown();'''),
('''\t\t\trelease.countDown();
\t\t\tawaitInitialization(breadcrumb);
\t\t\tif (scenario == Scenario.CANCEL) {
\t\t\t\tassertNotNull(cancellation.get(), "The pending initialization must report completion");
\t\t\t\tassertEquals(IStatus.CANCEL, cancellation.get().getSeverity());
\t\t\t\t// Reconcile/selection events may already have requested a valid retry.
\t\t\t\t// Verify an explicit retry remains usable as well.
\t\t\t\tbreadcrumb.setInput(first);
\t\t\t\tawaitInitialization(breadcrumb);
\t\t\t}''', '''\t\t\trelease.countDown();
\t\t\tif (scenario == Scenario.CANCEL) {
\t\t\t\t// Deliberately do not dispatch UI events before requesting the retry:
\t\t\t\t// the cancelled worker has finished, but its UI callback is still pending.
\t\t\t\tassertTrue(cancelledDone.await(10, TimeUnit.SECONDS), "Cancelled worker did not finish");
\t\t\t\tassertNotNull(cancellation.get());
\t\t\t\tassertEquals(IStatus.CANCEL, cancellation.get().getSeverity());
\t\t\t\tbreadcrumb.setInput(first);
\t\t\t}
\t\t\tawaitInitialization(breadcrumb);
\t\t\tif (scenario != Scenario.CLOSE)
\t\t\t\tawaitInput(viewer, scenario == Scenario.REPLACE ? "B" : "A");'''),
('''import org.eclipse.jdt.internal.ui.javaeditor.breadcrumb.IBreadcrumb;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;''', '''import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.javaeditor.breadcrumb.IBreadcrumb;''')])
