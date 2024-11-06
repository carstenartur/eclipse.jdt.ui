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
package org.eclipse.jdt.ui.tests.core.rules;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.jdt.testplugin.JavaTestPlugin;
import org.eclipse.jdt.testplugin.TestOptions;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.search.SearchParticipantRecord;
import org.eclipse.jdt.internal.ui.search.SearchParticipantsExtensionPoint;


public class JUnitSourceSetup implements BeforeEachCallback, AfterEachCallback {
	public static final String PROJECT_NAME= "JUnitSource";
	public static final String SRC_CONTAINER= "src";

	private IJavaProject fProject;
	private final SearchParticipantsExtensionPoint fExtensionPoint;

	static class NullExtensionPoint extends SearchParticipantsExtensionPoint {
		@Override
		public SearchParticipantRecord[] getSearchParticipants(IProject[] concernedProjects) {
			return new SearchParticipantRecord[0];
		}
	}

	public static IJavaProject getProject() {
		IProject project= ResourcesPlugin.getWorkspace().getRoot().getProject(PROJECT_NAME);
		return JavaCore.create(project);
	}

	public JUnitSourceSetup(SearchParticipantsExtensionPoint participants) {
		fExtensionPoint= participants;
	}

	public JUnitSourceSetup() {
		this( new NullExtensionPoint());
	}

	@Override
	public void beforeEach(ExtensionContext context) throws CoreException, InvocationTargetException, IOException {
		SearchParticipantsExtensionPoint.debugSetInstance(fExtensionPoint);
		fProject= JavaProjectHelper.createJavaProject(PROJECT_NAME, "bin");
		JavaProjectHelper.addRTJar(fProject);
		File junitSrcArchive= JavaTestPlugin.getDefault().getFileInPlugin(JavaProjectHelper.JUNIT_SRC_381);
		JavaProjectHelper.addSourceContainerWithImport(fProject, SRC_CONTAINER, junitSrcArchive, JavaProjectHelper.JUNIT_SRC_ENCODING);
		JavaCore.setOptions(TestOptions.getDefaultOptions());
		TestOptions.initializeCodeGenerationOptions();
		JavaPlugin.getDefault().getCodeTemplateStore().load();
	}

	@Override
	public void afterEach(ExtensionContext context) {
		try {
			JavaProjectHelper.delete(fProject);
		} catch (CoreException e) {
			e.printStackTrace();
		}
		SearchParticipantsExtensionPoint.debugSetInstance(null);
	}
}
