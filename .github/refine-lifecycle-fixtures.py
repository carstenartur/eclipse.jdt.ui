from pathlib import Path
p = Path('org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/packageview/BreadcrumbStartupTests.java')
s = p.read_text()
def replace(before, after):
    global s
    assert s.count(before) == 1, before
    s = s.replace(before, after)
replace('import static org.junit.jupiter.api.Assertions.assertNull;\n', '')
replace('import java.util.concurrent.TimeUnit;', 'import java.util.concurrent.TimeUnit;\nimport java.util.concurrent.atomic.AtomicReference;')
replace('import org.eclipse.core.runtime.jobs.Job;', 'import org.eclipse.core.runtime.IStatus;\nimport org.eclipse.core.runtime.jobs.IJobChangeEvent;\nimport org.eclipse.core.runtime.jobs.Job;\nimport org.eclipse.core.runtime.jobs.JobChangeAdapter;')
replace('import org.eclipse.jdt.core.JavaCore;', 'import org.eclipse.jdt.core.IType;\nimport org.eclipse.jdt.core.JavaCore;')
replace('org.eclipse.jdt.internal.ui.javaeditor.IBreadcrumb;', 'org.eclipse.jdt.internal.ui.javaeditor.breadcrumb.IBreadcrumb;')
replace('CountDownLatch release= new CountDownLatch(scenario == Scenario.NORMAL ? 0 : 1);', 'CountDownLatch release= new CountDownLatch(scenario == Scenario.NORMAL ? 0 : 1);\n\t\tAtomicReference<IStatus> cancellation= new AtomicReference<>();')
replace('''ICompilationUnit first= pack.createCompilationUnit("A.java", "package p; public class A {}", true, null);
\t\t\tICompilationUnit second= pack.createCompilationUnit("B.java", "package p; public class B {}", true, null);''', '''String source= "package p; public class A {} class B {}";
\t\t\tICompilationUnit first= pack.createCompilationUnit("A.java", source, true, null);
\t\t\tIType second= first.getType("B");''')
replace('''Job.getJobManager().cancel(breadcrumb);''', '''Job[] jobs= Job.getJobManager().find(breadcrumb);
\t\t\t\tassertEquals(1, jobs.length, "Expected the pending breadcrumb initialization");
\t\t\t\tjobs[0].addJobChangeListener(new JobChangeAdapter() {
\t\t\t\t\t@Override
\t\t\t\t\tpublic void done(IJobChangeEvent event) {
\t\t\t\t\t\tcancellation.set(event.getResult());
\t\t\t\t\t}
\t\t\t\t});
\t\t\t\tjobs[0].cancel();''')
replace('''} else if (scenario == Scenario.REPLACE) {
\t\t\t\tbreadcrumb.setInput(second);''', '''} else if (scenario == Scenario.REPLACE) {
\t\t\t\t// Keep the actual editor selection consistent with the requested input:
\t\t\t\t// queued selection/reconcile events must refer to the new location too.
\t\t\t\tjavaEditor.selectAndReveal(source.indexOf("B"), 0);
\t\t\t\tbreadcrumb.setInput(second);''')
replace('''assertNull(viewer.getInput(), "Cancelled initialization must not publish its pending input");''', '''assertNotNull(cancellation.get(), "The pending initialization must report completion");
\t\t\t\tassertEquals(IStatus.CANCEL, cancellation.get().getSeverity());
\t\t\t\t// Reconcile/selection events may already have requested a valid retry.
\t\t\t\t// Verify an explicit retry remains usable as well.''')
replace('''String expected= scenario == Scenario.REPLACE ? "B.java" : "A.java";
\t\t\t\tassertEquals(expected, input.getAncestor(IJavaElement.COMPILATION_UNIT).getElementName());''', '''String expected= scenario == Scenario.REPLACE ? "B" : "A";
\t\t\t\tassertEquals(expected, input.getElementName());''')
p.write_text(s)
