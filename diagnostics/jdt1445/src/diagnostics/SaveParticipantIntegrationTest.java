/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package diagnostics;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Hashtable;
import java.util.Map;
import java.util.UUID;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.CleanUpPostSaveListener;
import org.eclipse.jdt.internal.corext.fix.CleanUpPreferenceUtil;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension4;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.junit.Test;

/** Executes editor.doSave(), rather than invoking the formatter directly. */
public class SaveParticipantIntegrationTest {
    private static final String KEY = DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_CLOSING_PAREN_IN_CAST;
    private static final String DISK = "package test1;\npublic class E1 {\n    public void foo( Object o ) {\n        String s= (String)o;\n    }\n}";
    private static final String EDITOR = "package test1;\npublic class E1 {\n    public void foo( Object o ) {\n        String s    = (String)o;\n    }\n}";
    private static final String EXPECTED = "package test1;\npublic class E1 {\n    public void foo( Object o ) {\n        String s = (String) o;\n    }\n}";

    @Test
    public void saveFormatsChangedLineWithConsistentOptions() throws Exception {
        runSave(false);
    }

    @Test
    public void saveMustUseCompletedOptionUpdate() throws Exception {
        runSave(true);
    }

    private void runSave(boolean forceRace) throws Exception {
        assertNotNull("Test must execute on the SWT UI thread", Display.getCurrent());
        OptionsCacheConsistencyTest fixture = new OptionsCacheConsistencyTest();
        fixture.rememberOptions();
        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject("Save1445_" + UUID.randomUUID().toString().replace("-", ""));
        IWorkspaceDescription workspace = ResourcesPlugin.getWorkspace().getDescription();
        boolean autoBuild = workspace.isAutoBuilding();
        workspace.setAutoBuilding(false);
        ResourcesPlugin.getWorkspace().setDescription(workspace);
        JavaEditor editor = null;
        try {
            project.create(null);
            project.open(null);
            IProjectDescription description = project.getDescription();
            description.setNatureIds(new String[] { JavaCore.NATURE_ID });
            project.setDescription(description, null);
            IJavaProject javaProject = JavaCore.create(project);
            IFolder src = project.getFolder("src");
            src.create(true, true, null);
            javaProject.setRawClasspath(new IClasspathEntry[] { JavaCore.newSourceEntry(src.getFullPath()) }, project.getFullPath().append("bin"), null);
            IPackageFragment pack = javaProject.getPackageFragmentRoot(src).createPackageFragment("test1", false, null);
            ICompilationUnit unit = pack.createCompilationUnit("E1.java", DISK, false, null);

            Hashtable<String, String> options = JavaCore.getOptions();
            options.putAll(DefaultCodeFormatterConstants.getJavaConventionsSettings());
            options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);
            options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
            options.put(DefaultCodeFormatterConstants.FORMATTER_INDENTATION_SIZE, "4");
            options.put(KEY, JavaCore.INSERT);
            JavaCore.setOptions(options);

            ProjectScope scope = new ProjectScope(project);
            scope.getNode(JavaUI.ID_PLUGIN).putBoolean("editor_save_participant_" + CleanUpPostSaveListener.POSTSAVELISTENER_ID, true);
            CleanUpPreferenceUtil.saveSaveParticipantOptions(scope, Map.of(
                    CleanUpConstants.FORMAT_SOURCE_CODE, CleanUpOptions.TRUE,
                    CleanUpConstants.FORMAT_SOURCE_CODE_CHANGES_ONLY, CleanUpOptions.TRUE,
                    CleanUpConstants.CLEANUP_ON_SAVE_ADDITIONAL_OPTIONS, CleanUpOptions.FALSE,
                    CleanUpConstants.ORGANIZE_IMPORTS, CleanUpOptions.FALSE));

            editor = (JavaEditor) EditorUtility.openInEditor(unit);
            IDocument document = editor.getDocumentProvider().getDocument(editor.getEditorInput());
            unit.getBuffer().setContents(EDITOR);
            assertEquals("Working-copy buffer and editor must agree before saving", EDITOR, document.get());
            JavaModelManager.getIndexManager().waitForIndex(false, null);
            if (forceRace) {
                // Reuse the same latch-controlled fixture without changing any production code.
                Method race = OptionsCacheConsistencyTest.class.getDeclaredMethod("race", boolean.class);
                race.setAccessible(true);
                try {
                    race.invoke(fixture, false);
                } catch (InvocationTargetException ex) {
                    if (ex.getCause() instanceof Exception cause) {
                        throw cause;
                    }
                    throw new AssertionError(ex.getCause());
                }
            }
            assertEquals("The requested preference is definitely committed", JavaCore.INSERT, JavaCore.getOption(KEY));
            long stamp = ((IDocumentExtension4) document).getModificationStamp();
            System.out.printf("SAVE_BEFORE race=%s persisted=%s cache=%s project=%s bufferType=%s length=%d stamp=%d%n",
                    forceRace, JavaCore.getOption(KEY), JavaCore.getOptions().get(KEY), javaProject.getOptions(true).get(KEY),
                    unit.getBuffer().getClass().getName(), document.getLength(), stamp);
            editor.doSave(null);
            String actual = document.get();
            System.out.printf("SAVE_AFTER race=%s length=%d stamp=%d text=%s%n", forceRace,
                    document.getLength(), ((IDocumentExtension4) document).getModificationStamp(), actual.replace("\n", "\\n"));
            assertEquals("Editor and working copy must agree after saving", actual, unit.getBuffer().getContents());
            assertEquals("Real editor save action must use the completed option update", EXPECTED, actual);
        } finally {
            if (editor != null) {
                PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().closeEditor(editor, false);
            }
            if (project.exists()) {
                project.delete(true, true, null);
            }
            fixture.restoreOptions();
            IWorkspaceDescription restore = ResourcesPlugin.getWorkspace().getDescription();
            restore.setAutoBuilding(autoBuild);
            ResourcesPlugin.getWorkspace().setDescription(restore);
        }
    }
}
