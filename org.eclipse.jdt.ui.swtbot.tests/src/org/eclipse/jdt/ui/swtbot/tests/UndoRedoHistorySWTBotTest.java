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
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotEclipseEditor;
import org.eclipse.swtbot.swt.finder.exceptions.TimeoutException;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;

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
 * End-to-end diagnostic for https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/454.
 * <p>
 * This deliberately temporary test observes the document-specific operation
 * history, the editor actions, document events, operation-history events and
 * the SWT key events. Each failing assertion therefore reports the complete
 * state needed to distinguish a real edit from a key-binding, action-state or
 * operation-history problem.
 * </p>
 */
public class UndoRedoHistorySWTBotTest {

	private static final String PROJECT_NAME= "Issue454SWTBotProject";
	private static final String JAVA_FILE_NAME= "Issue454.java";
	private static final String TEXT_FILE_NAME= "Issue454.txt";
	private static final String JAVA_INITIAL_TEXT= "public class Issue454 {\n}\n";
	private static final String TEXT_INITIAL_TEXT= "issue 454\n";
	private static final String FIRST_EDIT= "first";
	private static final String SECOND_EDIT= "Second";
	private static final String THIRD_EDIT= "THIRD";
	private static final String TRACE_PROPERTY= "eclipse.jdt.ui.issue454.trace";
	private static final String KEYBOARD_STRATEGY_PROPERTY= "org.eclipse.swtbot.keyboard.strategy";
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
		activeKeyboardStrategy= System.getProperty(KEYBOARD_STRATEGY_PROPERTY, AWT_KEYBOARD_STRATEGY);
		SWTBotPreferences.KEYBOARD_STRATEGY= activeKeyboardStrategy;
		createJavaProjectAndFiles();
		openEditor(file, JavaUI.ID_CU_EDITOR, JAVA_FILE_NAME, JAVA_INITIAL_TEXT);
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
	void navigationAndEscapeKeysMustPreserveRedo() throws Exception {
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
				new Interaction("Escape", () -> pressSpecialKey(SWT.ESC)));
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
					return "Editor remained dirty after the save shortcut";
				}
			}, 10_000);
		}));
	}

	@Test
	void ordinaryTypingMustCreateANewBranchAndDiscardRedo() throws Exception {
		useKeyboardStrategy(AWT_KEYBOARD_STRATEGY);
		RedoState state= prepareRedoState(HistoryShape.PARTIAL_REDO);
		DiagnosticSession diagnostics= new DiagnosticSession(state.historyBeforeInteraction());
		diagnostics.attach();
		try {
			moveCaretTo(state.currentText().indexOf(SECOND_EDIT) + SECOND_EDIT.length());
			botEditor.typeText("h");
			HistorySnapshot afterTyping= snapshot("after ordinary typing");

			assertNotEquals(state.currentText(), botEditor.getText(),
					"The positive control did not edit the document");
			assertFalse(diagnostics.documentEvents().isEmpty(),
					() -> "The positive control produced no document event.\n" + diagnostics.dump(afterTyping));
			assertEquals(0, afterTyping.redo().size(),
					() -> "A real edit must discard the old redo branch.\n" + diagnostics.dump(afterTyping));
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
			pressSpecialKey(SWT.BS);
			HistorySnapshot afterBackspace= snapshot("after real Backspace");

			assertNotEquals(state.currentText(), botEditor.getText(),
					"The Backspace positive control did not edit the document");
			assertFalse(diagnostics.documentEvents().isEmpty(),
					() -> "The Backspace positive control produced no document event.\n"
							+ diagnostics.dump(afterBackspace));
			assertEquals(0, afterBackspace.redo().size(),
					() -> "A real deletion must discard the old redo branch.\n" + diagnostics.dump(afterBackspace));
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
			long stampBefore= documentExtension.getModificationStamp();
			botEditor.pressShortcut(SWT.CTRL, 'h');
			try {
				bot.waitUntil(Conditions.shellIsActive("Search"), 10_000);
			} catch (TimeoutException e) {
				HistorySnapshot afterShortcut= snapshot("after Ctrl+H without Search dialog");
				fail("Ctrl+H did not open the Search dialog.\n" + diagnostics.dump(afterShortcut), e);
			}
			searchShell= bot.shell("Search");

			HistorySnapshot afterShortcut= snapshot("after Ctrl+H");
			assertUnchanged(textBefore, stampBefore, state.historyBeforeInteraction(), afterShortcut, diagnostics,
					"Ctrl+H");
			closeSearchShell();
			botEditor.setFocus();
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
				long stampBefore= documentExtension.getModificationStamp();
				interaction.action().run();
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
			long stampBefore= documentExtension.getModificationStamp();
			interaction.action().run();
			HistorySnapshot after= snapshot("after " + interaction.name());
			assertUnchanged(textBefore, stampBefore, state.historyBeforeInteraction(), after, diagnostics,
					interaction.name());
			redoAndAssertNextState(state, diagnostics);
		} finally {
			diagnostics.detach();
		}
	}

	private void assertUnchanged(String textBefore, long stampBefore, HistorySnapshot before, HistorySnapshot after,
			DiagnosticSession diagnostics, String interactionName) throws Exception {
		assertEquals(textBefore, botEditor.getText(),
				() -> interactionName + " changed the document text.\n" + diagnostics.dump(after));
		assertEquals(stampBefore, documentExtension.getModificationStamp(),
				() -> interactionName + " changed the document modification stamp.\n" + diagnostics.dump(after));
		assertTrue(diagnostics.documentEvents().isEmpty(),
				() -> interactionName + " produced a document edit.\n" + diagnostics.dump(after));
		assertHistoryUnchanged(before, after, diagnostics.dump(after));
		assertTrue(uiCallUnchecked(action(ITextEditorActionConstants.REDO)::isEnabled),
				() -> interactionName + " disabled Redo although the redo stack is unchanged.\n"
						+ diagnostics.dump(after));
	}

	private RedoState prepareRedoState(HistoryShape shape) throws Exception {
		assertEquals(initialText, botEditor.getText());
		HistorySnapshot baseline= snapshot("before typing");

		moveCaretTo(initialText.endsWith("\n") ? initialText.length() - 1 : initialText.length());
		String afterFirstEdit= typeAndCommit(FIRST_EDIT);
		String afterSecondEdit= typeAndCommit(SECOND_EDIT);
		String fullyEditedText= typeAndCommit(THIRD_EDIT);

		HistorySnapshot afterTyping= snapshot("after three committed typing operations");
		assertTrue(afterTyping.undo().size() >= baseline.undo().size() + 3,
				() -> "Three committed edits did not create three undo entries.\n" + baseline.dump() + '\n'
						+ afterTyping.dump());

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
		assertEquals(currentText, botEditor.getText());
		return new RedoState(currentText, nextRedoText, beforeInteraction);
	}

	private String typeAndCommit(String text) throws Exception {
		botEditor.typeText(text);
		uiRun(undoManager::commit);
		String result= botEditor.getText();
		assertTrue(result.contains(text), () -> "Editor did not contain the typed text: " + text);
		return result;
	}

	private void redoAndAssertNextState(RedoState state, DiagnosticSession diagnostics) throws Exception {
		IAction redo= action(ITextEditorActionConstants.REDO);
		waitUntilEnabled(redo, "Redo");
		HistorySnapshot beforeRedo= snapshot("immediately before verification Redo");
		assertTrue(uiCallUnchecked(redo::isEnabled),
				() -> "Redo was disabled although the document was not edited.\n" + diagnostics.dump(beforeRedo));
		uiRun(redo::run);
		waitUntilText(state.nextRedoText());
		assertEquals(state.nextRedoText(), botEditor.getText(),
				() -> "Redo did not restore the expected next state.\n" + diagnostics.dump(snapshot("after failed Redo")));
	}

	private void runActionAndWait(IAction action, String expectedText, String name) throws Exception {
		waitUntilEnabled(action, name);
		uiRun(action::run);
		waitUntilText(expectedText);
	}

	private void assertHistoryUnchanged(HistorySnapshot before, HistorySnapshot after, String details) {
		assertSameOperationStates("Undo", before.undo(), after.undo(), details);
		assertSameOperationStates("Redo", before.redo(), after.redo(), details);
		assertSame(before.topUndo(), after.topUndo(), () -> "Top Undo operation changed.\n" + details);
		assertSame(before.topRedo(), after.topRedo(), () -> "Top Redo operation changed.\n" + details);
		assertEquals(before.canUndo(), after.canUndo(), () -> "canUndo state changed.\n" + details);
		assertEquals(before.canRedo(), after.canRedo(), () -> "canRedo state changed.\n" + details);
		assertEquals(before.managerUndoable(), after.managerUndoable(),
				() -> "Document undo-manager undoable state changed.\n" + details);
		assertEquals(before.managerRedoable(), after.managerRedoable(),
				() -> "Document undo-manager redoable state changed.\n" + details);
	}

	private static void assertSameOperationStates(String name, List<OperationState> expected,
			List<OperationState> actual, String details) {
		assertEquals(expected.size(), actual.size(), () -> name + " stack size changed.\n" + details);
		for (int i= 0; i < expected.size(); i++) {
			int index= i;
			OperationState expectedState= expected.get(i);
			OperationState actualState= actual.get(i);
			assertSame(expectedState.operation(), actualState.operation(),
					() -> name + " stack entry " + index + " changed identity.\n" + details);
			assertEquals(expectedState.label(), actualState.label(),
					() -> name + " stack entry " + index + " changed label.\n" + details);
			assertEquals(expectedState.canUndo(), actualState.canUndo(),
					() -> name + " stack entry " + index + " changed canUndo.\n" + details);
			assertEquals(expectedState.canRedo(), actualState.canRedo(),
					() -> name + " stack entry " + index + " changed canRedo.\n" + details);
			assertEquals(expectedState.contexts(), actualState.contexts(),
					() -> name + " stack entry " + index + " changed contexts.\n" + details);
		}
	}

	private HistorySnapshot snapshot(String label) {
		IUndoableOperation[] undo= history.getUndoHistory(undoContext);
		IUndoableOperation[] redo= history.getRedoHistory(undoContext);
		HistorySnapshot result= HistorySnapshot.capture(label, activeKeyboardStrategy, undo, redo,
				history.getUndoOperation(undoContext), history.getRedoOperation(undoContext), history.canUndo(undoContext),
				history.canRedo(undoContext), undoManager.undoable(), undoManager.redoable());
		trace(result);
		return result;
	}

	private static void trace(HistorySnapshot snapshot) {
		if (Boolean.getBoolean(TRACE_PROPERTY)) {
			System.out.println(snapshot.dump());
		}
	}

	private IAction action(String actionId) throws Exception {
		IAction result= uiCall(() -> textEditor.getAction(actionId));
		assertNotNull(result, () -> "Editor action not found: " + actionId);
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
				return name + " action was not enabled.\n" + snapshot("while waiting for " + name).dump();
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
				return name + " action remained enabled.\n" + snapshot("while waiting for disabled " + name).dump();
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
						+ botEditor.getText() + '\n' + snapshot("text wait timeout").dump();
			}
		}, 10_000);
	}

	private void pressSpecialKey(int keyCode) {
		botEditor.pressShortcut(SWT.NONE, keyCode, '\0');
	}

	private void moveCaretTo(int offset) throws Exception {
		uiRun(() -> textEditor.selectAndReveal(offset, 0));
		botEditor.setFocus();
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
		openEditor(textFile, EditorsUI.DEFAULT_TEXT_EDITOR_ID, TEXT_FILE_NAME, TEXT_INITIAL_TEXT);
	}

	private void openEditor(IFile editorFile, String editorId, String editorFileName, String editorInitialText)
			throws Exception {
		file= editorFile;
		fileName= editorFileName;
		initialText= editorInitialText;
		textEditor= uiCall(() -> {
			IWorkbenchWindow window= PlatformUI.getWorkbench().getActiveWorkbenchWindow();
			assertNotNull(window, "No active workbench window");
			IWorkbenchPage page= window.getActivePage();
			assertNotNull(page, "No active workbench page");
			IEditorPart editor= IDE.openEditor(page, file, editorId, true);
			assertTrue(editor instanceof ITextEditor, "The selected editor is not a text editor");
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
			boolean canUndo, boolean canRedo, List<String> contexts, String debug) {
		static OperationState capture(IUndoableOperation operation) {
			List<String> contextLabels= Arrays.stream(operation.getContexts())
					.map(context -> context.getClass().getName() + "(\"" + context.getLabel() + "\")")
					.toList();
			return new OperationState(operation, operation.getClass().getName(), identity(operation), operation.getLabel(),
					operation.canUndo(), operation.canRedo(), contextLabels, String.valueOf(operation));
		}

		String dump(int index) {
			return "  " + index + ": " + implementation + '@' + identity + " label=\"" + label + "\" canUndo="
					+ canUndo + " canRedo=" + canRedo + " contexts=" + contexts + " debug=\"" + debug + "\"\n";
		}
	}

	private record HistorySnapshot(String label, String keyboardStrategy, List<OperationState> undo,
			List<OperationState> redo, IUndoableOperation topUndo, IUndoableOperation topRedo, boolean canUndo,
			boolean canRedo, boolean managerUndoable, boolean managerRedoable, String dump) {
		static HistorySnapshot capture(String label, String keyboardStrategy, IUndoableOperation[] undo,
				IUndoableOperation[] redo, IUndoableOperation topUndo, IUndoableOperation topRedo, boolean canUndo,
				boolean canRedo, boolean managerUndoable, boolean managerRedoable) {
			List<OperationState> undoStates= Arrays.stream(undo).map(OperationState::capture).toList();
			List<OperationState> redoStates= Arrays.stream(redo).map(OperationState::capture).toList();
			StringBuilder result= new StringBuilder();
			result.append("=== ").append(label).append(" ===\n")
					.append("keyboardStrategy=").append(keyboardStrategy).append('\n')
					.append("history.canUndo=").append(canUndo)
					.append(" history.canRedo=").append(canRedo)
					.append(" manager.undoable=").append(managerUndoable)
					.append(" manager.redoable=").append(managerRedoable).append('\n')
					.append("topUndo=").append(describeIdentity(topUndo))
					.append(" topRedo=").append(describeIdentity(topRedo)).append('\n');
			append(result, "UNDO", undoStates);
			append(result, "REDO", redoStates);
			return new HistorySnapshot(label, keyboardStrategy, undoStates, redoStates, topUndo, topRedo, canUndo,
					canRedo, managerUndoable, managerRedoable, result.toString());
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
			keyEvents.attach(botEditor.getStyledText().widget);
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

		String dump(HistorySnapshot after) {
			return before.dump() + '\n' + after.dump() + '\n' + documentEvents.dump() + '\n'
					+ operationEvents.dump() + '\n' + keyEvents.dump() + '\n' + editorStateDump();
		}
	}

	private final class DocumentEventLog implements IDocumentListener {
		private final List<String> entries= new CopyOnWriteArrayList<>();

		@Override
		public void documentAboutToBeChanged(DocumentEvent event) {
			entries.add("ABOUT_TO_CHANGE " + describe(event));
		}

		@Override
		public void documentChanged(DocumentEvent event) {
			entries.add("CHANGED " + describe(event));
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

		private String describe(DocumentEvent event) {
			return "offset=" + event.getOffset() + " length=" + event.getLength() + " text=\""
					+ escape(event.getText()) + "\" eventStamp=" + event.getModificationStamp() + " documentStamp="
					+ documentExtension.getModificationStamp() + " documentLength=" + document.getLength();
		}
	}

	private static final class OperationEventLog implements IOperationHistoryListener {
		private final IUndoContext context;
		private final Set<IUndoableOperation> trackedOperations=
				Collections.newSetFromMap(new IdentityHashMap<>());
		private final List<String> entries= new CopyOnWriteArrayList<>();

		OperationEventLog(IUndoContext context, HistorySnapshot before) {
			this.context= context;
			before.undo().forEach(state -> trackedOperations.add(state.operation()));
			before.redo().forEach(state -> trackedOperations.add(state.operation()));
		}

		@Override
		public void historyNotification(OperationHistoryEvent event) {
			IUndoableOperation operation= event.getOperation();
			boolean hasContext= operation != null && operation.hasContext(context);
			if (operation != null && (hasContext || trackedOperations.contains(operation))) {
				trackedOperations.add(operation);
				entries.add(eventName(event.getEventType()) + " operation=" + describeIdentity(operation) + " label=\""
						+ operation.getLabel() + "\" hasDocumentContext=" + hasContext + " canUndo="
						+ operation.canUndo() + " canRedo=" + operation.canRedo());
			}
		}

		void clear() {
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
			entries.add(eventNameForSwt(event.type) + " keyCode=" + event.keyCode + " character="
					+ describeCharacter(event.character) + " stateMask=0x" + Integer.toHexString(event.stateMask)
					+ " detail=" + event.detail + " doit=" + event.doit);
		}
	}

	private String editorStateDump() {
		try {
			return "EDITOR STATE\n  file=" + fileName + " keyboardStrategy=" + activeKeyboardStrategy + " dirty="
					+ uiCall(textEditor::isDirty) + " stamp=" + documentExtension.getModificationStamp() + " length="
					+ document.getLength() + " text=\"" + escape(document.get()) + "\"";
		} catch (Exception e) {
			return "EDITOR STATE unavailable: " + e;
		}
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
