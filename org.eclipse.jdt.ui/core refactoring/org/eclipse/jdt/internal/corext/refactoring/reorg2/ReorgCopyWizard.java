/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.reorg2;

import org.eclipse.core.resources.IResource;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.ui.refactoring.RefactoringWizard;

import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;


public class ReorgCopyWizard extends RefactoringWizard {

	//TODO fix help context id
	private static final String HELP_CONTEXT_ID= "";

	public ReorgCopyWizard(CopyRefactoring2 ref) {
		super(ref, "Copy", HELP_CONTEXT_ID);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.refactoring.RefactoringWizard#addUserInputPages()
	 */
	protected void addUserInputPages() {
		addPage(new CopyInputPage());
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.refactoring.RefactoringWizard#hasPreviewPage()
	 */
	protected boolean hasPreviewPage() {
		return false;
	}

	private static class CopyInputPage extends ReorgUserInputPage{

		private static final String PAGE_NAME= "CopyInputPage"; //$NON-NLS-1$

		public CopyInputPage() {
			super(PAGE_NAME);
		}

		private CopyRefactoring2 getCopyRefactoring(){
			return (CopyRefactoring2) getRefactoring();
		}

		protected Object getInitiallySelectedElement() {
			return getCopyRefactoring().getCommonParentForInputElements();
		}

		protected RefactoringStatus verifyDestination(Object selected) throws JavaModelException{
			if (selected instanceof IJavaElement)
				return getCopyRefactoring().setDestination((IJavaElement)selected);
			if (selected instanceof IResource)
				return getCopyRefactoring().setDestination((IResource)selected);
			return RefactoringStatus.createFatalErrorStatus("The selected element cannot be the destination of this operation.");
		}		
	}
}
