/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial tests
 *******************************************************************************/
package org.eclipse.jdt.junit.tests;

import static org.eclipse.jdt.junit.tests.EnumSourceTestSupport.assertExcludeMode;
import static org.eclipse.jdt.junit.tests.EnumSourceTestSupport.assertFilterRemoved;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.eclipse.core.resources.IFile;

import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jface.text.IDocument;

import org.eclipse.ui.texteditor.ITextEditor;

import org.eclipse.ltk.core.refactoring.IUndoManager;
import org.eclipse.ltk.core.refactoring.RefactoringCore;

import org.eclipse.jdt.junit.JUnitCore;
import org.eclipse.jdt.testplugin.JavaProjectHelper;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.eclipse.jdt.internal.junit.ui.EnumSourceValidator;

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.tests.core.rules.Java1d8ProjectTestSetup;
import org.eclipse.jdt.ui.tests.core.rules.ProjectTestSetup;

/**
 * Exercises real Java editor documents and workspace files, not just source rewriting.
 * Each operation must retain the save state it had before the change.
 */
public class EnumSourceSaveStateTest {

	private static final String USER_EDIT= "// Unsaved user change" + System.lineSeparator(); //$NON-NLS-1$

	private enum EditorState {
		CLOSED, SAVED, DIRTY
	}

	private enum Operation {
		EXCLUDE, REINCLUDE_ONE, REINCLUDE_ALL
	}

	@Rule
	public ProjectTestSetup projectSetup= new Java1d8ProjectTestSetup();

	private IJavaProject fProject;
	private IPackageFragmentRoot fSourceFolder;
	private ITextEditor fEditor;

	@Before
	public void setUp() throws Exception {
		fProject= projectSetup.getProject();
		fSourceFolder= JavaProjectHelper.addSourceContainer(fProject, "src"); //$NON-NLS-1$
		JavaProjectHelper.addRTJar(fProject);
		JavaProjectHelper.addToClasspath(fProject, JavaCore.newContainerEntry(JUnitCore.JUNIT5_CONTAINER_PATH));
		JavaProjectHelper.set18CompilerOptions(fProject);
		RefactoringCore.getUndoManager().flush();
	}

	@After
	public void tearDown() throws Exception {
		try {
			if (fEditor != null) {
				fEditor.getSite().getPage().closeEditor(fEditor, false);
			}
		} finally {
			RefactoringCore.getUndoManager().flush();
			JavaProjectHelper.clear(fProject, projectSetup.getDefaultClasspath());
		}
	}

	@Test
	public void testExcludeWithClosedEditor() throws Exception {
		assertSaveState(Operation.EXCLUDE, EditorState.CLOSED);
	}

	@Test
	public void testExcludeWithSavedEditor() throws Exception {
		assertSaveState(Operation.EXCLUDE, EditorState.SAVED);
	}

	@Test
	public void testExcludeWithDirtyEditor() throws Exception {
		assertSaveState(Operation.EXCLUDE, EditorState.DIRTY);
	}

	@Test
	public void testReincludeOneWithClosedEditor() throws Exception {
		assertSaveState(Operation.REINCLUDE_ONE, EditorState.CLOSED);
	}

	@Test
	public void testReincludeOneWithSavedEditor() throws Exception {
		assertSaveState(Operation.REINCLUDE_ONE, EditorState.SAVED);
	}

	@Test
	public void testReincludeOneWithDirtyEditor() throws Exception {
		assertSaveState(Operation.REINCLUDE_ONE, EditorState.DIRTY);
	}

	@Test
	public void testReincludeAllWithClosedEditor() throws Exception {
		assertSaveState(Operation.REINCLUDE_ALL, EditorState.CLOSED);
	}

	@Test
	public void testReincludeAllWithSavedEditor() throws Exception {
		assertSaveState(Operation.REINCLUDE_ALL, EditorState.SAVED);
	}

	@Test
	public void testReincludeAllWithDirtyEditor() throws Exception {
		assertSaveState(Operation.REINCLUDE_ALL, EditorState.DIRTY);
	}

	@Test
	public void testRejectedLastValueDoesNotSaveDirtyEditor() throws Exception {
		IMethod method= createTest(Operation.REINCLUDE_ONE);
		ICompilationUnit cu= method.getCompilationUnit();
		String savedSource= readFile(cu);
		openEditor(cu, EditorState.DIRTY);
		String original= document().get();

		assertFalse("The last executable BLUE value must not be excluded", //$NON-NLS-1$
				EnumSourceValidator.excludeEnumValue(method, "BLUE")); //$NON-NLS-1$

		assertEquals("A rejected edit must preserve the document", original, document().get()); //$NON-NLS-1$
		assertEquals("A rejected edit must not save user changes", savedSource, readFile(cu)); //$NON-NLS-1$
		assertTrue("The editor must remain dirty", fEditor.isDirty()); //$NON-NLS-1$
		assertFalse("A rejected edit must not register an undo change", RefactoringCore.getUndoManager().anythingToUndo()); //$NON-NLS-1$
	}

	private void assertSaveState(Operation operation, EditorState state) throws Exception {
		String context= operation + "/" + state; //$NON-NLS-1$
		IMethod method= createTest(operation);
		ICompilationUnit cu= method.getCompilationUnit();
		String savedSource= readFile(cu);
		if (state != EditorState.CLOSED) {
			openEditor(cu, state);
		}
		String original= cu.getSource();
		assertEquals(context + ": fixture must still have its original disk content", savedSource, readFile(cu)); //$NON-NLS-1$

		boolean changed= switch (operation) {
			case EXCLUDE -> EnumSourceValidator.excludeEnumValue(method, "GREEN"); //$NON-NLS-1$
			case REINCLUDE_ONE -> EnumSourceValidator.removeValueFromExclusion(method, "GREEN"); //$NON-NLS-1$
			case REINCLUDE_ALL -> EnumSourceValidator.removeExcludeMode(method);
		};
		assertTrue(context + ": the requested source edit must succeed", changed); //$NON-NLS-1$
		String modified= cu.getSource();
		assertCompiles(cu);
		List<String> expectedExcluded= switch (operation) {
			case EXCLUDE -> List.of("GREEN"); //$NON-NLS-1$
			case REINCLUDE_ONE -> List.of("RED"); //$NON-NLS-1$
			case REINCLUDE_ALL -> List.of();
		};
		assertEquals(context + ": expected exclusions after the edit", expectedExcluded, EnumSourceValidator.getExcludedNames(method)); //$NON-NLS-1$
		if (operation == Operation.REINCLUDE_ALL) {
			assertFilterRemoved(method);
		} else {
			assertExcludeMode(method);
		}

		// Opening after a closed-file edit mirrors the action's open-in-editor step.
		if (state == EditorState.CLOSED) {
			openEditor(cu, EditorState.SAVED);
		}
		assertState(context, cu, modified, state == EditorState.DIRTY ? savedSource : modified, state == EditorState.DIRTY);
		if (state == EditorState.DIRTY) {
			assertTrue(context + ": unrelated user text must remain in the document", modified.startsWith(USER_EDIT)); //$NON-NLS-1$
		}

		IUndoManager undoManager= RefactoringCore.getUndoManager();
		assertTrue(context + ": edit must support undo", undoManager.anythingToUndo()); //$NON-NLS-1$
		undoManager.performUndo(null, new NullProgressMonitor());
		assertState(context + "/undo", cu, original, savedSource, state == EditorState.DIRTY); //$NON-NLS-1$
		assertTrue(context + ": edit must support redo", undoManager.anythingToRedo()); //$NON-NLS-1$
		undoManager.performRedo(null, new NullProgressMonitor());
		assertState(context + "/redo", cu, modified, state == EditorState.DIRTY ? savedSource : modified, state == EditorState.DIRTY); //$NON-NLS-1$
	}

	private void assertState(String context, ICompilationUnit cu, String expectedDocument, String expectedFile, boolean dirty) throws Exception {
		assertEquals(context + ": editor document", expectedDocument, document().get()); //$NON-NLS-1$
		assertEquals(context + ": Java working copy", expectedDocument, cu.getSource()); //$NON-NLS-1$
		assertEquals(context + ": workspace file", expectedFile, readFile(cu)); //$NON-NLS-1$
		assertEquals(context + ": editor dirty state", dirty, fEditor.isDirty()); //$NON-NLS-1$
	}

	private void openEditor(ICompilationUnit cu, EditorState state) throws Exception {
		fEditor= (ITextEditor) JavaUI.openInEditor(cu);
		assertNotNull("Expected a Java editor", fEditor); //$NON-NLS-1$
		assertFalse("The editor must start saved", fEditor.isDirty()); //$NON-NLS-1$
		if (state == EditorState.DIRTY) {
			document().replace(0, 0, USER_EDIT);
			assertTrue("The fixture must contain unsaved user text", fEditor.isDirty()); //$NON-NLS-1$
		}
	}

	private IDocument document() {
		return fEditor.getDocumentProvider().getDocument(fEditor.getEditorInput());
	}

	private IMethod createTest(Operation operation) throws Exception {
		String filter= operation == Operation.EXCLUDE ? "" //$NON-NLS-1$
				: ", mode = EnumSource.Mode.EXCLUDE, names = { \"RED\", \"GREEN\" }"; //$NON-NLS-1$
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null); //$NON-NLS-1$
		ICompilationUnit cu= pack.createCompilationUnit("MyTest.java", """
				package test1;

				import org.junit.jupiter.params.ParameterizedTest;
				import org.junit.jupiter.params.provider.EnumSource;

				public class MyTest {
				    enum Color { RED, GREEN, BLUE }

				    @ParameterizedTest
				    @EnumSource(value = Color.class%s)
				    public void testWithEnum(Color color) {
				    }
				}
				""".formatted(filter), false, null);
		assertCompiles(cu);
		return cu.getType("MyTest").getMethod("testWithEnum", new String[] { "QColor;" }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static void assertCompiles(ICompilationUnit cu) throws JavaModelException {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(cu);
		parser.setResolveBindings(true);
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		String errors= Arrays.stream(root.getProblems()).filter(IProblem::isError)
				.map(IProblem::getMessage).collect(Collectors.joining(System.lineSeparator()));
		assertEquals("Expected no compile errors for source:" + System.lineSeparator() + cu.getSource(), "", errors); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static String readFile(ICompilationUnit cu) throws Exception {
		IFile file= (IFile) cu.getResource();
		try (InputStream stream= file.getContents()) {
			return new String(stream.readAllBytes(), Charset.forName(file.getCharset()));
		}
	}
}
