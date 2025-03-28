/*******************************************************************************
 * Copyright (c) 2000, 2024 IBM Corporation and others.
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
 *     Red Hat Inc. - use HighlightedPositionCore
 *******************************************************************************/

package org.eclipse.jdt.internal.ui.javaeditor;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.resource.StringConverter;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;

import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextAttribute;

import org.eclipse.jdt.ui.text.IColorManager;
import org.eclipse.jdt.ui.text.IColorManagerExtension;
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration;

import org.eclipse.jdt.internal.ui.preferences.SyntaxColorHighlighting;
import org.eclipse.jdt.internal.ui.text.JavaPresentationReconciler;

/**
 * Semantic highlighting manager
 *
 * @since 3.0
 */
public class SemanticHighlightingManager implements IPropertyChangeListener {

	/**
	 * Highlighting.
	 */
	static class Highlighting { // TODO: rename to HighlightingStyle

		/** Text attribute */
		private TextAttribute fTextAttribute;
		/** Enabled state */
		private boolean fIsEnabled;
		/** Preference Key */
		private final String fPreferenceKey;

		/**
		 * Initialize with the given text attribute.
		 * @param textAttribute The text attribute
		 * @param isEnabled the enabled state
		 */
		public Highlighting(String preferenceKey, TextAttribute textAttribute, boolean isEnabled) {
			setTextAttribute(textAttribute);
			setEnabled(isEnabled);
			this.fPreferenceKey = preferenceKey;
		}

		/**
		 * @return Returns the text attribute.
		 */
		public TextAttribute getTextAttribute() {
			return fTextAttribute;
		}

		/**
		 * @param textAttribute The background to set.
		 */
		public void setTextAttribute(TextAttribute textAttribute) {
			fTextAttribute= textAttribute;
		}

		/**
		 * @return the enabled state
		 */
		public boolean isEnabled() {
			return fIsEnabled;
		}

		/**
		 * @param isEnabled the new enabled state
		 */
		public void setEnabled(boolean isEnabled) {
			fIsEnabled= isEnabled;
		}

		public String getPreferenceKey() {
			return fPreferenceKey;
		}

	}

	/**
	 * Highlighted Positions.
	 */
	static class HighlightedPosition extends HighlightedPositionCore {

		/**
		 * Initialize the styled positions with the given offset, length and foreground color.
		 *
		 * @param offset The position offset
		 * @param length The position length
		 * @param highlighting The position's highlighting
		 * @param lock The lock object
		 */
		public HighlightedPosition(int offset, int length, Highlighting highlighting, Object lock) {
			super(offset, length, highlighting, lock);
		}

		/**
		 * @return Returns a corresponding style range.
		 */
		public StyleRange createStyleRange() {
			int len= 0;
			if (getHighlighting().isEnabled())
				len= getLength();

			TextAttribute textAttribute= getHighlighting().getTextAttribute();
			int style= textAttribute.getStyle();
			int fontStyle= style & (SWT.ITALIC | SWT.BOLD | SWT.NORMAL);
			StyleRange styleRange= new StyleRange(getOffset(), len, textAttribute.getForeground(), textAttribute.getBackground(), fontStyle);
			styleRange.strikeout= (style & TextAttribute.STRIKETHROUGH) != 0;
			styleRange.underline= (style & TextAttribute.UNDERLINE) != 0;

			return styleRange;
		}

		/**
		 * Uses reference equality for the highlighting.
		 *
		 * @param off The offset
		 * @param len The length
		 * @param highlighting The highlighting
		 * @return <code>true</code> iff the given offset, length and highlighting are equal to the internal ones.
		 */
		public boolean isEqual(int off, int len, Highlighting highlighting) {
			return super.isEqual(off, len, highlighting);
		}

		/**
		 * Is this position contained in the given range (inclusive)? Synchronizes on position updater.
		 *
		 * @param off The range offset
		 * @param len The range length
		 * @return <code>true</code> iff this position is not delete and contained in the given range.
		 */
		@Override
		public boolean isContained(int off, int len) {
			return super.isContained(off, len);
		}

		@Override
		public void update(int off, int len) {
			super.update(off, len);
		}

		/*
		 * @see org.eclipse.jface.text.Position#setLength(int)
		 */
		@Override
		public void setLength(int length) {
			super.setLength(length);
		}

		/*
		 * @see org.eclipse.jface.text.Position#setOffset(int)
		 */
		@Override
		public void setOffset(int offset) {
			super.setOffset(offset);
		}

		/*
		 * @see org.eclipse.jface.text.Position#delete()
		 */
		@Override
		public void delete() {
			super.delete();
		}

		/*
		 * @see org.eclipse.jface.text.Position#undelete()
		 */
		@Override
		public void undelete() {
			super.undelete();
		}

		/**
		 * @return Returns the highlighting.
		 */
		@Override
		public Highlighting getHighlighting() {
			return (Highlighting)super.getHighlighting();
		}
	}

	/**
	 * Highlighted ranges.
	 */
	public static class HighlightedRange extends Region {
		/** The highlighting key as returned by {@link SemanticHighlighting#getPreferenceKey()}. */
		private String fKey;

		/**
		 * Initialize with the given offset, length and highlighting key.
		 *
		 * @param offset the offset
		 * @param length the length
		 * @param key the highlighting key as returned by
		 *            {@link SemanticHighlighting#getPreferenceKey()}
		 */
		public HighlightedRange(int offset, int length, String key) {
			super(offset, length);
			fKey= key;
		}

		/**
		 * @return the highlighting key as returned by {@link SemanticHighlighting#getPreferenceKey()}
		 */
		public String getKey() {
			return fKey;
		}

		/*
		 * @see org.eclipse.jface.text.Region#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object o) {
			return super.equals(o) && o instanceof HighlightedRange && fKey.equals(((HighlightedRange)o).getKey());
		}

		/*
		 * @see org.eclipse.jface.text.Region#hashCode()
		 */
		@Override
		public int hashCode() {
			return super.hashCode() | fKey.hashCode();
		}
	}

	/** Semantic highlighting presenter */
	private SemanticHighlightingPresenter fPresenter;
	/** Semantic highlighting reconciler */
	private SemanticHighlightingReconciler fReconciler;

	/** Semantic highlightings */
	private SemanticHighlighting[] fSemanticHighlightings;
	/** Highlightings */
	private Highlighting[] fHighlightings;
	private Highlighting[] fSyntaxHighlightings;

	/** The editor */
	private JavaEditor fEditor;
	/** The source viewer */
	private JavaSourceViewer fSourceViewer;
	/** The color manager */
	private IColorManager fColorManager;
	/** The preference store */
	private IPreferenceStore fPreferenceStore;
	/** The source viewer configuration */
	private JavaSourceViewerConfiguration fConfiguration;
	/** The presentation reconciler */
	private JavaPresentationReconciler fPresentationReconciler;

	/** The hard-coded ranges */
	private HighlightedRange[][] fHardcodedRanges;

	/**
	 * Install the semantic highlighting on the given editor infrastructure
	 *
	 * @param editor The Java editor
	 * @param sourceViewer The source viewer
	 * @param colorManager The color manager
	 * @param preferenceStore The preference store
	 */
	public void install(JavaEditor editor, JavaSourceViewer sourceViewer, IColorManager colorManager, IPreferenceStore preferenceStore) {
		fEditor= editor;
		fSourceViewer= sourceViewer;
		fColorManager= colorManager;
		fPreferenceStore= preferenceStore;
		if (fEditor != null) {
			fConfiguration= editor.createJavaSourceViewerConfiguration();
			fPresentationReconciler= (JavaPresentationReconciler) fConfiguration.getPresentationReconciler(sourceViewer);
		} else {
			fConfiguration= null;
			fPresentationReconciler= null;
		}

		fPreferenceStore.addPropertyChangeListener(this);

		if (isEnabled())
			enable();
	}

	/**
	 * Install the semantic highlighting on the given source viewer infrastructure. No reconciliation will be performed.
	 *
	 * @param sourceViewer the source viewer
	 * @param colorManager the color manager
	 * @param preferenceStore the preference store
	 * @param hardcodedRanges the hard-coded ranges to be highlighted
	 */
	public void install(JavaSourceViewer sourceViewer, IColorManager colorManager, IPreferenceStore preferenceStore, HighlightedRange[][] hardcodedRanges) {
		fHardcodedRanges= hardcodedRanges;
		install(null, sourceViewer, colorManager, preferenceStore);
	}

	/**
	 * Create semantic highlighting presenter instance to use.
	 *
	 * @return semantic highlighting presenter to use
	 */
	protected SemanticHighlightingPresenter createSemanticHighlightingPresenter() {
		return new SemanticHighlightingPresenter();
	}

	/**
	 * Create semantic highlighting reconciler instance to use.
	 *
	 * @return semantic highlighting reconciler to use
	 */
	protected SemanticHighlightingReconciler createSemanticHighlightingReconciler() {
		return new SemanticHighlightingReconciler();
	}

	/**
	 * Enable semantic highlighting.
	 */
	private void enable() {
		initializeHighlightings();

		fPresenter= createSemanticHighlightingPresenter();
		fPresenter.install(fSourceViewer, fPresentationReconciler);

		if (fEditor != null) {
			fReconciler= createSemanticHighlightingReconciler();
			fReconciler.install(fEditor, fSourceViewer, fPresenter, fSemanticHighlightings, fHighlightings, fSyntaxHighlightings);
		} else {
			fPresenter.updatePresentation(null, createHardcodedPositions(), new HighlightedPosition[0]);
		}
	}

	/**
	 * Computes the hard-coded positions from the hard-coded ranges
	 *
	 * @return the hard-coded positions
	 */
	private HighlightedPosition[] createHardcodedPositions() {
		List<HighlightedPosition> positions= new ArrayList<>();
		for (HighlightedRange[] hardcodedRange : fHardcodedRanges) {
			HighlightedRange range= null;
			Highlighting hl= null;
			for (HighlightedRange r : hardcodedRange) {
				hl= getHighlighting(r.getKey());
				if (hl.isEnabled()) {
					range= r;
					break;
				}
			}
			if (range != null)
				positions.add(fPresenter.createHighlightedPosition(range.getOffset(), range.getLength(), hl));
		}
		return positions.toArray(new HighlightedPosition[positions.size()]);
	}

	/**
	 * Returns the highlighting corresponding to the given key.
	 *
	 * @param key the highlighting key as returned by {@link SemanticHighlighting#getPreferenceKey()}
	 * @return the corresponding highlighting
	 */
	private Highlighting getHighlighting(String key) {
		for (Highlighting highlighting : fHighlightings) {
			if (key.equals(highlighting.getPreferenceKey()))
				return highlighting;
		}
		return null;
	}

	/**
	 * Uninstall the semantic highlighting
	 */
	public void uninstall() {
		disable();

		if (fPreferenceStore != null) {
			fPreferenceStore.removePropertyChangeListener(this);
			fPreferenceStore= null;
		}

		fEditor= null;
		fSourceViewer= null;
		fColorManager= null;
		fConfiguration= null;
		fPresentationReconciler= null;
		fHardcodedRanges= null;
	}

	/**
	 * Disable semantic highlighting.
	 */
	private void disable() {
		if (fReconciler != null) {
			fReconciler.uninstall();
			fReconciler= null;
		}

		if (fPresenter != null) {
			fPresenter.uninstall();
			fPresenter= null;
		}

		if (fSemanticHighlightings != null)
			disposeHighlightings();
	}

	/**
	 * @return <code>true</code> iff semantic highlighting is enabled in the preferences
	 */
	private boolean isEnabled() {
		return SemanticHighlightings.isEnabled(fPreferenceStore);
	}

	/**
	 * Initialize semantic highlightings.
	 */
	private void initializeHighlightings() {
		fSemanticHighlightings= SemanticHighlightings.getSemanticHighlightings();
		fHighlightings= new Highlighting[fSemanticHighlightings.length];
		for (int i= 0, n= fSemanticHighlightings.length; i < n; i++) {
			SemanticHighlighting semanticHighlighting= fSemanticHighlightings[i];

			String colorKey= SemanticHighlightings.getColorPreferenceKey(semanticHighlighting);
			String boldKey= SemanticHighlightings.getBoldPreferenceKey(semanticHighlighting);
			String italicKey= SemanticHighlightings.getItalicPreferenceKey(semanticHighlighting);
			String strikethroughKey= SemanticHighlightings.getStrikethroughPreferenceKey(semanticHighlighting);
			String underlineKey= SemanticHighlightings.getUnderlinePreferenceKey(semanticHighlighting);
			boolean isEnabled= fPreferenceStore.getBoolean(SemanticHighlightings.getEnabledPreferenceKey(semanticHighlighting));

			fHighlightings[i]= createHighlightingFromPereferences(semanticHighlighting.getPreferenceKey(), isEnabled, colorKey, boldKey, italicKey, strikethroughKey, underlineKey);
		}

		SyntaxColorHighlighting[] syntaxHighlightings= SyntaxColorHighlighting.getSyntaxColorHighlightings();
		fSyntaxHighlightings= new Highlighting[syntaxHighlightings.length];
		for (int i= 0; i < syntaxHighlightings.length; i++) {
			SyntaxColorHighlighting h= syntaxHighlightings[i];
			fSyntaxHighlightings[i]= createHighlightingFromPereferences(h.preferenceKey(), true, h.preferenceKey(), h.getBoldPreferenceKey(), h.getItalicPreferenceKey(), h.getStrikethroughPreferenceKey(), h.getUnderlinePreferenceKey());
		}
	}

	private Highlighting createHighlightingFromPereferences(String key, boolean isEnabled, String colorKey, String boldKey, String italicKey, String strikethroughKey, String underlineKey) {
		addColor(colorKey);

		int style= fPreferenceStore.getBoolean(boldKey) ? SWT.BOLD : SWT.NORMAL;

		if (fPreferenceStore.getBoolean(italicKey))
			style |= SWT.ITALIC;

		if (fPreferenceStore.getBoolean(strikethroughKey))
			style |= TextAttribute.STRIKETHROUGH;

		if (fPreferenceStore.getBoolean(underlineKey))
			style |= TextAttribute.UNDERLINE;

		return new Highlighting(key, new TextAttribute(fColorManager.getColor(PreferenceConverter.getColor(fPreferenceStore, colorKey)), null, style), isEnabled);
	}

	/**
	 * Dispose the semantic highlightings.
	 */
	private void disposeHighlightings() {
		for (SemanticHighlighting fSemanticHighlighting : fSemanticHighlightings) {
			removeColor(SemanticHighlightings.getColorPreferenceKey(fSemanticHighlighting));
		}
		for (Highlighting h : fSyntaxHighlightings) {
			removeColor(h.getPreferenceKey());
		}

		fSemanticHighlightings= null;
		fHighlightings= null;
		fSyntaxHighlightings= null;
	}

	/*
	 * @see org.eclipse.jface.util.IPropertyChangeListener#propertyChange(org.eclipse.jface.util.PropertyChangeEvent)
	 */
	@Override
	public void propertyChange(PropertyChangeEvent event) {
		handlePropertyChangeEvent(event);
	}

	/**
	 * Handle the given property change event
	 *
	 * @param event The event
	 */
	private void handlePropertyChangeEvent(PropertyChangeEvent event) {
		if (fPreferenceStore == null)
			return; // Uninstalled during event notification

		if (fConfiguration != null)
			fConfiguration.handlePropertyChangeEvent(event);

		if (SemanticHighlightings.affectsEnablement(fPreferenceStore, event)) {
			if (isEnabled())
				enable();
			else
				disable();
		}

		if (!isEnabled())
			return;

		boolean refreshNeeded= false;

		for (int i= 0, n= fSemanticHighlightings.length; i < n; i++) {
			SemanticHighlighting semanticHighlighting= fSemanticHighlightings[i];

			String colorKey= SemanticHighlightings.getColorPreferenceKey(semanticHighlighting);
			if (colorKey.equals(event.getProperty())) {
				adaptToTextForegroundChange(fHighlightings[i], event);
				fPresenter.highlightingStyleChanged(fHighlightings[i]);
				refreshNeeded= true;
				continue;
			}

			String boldKey= SemanticHighlightings.getBoldPreferenceKey(semanticHighlighting);
			if (boldKey.equals(event.getProperty())) {
				adaptToTextStyleChange(fHighlightings[i], event, SWT.BOLD);
				fPresenter.highlightingStyleChanged(fHighlightings[i]);
				refreshNeeded= true;
				continue;
			}

			String italicKey= SemanticHighlightings.getItalicPreferenceKey(semanticHighlighting);
			if (italicKey.equals(event.getProperty())) {
				adaptToTextStyleChange(fHighlightings[i], event, SWT.ITALIC);
				fPresenter.highlightingStyleChanged(fHighlightings[i]);
				refreshNeeded= true;
				continue;
			}

			String strikethroughKey= SemanticHighlightings.getStrikethroughPreferenceKey(semanticHighlighting);
			if (strikethroughKey.equals(event.getProperty())) {
				adaptToTextStyleChange(fHighlightings[i], event, TextAttribute.STRIKETHROUGH);
				fPresenter.highlightingStyleChanged(fHighlightings[i]);
				refreshNeeded= true;
				continue;
			}

			String underlineKey= SemanticHighlightings.getUnderlinePreferenceKey(semanticHighlighting);
			if (underlineKey.equals(event.getProperty())) {
				adaptToTextStyleChange(fHighlightings[i], event, TextAttribute.UNDERLINE);
				fPresenter.highlightingStyleChanged(fHighlightings[i]);
				refreshNeeded= true;
				continue;
			}

			String enabledKey= SemanticHighlightings.getEnabledPreferenceKey(semanticHighlighting);
			if (enabledKey.equals(event.getProperty())) {
				adaptToEnablementChange(fHighlightings[i], event);
				fPresenter.highlightingStyleChanged(fHighlightings[i]);
				refreshNeeded= true;
				continue;
			}
		}

		SyntaxColorHighlighting[] editorHighlightings= SyntaxColorHighlighting.getSyntaxColorHighlightings();
		for (int i= 0, n= editorHighlightings.length; i < n; i++) {
			SyntaxColorHighlighting h= editorHighlightings[i];

			String colorKey= h.preferenceKey();
			if (colorKey.equals(event.getProperty())) {
				adaptToTextForegroundChange(fSyntaxHighlightings[i], event);
				fPresenter.highlightingStyleChanged(fSyntaxHighlightings[i]);
				refreshNeeded= true;
				continue;
			}

			String boldKey= h.getBoldPreferenceKey();
			if (boldKey.equals(event.getProperty())) {
				adaptToTextStyleChange(fSyntaxHighlightings[i], event, SWT.BOLD);
				fPresenter.highlightingStyleChanged(fSyntaxHighlightings[i]);
				refreshNeeded= true;
				continue;
			}

			String italicKey= h.getItalicPreferenceKey();
			if (italicKey.equals(event.getProperty())) {
				adaptToTextStyleChange(fSyntaxHighlightings[i], event, SWT.ITALIC);
				fPresenter.highlightingStyleChanged(fSyntaxHighlightings[i]);
				refreshNeeded= true;
				continue;
			}

			String strikethroughKey= h.getStrikethroughPreferenceKey();
			if (strikethroughKey.equals(event.getProperty())) {
				adaptToTextStyleChange(fSyntaxHighlightings[i], event, TextAttribute.STRIKETHROUGH);
				fPresenter.highlightingStyleChanged(fSyntaxHighlightings[i]);
				refreshNeeded= true;
				continue;
			}

			String underlineKey= h.getUnderlinePreferenceKey();
			if (underlineKey.equals(event.getProperty())) {
				adaptToTextStyleChange(fSyntaxHighlightings[i], event, TextAttribute.UNDERLINE);
				fPresenter.highlightingStyleChanged(fSyntaxHighlightings[i]);
				refreshNeeded= true;
				continue;
			}

		}

		if (refreshNeeded && fReconciler != null)
			fReconciler.refresh();
	}

	private void adaptToEnablementChange(Highlighting highlighting, PropertyChangeEvent event) {
		Object value= event.getNewValue();
		boolean eventValue;
		if (value instanceof Boolean)
			eventValue= ((Boolean) value);
		else if (IPreferenceStore.TRUE.equals(value))
			eventValue= true;
		else
			eventValue= false;
		highlighting.setEnabled(eventValue);
	}

	private void adaptToTextForegroundChange(Highlighting highlighting, PropertyChangeEvent event) {
		RGB rgb= null;

		Object value= event.getNewValue();
		if (value instanceof RGB)
			rgb= (RGB) value;
		else if (value instanceof String)
			rgb= StringConverter.asRGB((String) value);

		if (rgb != null) {

			String property= event.getProperty();
			Color color= fColorManager.getColor(property);

			if ((color == null || !rgb.equals(color.getRGB())) && fColorManager instanceof IColorManagerExtension) {
				IColorManagerExtension ext= (IColorManagerExtension) fColorManager;
				ext.unbindColor(property);
				ext.bindColor(property, rgb);
				color= fColorManager.getColor(property);
			}

			TextAttribute oldAttr= highlighting.getTextAttribute();
			highlighting.setTextAttribute(new TextAttribute(color, oldAttr.getBackground(), oldAttr.getStyle()));
		}
	}

	private void adaptToTextStyleChange(Highlighting highlighting, PropertyChangeEvent event, int styleAttribute) {
		boolean eventValue= false;
		Object value= event.getNewValue();
		if (value instanceof Boolean)
			eventValue= ((Boolean) value);
		else if (IPreferenceStore.TRUE.equals(value))
			eventValue= true;

		TextAttribute oldAttr= highlighting.getTextAttribute();
		boolean activeValue= (oldAttr.getStyle() & styleAttribute) == styleAttribute;

		if (activeValue != eventValue)
			highlighting.setTextAttribute(new TextAttribute(oldAttr.getForeground(), oldAttr.getBackground(), eventValue ? oldAttr.getStyle() | styleAttribute : oldAttr.getStyle() & ~styleAttribute));
	}

	private void addColor(String colorKey) {
		if (fColorManager != null && colorKey != null && fColorManager.getColor(colorKey) == null) {
			RGB rgb= PreferenceConverter.getColor(fPreferenceStore, colorKey);
			if (fColorManager instanceof IColorManagerExtension) {
				IColorManagerExtension ext= (IColorManagerExtension) fColorManager;
				ext.unbindColor(colorKey);
				ext.bindColor(colorKey, rgb);
			}
		}
	}

	private void removeColor(String colorKey) {
		if (fColorManager instanceof IColorManagerExtension)
			((IColorManagerExtension) fColorManager).unbindColor(colorKey);
	}

	/**
	 * Returns this hightlighter's reconciler.
	 *
	 * @return the semantic highlighter reconciler or <code>null</code> if none
	 * @since 3.3
	 */
	public SemanticHighlightingReconciler getReconciler() {
		return fReconciler;
	}
}
