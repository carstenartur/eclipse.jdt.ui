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
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Watches one imported JUnit result file and coalesces file-system events before notifying
 * its listener.
 */
public final class ImportedTestRunFileWatcher implements AutoCloseable {

	private static final long DEBOUNCE_MILLIS= 500;

	private final Consumer<Path> fChangeListener;
	private final ScheduledExecutorService fDebouncer;

	private WatchService fWatchService;
	private Path fWatchedFile;
	private ScheduledFuture<?> fPendingNotification;

	/**
	 * Creates a watcher.
	 *
	 * @param changeListener listener notified with the normalized absolute path
	 */
	public ImportedTestRunFileWatcher(Consumer<Path> changeListener) {
		fChangeListener= Objects.requireNonNull(changeListener);
		fDebouncer= Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread= new Thread(runnable, "JUnit imported test results debounce"); //$NON-NLS-1$
			thread.setDaemon(true);
			return thread;
		});
	}

	/**
	 * Starts watching the given file. Calling this method for the currently watched path is a
	 * no-op; calling it for another path replaces the existing registration.
	 *
	 * @param file file to watch
	 * @throws IOException if its parent directory cannot be watched
	 */
	public synchronized void watch(Path file) throws IOException {
		Path normalizedFile= Objects.requireNonNull(file).toAbsolutePath().normalize();
		if (normalizedFile.equals(fWatchedFile) && fWatchService != null) {
			return;
		}

		stopWatching();
		Path parent= normalizedFile.getParent();
		if (parent == null) {
			throw new IOException("The imported test result file has no parent directory."); //$NON-NLS-1$
		}

		WatchService watchService= parent.getFileSystem().newWatchService();
		try {
			parent.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
					StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
		} catch (IOException | RuntimeException e) {
			watchService.close();
			throw e;
		}

		fWatchService= watchService;
		fWatchedFile= normalizedFile;
		Thread thread= new Thread(() -> runWatcher(watchService, normalizedFile),
				"JUnit imported test results watcher"); //$NON-NLS-1$
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * Stops watching the current file while keeping this watcher reusable.
	 */
	public synchronized void clear() {
		stopWatching();
	}

	private void runWatcher(WatchService watchService, Path watchedFile) {
		while (true) {
			WatchKey key;
			try {
				key= watchService.take();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (ClosedWatchServiceException e) {
				return;
			}

			boolean relevantChange= false;
			for (WatchEvent<?> event : key.pollEvents()) {
				if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
					relevantChange= true;
					continue;
				}
				Object context= event.context();
				if (context instanceof Path changedPath
						&& watchedFile.getFileName().equals(changedPath.getFileName())) {
					relevantChange= true;
				}
			}

			if (relevantChange) {
				scheduleNotification(watchService, watchedFile);
			}
			if (!key.reset()) {
				return;
			}
		}
	}

	private synchronized void scheduleNotification(WatchService watchService, Path watchedFile) {
		if (watchService != fWatchService || !watchedFile.equals(fWatchedFile)) {
			return;
		}
		if (fPendingNotification != null) {
			fPendingNotification.cancel(false);
		}
		try {
			fPendingNotification= fDebouncer.schedule(
					() -> notifyChange(watchService, watchedFile), DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
		} catch (RejectedExecutionException e) {
			// The view is being disposed.
		}
	}

	private void notifyChange(WatchService watchService, Path watchedFile) {
		synchronized (this) {
			if (watchService != fWatchService || !watchedFile.equals(fWatchedFile)) {
				return;
			}
			fPendingNotification= null;
		}
		try {
			fChangeListener.accept(watchedFile);
		} catch (RuntimeException e) {
			JUnitPlugin.log(e);
		}
	}

	private void stopWatching() {
		if (fPendingNotification != null) {
			fPendingNotification.cancel(false);
			fPendingNotification= null;
		}
		if (fWatchService != null) {
			try {
				fWatchService.close();
			} catch (IOException e) {
				// Nothing useful can be done while stopping the watcher.
			}
		}
		fWatchService= null;
		fWatchedFile= null;
	}

	@Override
	public synchronized void close() {
		stopWatching();
		fDebouncer.shutdownNow();
	}
}
