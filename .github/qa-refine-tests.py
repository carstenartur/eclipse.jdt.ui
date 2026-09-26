from pathlib import Path
p = Path('org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/packageview/BreadcrumbStartupTests.java')
s = p.read_text()
s = s.replace('import java.util.concurrent.atomic.AtomicReference;', 'import java.util.concurrent.atomic.AtomicInteger;\nimport java.util.concurrent.atomic.AtomicReference;')
s = s.replace('import org.eclipse.core.runtime.IStatus;', 'import org.eclipse.core.runtime.IProgressMonitor;\nimport org.eclipse.core.runtime.IStatus;\nimport org.eclipse.core.runtime.Status;')
s = s.replace('import org.eclipse.jdt.core.IJavaElementDelta;\nimport org.eclipse.jdt.core.IJavaElement;', 'import org.eclipse.jdt.core.IJavaElement;\nimport org.eclipse.jdt.core.IJavaElementDelta;')
s = s.replace('private enum Scenario { NORMAL, SLOW, CLOSE, CANCEL, REPLACE, CLASSPATH, RESOLVED_CLASSPATH, COALESCED_CLASSPATH, NULL_COLD, PENDING_CLASSPATH }', '''private enum Scenario {
		NORMAL, SLOW, CLOSE, CANCEL, REPLACE,
		CLASSPATH, RESOLVED_CLASSPATH, COALESCED_CLASSPATH, NULL_COLD, PENDING_CLASSPATH,
		ROOT_CLASSPATH, OTHER_PROJECT, CONTENT_ONLY
	}''')
a = s.index('\n\tprivate static IElementChangedListener modelListener(')
s = s[:a] + '''
	@Test
	public void siblingRootClasspathChangeInvalidatesProject() throws Exception {
		exercise(Scenario.ROOT_CLASSPATH);
	}

	@Test
	public void anotherProjectDoesNotScheduleBreadcrumbWork() throws Exception {
		exercise(Scenario.OTHER_PROJECT);
	}

	@Test
	public void contentRefreshDoesNotReinitializeTheClasspath() throws Exception {
		exercise(Scenario.CONTENT_ONLY);
	}
''' + s[a:]
s = s.replace('modelListener(breadcrumb).elementChanged(new ElementChangedEvent(delta, ElementChangedEvent.POST_CHANGE));', 'deliverDelta(breadcrumb, delta);')
a = s.index('\n\tprivate static void awaitContainerAndBreadcrumb(')
s = s[:a] + '''
	private static void deliverDelta(IBreadcrumb breadcrumb, IJavaElementDelta delta) throws Exception {
		IElementChangedListener listener= modelListener(breadcrumb);
		Job notification= new Job("Deliver breadcrumb model notification") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				listener.elementChanged(new ElementChangedEvent(delta, ElementChangedEvent.POST_CHANGE));
				return Status.OK_STATUS;
			}
		};
		notification.schedule();
		assertTrue(notification.join(10_000, null), "Model notification blocked outside the UI thread");
		assertTrue(notification.getResult().isOK(), () -> notification.getResult().toString());
	}

	private static void assertNoUnnecessaryInitialization(IBreadcrumb breadcrumb, IJavaProject project,
			ICompilationUnit unit, boolean otherProject) throws Exception {
		AtomicInteger scheduled= new AtomicInteger();
		JobChangeAdapter observer= new JobChangeAdapter() {
			@Override
			public void scheduled(IJobChangeEvent event) {
				Job job= event.getJob();
				if (job.belongsTo(breadcrumb) && (otherProject || !(job instanceof UIJob)))
					scheduled.incrementAndGet();
			}
		};
		Job.getJobManager().addJobChangeListener(observer);
		try {
			JavaElementDelta delta= new JavaElementDelta(project.getJavaModel());
			if (otherProject)
				delta.changed(project.getJavaModel().getJavaProject("OtherBreadcrumbProject"), IJavaElementDelta.F_CLASSPATH_CHANGED);
			else
				delta.changed(unit, IJavaElementDelta.F_CONTENT);
			deliverDelta(breadcrumb, delta);
			awaitInitialization(breadcrumb);
			assertEquals(0, scheduled.get(), "Unrelated changes must not start initialization work");
		} finally {
			Job.getJobManager().removeJobChangeListener(observer);
		}
	}
''' + s[a:]
s = s.replace('"Breadcrumb did not publish the latest input: " + expected', '"Breadcrumb did not publish the latest input: " + expected + "; actual: " + viewer.getInput()')
s = s.replace('|| scenario == Scenario.COALESCED_CLASSPATH || scenario == Scenario.NULL_COLD) {', '|| scenario == Scenario.COALESCED_CLASSPATH || scenario == Scenario.NULL_COLD || scenario == Scenario.ROOT_CLASSPATH) {')
s = s.replace('modelListener(breadcrumb).elementChanged(new ElementChangedEvent(content, ElementChangedEvent.POST_CHANGE));', 'deliverDelta(breadcrumb, content);')
s = s.replace('''				if (scenario == Scenario.NULL_COLD)
					breadcrumb.setInput(null);
				else
''', '''				if (scenario == Scenario.NULL_COLD) {
					breadcrumb.setInput(null);
				} else if (scenario == Scenario.ROOT_CLASSPATH) {
					JavaElementDelta delta= new JavaElementDelta(project.getJavaModel());
					// A sibling root is not an ancestor of the displayed Java element.
					delta.changed(project.getPackageFragmentRoot(project.getProject().getFolder("other-src")),
							IJavaElementDelta.F_ADDED_TO_CLASSPATH);
					deliverDelta(breadcrumb, delta);
				} else
''')
needle = '\t\t\tassertTrue(StartupClasspathContainerInitializer.CALLS.get() > 0, "Test must actually initialize the cold container");'
insert = '''			if (scenario == Scenario.OTHER_PROJECT || scenario == Scenario.CONTENT_ONLY) {
				TestUtils.waitForEditorJobs(60_000, true);
				awaitInitialization(breadcrumb);
				assertNoUnnecessaryInitialization(breadcrumb, project, first, scenario == Scenario.OTHER_PROJECT);
				awaitInput(viewer, "A");
			}
'''
s = s.replace(needle, insert + needle)
s = s.replace('\t\t\tControl control= viewer.getControl();\n', '\t\t\tControl control= viewer.getControl();\n\t\t\t// The editor history can restore a selection saved by another scenario.\n\t\t\t// Pin the input before testing classpath changes or pending completions.\n\t\t\tjavaEditor.selectAndReveal(source.indexOf("A {}"), 0);\n')
# Unlike waitForEditorJobs(), this helper leaves the editor open.
s = s.replace('TestUtils.waitForEditorJobs(60_000, true);\n\t\t\t\tawaitInitialization(breadcrumb);', 'TestUtils.waitForReconciler(javaEditor, 60_000);\n\t\t\t\tawaitInitialization(breadcrumb);')
p.write_text(s)
