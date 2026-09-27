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
p.write_text(s)
