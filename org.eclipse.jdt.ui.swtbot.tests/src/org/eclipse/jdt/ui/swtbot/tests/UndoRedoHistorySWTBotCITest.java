/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.swtbot.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEclipseEditor;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.TimeoutException;

import org.eclipse.jface.bindings.TriggerSequence;
import org.eclipse.jface.bindings.keys.KeySequence;
import org.eclipse.jface.bindings.keys.KeyStroke;

import org.eclipse.ui.keys.IBindingService;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * CI adapter for {@link UndoRedoHistorySWTBotTest}.
 * <p>
 * The first upstream run exposed two test-driver assumptions before either
 * assertion could say anything about issue 454:
 * </p>
 * <ul>
 * <li>the SWT keyboard strategy upper-cased lower-case setup text on GTK;</li>
 * <li>Ctrl+Y is deliberately unbound on GTK, where the active Redo binding is
 * Ctrl+Shift+Z.</li>
 * </ul>
 * <p>
 * This subclass inherits all other diagnostic scenarios unchanged and replaces
 * only those two methods. Reflection is intentionally confined to this
 * investigation-only adapter so the detailed diagnostic implementation can
 * remain unchanged while the follow-up CI run tests the intended interactions.
 * </p>
 */
public class UndoRedoHistorySWTBotCITest extends UndoRedoHistorySWTBotTest {

	private static final String REDO_COMMAND_ID= "org.eclipse.ui.edit.redo";
	private static final String AWT_KEYBOARD_STRATEGY=
			"org.eclipse.swtbot.swt.finder.keyboard.AWTKeyboardStrategy";
	private static final String SWT_KEYBOARD_STRATEGY=
			"org.eclipse.swtbot.swt.finder.keyboard.SWTKeyboardStrategy";

	@Override
	@Test
	void swtEventCtrlHMustPreserveHistoryInTheMiddleOfTheStack() throws Exception {
		// Create the stack with the native strategy that already passed in the
		// first CI run. Switch only for the Ctrl+H event under investigation.
		invoke("useKeyboardStrategy", new Class<?>[] { String.class }, AWT_KEYBOARD_STRATEGY);
		Object state= preparePartialRedoState();
		invoke("useKeyboardStrategy", new Class<?>[] { String.class }, SWT_KEYBOARD_STRATEGY);
		invoke("assertCtrlHPreservesHistory", new Class<?>[] { redoStateClass() }, state);
	}

	@Override
	@Test
	void redoShortcutMustRestoreTheExpectedNextState() throws Exception {
		invoke("useKeyboardStrategy", new Class<?>[] { String.class }, AWT_KEYBOARD_STRATEGY);
		Object state= preparePartialRedoState();
		String expected= (String) invokeOn(state, "nextRedoText", new Class<?>[0]);

		ITextEditor textEditor= field("textEditor", ITextEditor.class);
		SWTBotEclipseEditor botEditor= field("botEditor", SWTBotEclipseEditor.class);
		SWTWorkbenchBot bot= field("bot", SWTWorkbenchBot.class);
		KeySequence binding= uiCall(() -> activeKeySequence(textEditor));
		KeyStroke[] strokes= binding.getKeyStrokes();
		assertEquals(1, strokes.length, "Redo unexpectedly uses a multi-stroke binding: " + binding);
		KeyStroke stroke= strokes[0];
		int naturalKey= stroke.getNaturalKey();
		assertTrue(naturalKey > 0 && naturalKey <= Character.MAX_VALUE,
				"Redo binding does not end in a character key: " + binding);

		List<String> keyEvents= new CopyOnWriteArrayList<>();
		StyledText styledText= botEditor.getStyledText().widget;
		Listener listener= event -> keyEvents.add(describe(event));
		uiRun(() -> {
			styledText.addListener(SWT.KeyDown, listener);
			styledText.addListener(SWT.KeyUp, listener);
			styledText.addListener(SWT.Traverse, listener);
		});
		try {
			botEditor.pressShortcut(stroke.getModifierKeys(), (char) naturalKey);
			try {
				bot.waitUntil(new DefaultCondition() {
					@Override
					public boolean test() {
						return Objects.equals(expected, botEditor.getText());
					}

					@Override
					public String getFailureMessage() {
						return "Expected Redo result was not reached";
					}
				}, 10_000);
			} catch (TimeoutException e) {
				fail("The active Redo binding did not restore the expected text.\n"
						+ "binding=" + binding + "\n"
						+ "keyEvents=" + keyEvents + "\n"
						+ "expected=\"" + escape(expected) + "\"\n"
						+ "actual=\"" + escape(botEditor.getText()) + "\"\n"
						+ currentStateDump(), e);
			}
			assertEquals(expected, botEditor.getText(),
					() -> "Active Redo binding produced the wrong text.\n"
							+ "binding=" + binding + "\nkeyEvents=" + keyEvents + "\n" + currentStateDump());
		} finally {
			uiRun(() -> {
				if (!styledText.isDisposed()) {
					styledText.removeListener(SWT.KeyDown, listener);
					styledText.removeListener(SWT.KeyUp, listener);
					styledText.removeListener(SWT.Traverse, listener);
				}
			});
		}
	}

	private Object preparePartialRedoState() throws Exception {
		Class<?> shapeClass= historyShapeClass();
		@SuppressWarnings({ "rawtypes", "unchecked" })
		Object partialRedo= Enum.valueOf((Class<? extends Enum>) shapeClass, "PARTIAL_REDO");
		return invoke("prepareRedoState", new Class<?>[] { shapeClass }, partialRedo);
	}

	private static KeySequence activeKeySequence(ITextEditor textEditor) {
		IBindingService bindingService= textEditor.getSite().getService(IBindingService.class);
		assertNotNull(bindingService, "No IBindingService is available");
		TriggerSequence trigger= bindingService.getBestActiveBindingFor(REDO_COMMAND_ID);
		assertNotNull(trigger, "No active Redo binding is configured");
		assertTrue(trigger instanceof KeySequence, "Active Redo binding is not a KeySequence: " + trigger);
		return (KeySequence) trigger;
	}

	private String currentStateDump() {
		try {
			return String.valueOf(invoke("safeCurrentStateDump", new Class<?>[0]));
		} catch (Exception e) {
			return "State dump unavailable: " + e;
		}
	}

	private static String describe(Event event) {
		return "type=" + event.type + " keyCode=" + event.keyCode + " character="
				+ (int) event.character + " stateMask=0x" + Integer.toHexString(event.stateMask)
				+ " detail=" + event.detail + " doit=" + event.doit;
	}

	private static String escape(String text) {
		return text.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n")
				.replace("\t", "\\t").replace("\b", "\\b").replace("\0", "\\0");
	}

	private static Class<?> historyShapeClass() throws ClassNotFoundException {
		return Class.forName(UndoRedoHistorySWTBotTest.class.getName() + "$HistoryShape");
	}

	private static Class<?> redoStateClass() throws ClassNotFoundException {
		return Class.forName(UndoRedoHistorySWTBotTest.class.getName() + "$RedoState");
	}

	private Object invoke(String name, Class<?>[] parameterTypes, Object... arguments) throws Exception {
		return invokeOn(this, name, parameterTypes, arguments);
	}

	private static Object invokeOn(Object receiver, String name, Class<?>[] parameterTypes, Object... arguments)
			throws Exception {
		Method method= findMethod(receiver.getClass(), name, parameterTypes);
		method.setAccessible(true);
		try {
			return method.invoke(receiver, arguments);
		} catch (InvocationTargetException e) {
			Throwable cause= e.getCause();
			if (cause instanceof Exception exception) {
				throw exception;
			}
			if (cause instanceof Error error) {
				throw error;
			}
			throw new AssertionError(cause);
		}
	}

	private static Method findMethod(Class<?> type, String name, Class<?>[] parameterTypes)
			throws NoSuchMethodException {
		Class<?> current= type;
		while (current != null) {
			try {
				return current.getDeclaredMethod(name, parameterTypes);
			} catch (NoSuchMethodException e) {
				current= current.getSuperclass();
			}
		}
		throw new NoSuchMethodException(name);
	}

	private <T> T field(String name, Class<T> type) throws Exception {
		Field field= UndoRedoHistorySWTBotTest.class.getDeclaredField(name);
		field.setAccessible(true);
		return type.cast(field.get(this));
	}

	private static void uiRun(ThrowingRunnable runnable) throws Exception {
		uiCall(() -> {
			runnable.run();
			return null;
		});
	}

	private static <T> T uiCall(ThrowingSupplier<T> supplier) throws Exception {
		AtomicReference<T> result= new AtomicReference<>();
		AtomicReference<Throwable> failure= new AtomicReference<>();
		Display.getDefault().syncExec(() -> {
			try {
				result.set(supplier.get());
			} catch (Throwable t) {
				failure.set(t);
			}
		});
		Throwable throwable= failure.get();
		if (throwable instanceof Exception exception) {
			throw exception;
		}
		if (throwable instanceof Error error) {
			throw error;
		}
		if (throwable != null) {
			throw new AssertionError(throwable);
		}
		return result.get();
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	@FunctionalInterface
	private interface ThrowingSupplier<T> {
		T get() throws Exception;
	}
}
