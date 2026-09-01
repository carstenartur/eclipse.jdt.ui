/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer using github copilot - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.junit.ui;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;

import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.JavaUI;

import org.eclipse.jdt.internal.junit.Messages;
import org.eclipse.jdt.internal.junit.model.TestCaseElement;
import org.eclipse.jdt.internal.junit.ui.EnumSourceValidator.ExclusionTarget;

/**
 * Context-menu action that adds the selected enum constant to the
 * {@code @EnumSource} EXCLUDE filter.
 *
 * @since 3.17
 */
public final class ExcludeParameterValueAction extends Action {

	private ExclusionTarget fTarget;

	public ExcludeParameterValueAction() {
		super(JUnitMessages.ExcludeParameterValueAction_label);
	}

	/**
	 * Updates this action for the selected test invocation.
	 *
	 * @param testCaseElement the selected test invocation
	 */
	public void update(TestCaseElement testCaseElement) {
		fTarget= EnumSourceValidator.findExclusionTarget(testCaseElement);
		setEnabled(fTarget != null);
	}

	@Override
	public void run() {
		if (fTarget == null) {
			return;
		}

		int remaining= fTarget.getRemainingValues();
		if (remaining <= 1) {
			String message= remaining == 0
					? Messages.format(JUnitMessages.ExcludeParameterValueAction_warning_noValues,
							fTarget.getEnumConstantName())
					: Messages.format(JUnitMessages.ExcludeParameterValueAction_warning_oneValue,
							fTarget.getEnumConstantName());
			if (!MessageDialog.openQuestion(null, JUnitMessages.ExcludeParameterValueAction_label, message)) {
				return;
			}
		}

		try {
			if (EnumSourceValidator.excludeEnumValue(
					fTarget.getMethod(), fTarget.getEnumConstantName())) {
				JavaUI.openInEditor(fTarget.getMethod());
			}
		} catch (JavaModelException e) {
			JUnitPlugin.log(e);
		} catch (Exception e) {
			JUnitPlugin.log(e);
		}
	}
}
