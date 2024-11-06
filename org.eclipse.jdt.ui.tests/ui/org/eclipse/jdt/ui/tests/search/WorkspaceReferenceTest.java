/*******************************************************************************
 * Copyright (c) 2000, 2020 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.jdt.ui.tests.core.rules.JUnitSourceSetup;

public class WorkspaceReferenceTest {

	@RegisterExtension
	public JUnitSourceSetup projectSetup = new JUnitSourceSetup();

	@Test
	public void testSimpleMethodRef() throws Exception {
		assertEquals(9, SearchTestHelper.countMethodRefs("junit.framework.Test", "countTestCases", new String[0]));
	}

	@Test
	public void testFindOverridden() throws Exception {
		assertEquals(6, SearchTestHelper.countMethodRefs("junit.framework.TestCase", "countTestCases", new String[0]));
	}
}
