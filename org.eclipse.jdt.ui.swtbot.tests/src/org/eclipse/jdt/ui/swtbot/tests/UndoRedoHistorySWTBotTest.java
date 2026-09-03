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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEclipseEditor;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.TimeoutException;

import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.IOperationHistoryListener;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.IUndoableOperation;
import org.eclipse.core.commands.operations.OperationHistoryEvent;
import org.eclipse.core.commands.operations.OperationHistoryFactory;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Platform;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension4;
import org.eclipse.jface.text.IDocumentListener;

import org.eclipse.text.undo.DocumentUndoManagerRegistry;
import org.eclipse.text.undo.IDocumentUndoManager;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ui.JavaUI;

/**
 * Diagnostic-only end-to-end tests for
 * https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/454.
 * <p>
 * The test deliberately observes the document-specific operation history in
 * addition to the visible editor text. A failure reports immutable before and
 * after snapshots, document events, operation-history events, SWT key events,
 * action state, focus, shells, and the complete document text.
 * </p>
 * <p>
 * This class is intended to confirm or disprove the reported problem in the
 * normal upstream build. It is not yet proposed as a permanent regression
 * suite.
 * </p>
 */
public class UndoRedoHistorySWTBotTest {

	private static final String PROJECT_NAME= "Issue454SWTBotProject";
	private static final String JAVA_FILE_NAME= "Issue454.java";
	private static final String TEXT_FILE_NAME= "Issue454.txt";
	private static final String JAVA_INITIAL_TEXT=
			"public class Issue454 {\n\tString value = \"\";\n}\n";
	private static final String TEXT_INITIAL_TEXT= "issue 454: \n";
	private static final String FIRST_EDIT= "first";
	private static final String SECOND_EDIT= "second";
	private static final String THIRD_EDIT= "third";
	private static final String TRACE_PROPERTY= "eclipse.jdt.ui.issue454.trace";
	private static final String AWT_KEYBOARD_STRATEGY=
			"org.eclipse.swtbot.swt.finder.keyboard.AWTKeyboardStrategy";
	private static final String SWT_KEYBOARD_STRATEGY=
			"org.eclipse.swtbot.swt.finder.keyboard.SWTKeyboardStrategy";

	private static String originalKeyboardStrategy;

	private SWTWorkbenchBot bot;
	private SWTBotEclipseEditor botEditor;
	private SWTBotShell searchShell;
	private IProject project;
	private IFile file;
	private String fileName;
	private String initialText;
	private int insertionOffset;
	private ITextEditor textEditor;
	private IDocument document;
	private IDocumentExtension4 documentExtension;
	private IDocumentUndoManager undoManager;
	private IUndoContext undoContext;
	private IOperationHistory history;
	private String activeKeyboardStrategy;

	@BeforeAll
	static void rememberKeyboardStrategy() {
		originalKeyboardStrategy= SWTBotPreferences.KEYBOARD_STRATEGY;
	}

	@AfterAll
	static void restoreKeyboardStrategy() {
		SWTBotPreferences.KEYBOARD_STRATEGY= originalKeyboardStrategy;
	}

	@BeforeEach
	void setUp() throws Exception {
		bot= new SWTWorkbenchBot();
		activeKeyboardStrategy= AWT_KEYBOARD_STRATEGY;
		SWTBotPreferences.KEYBOARD_STRATEGY= activeKeyboardStrategy;
		createJavaProjectAndFiles();
		openEditor(file, JavaUI.ID_CU_EDITOR, JAVA_FILE_NAME, JAVA_INITIAL_TEXT,
				JAVA_INITIAL_TEXT.indexOf("\"\"") + 1);
	}

	@AfterEach
	void tearDown() throws Exception {
		closeSearchShell();
		closeEditor();
		if (project != null && project.exists()) {
			project.delete(true, true, null);
		}
	}

	@Test
	void nativeCtrlHMustPreserveHistoryInTheMiddleOfTheStack() throws Exception {
		useKeyboardStrategy(AWT_KEYBOARD_STRATEGY);
		assertCtrlHPreservesHistory(prepareRedoState(HistoryShape.PARTIAL_REDO));
	}

	@Test
	void swtEventCtrlHMustPreserveHistoryInTheMiddleOfTheStack() throws Exception {
		useKeyboardStrategy(SWT_KEYBOARD_STRATEGY);
		assertCtrlHPreservesHistory(prepareRedoState(HistoryShape.PARTIAL_REDO));
	}

	@Test
	void nativeCtrlHMustPreserveHistoryAfterASingleUndo() throws Exception {
		useKeyboardStrategy(AWT_KEYBOARD_STRATEGY);
		assertCtrlHPreservesHistory(prepareRedoState(HistoryShape.SINGLE_UNDO));
	}

	@Test
	void nativeCtrlHMustPreserveHistoryWhenAllEditsAreUndone() throws Exception {
		useKeyboardStrategy(AWT_KEYBOARD_STRATEGY);
		assertCtrlHPreservesHistory(prepareRedoState(HistoryShape.ALL_UNDONE));
	}

	@Test
	void nativeCtrlHMustAlsoPreserveHistoryInTheGenericTextEditor() throws Exception {
		reopenGenericTextEditor();
		useKeyboardStrategy(AWT_KEYBOARD_STRATEGY);
		assertCtrlHPreservesHistory(prepareRedoState(HistoryShape.PARTIAL_REDO));
	}

	@Test
	void navigationEscapeFunctionAndModifierKeysMustPreserveRedo() throws Exception {
		useKeyboardStrategy(AWT_KEYBOARD_STRATEGY);
		RedoState state= prepareRedoState(HistoryShape.PARTIAL_REDO);
		List<Interaction> interactions= List.of(
				new Interaction("Left Arrow", () -> pressSpecialKey(SWT.ARROW_LEFT)),
				new Interaction("Right Arrow", () -> pressSpecialKey(SWT.ARROW_RIGHT)),
				new Interaction("Up Arrow", () -> pressSpecialKey(SWT.ARROW_UP)),
				new Interaction("Down Arrow", () -> pressSpecialKey(SWT.ARROW_DOWN)),
				new Interaction("Home", () -> pressSpecialKey(SWT.HOME)),
				new Interaction("End", () -> pressSpecialKey(SWT.END)),
				new Interaction("Page Up", () -> pressSpecialKey(SWT.PAGE_UP)),
				new Interaction("Page Down", () -> pressSpecialKey(SWT.PAGE_DOWN)),
				new Interaction("Escape", () -> pressSpecialKey(SWT.ESC)),
				new Interaction("F12", () -> pressSpecialKey(SWT.F12)),
				new Interaction("Shift modifier only", () -> pressModifierOnly(SWT.SHIFT)),
				new Interaction("Control modifier only", () -> pressModifierOnly(SWT.CTRL)));
		assertInteractionsPreserveHistory(state, interactions);
	}

	@Test
	void noOpDeletionAtDocumentBoundariesMustPreserveRedo() throws Exception {
		useKeyboardStrategy(AWT_KEYBOARD_STRATEGY);
		RedoState state= prepareRedoState(HistoryShape.PARTIAL_REDO);
		List<Interaction> interactions= List.of(
				new Interaction("Backspace at document start", () -> {
					moveCaretTo(0);
					pressSpecialKey(SWT.BS);
				}),
				new Interaction("Delete at document end", () -> {
					moveCaretTo(document.getLength());
					pressSpecialKey(SWT.DEL);
				}));
		assertInteractionsPreserveHistory(state, interactions);
	}

	@Test
	void saveShortcutMustPreserveRedo() throws Exception {
		useKeyboardStrategy(AWT_KEYBOARD_STRATEGY);
		RedoState state= prepareRedoState(HistoryShape.PARTIAL_REDO);
		assertInteractionPreservesHistory(state, new Interaction("Save shortcut", () -> {
			botEditor.pressShortcut(SWT.MOD1, 's');
			bot.waitUntil(new DefaultCondition() {
				@Override
				public boolean test() {
					return !uiCallUnchecked(textEditor::isDirty);
				}

				@Override
				public String getFailureMessage() {
					return "Editor remained dirty after the save shortcut.\n" + safeCurrentStateDump();
				}
			}, 10_000);
		}));
	}

	@Test
	void redoShortcutMustRestoreTheExpectedNextState() throws Exception {
		useKeyboardStrategy(AWT_KEYBOARD_STRATEGY);
		RedoState state= prepareRedoState(HistoryShape.PARTIAL_REDO);
		DiagnosticSession diagnostics= new DiagnosticSession(state.historyBeforeInteraction());
		diagnostics.attach();
		try {
			botEditor.pressShortcut(SWT.MOD1, 'y');
			try {
				waitUntilText(state.nextRedoText());
			} catch (TimeoutException e) {
				fail("The Redo shortcut did not restore the expected text.\n"
						+ diagnostics.dump(snapshotUnchecked("after failed Redo shortcut")), e);
			}
			assertEquals(state.nextRedoText(), botEditor.getText(),
					() -> "The Redo shortcut did not restore the expected text.\n"
							+ diagnostics.dump(snapshotUnchecked("after Redo shortcut")));
		} finally {
			diagnostics.detach();
		}
	}

	@Test
	void ordinaryTypingMustCreateANewBranchAndDiscardRedo() throws Exception {
		useKeyboardStrategy(AWT_KEYBOARD_STRATEGY);
		RedoState state= prepareRedoState(HistoryShape.PARTIAL_REDO);
		DiagnosticSession diagnostics= new DiagnosticSession(state.historyBeforeInteraction());
		diagnostics.attach();
		try {
			moveCaretTo(state.currentText().indexOf(SECOND_EDIT) + SECOND_EDIT.length());
			String before= botEditor.getText();
			int caret= caretOffset();
			String expected= insertAt(before, caret, "h");
			botEditor.typeText("h");
			waitUntilText(expected);
			HistorySnapshot after= snapshot("after ordinary typing");
			String details= diagnostics.dump(after);

			assertNotEquals(state.currentText(), botEditor.getText(),
					"The positive control did not edit the document.\n" + details);
			assertFalse(diagnostics.documentEvents().isEmpty(),
					"The positive control produced no document event.\n" + details);
			assertEquals(0, after.redo().size(),
					"A real edit must discard the old redo branch.\n" + details);
			assertTrue(diagnostics.operationEvents().contains(OperationHistoryEvent.OPERATION_REMOVED),
					"No removal of the old redo branch was observed.\n" + details);
			waitUntilDisabled(action(ITextEditorActionConstants.REDO), "Redo");
		} finally {
			diagnostics.detach();
		}
	}

	@Test
	void realBackspaceMustCreateANewBranchAndDiscardRedo() throws Exception {
		useKeyboardStrategy(AWT_KEYBOARD_STRATEGY);
		RedoState state= prepareRedoState(HistoryShape.PARTIAL_REDO);
		DiagnosticSession diagnostics= new DiagnosticSession(state.historyBeforeInteraction());
		diagnostics.attach();
		try {
			int caret= state.currentText().indexOf(SECOND_EDIT) + SECOND_EDIT.length();
			moveCaretTo(caret);
			String expected= state.currentText().substring(0, caret - 1) + state.currentText().substring(caret);
			pressSpecialKey(SWT.BS);
			waitUntilText(expected);
			HistorySnapshot after= snapshot("after real Backspace");
			String details= diagnostics.dump(after);

			assertNotEquals(state.currentText(), botEditor.getText(),
					"The Backspace positive control did not edit the document.\n" + details);
			assertFalse(diagnostics.documentEvents().isEmpty(),
					"The Backspace positive control produced no document event.\n" + details);
			assertEquals(0, after.redo().size(),
					"A real deletion must discard the old redo branch.\n" + details);
			assertTrue(diagnostics.operationEvents().contains(OperationHistoryEvent.OPERATION_REMOVED),
					"No removal of the old redo branch was observed.\n" + details);
			waitUntilDisabled(action(ITextEditorActionConstants.REDO), "Redo");
		} finally {
			diagnostics.detach();
		}
	}

	private void assertCtrlHPreservesHistory(RedoState state) throws Exception {
		DiagnosticSession diagnostics= new DiagnosticSession(state.historyBeforeInteraction());
		diagnostics.attach();
		try {
			String textBefore= botEditor.getText();
			long stampBefore= modificationStamp();
			botEditor.pressShortcut(SWT.CTRL, 'h');
			try {
				bot.waitUntil(Conditions.shellIsActive("Search"), 10_000);
			} catch (TimeoutException e) {
				HistorySnapshot after= snapshotUnchecked("after Ctrl+H without Search dialog");
				fail("Ctrl+H did not open the Search dialog.\n" + diagnostics.dump(after), e);
			}
			searchShell= bot.shell("Search");
			settleInput();

			HistorySnapshot after= snapshot("after Ctrl+H");
			assertUnchanged(textBefore, stampBefore, state.historyBeforeInteraction(), after, diagnostics,
					"Ctrl+H");
			closeSearchShell();
			botEditor.setFocus();
			settleInput();
			redoAndAssertNextState(state, diagnostics);
		} finally {
			diagnostics.detach();
		}
	}

	private void assertInteractionsPreserveHistory(RedoState state, List<Interaction> interactions) throws Exception {
		DiagnosticSession diagnostics= new DiagnosticSession(state.historyBeforeInteraction());
		diagnostics.attach();
		try {
			for (Interaction interaction : interactions) {
				diagnostics.clearEvents();
				String textBefore= botEditor.getText();
				long stampBefore= modificationStamp();
				try {
					interaction.action().run();
				} catch (Throwable t) {
					fail("Interaction failed: " + interaction.name() + "\n"
							+ diagnostics.dump(snapshotUnchecked("after failed " + interaction.name())), t);
				}
				settleInput();
				HistorySnapshot after= snapshot("after " + interaction.name());
				assertUnchanged(textBefore, stampBefore, state.historyBeforeInteraction(), after, diagnostics,
						interaction.name());
			}
			redoAndAssertNextState(state, diagnostics);
		} finally {
			diagnostics.detach();
		}
	}

	private void assertInteractionPreservesHistory(RedoState state, Interaction interaction) throws Exception {
		DiagnosticSession diagnostics= new DiagnosticSession(state.historyBeforeInteraction());
		diagnostics.attach();
		try {
			String textBefore= botEditor.getText();
			long stampBefore= modificationStamp();
			try {
				interaction.action().run();
			} catch (Throwable t) {
				fail("Interaction failed: " + interaction.name() + "\n"
						+ diagnostics.dump(snapshotUnchecked("after failed " + interaction.name())), t);
			}
			settleInput();
			HistorySnapshot after= snapshot("after " + interaction.name());
			assertUnchanged(textBefore, stampBefore, state.historyBeforeInteraction(), after, diagnostics,
					interaction.name());
			redoAndAssertNextState(state, diagnostics);
		} finally {
			diagnostics.detach();
		}
	}

	private void assertUnchanged(String textBefore, long stampBefore, HistorySnapshot before, HistorySnapshot after,
			DiagnosticSession diagnostics, String interactionName) {
		String details= diagnostics.dump(after);
		assertEquals(textBefore, after.text(), interactionName + " changed the document text.\n" + details);
		assertEquals(stampBefore, after.modificationStamp(),
				interactionName + " changed the document modification stamp.\n" + details);
		assertTrue(diagnostics.documentEvents().isEmpty(),
				interactionName + " produced a document edit.\n" + details);
		assertHistoryUnchanged(before, after, details);
	}

	private RedoState prepareRedoState(HistoryShape shape) throws Exception {
		assertEquals(initialText, botEditor.getText());
		HistorySnapshot baseline= snapshot("before typing");

		moveCaretTo(insertionOffset);
		String afterFirstEdit= typeAndCommit(FIRST_EDIT);
		String afterSecondEdit= typeAndCommit(SECOND_EDIT);
		String fullyEditedText= typeAndCommit(THIRD_EDIT);

		HistorySnapshot afterTyping= snapshot("after three committed typing operations");
		assertEquals(baseline.undo().size() + 3, afterTyping.undo().size(),
				() -> "Three explicitly committed edits did not create exactly three undo entries.\n"
						+ baseline.dump() + '\n' + afterTyping.dump());

		IAction undo= action(ITextEditorActionConstants.UNDO);
		IAction redo= action(ITextEditorActionConstants.REDO);
		String currentText;
		String nextRedoText;
		switch (shape) {
			case SINGLE_UNDO:
				runActionAndWait(undo, afterSecondEdit, "single Undo");
				currentText= afterSecondEdit;
				nextRedoText= fullyEditedText;
				break;
			case PARTIAL_REDO:
				runActionAndWait(undo, afterSecondEdit, "first Undo");
				runActionAndWait(undo, afterFirstEdit, "second Undo");
				runActionAndWait(redo, afterSecondEdit, "partial Redo");
				currentText= afterSecondEdit;
				nextRedoText= fullyEditedText;
				break;
			case ALL_UNDONE:
				runActionAndWait(undo, afterSecondEdit, "first Undo");
				runActionAndWait(undo, afterFirstEdit, "second Undo");
				runActionAndWait(undo, initialText, "third Undo");
				currentText= initialText;
				nextRedoText= afterFirstEdit;
				break;
			default:
				throw new AssertionError(shape);
		}

		HistorySnapshot beforeInteraction= snapshot("prepared " + shape);
		assertTrue(beforeInteraction.redo().size() > 0,
				() -> "The prepared state has no redo entry.\n" + beforeInteraction.dump());
		assertTrue(beforeInteraction.redoActionEnabled(),
				() -> "The Redo action is disabled in the prepared state.\n" + beforeInteraction.dump());
		assertEquals(currentText, beforeInteraction.text());
		return new RedoState(currentText, nextRedoText, beforeInteraction);
	}

	private String typeAndCommit(String text) throws Exception {
		String before= botEditor.getText();
		int caret= caretOffset();
		String expected= insertAt(before, caret, text);
		botEditor.typeText(text);
		waitUntilText(expected);
		uiRun(undoManager::commit);
		settleInput();
		return expected;
	}

	private void redoAndAssertNextState(RedoState state, DiagnosticSession diagnostics) throws Exception {
		IAction redo= action(ITextEditorActionConstants.REDO);
		waitUntilEnabled(redo, "Redo");
		HistorySnapshot beforeRedo= snapshot("immediately before verification Redo");
		assertTrue(beforeRedo.redoActionEnabled(),
				() -> "Redo was disabled although the document was not edited.\n"
						+ diagnostics.dump(beforeRedo));
		uiRun(redo::run);
		try {
			waitUntilText(state.nextRedoText());
		} catch (TimeoutException e) {
			fail("Redo did not restore the expected next state.\n"
					+ diagnostics.dump(snapshotUnchecked("after timed-out verification Redo")), e);
		}
		assertEquals(state.nextRedoText(), botEditor.getText(),
				() -> "Redo did not restore the expected next state.\n"
						+ diagnostics.dump(snapshotUnchecked("after failed verification Redo")));
	}

	private void runActionAndWait(IAction action, String expectedText, String name) throws Exception {
		waitUntilEnabled(action, name);
		uiRun(action::run);
		waitUntilText(expectedText);
		settleInput();
	}

	private void assertHistoryUnchanged(HistorySnapshot before, HistorySnapshot after, String details) {
		assertSameOperationStates("Undo", before.undo(), after.undo(), details);
		assertSameOperationStates("Redo", before.redo(), after.redo(), details);
		assertSame(before.topUndo(), after.topUndo(), "Top Undo operation changed.\n" + details);
		assertSame(before.topRedo(), after.topRedo(), "Top Redo operation changed.\n" + details);
		assertEquals(before.historyCanUndo(), after.historyCanUndo(), "history.canUndo changed.\n" + details);
		assertEquals(before.historyCanRedo(), after.historyCanRedo(), "history.canRedo changed.\n" + details);
		assertEquals(before.managerUndoable(), after.managerUndoable(),
				"Document undo-manager undoable state changed.\n" + details);
		assertEquals(before.managerRedoable(), after.managerRedoable(),
				"Document undo-manager redoable state changed.\n" + details);
		assertEquals(before.undoActionEnabled(), after.undoActionEnabled(), "Undo action state changed.\n" + details);
		assertEquals(before.redoActionEnabled(), after.redoActionEnabled(), "Redo action state changed.\n" + details);
	}

	private static void assertSameOperationStates(String name, List<OperationState> expected,
			List<OperationState> actual, String details) {
		assertEquals(expected.size(), actual.size(), name + " stack size changed.\n" + details);
		for (int i= 0; i < expected.size(); i++) {
			OperationState expectedState= expected.get(i);
			OperationState actualState= actual.get(i);
			assertSame(expectedState.operation(), actualState.operation(),
					name + " stack entry " + i + " changed identity.\n" + details);
			assertEquals(expectedState.label(), actualState.label(),
					name + " stack entry " + i + " changed label.\n" + details);
			assertEquals(expectedState.canUndo(), actualState.canUndo(),
					name + " stack entry " + i + " changed canUndo.\n" + details);
			assertEquals(expectedState.canRedo(), actualState.canRedo(),
					name + " stack entry " + i + " changed canRedo.\n" + details);
			assertSameContexts(name, i, expectedState.contexts(), actualState.contexts(), details);
		}
	}

	private static void assertSameContexts(String name, int operationIndex, List<IUndoContext> expected,
			List<IUndoContext> actual, String details) {
		assertEquals(expected.size(), actual.size(),
				name + " stack entry " + operationIndex + " changed context count.\n" + details);
		for (int i= 0; i < expected.size(); i++) {
			assertSame(expected.get(i), actual.get(i),
					name + " stack entry " + operationIndex + " changed context " + i + ".\n" + details);
		}
	}

	private HistorySnapshot snapshot(String label) throws Exception {
		HistorySnapshot result= uiCall(() -> {
			IUndoableOperation[] undo= history.getUndoHistory(undoContext);
			IUndoableOperation[] redo= history.getRedoHistory(undoContext);
			IAction undoAction= textEditor.getAction(ITextEditorActionConstants.UNDO);
			IAction redoAction= textEditor.getAction(ITextEditorActionConstants.REDO);
			StyledText styledText= botEditor.getStyledText().widget;
			return HistorySnapshot.capture(label, activeKeyboardStrategy, undo, redo,
					history.getUndoOperation(undoContext), history.getRedoOperation(undoContext),
					history.canUndo(undoContext), history.canRedo(undoContext), undoManager.undoable(),
					undoManager.redoable(), undoAction != null && undoAction.isEnabled(),
					redoAction != null && redoAction.isEnabled(), document.get(),
					documentExtension.getModificationStamp(), textEditor.isDirty(), styledText.getCaretOffset(),
					describeControl(Display.getCurrent().getFocusControl()), shellSummary(),
					textEditor.getClass().getName(), textEditor.getSite().getId());
		});
		trace(result);
		return result;
	}

	private HistorySnapshot snapshotUnchecked(String label) {
		try {
			return snapshot(label);
		} catch (Throwable t) {
			return HistorySnapshot.failed(label, activeKeyboardStrategy, t);
		}
	}

	private static void trace(HistorySnapshot snapshot) {
		if (Boolean.getBoolean(TRACE_PROPERTY)) {
			System.out.println(snapshot.dump());
		}
	}

	private IAction action(String actionId) throws Exception {
		IAction result= uiCall(() -> textEditor.getAction(actionId));
		assertNotNull(result, "Editor action not found: " + actionId);
		return result;
	}

	private void waitUntilEnabled(IAction action, String name) {
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				return uiCallUnchecked(action::isEnabled);
			}

			@Override
			public String getFailureMessage() {
				return name + " action was not enabled.\n" + safeCurrentStateDump();
			}
		}, 10_000);
	}

	private void waitUntilDisabled(IAction action, String name) {
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				return !uiCallUnchecked(action::isEnabled);
			}

			@Override
			public String getFailureMessage() {
				return name + " action remained enabled.\n" + safeCurrentStateDump();
			}
		}, 10_000);
	}

	private void waitUntilText(String expected) {
		bot.waitUntil(new DefaultCondition() {
			@Override
			public boolean test() {
				return Objects.equals(expected, botEditor.getText());
			}

			@Override
			public String getFailureMessage() {
				return "Expected editor text was not reached. Expected:\n" + expected + "\nActual:\n"
						+ botEditor.getText() + '\n' + safeCurrentStateDump();
			}
		}, 10_000);
	}

	private String safeCurrentStateDump() {
		return snapshotUnchecked("state at timeout").dump() + '\n' + environmentDump();
	}

	private void pressSpecialKey(int keyCode) {
		botEditor.pressShortcut(SWT.NONE, keyCode, '\0');
	}

	private void pressModifierOnly(int modifier) {
		botEditor.pressShortcut(modifier, 0, '\0');
	}

	private void moveCaretTo(int offset) throws Exception {
		uiRun(() -> textEditor.selectAndReveal(offset, 0));
		botEditor.setFocus();
		settleInput();
	}

	private int caretOffset() throws Exception {
		return uiCall(() -> botEditor.getStyledText().widget.getCaretOffset());
	}

	private long modificationStamp() throws Exception {
		return uiCall(documentExtension::getModificationStamp);
	}

	private void settleInput() throws Exception {
		Thread.sleep(100);
		uiRun(() -> {
			// UI-thread barrier: all SWT events already queued run before this call.
		});
	}

	private void useKeyboardStrategy(String strategy) {
		activeKeyboardStrategy= strategy;
		SWTBotPreferences.KEYBOARD_STRATEGY= strategy;
		botEditor= bot.editorByTitle(fileName).toTextEditor();
		botEditor.setFocus();
		System.out.println("Issue 454 SWTBot keyboard strategy: " + strategy);
	}

	private void reopenGenericTextEditor() throws Exception {
		closeEditor();
		IFile textFile= project.getFile(TEXT_FILE_NAME);
		openEditor(textFile, EditorsUI.DEFAULT_TEXT_EDITOR_ID, TEXT_FILE_NAME, TEXT_INITIAL_TEXT,
				TEXT_INITIAL_TEXT.length() - 1);
	}

	private void openEditor(IFile editorFile, String editorId, String editorFileName, String editorInitialText,
			int editorInsertionOffset) throws Exception {
		file= editorFile;
		fileName= editorFileName;
		initialText= editorInitialText;
		insertionOffset= editorInsertionOffset;
		textEditor= uiCall(() -> {
			IWorkbenchWindow window= PlatformUI.getWorkbench().getActiveWorkbenchWindow();
			assertNotNull(window, "No active workbench window");
			IWorkbenchPage page= window.getActivePage();
			assertNotNull(page, "No active workbench page");
			IEditorPart editor= IDE.openEditor(page, file, editorId, true);
			assertTrue(editor instanceof ITextEditor, "The selected editor is not a text editor: " + editor);
			return (ITextEditor) editor;
		});
		botEditor= bot.editorByTitle(fileName).toTextEditor();
		botEditor.setFocus();
		document= uiCall(() -> textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput()));
		assertNotNull(document, "The editor has no document");
		assertTrue(document instanceof IDocumentExtension4, "The editor document has no modification stamp");
		documentExtension= (IDocumentExtension4) document;
		undoManager= DocumentUndoManagerRegistry.getDocumentUndoManager(document);
		assertNotNull(undoManager, "The editor document is not connected to a document undo manager");
		undoContext= undoManager.getUndoContext();
		assertNotNull(undoContext, "The document undo manager has no undo context");
		history= OperationHistoryFactory.getOperationHistory();
	}

	private void closeEditor() throws Exception {
		if (textEditor == null) {
			return;
		}
		ITextEditor editorToClose= textEditor;
		uiRun(() -> {
			IWorkbenchWindow window= PlatformUI.getWorkbench().getActiveWorkbenchWindow();
			if (window != null && window.getActivePage() != null) {
				window.getActivePage().closeEditor((IEditorPart) editorToClose, false);
			}
		});
		textEditor= null;
		botEditor= null;
		document= null;
		documentExtension= null;
		undoManager= null;
		undoContext= null;
		history= null;
	}

	private void closeSearchShell() {
		if (searchShell == null || !searchShell.isOpen()) {
			searchShell= null;
			return;
		}
		searchShell.close();
		searchShell= null;
	}

	private void createJavaProjectAndFiles() throws Exception {
		project= ResourcesPlugin.getWorkspace().getRoot().getProject(PROJECT_NAME);
		if (project.exists()) {
			project.delete(true, true, null);
		}
		project.create(null);
		project.open(null);

		IProjectDescription description= project.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		project.setDescription(description, null);

		IFolder sourceFolder= project.getFolder("src");
		sourceFolder.create(true, true, null);
		IFolder outputFolder= project.getFolder("bin");
		outputFolder.create(true, true, null);

		IJavaProject javaProject= JavaCore.create(project);
		javaProject.setOutputLocation(outputFolder.getFullPath(), null);
		IClasspathEntry sourceEntry= JavaCore.newSourceEntry(sourceFolder.getFullPath());
		javaProject.setRawClasspath(new IClasspathEntry[] { sourceEntry }, null);

		IFile javaFile= sourceFolder.getFile(JAVA_FILE_NAME);
		javaFile.create(new ByteArrayInputStream(JAVA_INITIAL_TEXT.getBytes(UTF_8)), true, null);
		IFile textFile= project.getFile(TEXT_FILE_NAME);
		textFile.create(new ByteArrayInputStream(TEXT_INITIAL_TEXT.getBytes(UTF_8)), true, null);
		file= javaFile;
	}

	private String environmentDump() {
		try {
			return uiCall(() -> "ENVIRONMENT\n"
					+ "  os.name=" + System.getProperty("os.name") + '\n'
					+ "  os.version=" + System.getProperty("os.version") + '\n'
					+ "  java.version=" + System.getProperty("java.version") + '\n'
					+ "  eclipse.os=" + Platform.getOS() + '\n'
					+ "  eclipse.ws=" + Platform.getWS() + '\n'
					+ "  keyboardStrategy=" + activeKeyboardStrategy + '\n'
					+ "  activeShell=" + describeShell(Display.getCurrent().getActiveShell()) + '\n'
					+ "  focusControl=" + describeControl(Display.getCurrent().getFocusControl()) + '\n'
					+ "  shells=" + shellSummary());
		} catch (Throwable t) {
			return "ENVIRONMENT unavailable: " + t;
		}
	}

	private static String shellSummary() {
		Display display= Display.getCurrent();
		if (display == null) {
			return "<not on UI thread>";
		}
		List<String> shells= new ArrayList<>();
		for (Shell shell : display.getShells()) {
			shells.add(describeShell(shell));
		}
		return shells.toString();
	}

	private static String describeShell(Shell shell) {
		if (shell == null) {
			return "<none>";
		}
		return shell.getClass().getName() + "(text=\"" + escape(shell.getText()) + "\", visible="
				+ shell.isVisible() + ", disposed=" + shell.isDisposed() + ')';
	}

	private static String describeControl(Control control) {
		if (control == null) {
			return "<none>";
		}
		return control.getClass().getName() + '@' + identity(control);
	}

	private static String insertAt(String text, int offset, String insertion) {
		return text.substring(0, offset) + insertion + text.substring(offset);
	}

	private static void uiRun(UIRunnable runnable) throws Exception {
		uiCall(() -> {
			runnable.run();
			return null;
		});
	}

	private static <T> T uiCall(UICall<T> call) throws Exception {
		AtomicReference<T> result= new AtomicReference<>();
		AtomicReference<Throwable> failure= new AtomicReference<>();
		Display.getDefault().syncExec(() -> {
			try {
				result.set(call.call());
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

	private static boolean uiCallUnchecked(BooleanSupplier supplier) {
		try {
			return uiCall(supplier::getAsBoolean);
		} catch (Exception e) {
			throw new AssertionError(e);
		}
	}

	private enum HistoryShape {
		SINGLE_UNDO,
		PARTIAL_REDO,
		ALL_UNDONE
	}

	private record RedoState(String currentText, String nextRedoText, HistorySnapshot historyBeforeInteraction) {
	}

	private record Interaction(String name, ThrowingRunnable action) {
	}

	private record OperationState(IUndoableOperation operation, String implementation, String identity, String label,
			boolean canUndo, boolean canRedo, List<IUndoContext> contexts, String contextDescriptions, String debug) {

		static OperationState capture(IUndoableOperation operation) {
			IUndoContext[] operationContexts= operation.getContexts();
			List<IUndoContext> contexts= List.copyOf(Arrays.asList(operationContexts));
			String contextDescriptions= contexts.stream().map(UndoRedoHistorySWTBotTest::describeContext).toList()
					.toString();
			return new OperationState(operation, operation.getClass().getName(),
					UndoRedoHistorySWTBotTest.identity(operation), String.valueOf(operation.getLabel()),
					operation.canUndo(), operation.canRedo(), contexts, contextDescriptions, String.valueOf(operation));
		}

		String dump(int index) {
			return "  " + index + ": " + implementation + '@' + identity + " label=\"" + escape(label)
					+ "\" canUndo=" + canUndo + " canRedo=" + canRedo + " contexts=" + contextDescriptions
					+ " debug=\"" + escape(debug) + "\"\n";
		}

		String eventDescription() {
			return implementation + '@' + identity + " label=\"" + escape(label) + "\" canUndo=" + canUndo
					+ " canRedo=" + canRedo + " contexts=" + contextDescriptions;
		}
	}

	private record HistorySnapshot(String label, String keyboardStrategy, List<OperationState> undo,
			List<OperationState> redo, IUndoableOperation topUndo, IUndoableOperation topRedo, boolean historyCanUndo,
			boolean historyCanRedo, boolean managerUndoable, boolean managerRedoable, boolean undoActionEnabled,
			boolean redoActionEnabled, String text, long modificationStamp, boolean dirty, int caretOffset,
			String focusControl, String shells, String editorClass, String editorId, String dump) {

		static HistorySnapshot capture(String label, String keyboardStrategy, IUndoableOperation[] undo,
				IUndoableOperation[] redo, IUndoableOperation topUndo, IUndoableOperation topRedo,
				boolean historyCanUndo, boolean historyCanRedo, boolean managerUndoable, boolean managerRedoable,
				boolean undoActionEnabled, boolean redoActionEnabled, String text, long modificationStamp,
				boolean dirty, int caretOffset, String focusControl, String shells, String editorClass, String editorId) {
			List<OperationState> undoStates= Arrays.stream(undo).map(OperationState::capture).toList();
			List<OperationState> redoStates= Arrays.stream(redo).map(OperationState::capture).toList();
			StringBuilder result= new StringBuilder();
			result.append("=== ").append(label).append(" ===\n")
					.append("keyboardStrategy=").append(keyboardStrategy).append('\n')
					.append("editor=").append(editorClass).append(" id=").append(editorId).append('\n')
					.append("history.canUndo=").append(historyCanUndo)
					.append(" history.canRedo=").append(historyCanRedo)
					.append(" manager.undoable=").append(managerUndoable)
					.append(" manager.redoable=").append(managerRedoable)
					.append(" action.undo.enabled=").append(undoActionEnabled)
					.append(" action.redo.enabled=").append(redoActionEnabled).append('\n')
					.append("topUndo=").append(describeIdentity(topUndo))
					.append(" topRedo=").append(describeIdentity(topRedo)).append('\n')
					.append("stamp=").append(modificationStamp).append(" dirty=").append(dirty)
					.append(" caret=").append(caretOffset).append(" focus=").append(focusControl).append('\n')
					.append("shells=").append(shells).append('\n')
					.append("text=\"").append(escape(text)).append("\"\n");
			append(result, "UNDO", undoStates);
			append(result, "REDO", redoStates);
			return new HistorySnapshot(label, keyboardStrategy, undoStates, redoStates, topUndo, topRedo,
					historyCanUndo, historyCanRedo, managerUndoable, managerRedoable, undoActionEnabled,
					redoActionEnabled, text, modificationStamp, dirty, caretOffset, focusControl, shells, editorClass,
					editorId, result.toString());
		}

		static HistorySnapshot failed(String label, String keyboardStrategy, Throwable failure) {
			String dump= "=== " + label + " ===\nSnapshot failed: " + failure;
			return new HistorySnapshot(label, keyboardStrategy, List.of(), List.of(), null, null, false, false,
					false, false, false, false, "", IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP, false, -1,
					"<unknown>", "<unknown>", "<unknown>", "<unknown>", dump);
		}

		private static void append(StringBuilder target, String name, List<OperationState> operations) {
			target.append(name).append('[').append(operations.size()).append("]\n");
			for (int i= 0; i < operations.size(); i++) {
				target.append(operations.get(i).dump(i));
			}
		}
	}

	private final class DiagnosticSession {
		private final HistorySnapshot before;
		private final DocumentEventLog documentEvents= new DocumentEventLog();
		private final OperationEventLog operationEvents;
		private final KeyEventLog keyEvents= new KeyEventLog();

		DiagnosticSession(HistorySnapshot before) {
			this.before= before;
			operationEvents= new OperationEventLog(undoContext, before);
		}

		void attach() throws Exception {
			document.addDocumentListener(documentEvents);
			history.addOperationHistoryListener(operationEvents);
			try {
				keyEvents.attach(botEditor.getStyledText().widget);
			} catch (Exception e) {
				history.removeOperationHistoryListener(operationEvents);
				document.removeDocumentListener(documentEvents);
				throw e;
			}
		}

		void detach() throws Exception {
			if (document != null) {
				document.removeDocumentListener(documentEvents);
			}
			if (history != null) {
				history.removeOperationHistoryListener(operationEvents);
			}
			keyEvents.detach();
		}

		void clearEvents() {
			documentEvents.clear();
			operationEvents.clear();
			keyEvents.clear();
		}

		DocumentEventLog documentEvents() {
			return documentEvents;
		}

		OperationEventLog operationEvents() {
			return operationEvents;
		}

		String dump(HistorySnapshot after) {
			return before.dump() + '\n' + after.dump() + '\n' + documentEvents.dump() + '\n'
					+ operationEvents.dump() + '\n' + keyEvents.dump() + '\n' + environmentDump();
		}
	}

	private final class DocumentEventLog implements IDocumentListener {
		private final List<String> entries= new CopyOnWriteArrayList<>();

		@Override
		public void documentAboutToBeChanged(DocumentEvent event) {
			record("ABOUT_TO_CHANGE", event);
		}

		@Override
		public void documentChanged(DocumentEvent event) {
			record("CHANGED", event);
		}

		private void record(String kind, DocumentEvent event) {
			try {
				entries.add(kind + " offset=" + event.getOffset() + " length=" + event.getLength()
						+ " text=\"" + escape(event.getText()) + "\" eventStamp="
						+ event.getModificationStamp() + " documentStamp="
						+ documentExtension.getModificationStamp() + " documentLength=" + document.getLength());
			} catch (Throwable t) {
				entries.add(kind + " <listener diagnostic failed: " + t + '>');
			}
		}

		boolean isEmpty() {
			return entries.isEmpty();
		}

		void clear() {
			entries.clear();
		}

		String dump() {
			return "DOCUMENT EVENTS[" + entries.size() + "]\n  " + String.join("\n  ", entries);
		}
	}

	private static final class OperationEventLog implements IOperationHistoryListener {
		private final IUndoContext context;
		private final Map<IUndoableOperation, String> knownOperations=
				Collections.synchronizedMap(new IdentityHashMap<>());
		private final List<Integer> eventTypes= new CopyOnWriteArrayList<>();
		private final List<String> entries= new CopyOnWriteArrayList<>();

		OperationEventLog(IUndoContext context, HistorySnapshot before) {
			this.context= context;
			before.undo().forEach(state -> knownOperations.put(state.operation(), state.eventDescription()));
			before.redo().forEach(state -> knownOperations.put(state.operation(), state.eventDescription()));
		}

		@Override
		public void historyNotification(OperationHistoryEvent event) {
			try {
				IUndoableOperation operation= event.getOperation();
				if (operation == null) {
					return;
				}
				int type= event.getEventType();
				boolean alreadyKnown= knownOperations.containsKey(operation);
				boolean removed= type == OperationHistoryEvent.OPERATION_REMOVED;
				boolean hasContext= !removed && safeHasContext(operation, context);
				if (!alreadyKnown && !hasContext) {
					return;
				}

				String description;
				if (removed) {
					description= knownOperations.getOrDefault(operation,
							describeIdentity(operation) + " <removed before it was observed>");
				} else {
					description= safeOperationDescription(operation);
					knownOperations.put(operation, description);
				}
				eventTypes.add(type);
				entries.add(eventName(type) + " operation=" + description + " hasDocumentContext="
						+ (removed ? "<not queried after dispose>" : hasContext) + " status="
						+ String.valueOf(event.getStatus()));
			} catch (Throwable t) {
				entries.add("OPERATION_LISTENER_FAILURE " + t);
			}
		}

		boolean contains(int eventType) {
			return eventTypes.contains(eventType);
		}

		void clear() {
			eventTypes.clear();
			entries.clear();
		}

		String dump() {
			return "OPERATION EVENTS[" + entries.size() + "]\n  " + String.join("\n  ", entries);
		}
	}

	private static final class KeyEventLog {
		private final List<String> entries= new CopyOnWriteArrayList<>();
		private final Listener listener= this::record;
		private StyledText styledText;

		void attach(StyledText text) throws Exception {
			styledText= text;
			uiRun(() -> {
				styledText.addListener(SWT.KeyDown, listener);
				styledText.addListener(SWT.KeyUp, listener);
				styledText.addListener(SWT.Traverse, listener);
			});
		}

		void detach() throws Exception {
			StyledText text= styledText;
			styledText= null;
			if (text == null || text.isDisposed()) {
				return;
			}
			uiRun(() -> {
				if (!text.isDisposed()) {
					text.removeListener(SWT.KeyDown, listener);
					text.removeListener(SWT.KeyUp, listener);
					text.removeListener(SWT.Traverse, listener);
				}
			});
		}

		void clear() {
			entries.clear();
		}

		String dump() {
			return "KEY EVENTS[" + entries.size() + "]\n  " + String.join("\n  ", entries);
		}

		private void record(Event event) {
			try {
				entries.add(eventNameForSwt(event.type) + " keyCode=" + event.keyCode + " character="
						+ describeCharacter(event.character) + " stateMask=0x"
						+ Integer.toHexString(event.stateMask) + " keyLocation=" + event.keyLocation
						+ " detail=" + event.detail + " doit=" + event.doit);
			} catch (Throwable t) {
				entries.add("KEY_LISTENER_FAILURE " + t);
			}
		}
	}

	private static boolean safeHasContext(IUndoableOperation operation, IUndoContext context) {
		try {
			return operation.hasContext(context);
		} catch (Throwable t) {
			return false;
		}
	}

	private static String safeOperationDescription(IUndoableOperation operation) {
		return describeIdentity(operation) + " label=\"" + escape(safeValue(operation::getLabel))
				+ "\" canUndo=" + safeValue(operation::canUndo) + " canRedo=" + safeValue(operation::canRedo)
				+ " contexts=" + safeContexts(operation);
	}

	private static String safeContexts(IUndoableOperation operation) {
		try {
			return Arrays.stream(operation.getContexts()).map(UndoRedoHistorySWTBotTest::describeContext).toList()
					.toString();
		} catch (Throwable t) {
			return "<unavailable: " + t + '>';
		}
	}

	private static String safeValue(Supplier<?> supplier) {
		try {
			return String.valueOf(supplier.get());
		} catch (Throwable t) {
			return "<unavailable: " + t + '>';
		}
	}

	private static String describeContext(IUndoContext context) {
		return context.getClass().getName() + '@' + identity(context) + "(\"" + escape(context.getLabel()) + "\")";
	}

	private static String eventName(int eventType) {
		return switch (eventType) {
			case OperationHistoryEvent.ABOUT_TO_EXECUTE -> "ABOUT_TO_EXECUTE";
			case OperationHistoryEvent.ABOUT_TO_REDO -> "ABOUT_TO_REDO";
			case OperationHistoryEvent.ABOUT_TO_UNDO -> "ABOUT_TO_UNDO";
			case OperationHistoryEvent.DONE -> "DONE";
			case OperationHistoryEvent.OPERATION_ADDED -> "OPERATION_ADDED";
			case OperationHistoryEvent.OPERATION_CHANGED -> "OPERATION_CHANGED";
			case OperationHistoryEvent.OPERATION_NOT_OK -> "OPERATION_NOT_OK";
			case OperationHistoryEvent.OPERATION_REMOVED -> "OPERATION_REMOVED";
			case OperationHistoryEvent.REDONE -> "REDONE";
			case OperationHistoryEvent.UNDONE -> "UNDONE";
			default -> "EVENT_" + eventType;
		};
	}

	private static String eventNameForSwt(int eventType) {
		return switch (eventType) {
			case SWT.KeyDown -> "KEY_DOWN";
			case SWT.KeyUp -> "KEY_UP";
			case SWT.Traverse -> "TRAVERSE";
			default -> "SWT_EVENT_" + eventType;
		};
	}

	private static String describeIdentity(IUndoableOperation operation) {
		return operation == null ? "<none>" : operation.getClass().getName() + '@' + identity(operation);
	}

	private static String identity(Object object) {
		return Integer.toHexString(System.identityHashCode(object));
	}

	private static String describeCharacter(char character) {
		return "'" + escape(Character.toString(character)) + "'(" + (int) character + ')';
	}

	private static String escape(String text) {
		if (text == null) {
			return "<null>";
		}
		return text.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n")
				.replace("\t", "\\t").replace("\b", "\\b").replace("\0", "\\0");
	}

	@FunctionalInterface
	private interface UICall<T> {
		T call() throws Exception;
	}

	@FunctionalInterface
	private interface UIRunnable {
		void run() throws Exception;
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
