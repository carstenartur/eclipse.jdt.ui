/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.text.tests.performance;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

import org.eclipse.test.performance.PerformanceMeter;

import org.eclipse.ui.texteditor.AbstractTextEditor;

import org.eclipse.jdt.text.tests.performance.DisplayWaiter.Timeout;

public abstract class ScrollEditorTest extends TextPerformanceTestCase {
	
	private static final int WARM_UP_RUNS= 3;
	
	private static final int MEASURED_RUNS= 3;
	
	private static final String PAGE_SCROLLING_FILE= PerformanceTestSetup.STYLED_TEXT;
	
	private static final String LINE_SCROLLING_FILE= PerformanceTestSetup.TEXT_LAYOUT;
	
	private static final int[] CTRL_HOME= new int[] { SWT.CTRL, SWT.HOME };
	private static final int[] CTRL_DOWN= new int[] { SWT.CTRL, SWT.ARROW_DOWN };
	private static final int[] PG_DOWN= new int[] { SWT.PAGE_DOWN };
	private static final int[] SHIFT_PG_DOWN= new int[] { SWT.SHIFT, SWT.PAGE_DOWN };
	private static final int[] DOWN= new int[] { SWT.ARROW_DOWN };
	private static final int[] SHIFT_DOWN= new int[] { SWT.SHIFT, SWT.ARROW_DOWN };
	private static final int[] PG_UP= new int[] { SWT.PAGE_UP };
	
	protected abstract static class ScrollingMode {
		public ScrollingMode(int[] scroll_combo, int[] home_combo) {
			super();
			SCROLL_COMBO= scroll_combo;
			HOME_COMBO= home_combo;
		}
		public final int[] SCROLL_COMBO;
		public final int[] HOME_COMBO;
		public abstract int computeOperations(int numberOfLines, int visibleLines);
		public abstract String getFile();
	}
	
	protected static final ScrollingMode PAGE_WISE= new ScrollingMode(PG_DOWN, CTRL_HOME) {
		public int computeOperations(int numberOfLines, int visibleLines) {
			return numberOfLines / visibleLines;
		}
		public String getFile() {
			return PAGE_SCROLLING_FILE;
		}
	};
	protected static final ScrollingMode PAGE_WISE_SELECT= new ScrollingMode(SHIFT_PG_DOWN, CTRL_HOME) {
		public int computeOperations(int numberOfLines, int visibleLines) {
			return numberOfLines / visibleLines;
		}
		public String getFile() {
			return PAGE_SCROLLING_FILE;
		}
	};
	protected static final ScrollingMode LINE_WISE_NO_CARET_MOVE= new ScrollingMode(CTRL_DOWN, PG_UP) {
		public int computeOperations(int numberOfLines, int visibleLines) {
			return numberOfLines - visibleLines;
		}
		public String getFile() {
			return LINE_SCROLLING_FILE;
		}
	};
	protected static final ScrollingMode LINE_WISE= new ScrollingMode(DOWN, CTRL_HOME) {
		public int computeOperations(int numberOfLines, int visibleLines) {
			return numberOfLines - 1;
		}
		public String getFile() {
			return LINE_SCROLLING_FILE;
		}
	};
	protected static final ScrollingMode LINE_WISE_SELECT= new ScrollingMode(SHIFT_DOWN, CTRL_HOME) {
		public int computeOperations(int numberOfLines, int visibleLines) {
			return numberOfLines - 1;
		}
		public String getFile() {
			return LINE_SCROLLING_FILE;
		}
	};
	
	protected void setUp() throws Exception {
		super.setUp();
		EditorTestHelper.bringToTop();
		setWarmUpRuns(WARM_UP_RUNS);
		setMeasuredRuns(MEASURED_RUNS);
	}

	protected void setUp(AbstractTextEditor editor) throws Exception { }
	
	protected void measure(ScrollingMode mode) throws Exception {
		measure(mode, createPerformanceMeter(), getWarmUpRuns(), getMeasuredRuns());
	}

	protected void measure(ScrollingMode mode, PerformanceMeter performanceMeter, int warmUpRuns, int measuredRuns) throws Exception {
		AbstractTextEditor editor= null;
		try {
			editor= (AbstractTextEditor) EditorTestHelper.openInEditor(ResourceTestHelper.findFile(mode.getFile()), getEditor(), true);
			setUp(editor);
			EditorTestHelper.joinBackgroundActivities(editor);
			
			StyledText text= (StyledText) editor.getAdapter(Control.class);
			measure(text, mode, getNullPerformanceMeter(), warmUpRuns);
			measure(text, mode, performanceMeter, measuredRuns);
			commitAllMeasurements();
			assertAllPerformance();
		} finally {
			EditorTestHelper.closeAllEditors();
		}
	}

	protected abstract String getEditor();

	private void measure(final StyledText text, ScrollingMode mode, PerformanceMeter performanceMeter, int runs) {
		Display display= EditorTestHelper.getActiveDisplay();
		final int numberOfLines= text.getLineCount();
		final int visibleLinesInViewport= text.getClientArea().height / text.getLineHeight();
		int operations= mode.computeOperations(numberOfLines, visibleLinesInViewport);
		
		DisplayWaiter waiter= new DisplayWaiter(display, true);
		try {
			for (int i= 0; i < runs; i++) {
				// 1: post scroll events
				long delay= 3000;
				Timeout timeout= waiter.restart(delay);
				performanceMeter.start();
				for (int j= 0; j < operations && !timeout.hasTimedOut(); j++) {
					// avoid overhead: assertTrue(text.getTopIndex() + visibleLinesInViewport < numberOfLines - 1);
					SWTEventHelper.pressKeyCodeCombination(display, mode.SCROLL_COMBO, false);
					sleepAndRun(display);
					// average for select & scroll is 30ms/line 
					// - give it ten time as much to allow for GCs (300ms),
					// check back every 10 lines == never wait longer than 3s.
					if (j % 10 == 9)
						timeout= waiter.restart(delay);
				}
				performanceMeter.stop();
				assertFalse("Failed to receive event within " + delay + "ms.", timeout.hasTimedOut());
				
				// 2: wait until the events have been swallowed
				timeout= waiter.restart(2000);
				while (!timeout.hasTimedOut() && text.getTopIndex() + visibleLinesInViewport < numberOfLines - 1) {
					sleepAndRun(display);
				}
				assertFalse("Never scrolled to the bottom within 2000ms.\nTopIndex: " + text.getTopIndex() + " visibleLines: " + visibleLinesInViewport + " totalLines: " + numberOfLines + " operations: " + operations, timeout.hasTimedOut());
				
				// 3: go back home
				timeout= waiter.restart(2000);
				SWTEventHelper.pressKeyCodeCombination(display, mode.HOME_COMBO, false);
				while (!timeout.hasTimedOut() && text.getTopIndex() != 0) {
					sleepAndRun(display);
				}
				
				assertFalse("Never went back to the top within 2000ms.", timeout.hasTimedOut());
				
				waiter.hold();
			}
		} finally {
			waiter.stop();
		}
	}

	private void sleepAndRun(Display display) {
		if (display.sleep())
			EditorTestHelper.runEventQueue(display);
	}
}
