from pathlib import Path
p = Path('org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/JavaEditorBreadcrumb.java')
s = p.read_text()
def replace(before, after):
    global s
    assert s.count(before) == 1, before[:100]
    s = s.replace(before, after)
replace('import org.eclipse.swt.widgets.Composite;', 'import org.eclipse.swt.widgets.Composite;\nimport org.eclipse.swt.widgets.Display;')
replace('import org.eclipse.core.runtime.IAdaptable;', '''import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;''')
replace('import org.eclipse.ui.contexts.IContextService;', 'import org.eclipse.ui.contexts.IContextService;\nimport org.eclipse.ui.progress.UIJob;')
replace('import org.eclipse.jdt.ui.JavaElementLabels;', 'import org.eclipse.jdt.ui.JavaElementLabels;\nimport org.eclipse.jdt.ui.JavaUI;')
replace('import org.eclipse.jdt.internal.ui.JavaPlugin;', 'import org.eclipse.jdt.internal.ui.JavaPlugin;\nimport org.eclipse.jdt.internal.ui.JavaUIMessages;')
replace('''\tprivate ElementChangeListener fElementChangeListener;''', '''\tprivate ElementChangeListener fElementChangeListener;

\t// Accessed only on the UI thread. Keep the latest input while initialization runs.
\tprivate IJavaProject fInitializedProject;
\tprivate IJavaProject fInitializingProject;
\tprivate Job fInitializationJob;
\tprivate Object fPendingInput;
\tprivate boolean fPendingRefresh;''')
replace('''\tpublic void dispose() {
\t\tsuper.dispose();''', '''\tpublic void dispose() {
\t\tcancelInitialization();
\t\tfInitializedProject= null;
\t\tsuper.dispose();''')
replace('''\t\t\t\tObject newInput= getCurrentInput();
\t\t\t\tif (newInput instanceof IJavaElement)
\t\t\t\t\tnewInput= getInput((IJavaElement) newInput);

\t\t\t\tfViewer.setInput(newInput);
\t\t\t\tfRunnable= null;''', '''\t\t\t\tupdateInput(getCurrentInput(), true);
\t\t\t\tfRunnable= null;''')
replace('''\tpublic void setInput(Object element) {
\t\tif (element == null) {
\t\t\telement= getCurrentInput();
\t\t\tif (element instanceof IType) {
\t\t\t\telement= ((IType) element).getDeclaringType();
\t\t\t}
\t\t}

\t\tif (element instanceof IJavaElement) {
\t\t\tsuper.setInput(getInput((IJavaElement) element));
\t\t} else {
\t\t\tsuper.setInput(element);
\t\t}
\t}''', '''\tpublic void setInput(Object element) {
\t\tupdateInput(element, false);
\t}

\tprivate void updateInput(Object element, boolean refresh) {
\t\tif (fViewer == null || fViewer.getControl().isDisposed())
\t\t\treturn;
\t\tif (element == null) {
\t\t\telement= getCurrentInput();
\t\t\tif (element instanceof IType) {
\t\t\t\telement= ((IType) element).getDeclaringType();
\t\t\t}
\t\t}

\t\tif (element instanceof IJavaElement javaElement) {
\t\t\tIJavaProject project= javaElement.getJavaProject();
\t\t\tif (project != null && (!project.equals(fInitializedProject) || !project.isOpen())) {
\t\t\t\tif (!project.equals(fInitializingProject)) {
\t\t\t\t\tcancelInitialization();
\t\t\t\t\tinitializeProject(project);
\t\t\t\t}
\t\t\t\tfPendingInput= element;
\t\t\t\tfPendingRefresh|= refresh;
\t\t\t\treturn;
\t\t\t}
\t\t}

\t\tcancelInitialization();
\t\tObject input= element instanceof IJavaElement javaElement ? getInput(javaElement) : element;
\t\tif (refresh)
\t\t\tfViewer.setInput(input);
\t\telse
\t\t\tsuper.setInput(input);
\t}

\tprivate void initializeProject(IJavaProject project) {
\t\tDisplay display= fViewer.getControl().getDisplay();
\t\tJob job= new Job(JavaUIMessages.JavaPlugin_initializing_ui) {
\t\t\t@Override
\t\t\tprotected IStatus run(IProgressMonitor monitor) {
\t\t\t\ttry {
\t\t\t\t\tif (monitor.isCanceled())
\t\t\t\t\t\treturn Status.CANCEL_STATUS;
\t\t\t\t\t// Labels, icons and build-path checks also access the classpath. Prepare
\t\t\t\t\t// the model before ANY of them run while restoring the editor's UI.
\t\t\t\t\tproject.getResolvedClasspath(true);
\t\t\t\t\tif (monitor.isCanceled())
\t\t\t\t\t\treturn Status.CANCEL_STATUS;
\t\t\t\t\tproject.open(monitor);
\t\t\t\t\treturn monitor.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;
\t\t\t\t} catch (JavaModelException e) {
\t\t\t\t\tif (monitor.isCanceled())
\t\t\t\t\t\treturn Status.CANCEL_STATUS;
\t\t\t\t\tJavaPlugin.log(e);
\t\t\t\t\treturn e.getStatus();
\t\t\t\t}
\t\t\t}

\t\t\t@Override
\t\t\tpublic boolean belongsTo(Object family) {
\t\t\t\treturn JavaUI.ID_PLUGIN.equals(family);
\t\t\t}
\t\t};
\t\tjob.addJobChangeListener(new JobChangeAdapter() {
\t\t\t@Override
\t\t\tpublic void done(IJobChangeEvent event) {
\t\t\t\tUIJob update= new UIJob(display, JavaUIMessages.JavaPlugin_initializing_ui) {
\t\t\t\t\t@Override
\t\t\t\t\tpublic IStatus runInUIThread(IProgressMonitor monitor) {
\t\t\t\t\t\tif (fViewer == null || fViewer.getControl().isDisposed() || fInitializationJob != job)
\t\t\t\t\t\t\treturn Status.CANCEL_STATUS;
\t\t\t\t\t\tfInitializationJob= null;
\t\t\t\t\t\tfInitializingProject= null;
\t\t\t\t\t\tObject input= fPendingInput;
\t\t\t\t\t\tboolean refresh= fPendingRefresh;
\t\t\t\t\t\tfPendingInput= null;
\t\t\t\t\t\tfPendingRefresh= false;
\t\t\t\t\t\tif (event.getResult().isOK()) {
\t\t\t\t\t\t\tfInitializedProject= project;
\t\t\t\t\t\t\tupdateInput(input, refresh);
\t\t\t\t\t\t}
\t\t\t\t\t\treturn Status.OK_STATUS;
\t\t\t\t\t}
\t\t\t\t};
\t\t\t\tupdate.setSystem(true);
\t\t\t\tupdate.schedule();
\t\t\t}
\t\t});
\t\tfInitializingProject= project;
\t\tfInitializationJob= job;
\t\tjob.setPriority(Job.DECORATE);
\t\tjob.schedule();
\t}

\tprivate void cancelInitialization() {
\t\tJob job= fInitializationJob;
\t\tfInitializationJob= null;
\t\tfInitializingProject= null;
\t\tfPendingInput= null;
\t\tfPendingRefresh= false;
\t\tif (job != null)
\t\t\tjob.cancel();
\t}''')
p.write_text(s)
