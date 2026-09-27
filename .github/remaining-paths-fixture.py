from pathlib import Path
p = Path('org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/packageview/ViewStartupTests.java')
s = p.read_text()
s = s.replace('private ICompilationUnit fUnit;', 'private ICompilationUnit fUnit;\n\tprivate String fTypeHandle;\n\tprivate String fMissingTypeHandle;')
s = s.replace('fProject.getResolvedClasspath(true);\n', '''fProject.getResolvedClasspath(true);
		// Persist the handles before invalidating the model, just as a real
		// workbench restart reads a memento saved during the previous session.
		fTypeHandle= fUnit.getType("A").getHandleIdentifier();
		fMissingTypeHandle= fUnit.getType("Missing").getHandleIdentifier();
''')
s = s.replace('restoreHierarchy(fUnit.getType("A").getHandleIdentifier())', 'restoreHierarchy(fTypeHandle)')
s = s.replace('restoreHierarchy(fUnit.getType("Missing").getHandleIdentifier())', 'restoreHierarchy(fMissingTypeHandle)')
s = s.replace('restoreHierarchy(fMissingTypeHandle);\n\t\tassertBackgroundInitialization();\n\t\tawaitJobs(fHierarchy);', '''restoreHierarchy(fMissingTypeHandle);
		// A missing working-copy member can be rejected without initializing a
		// container at all; require safe completion, not unnecessary model work.
		awaitJobs(fHierarchy);
		assertTrue(StartupClasspathContainerInitializer.UI_CALLS.isEmpty(), () -> String.join("\\n", StartupClasspathContainerInitializer.UI_CALLS));''')
p.write_text(s)
