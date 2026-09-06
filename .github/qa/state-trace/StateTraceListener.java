// Copyright (c) 2026 Carsten Hammer.
// SPDX-License-Identifier: EPL-2.0
package org.eclipse.jdt.ui.tests;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

/** Diagnostic only: never restore state, dispatch UI events or wait for jobs here. */
public final class StateTraceListener implements TestExecutionListener {
    private static final Sink SINK = new Sink();
    private static final AtomicInteger IDS = new AtomicInteger();
    private final int listener = IDS.incrementAndGet();
    private final Map<String, Map<String, String>> starts = new HashMap<>();
    private Map<String, String> previous = Map.of();
    private long planned;
    private long finished;

    public StateTraceListener() {
        SINK.write(Map.of("event", "LISTENER", "listener", listener));
    }

    @Override
    public void testPlanExecutionStarted(TestPlan plan) {
        planned = plan.countTestIdentifiers(TestIdentifier::isTest);
        event("PLAN_START", null, Map.of("planned", planned));
    }

    @Override
    public void testPlanExecutionFinished(TestPlan plan) {
        event("PLAN_FINISH", null, Map.of("planned", planned, "finished", finished));
    }

    @Override
    public void executionStarted(TestIdentifier test) {
        event("START", test, Map.of());
    }

    @Override
    public void executionFinished(TestIdentifier test, TestExecutionResult result) {
        if (test.isTest()) finished++;
        var extra = new LinkedHashMap<String, Object>();
        extra.put("status", result.getStatus().name());
        // Throwable messages may contain arbitrary application data; retain only type and stack frames.
        result.getThrowable().ifPresent(t -> {
            extra.put("errorType", t.getClass().getName());
            extra.put("stack", java.util.Arrays.stream(t.getStackTrace()).limit(20).map(Object::toString).toList());
        });
        event("FINISH", test, extra);
    }

    @Override
    public void executionSkipped(TestIdentifier test, String reason) {
        event("SKIP", test, Map.of());
    }

    @Override
    public void dynamicTestRegistered(TestIdentifier test) {
        event("REGISTER", test, Map.of());
    }

    private synchronized void event(String event, TestIdentifier test, Map<String, ?> extra) {
        var row = new LinkedHashMap<String, Object>();
        row.put("event", event);
        row.put("listener", listener);
        if (test != null) {
            row.put("id", test.getUniqueId());
            row.put("parent", test.getParentId().orElse(""));
            row.put("isTest", test.isTest());
            row.put("source", test.getSource().map(Object::toString).orElse(""));
        }
        row.putAll(extra);
        if (SINK.state && !event.equals("REGISTER")) {
            Map<String, String> state = snapshot();
            row.put("observedDelta", delta(previous, state));
            previous = state;
            if (test != null && event.equals("START")) starts.put(test.getUniqueId(), state);
            if (test != null && event.equals("FINISH")) {
                Map<String, String> before = starts.remove(test.getUniqueId());
                row.put("boundaryDelta", before == null ? Map.of() : delta(before, state));
            }
        }
        SINK.write(row);
    }

    private static Map<String, String> snapshot() {
        Map<String, String> state = new TreeMap<>();
        try {
            Properties properties = System.getProperties();
            Properties copy;
            synchronized (properties) { copy = (Properties) properties.clone(); }
            state.put("propertiesObject", Integer.toHexString(System.identityHashCode(properties)));
            for (Object key : copy.keySet()) {
                if (key instanceof String name) {
                    Object value = copy.get(key);
                    state.put("property/" + name, value instanceof String s ? s : "<non-string:" + value.getClass().getName() + ">");
                }
            }
            state.put("locale/default", Locale.getDefault().toLanguageTag());
            state.put("locale/display", Locale.getDefault(Locale.Category.DISPLAY).toLanguageTag());
            state.put("locale/format", Locale.getDefault(Locale.Category.FORMAT).toLanguageTag());
            state.put("timezone", TimeZone.getDefault().getID());
            // These public accessors can initialize caches. The order-only control omits all of them.
            JavaCore.getOptions().forEach((key, value) -> state.put("javaOption/" + key, value));
            state.put("workspace/autoBuilding", Boolean.toString(ResourcesPlugin.getWorkspace().getDescription().isAutoBuilding()));
            for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
                state.put("project/" + project.getName(), Boolean.toString(project.isOpen()));
            }
            var root = Platform.getPreferencesService().getRootNode();
            for (String qualifier : List.of("org.eclipse.jdt.core", "org.eclipse.jdt.ui", "org.eclipse.jdt.junit.core", "org.eclipse.ui.workbench")) {
                String path = "/instance/" + qualifier;
                if (root.nodeExists(path)) {
                    var node = root.node(path);
                    for (String key : node.keys()) state.put("preference/" + qualifier + "/" + key, node.get(key, ""));
                }
            }
            Map<String, Integer> jobs = new TreeMap<>();
            for (Job job : Job.getJobManager().find(null)) {
                String key = job.getClass().getName() + "/" + job.getState();
                jobs.merge(key, 1, Integer::sum);
            }
            jobs.forEach((key, value) -> state.put("job/" + key, value.toString()));
            // Do not perform syncExec from a notification callback. That could create new deadlocks.
            if (Display.getCurrent() != null && PlatformUI.isWorkbenchRunning()) {
                List<String> views = new ArrayList<>();
                for (var window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
                    for (var page : window.getPages()) {
                        for (var view : page.getViewReferences()) views.add(view.getId());
                        for (var editor : page.getEditorReferences()) views.add("editor:" + editor.getId());
                        if (page.getActivePart() != null) views.add("active:" + page.getActivePart().getSite().getId());
                    }
                }
                java.util.Collections.sort(views);
                state.put("workbench/parts", views.toString());
            } else state.put("workbench/parts", "<not-ui-thread>");
        } catch (Exception | LinkageError error) {
            SINK.write(Map.of("event", "OBSERVER_ERROR", "errorType", error.getClass().getName()));
        }
        return state;
    }

    private static Map<String, Object> delta(Map<String, String> before, Map<String, String> after) {
        Map<String, Object> result = new TreeMap<>();
        Set<String> keys = new TreeSet<>(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            if (!Objects.equals(before.get(key), after.get(key)) || before.containsKey(key) != after.containsKey(key)) {
                var change = new LinkedHashMap<String, Object>();
                change.put("beforePresent", before.containsKey(key));
                change.put("afterPresent", after.containsKey(key));
                change.put("before", safe(key, before.get(key)));
                change.put("after", safe(key, after.get(key)));
                result.put(key, change);
            }
        }
        return result;
    }

    private static String safe(String key, String value) {
        if (value == null) return "<absent>";
        boolean allowed = !key.startsWith("property/") && !key.startsWith("preference/");
        allowed |= Set.of("property/file.encoding", "property/sun.jnu.encoding", "property/user.language",
                "property/user.country", "property/user.timezone", "property/line.separator",
                "property/jdt.stateTrace.smoke", "property/modules").contains(key);
        allowed |= key.endsWith("/defaultEditorForContentType_org.eclipse.jdt.core.javaClass");
        if (!allowed) return "<redacted:changed-value>";
        return value.length() > 512 ? value.substring(0, 512) + "<truncated>" : value;
    }

    /** Called only on the existing null-proposal failure path; does not call getSource(). */
    public static void missingSource(IClassFile classFile) {
        if (!SINK.state) return;
        var row = new LinkedHashMap<String, Object>();
        row.put("event", "MISSING_SOURCE");
        try {
            var root = (IPackageFragmentRoot) classFile.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
            row.put("classFile", classFile.getHandleIdentifier());
            row.put("sourceAttachment", String.valueOf(root.getSourceAttachmentPath()));
            row.put("attachmentRoot", String.valueOf(root.getSourceAttachmentRootPath()));
            IFile cp = classFile.getJavaProject().getProject().getFile(".classpath");
            if (cp.exists()) row.put("classpathSha256", digest(Files.readAllBytes(cp.getLocation().toFile().toPath())));
            var attachment = root.getSourceAttachmentPath();
            if (attachment != null) {
                IResource resource = ResourcesPlugin.getWorkspace().getRoot().findMember(attachment);
                Path file = resource == null ? attachment.toFile().toPath() : resource.getLocation().toFile().toPath();
                row.put("archiveExists", Files.isRegularFile(file));
                if (Files.isRegularFile(file)) {
                    row.put("archiveSize", Files.size(file));
                    try (ZipFile zip = new ZipFile(file.toFile())) {
                        row.put("archiveEntries", zip.size());
                        var entry = zip.getEntry("pack/age/X.java");
                        row.put("xJavaPresent", entry != null);
                        if (entry != null) {
                            try (var input = zip.getInputStream(entry)) {
                                row.put("xJavaSha256", digest(input.readNBytes(1024 * 1024)));
                            }
                        }
                    }
                }
            }
        } catch (Exception | LinkageError error) {
            row.put("inspectionError", error.getClass().getName());
        }
        SINK.write(row);
    }

    private static String digest(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static final class Sink {
        final boolean state = "state".equals(System.getenv("JDT_STATE_TRACE_MODE"));
        private final PrintWriter out;
        private long sequence;
        Sink() {
            PrintWriter writer = null;
            String directory = System.getenv("JDT_STATE_TRACE_DIR");
            if (directory != null) {
                try {
                    Path folder = Path.of(directory);
                    Files.createDirectories(folder);
                    writer = new PrintWriter(Files.newBufferedWriter(folder.resolve("trace-" + ProcessHandle.current().pid() + ".jsonl"), StandardCharsets.UTF_8));
                } catch (Exception error) {
                    System.err.println("STATE_TRACE_IO_ERROR: " + error.getClass().getName());
                }
            }
            out = writer;
        }
        synchronized void write(Map<String, ?> row) {
            if (out == null) return;
            var event = new LinkedHashMap<String, Object>();
            event.put("seq", ++sequence);
            event.put("pid", ProcessHandle.current().pid());
            event.put("utc", Instant.now().toString());
            event.put("nano", System.nanoTime());
            event.put("thread", Thread.currentThread().getName());
            event.put("mode", state ? "state" : "order");
            event.putAll(row);
            out.println(json(event));
            out.flush();
            if (out.checkError()) System.err.println("STATE_TRACE_IO_ERROR: output write failed");
        }
    }

    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> map) {
            return "{" + map.entrySet().stream().map(e -> json(String.valueOf(e.getKey())) + ":" + json(e.getValue())).collect(java.util.stream.Collectors.joining(",")) + "}";
        }
        if (value instanceof Iterable<?> values) {
            List<String> items = new ArrayList<>();
            values.forEach(item -> items.add(json(item)));
            return "[" + String.join(",", items) + "]";
        }
        StringBuilder result = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) {
            if (c == '\\' || c == '"') result.append('\\').append(c);
            else if (c < 32 || Character.isSurrogate(c)) result.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            else result.append(c);
        }
        return result.append('"').toString();
    }
}
