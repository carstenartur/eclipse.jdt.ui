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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.junit.ui;

import java.io.IOException;
import java.nio.file.Path;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.ErrorDialog;

import org.eclipse.swt.widgets.Display;

import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import org.eclipse.jdt.internal.junit.JUnitCorePlugin;
import org.eclipse.jdt.internal.junit.model.JUnitModel;
import org.eclipse.jdt.internal.junit.model.TestRunSession;

/**
 * Controls manual and automatic reload of the file-imported test run displayed in one JUnit
 * view.
 */
final class ImportedTestRunReloadController implements AutoCloseable {

	private static final int AUTO_RELOAD_ATTEMPTS= 5;
	private static final long AUTO_RELOAD_RETRY_DELAY_MILLIS= 300;

	private final TestRunnerViewPart fView;
	private final ReloadAction fReloadAction;
	private final AutoReloadAction fAutoReloadAction;
	private final ImportedTestRunFileWatcher fFileWatcher;

	private volatile Job fReloadJob;

	ImportedTestRunReloadController(TestRunnerViewPart view) {
		fView= view;
		fReloadAction= new ReloadAction();
		fAutoReloadAction= new AutoReloadAction();
		fFileWatcher= new ImportedTestRunFileWatcher(this::handleFileChanged);
	}

	Action getReloadAction() {
		return fReloadAction;
	}

	Action getAutoReloadAction() {
		return fAutoReloadAction;
	}

	void update() {
		Path sourceFile= getActiveSourceFile();
		fReloadAction.setEnabled(sourceFile != null && fReloadJob == null);
		fAutoReloadAction.setEnabled(sourceFile != null);

		if (!fAutoReloadAction.isChecked() || sourceFile == null) {
			fFileWatcher.clear();
			return;
		}
		try {
			fFileWatcher.watch(sourceFile);
		} catch (IOException e) {
			fAutoReloadAction.setChecked(false);
			fFileWatcher.clear();
			JUnitPlugin.log(new Status(IStatus.ERROR, JUnitPlugin.PLUGIN_ID,
					JUnitMessages.TestRunnerViewPart_auto_reload_error, e));
		}
	}

	private Path getActiveSourceFile() {
		TestRunSession session= fView.getTestRunSession();
		return session == null ? null : JUnitCorePlugin.getModel().getImportedTestRunFile(session);
	}

	private void handleFileChanged(Path sourceFile) {
		Display display= fView.getSite().getShell().getDisplay();
		if (display.isDisposed()) {
			return;
		}
		display.asyncExec(() -> {
			if (!fView.getSite().getShell().isDisposed() && fAutoReloadAction.isChecked()
					&& sourceFile.equals(getActiveSourceFile())) {
				reload(false);
			}
		});
	}

	private void reload(boolean manual) {
		TestRunSession session= fView.getTestRunSession();
		Path sourceFile= session == null ? null : JUnitCorePlugin.getModel().getImportedTestRunFile(session);
		if (sourceFile == null || fReloadJob != null) {
			return;
		}

		Display display= fView.getSite().getShell().getDisplay();
		Job reloadJob= new Job(JUnitMessages.TestRunnerViewPart_reload_job_name) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				CoreException failure= null;
				int attempts= manual ? 1 : AUTO_RELOAD_ATTEMPTS;
				try {
					for (int attempt= 0; attempt < attempts && !monitor.isCanceled(); attempt++) {
						try {
							JUnitModel.reloadTestRunSession(session);
							return Status.OK_STATUS;
						} catch (CoreException e) {
							failure= e;
							if (attempt + 1 < attempts && !sleepBeforeRetry()) {
								return Status.CANCEL_STATUS;
							}
						}
					}

					if (failure != null) {
						reportFailure(failure, manual, display);
					}
					return monitor.isCanceled() ? Status.CANCEL_STATUS : Status.OK_STATUS;
				} finally {
					if (!display.isDisposed()) {
						display.asyncExec(() -> {
							if (fReloadJob == this) {
								fReloadJob= null;
								update();
							}
						});
					}
				}
			}
		};
		reloadJob.setUser(manual);
		fReloadJob= reloadJob;
		update();
		reloadJob.schedule();
	}

	private static boolean sleepBeforeRetry() {
		try {
			Thread.sleep(AUTO_RELOAD_RETRY_DELAY_MILLIS);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private void reportFailure(CoreException failure, boolean manual, Display display) {
		if (!manual) {
			JUnitPlugin.log(failure);
			return;
		}
		IStatus status= failure.getStatus();
		if (!display.isDisposed()) {
			display.asyncExec(() -> {
				if (!fView.getSite().getShell().isDisposed()) {
					ErrorDialog.openError(fView.getSite().getShell(),
							JUnitMessages.TestRunnerViewPart_reload_error_title,
							status.getMessage(), status);
				}
			});
		}
	}

	@Override
	public void close() {
		Job reloadJob= fReloadJob;
		if (reloadJob != null) {
			reloadJob.cancel();
			fReloadJob= null;
		}
		fFileWatcher.close();
	}

	private final class ReloadAction extends Action {
		ReloadAction() {
			setText(JUnitMessages.TestRunnerViewPart_reload_action_label);
			setToolTipText(JUnitMessages.TestRunnerViewPart_reload_action_tooltip);
			setImageDescriptor(PlatformUI.getWorkbench().getSharedImages()
					.getImageDescriptor(ISharedImages.IMG_ELCL_SYNCED));
			setDisabledImageDescriptor(PlatformUI.getWorkbench().getSharedImages()
					.getImageDescriptor(ISharedImages.IMG_ELCL_SYNCED_DISABLED));
			setEnabled(false);
		}

		@Override
		public void run() {
			reload(true);
		}
	}

	private final class AutoReloadAction extends Action {
		AutoReloadAction() {
			super(JUnitMessages.TestRunnerViewPart_auto_reload_action_label, AS_CHECK_BOX);
			setToolTipText(JUnitMessages.TestRunnerViewPart_auto_reload_action_tooltip);
			setEnabled(false);
		}

		@Override
		public void run() {
			update();
		}
	}
}
