package probe;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.*;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerPart;
import org.eclipse.jdt.internal.ui.typehierarchy.TypeHierarchyViewPart;

/** Test-only startup extension; restart mode never opens an editor or changes model state. */
public final class Probe implements IStartup {
    private static final Path OUT = Path.of(System.getProperty("probe.out"));
    private static final String PHASE = System.getProperty("probe.phase");
    private static final String MODE = System.getProperty("probe.mode");
    private int beats;

    public static synchronized void log(String text) {
        try {
            Files.createDirectories(OUT);
            Files.writeString(OUT.resolve("workbench.log"), Instant.now() + " " + text + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override
    public void earlyStartup() {
        log("EARLY_STARTUP phase=" + PHASE + " mode=" + MODE + " pid=" + ProcessHandle.current().pid());
        try { Files.writeString(OUT.resolve("early-startup"), Instant.now().toString()); }
        catch (Exception e) { throw new RuntimeException(e); }
        IWorkbench workbench = PlatformUI.getWorkbench();
        Display display = workbench.getDisplay();
        display.asyncExec(() -> {
            try {
                log("UI_READY");
                heartbeat(display);
                for (String id : new String[] {"org.eclipse.jdt.ui", "org.eclipse.jdt.core", "org.eclipse.pde.core", "org.eclipse.ui.workbench"}) {
                    var bundle = Platform.getBundle(id);
                    log("BUNDLE " + id + " " + bundle.getVersion() + " " + bundle.getLocation());
                }
                if ("seed".equals(PHASE)) seed(workbench);
                else inspect(workbench, "RESTORED");
                display.timerExec("seed".equals(PHASE) ? 15000 : 10000, () -> finish(workbench));
            } catch (Throwable e) {
                log("FATAL " + e);
                e.printStackTrace();
                try { Files.writeString(OUT.resolve("fatal"), e.toString()); } catch (Exception ignored) { }
                display.timerExec(1000, () -> workbench.close());
            }
        });
    }

    private void heartbeat(Display display) {
        if (display.isDisposed()) return;
        try { Files.writeString(OUT.resolve("heartbeat"), Integer.toString(++beats)); }
        catch (Exception e) { log("HEARTBEAT_ERROR " + e); }
        display.timerExec(100, () -> heartbeat(display));
    }

    private static void file(IProject project, String name, String contents) throws Exception {
        IFile file = project.getFile(name);
        byte[] bytes = contents.getBytes(StandardCharsets.UTF_8);
        file.create(new ByteArrayInputStream(bytes), true, null);
    }

    private void seed(IWorkbench workbench) throws Exception {
        var intro = workbench.getIntroManager().getIntro();
        if (intro != null) workbench.getIntroManager().closeIntro(intro);
        IWorkbenchPage page = workbench.showPerspective(JavaUI.ID_PERSPECTIVE, workbench.getActiveWorkbenchWindow());
        for (IViewReference view : page.getViewReferences()) page.hideView(view);
        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IProject project = workspace.getRoot().getProject("startup.probe");
        IProjectDescription description = workspace.newProjectDescription(project.getName());
        project.create(description, null);
        project.open(null);
        project.getFolder("src").create(true, true, null);
        project.getFolder("src/example").create(true, true, null);
        project.getFolder("META-INF").create(true, true, null);
        file(project, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nBundle-ManifestVersion: 2\nBundle-Name: Startup Probe\nBundle-SymbolicName: startup.probe\nBundle-Version: 1.0.0.qualifier\nBundle-RequiredExecutionEnvironment: JavaSE-21\nRequire-Bundle: org.eclipse.core.runtime\n\n");
        file(project, "build.properties", "source.. = src/\noutput.. = bin/\nbin.includes = META-INF/,\\\n               .\n");
        file(project, ".classpath", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<classpath><classpathentry kind=\"src\" path=\"src\"/><classpathentry kind=\"con\" path=\"org.eclipse.jdt.launching.JRE_CONTAINER\"/><classpathentry kind=\"con\" path=\"org.eclipse.pde.core.requiredPlugins\"/><classpathentry kind=\"output\" path=\"bin\"/></classpath>\n");
        file(project, "src/example/Example.java", "package example;\npublic class Example extends org.eclipse.core.runtime.Plugin {\n    public String value() { return \"restart\"; }\n}\n");
        description = project.getDescription();
        description.setNatureIds(new String[] {JavaCore.NATURE_ID, "org.eclipse.pde.PluginNature"});
        ICommand java = description.newCommand(); java.setBuilderName(JavaCore.BUILDER_ID);
        ICommand manifest = description.newCommand(); manifest.setBuilderName("org.eclipse.pde.ManifestBuilder");
        ICommand schema = description.newCommand(); schema.setBuilderName("org.eclipse.pde.SchemaBuilder");
        description.setBuildSpec(new ICommand[] {java, manifest, schema});
        project.setDescription(description, null);
        JavaPlugin.getDefault().getPreferenceStore().setValue(JavaEditor.EDITOR_SHOW_BREADCRUMB + "." + page.getPerspective().getId(), true);
        if (!"editor".equals(MODE)) {
            PackageExplorerPart explorer = (PackageExplorerPart) page.showView(JavaUI.ID_PACKAGES);
            explorer.setLinkingEnabled(true);
        }
        IEditorPart editor = page.openEditor(new FileEditorInput(project.getFile("src/example/Example.java")), JavaUI.ID_CU_EDITOR);
        if ("hierarchy".equals(MODE)) {
            ICompilationUnit unit = JavaCore.createCompilationUnitFrom(project.getFile("src/example/Example.java"));
            TypeHierarchyViewPart hierarchy = (TypeHierarchyViewPart) page.showView(JavaUI.ID_TYPE_HIERARCHY);
            hierarchy.setInputElement(unit.getType("Example"));
            page.activate(editor);
        }
        log("SEEDED PDE=" + project.hasNature("org.eclipse.pde.PluginNature") + " JAVA=" + project.hasNature(JavaCore.NATURE_ID));
        inspect(workbench, "SEEDED");
    }

    private void inspect(IWorkbench workbench, String label) throws Exception {
        IWorkbenchPage page = workbench.getActiveWorkbenchWindow().getActivePage();
        log(label + " EDITOR_REFS=" + page.getEditorReferences().length);
        for (IEditorReference reference : page.getEditorReferences()) {
            IEditorPart editor = reference.getEditor(false); // Do not force editor restoration.
            log(label + " EDITOR " + reference.getId() + " " + reference.getName() + " instantiated=" + (editor != null));
            if (editor instanceof JavaEditor javaEditor && javaEditor.getBreadcrumb() != null) {
                Object selectionProvider = javaEditor.getBreadcrumb().getSelectionProvider();
                Object input = selectionProvider instanceof Viewer viewer ? viewer.getInput() : null;
                log(label + " BREADCRUMB=" + (input instanceof IJavaElement java ? java.getElementName() : String.valueOf(input)));
            }
        }
        for (IViewReference reference : page.getViewReferences()) {
            IViewPart view = reference.getView(false);
            log(label + " VIEW " + reference.getId() + " instantiated=" + (view != null));
            if (view instanceof PackageExplorerPart explorer) log(label + " LINK=" + explorer.isLinkingEnabled());
            if (view instanceof TypeHierarchyViewPart hierarchy) {
                IJavaElement input = hierarchy.getInputElement();
                log(label + " HIERARCHY=" + (input == null ? "null" : input.getElementName()));
            }
        }
    }

    private void finish(IWorkbench workbench) {
        try {
            inspect(workbench, "FINAL");
            for (Job job : Job.getJobManager().find(null))
                if (job.getState() != Job.NONE) log("JOB " + job.getName() + " state=" + job.getState());
            Display display = workbench.getDisplay();
            Image image = new Image(display, display.getBounds());
            GC gc = new GC(display);
            try { gc.copyArea(image, 0, 0); } finally { gc.dispose(); }
            ImageLoader loader = new ImageLoader(); loader.data = new org.eclipse.swt.graphics.ImageData[] {image.getImageData()};
            loader.save(OUT.resolve("workbench.png").toString(), SWT.IMAGE_PNG); image.dispose();
            Files.writeString(OUT.resolve("finished"), "true");
            log("CLOSE_WORKBENCH");
            workbench.close(); // Save the normal workbench state with editor still open.
        } catch (Throwable e) {
            log("FINISH_ERROR " + e); e.printStackTrace();
        }
    }
}
