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

import java.util.List;

import org.eclipse.jface.action.Action;

import org.eclipse.jdt.core.IMethod;

import org.eclipse.jdt.internal.junit.model.TestElement;
import org.eclipse.jdt.internal.junit.model.TestSuiteElement;

/**
 * Action to re-include a single excluded enum value from @EnumSource annotation.
 *
 * @since 3.15
 */
public class ReincludeEnumValueAction extends Action {

	private final String fEnumValue;
	private IMethod fMethod;

	public ReincludeEnumValueAction(String enumValue) {
		super(enumValue);
		fEnumValue= enumValue;
	}

	/**
	 * Update the action based on the current test element selection.
	 *
	 * @param element the selected test element
	 */
	public void update(TestElement element) {
		fMethod= findMethod(element);
		setEnabled(fMethod != null);
	}

	@Override
	public void run() {
		if (fMethod == null) {
			return;
		}

		try {
			List<String> excludedNames= EnumSourceValidator.getExcludedNames(fMethod);
			excludedNames.remove(fEnumValue);

			if (excludedNames.isEmpty()) {
				EnumSourceValidator.removeExcludeMode(fMethod);
			} else {
				EnumSourceValidator.updateExcludedNames(fMethod, excludedNames);
			}
		} catch (Exception e) {
			JUnitPlugin.log(e);
		}
	}

	private IMethod findMethod(TestElement element) {
		if (!(element instanceof TestSuiteElement)) {
			return null;
		}
		return TestMethodFinder.findMethodForParameterizedTest((TestSuiteElement) element);
	}
}
