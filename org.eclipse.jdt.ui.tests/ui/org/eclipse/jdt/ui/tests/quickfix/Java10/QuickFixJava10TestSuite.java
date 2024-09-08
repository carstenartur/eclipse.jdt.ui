/*******************************************************************************
 * Copyright (c) 2024 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java10;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
	ExplicitEncodingCleanUpTest.class
})
public class QuickFixJava10TestSuite {
}