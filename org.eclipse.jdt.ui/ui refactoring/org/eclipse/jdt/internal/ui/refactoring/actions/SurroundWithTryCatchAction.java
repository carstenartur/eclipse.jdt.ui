/*
 * (c) Copyright 2001 MyCorporation.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.refactoring.actions;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jdt.internal.corext.refactoring.base.ChangeContext;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.surround.SurroundWithTryCatchRefactoring;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.preferences.CodeFormatterPreferencePage;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;
import org.eclipse.jdt.internal.ui.refactoring.PerformChangeOperation;
import org.eclipse.jdt.internal.ui.refactoring.changes.AbortChangeExceptionHandler;
import org.eclipse.jdt.internal.ui.util.BusyIndicatorRunnableContext;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

public class SurroundWithTryCatchAction extends TextSelectionAction {

	private static final String TITLE= "Surround with try/catch block";

	public SurroundWithTryCatchAction() {
		this(null);
	}
	
	public SurroundWithTryCatchAction(JavaEditor editor) {
		super("Surround with try/catch", TITLE, "This action in unavailable on the current text selection. Select a set of statements."); 
		setEditor(editor);
	}
	
	public void run() {
		SurroundWithTryCatchRefactoring refactoring= new SurroundWithTryCatchRefactoring(
			getCompilationUnit(), getTextSelection(),
			CodeFormatterPreferencePage.getTabSize(),
			JavaPreferencesSettings.getCodeGenerationSettings());
		try {
			RefactoringStatus status= refactoring.checkActivation(new NullProgressMonitor());
			if (status.hasFatalError()) {
				RefactoringAction.openErrorDialog(TITLE, status);
				return;
			}
			PerformChangeOperation op= new PerformChangeOperation(refactoring.createChange(new NullProgressMonitor()));
			op.setChangeContext(new ChangeContext(new AbortChangeExceptionHandler()));
			new BusyIndicatorRunnableContext().run(false, false, op);
		} catch (CoreException e) {
			ExceptionHandler.handle(e, TITLE, "Unexpected exception occurred. See log for details.");
		} catch (InvocationTargetException e) {
			ExceptionHandler.handle(e, TITLE, "Unexpected exception occurred. See log for details.");
		} catch (InterruptedException e) {
			// not cancelable
		}
	}
}
