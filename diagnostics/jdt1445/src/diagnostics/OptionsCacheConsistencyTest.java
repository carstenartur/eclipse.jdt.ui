/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package diagnostics;

import static org.junit.Assert.*;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IRegion;
import org.eclipse.text.edits.TextEdit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Headless PDE/JUnit diagnosis for eclipse.jdt.ui#1445 and #79.
 * Executes the real JavaModelManager and the real JDT formatter, not a model of them.
 * Reflection is used only to force a cold cache and install a delegating read barrier.
 * The barrier delays a value that the real preferences object has already returned.
 */
public class OptionsCacheConsistencyTest {
    private static final long TIMEOUT = 30;
    private static final String KEY = DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_CLOSING_PAREN_IN_CAST;
    private static final String SOURCE = "package test1;\npublic class E1 {\n    public void foo( Object o ) {\n        String s    = (String)o;\n    }\n}";
    private static final String EXPECTED = "package test1;\npublic class E1 {\n    public void foo( Object o ) {\n        String s = (String) o;\n    }\n}";
    private Hashtable<String, String> savedOptions;

    @Before
    public void rememberOptions() {
        assertTrue("Must run inside Eclipse, not plain JUnit", Platform.isRunning());
        ResourcesPlugin.getWorkspace();
        savedOptions = JavaCore.getOptions();
    }

    @After
    public void restoreOptions() {
        if (savedOptions != null) {
            JavaCore.setOptions(savedOptions);
        }
    }

    @Test
    public void returnedOptionsAreDefensiveCopies() {
        Hashtable<String, String> options = JavaCore.getOptions();
        String before = options.get(KEY);
        options.put(KEY, "not-an-option-value");
        assertEquals(before, JavaCore.getOptions().get(KEY));
    }

    @Test
    public void sequentialUpdatesReachBothReadApis() {
        Hashtable<String, String> options = formatterOptions();
        options.put(KEY, JavaCore.DO_NOT_INSERT);
        JavaCore.setOptions(options);
        assertEquals(JavaCore.DO_NOT_INSERT, JavaCore.getOptions().get(KEY));
        options.put(KEY, JavaCore.INSERT);
        JavaCore.setOptions(options);
        assertEquals(JavaCore.INSERT, JavaCore.getOption(KEY));
        assertEquals(JavaCore.INSERT, JavaCore.getOptions().get(KEY));
    }

    @Test
    public void isolatedFormatterIsStableWithFixedSourceAndOptions() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            Map<String, String> fixed = Map.copyOf(formatterOptions());
            for (int worker = 0; worker < 4; worker++) {
                tasks.add(pool.submit(() -> {
                    for (int run = 0; run < 50; run++) {
                        try {
                            assertEquals(EXPECTED, formatChangedLine(fixed));
                        } catch (Exception ex) {
                            throw new AssertionError(ex);
                        }
                    }
                }));
            }
            for (Future<?> task : tasks) {
                task.get(TIMEOUT, TimeUnit.SECONDS);
            }
            System.out.println("FORMATTER_CONTROL: 200 invocations, four workers, fixed source/options");
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(TIMEOUT, TimeUnit.SECONDS));
        }
    }

    @Test
    public void readerMustNotOverwriteCompletedSetOptions() throws Exception {
        Observation observation = race(false);
        assertEquals("Both operations completed: cached options must match persisted preferences", observation.persisted(), observation.cached().get(KEY));
    }

    @Test
    public void readerMustNotUndoPreferenceInvalidation() throws Exception {
        Observation observation = race(true);
        assertEquals("A read must not resurrect a cache invalidated by a completed preference update", observation.persisted(), observation.cached().get(KEY));
    }

    @Test
    public void completedOptionUpdateMustReachChangedLineFormatter() throws Exception {
        Observation observation = race(false);
        assertEquals("Fresh formatter after both threads completed must see the newly selected spacing", EXPECTED, formatChangedLine(observation.cached()));
    }

    private record Observation(String persisted, Hashtable<String, String> cached) {}

    /** This is an interleaving test; there are no sleep-based scheduling guesses. */
    private Observation race(boolean directPreferenceWrite) throws Exception {
        JavaModelManager manager = JavaModelManager.getJavaModelManager();
        Hashtable<String, String> oldOptions = formatterOptions();
        oldOptions.put(KEY, JavaCore.DO_NOT_INSERT);
        JavaCore.setOptions(oldOptions);
        assertEquals(JavaCore.DO_NOT_INSERT, JavaCore.getOption(KEY));

        Field lookupField = JavaModelManager.class.getDeclaredField("preferencesLookup");
        lookupField.setAccessible(true);
        Object lookup = lookupField.get(manager);
        int length = Array.getLength(lookup);
        Object[] originalNodes = new Object[length];
        for (int i = 0; i < length; i++) {
            originalNodes[i] = Array.get(lookup, i);
        }
        Field cacheField = JavaModelManager.class.getDeclaredField("optionsCache");
        cacheField.setAccessible(true);

        CountDownLatch oldValueRead = new CountDownLatch(1);
        CountDownLatch resumeReader = new CountDownLatch(1);
        AtomicBoolean intercepted = new AtomicBoolean();
        AtomicReference<Thread> readerThread = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "jdt1445-options-reader");
            thread.setDaemon(true);
            return thread;
        });
        Future<Hashtable<String, String>> reader = null;
        try {
            for (int i = 0; i < length; i++) {
                Object delegate = originalNodes[i];
                if (delegate == null) {
                    continue;
                }
                Object wrapper = Proxy.newProxyInstance(IEclipsePreferences.class.getClassLoader(),
                        new Class<?>[] { IEclipsePreferences.class }, (proxy, method, args) -> {
                    Object value;
                    try {
                        value = method.invoke(delegate, args);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    }
                    if (Thread.currentThread() == readerThread.get()
                            && method.getName().equals("get") && args != null && args.length == 2
                            && KEY.equals(args[0]) && JavaCore.DO_NOT_INSERT.equals(value)
                            && intercepted.compareAndSet(false, true)) {
                        oldValueRead.countDown();
                        if (!resumeReader.await(TIMEOUT, TimeUnit.SECONDS)) {
                            throw new AssertionError("Timed out waiting to resume options reader");
                        }
                    }
                    return value;
                });
                Array.set(lookup, i, wrapper);
            }
            cacheField.set(manager, null);
            reader = executor.submit(() -> {
                readerThread.set(Thread.currentThread());
                return JavaCore.getOptions();
            });
            assertTrue("Reader did not reach the controlled preference read", oldValueRead.await(TIMEOUT, TimeUnit.SECONDS));

            if (directPreferenceWrite) {
                InstanceScope.INSTANCE.getNode(JavaCore.PLUGIN_ID).put(KEY, JavaCore.INSERT);
            } else {
                Hashtable<String, String> newOptions = formatterOptions();
                newOptions.put(KEY, JavaCore.INSERT);
                JavaCore.setOptions(newOptions);
                assertEquals("Writer must publish the new value before reader is resumed", JavaCore.INSERT, JavaCore.getOptions().get(KEY));
            }
            assertEquals(JavaCore.INSERT, JavaCore.getOption(KEY));
            resumeReader.countDown();
            Hashtable<String, String> overlappingRead = reader.get(TIMEOUT, TimeUnit.SECONDS);
            // The overlapping read may legally observe the old value; a LATER read may not.
            String persisted = JavaCore.getOption(KEY);
            Hashtable<String, String> cached = JavaCore.getOptions();
            System.out.printf("OPTIONS_RACE writer=%s overlapping=%s persisted=%s subsequentCached=%s%n",
                    directPreferenceWrite ? "preferences.put" : "JavaCore.setOptions",
                    overlappingRead.get(KEY), persisted, cached.get(KEY));
            return new Observation(persisted, cached);
        } finally {
            resumeReader.countDown();
            try {
                if (reader != null) {
                    reader.get(TIMEOUT, TimeUnit.SECONDS);
                }
            } finally {
                for (int i = 0; i < length; i++) {
                    Array.set(lookup, i, originalNodes[i]);
                }
                executor.shutdownNow();
                assertTrue("Reader thread leaked", executor.awaitTermination(TIMEOUT, TimeUnit.SECONDS));
            }
        }
    }

    private Hashtable<String, String> formatterOptions() {
        Hashtable<String, String> options = new Hashtable<>(savedOptions);
        options.putAll(DefaultCodeFormatterConstants.getJavaConventionsSettings());
        options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);
        options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
        options.put(DefaultCodeFormatterConstants.FORMATTER_INDENTATION_SIZE, "4");
        options.put(KEY, JavaCore.INSERT);
        return options;
    }

    private static String formatChangedLine(Map<String, String> options) throws Exception {
        Document target = new Document(SOURCE);
        IRegion line = target.getLineInformation(3);
        CodeFormatter formatter = ToolFactory.createCodeFormatter(new HashMap<>(options));
        TextEdit edit = formatter.format(CodeFormatter.K_COMPILATION_UNIT | CodeFormatter.F_INCLUDE_COMMENTS,
                SOURCE, new IRegion[] { line }, 0, "\n");
        assertNotNull("Formatter rejected the source", edit);
        edit.apply(target);
        return target.get();
    }
}
