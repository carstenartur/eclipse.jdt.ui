/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.jdt.internal.ui.preferences;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.eclipse.core.runtime.Preferences;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;

import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.model.WorkbenchViewerSorter;
import org.eclipse.ui.texteditor.ChainedPreferenceStore;

import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.IColorManager;
import org.eclipse.jdt.ui.text.JavaSourceViewerConfiguration;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer;
import org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlighting;
import org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlightingManager;
import org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlightings;
import org.eclipse.jdt.internal.ui.javaeditor.SemanticHighlightingManager.HighlightedRange;
import org.eclipse.jdt.internal.ui.text.IJavaPartitions;
import org.eclipse.jdt.internal.ui.text.JavaColorManager;
import org.eclipse.jdt.internal.ui.text.PreferencesAdapter;

/**
 * Configures Java Editor hover preferences.
 * 
 * @since 2.1
 */
class JavaEditorColoringConfigurationBlock extends AbstractConfigurationBlock {
	
	/**
	 * Item in the highlighting color list.
	 * 
	 * @since 3.0
	 */
	private static class HighlightingColorListItem {
		/** Display name */
		private String fDisplayName;
		/** Color preference key */
		private String fColorKey;
		/** Bold preference key */
		private String fBoldKey;
		/** Italic preference key */
		private String fItalicKey;
		/**
		 * Initialize the item with the given values.
		 * @param displayName the display name
		 * @param colorKey the color preference key
		 * @param boldKey the bold preference key
		 * @param italicKey the italic preference key
		 */
		public HighlightingColorListItem(String displayName, String colorKey, String boldKey, String italicKey) {
			fDisplayName= displayName;
			fColorKey= colorKey;
			fBoldKey= boldKey;
			fItalicKey= italicKey;
		}
		
		/**
		 * @return the bold preference key
		 */
		public String getBoldKey() {
			return fBoldKey;
		}
		
		/**
		 * @return the bold preference key
		 */
		public String getItalicKey() {
			return fItalicKey;
		}
		
		/**
		 * @return the color preference key
		 */
		public String getColorKey() {
			return fColorKey;
		}
		
		/**
		 * @return the display name
		 */
		public String getDisplayName() {
			return fDisplayName;
		}
	}

	private static class SemanticHighlightingColorListItem extends HighlightingColorListItem {
	
		/** Enablement preference key */
		private final String fEnableKey;
		
		/**
		 * Initialize the item with the given values.
		 * @param displayName the display name
		 * @param colorKey the color preference key
		 * @param boldKey the bold preference key
		 * @param italicKey the italic preference key
		 * @param enableKey the enable preference key
		 */
		public SemanticHighlightingColorListItem(String displayName, String colorKey, String boldKey, String italicKey, String enableKey) {
			super(displayName, colorKey, boldKey, italicKey);
			fEnableKey= enableKey;
		}
	
		/**
		 * @return the enablement preference key
		 */
		public String getEnableKey() {
			return fEnableKey;
		}
	}

	/**
	 * Color list label provider.
	 * 
	 * @since 3.0
	 */
	private class ColorListLabelProvider extends LabelProvider {
		/*
		 * @see org.eclipse.jface.viewers.ILabelProvider#getText(java.lang.Object)
		 */
		public String getText(Object element) {
			return ((HighlightingColorListItem)element).getDisplayName();
		}
	}

	/**
	 * Color list content provider.
	 * 
	 * @since 3.0
	 */
	private class ColorListContentProvider implements IStructuredContentProvider {
	
		/*
		 * @see org.eclipse.jface.viewers.IStructuredContentProvider#getElements(java.lang.Object)
		 */
		public Object[] getElements(Object inputElement) {
			return ((java.util.List)inputElement).toArray();
		}
	
		/*
		 * @see org.eclipse.jface.viewers.IContentProvider#dispose()
		 */
		public void dispose() {
		}
	
		/*
		 * @see org.eclipse.jface.viewers.IContentProvider#inputChanged(org.eclipse.jface.viewers.Viewer, java.lang.Object, java.lang.Object)
		 */
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}
	}

	private static final String BOLD= PreferenceConstants.EDITOR_BOLD_SUFFIX;
	/**
	 * Preference key suffix for italic preferences.
	 * @since  3.0
	 */
	private static final String ITALIC= PreferenceConstants.EDITOR_ITALIC_SUFFIX;
	private static final String COMPILER_TASK_TAGS= JavaCore.COMPILER_TASK_TAGS;
	/**
	 * The keys of the overlay store. 
	 */
	private final String[][] fSyntaxColorListModel= new String[][] {
			{ PreferencesMessages.getString("JavaEditorPreferencePage.multiLineComment"), PreferenceConstants.EDITOR_MULTI_LINE_COMMENT_COLOR }, //$NON-NLS-1$
			{ PreferencesMessages.getString("JavaEditorPreferencePage.singleLineComment"), PreferenceConstants.EDITOR_SINGLE_LINE_COMMENT_COLOR }, //$NON-NLS-1$
			{ PreferencesMessages.getString("JavaEditorPreferencePage.returnKeyword"), PreferenceConstants.EDITOR_JAVA_KEYWORD_RETURN_COLOR }, //$NON-NLS-1$
			{ PreferencesMessages.getString("JavaEditorPreferencePage.keywords"), PreferenceConstants.EDITOR_JAVA_KEYWORD_COLOR }, //$NON-NLS-1$
			{ PreferencesMessages.getString("JavaEditorPreferencePage.strings"), PreferenceConstants.EDITOR_STRING_COLOR }, //$NON-NLS-1$
			{ PreferencesMessages.getString("JavaEditorPreferencePage.methodNames"), PreferenceConstants.EDITOR_JAVA_METHOD_NAME_COLOR }, //$NON-NLS-1$
			{ PreferencesMessages.getString("JavaEditorPreferencePage.operators"), PreferenceConstants.EDITOR_JAVA_OPERATOR_COLOR }, //$NON-NLS-1$
			{ PreferencesMessages.getString("JavaEditorPreferencePage.others"), PreferenceConstants.EDITOR_JAVA_DEFAULT_COLOR }, //$NON-NLS-1$
			{ PreferencesMessages.getString("JavaEditorPreferencePage.javaCommentTaskTags"), PreferenceConstants.EDITOR_TASK_TAG_COLOR }, //$NON-NLS-1$
			{ PreferencesMessages.getString("JavaEditorPreferencePage.javaDocKeywords"), PreferenceConstants.EDITOR_JAVADOC_KEYWORD_COLOR }, //$NON-NLS-1$
			{ PreferencesMessages.getString("JavaEditorPreferencePage.javaDocHtmlTags"), PreferenceConstants.EDITOR_JAVADOC_TAG_COLOR }, //$NON-NLS-1$
			{ PreferencesMessages.getString("JavaEditorPreferencePage.javaDocLinks"), PreferenceConstants.EDITOR_JAVADOC_LINKS_COLOR }, //$NON-NLS-1$
			{ PreferencesMessages.getString("JavaEditorPreferencePage.javaDocOthers"), PreferenceConstants.EDITOR_JAVADOC_DEFAULT_COLOR } //$NON-NLS-1$
	};
	private ColorEditor fSyntaxForegroundColorEditor;
	private Button fBoldCheckBox;
	private Button fEnableCheckbox;
	/**
	 * Check box for italic preference.
	 * @since  3.0
	 */
	private Button fItalicCheckBox;
	private JavaSourceViewer fPreviewViewer;
	/**
	 * Highlighting color list
	 * @since  3.0
	 */
	private final java.util.List fHighlightingColorList= new ArrayList();
	/**
	 * Highlighting color list viewer
	 * @since  3.0
	 */
	private TableViewer fHighlightingColorListViewer;
	/**
	 * Semantic Highlighting color list
	 * @since  3.0
	 */
	private java.util.List fSemanticHighlightingColorList= new ArrayList();
	/**
	 * Semantic highlighting manager
	 * @since  3.0
	 */
	private SemanticHighlightingManager fSemanticHighlightingManager;
	/**
	 * The color manager.
	 * @since 3.1
	 */
	private IColorManager fColorManager;
	private FontMetrics fFontMetrics;

	public JavaEditorColoringConfigurationBlock(OverlayPreferenceStore store) {
		super(store);
		
		fColorManager= new JavaColorManager(false);
		SemanticHighlighting[] semanticHighlightings= SemanticHighlightings.getSemanticHighlightings();
		for (int i= 0, n= semanticHighlightings.length; i < n; i++)
			fSemanticHighlightingColorList.add(new SemanticHighlightingColorListItem(semanticHighlightings[i].getDisplayName(), SemanticHighlightings.getColorPreferenceKey(semanticHighlightings[i]), SemanticHighlightings.getBoldPreferenceKey(semanticHighlightings[i]), SemanticHighlightings.getItalicPreferenceKey(semanticHighlightings[i]), SemanticHighlightings.getEnabledPreferenceKey(semanticHighlightings[i])));

		store.addKeys(createOverlayStoreKeys());
	}

	private OverlayPreferenceStore.OverlayKey[] createOverlayStoreKeys() {
		
		ArrayList overlayKeys= new ArrayList();
		
		for (int i= 0; i < fSyntaxColorListModel.length; i++) {
			String colorKey= fSyntaxColorListModel[i][1];
			overlayKeys.add(new OverlayPreferenceStore.OverlayKey(OverlayPreferenceStore.STRING, colorKey));
			overlayKeys.add(new OverlayPreferenceStore.OverlayKey(OverlayPreferenceStore.BOOLEAN, colorKey + BOLD));
			overlayKeys.add(new OverlayPreferenceStore.OverlayKey(OverlayPreferenceStore.BOOLEAN, colorKey + ITALIC));
		}
		
		for (int i= 0, n= fSemanticHighlightingColorList.size(); i < n; i++) {
			SemanticHighlightingColorListItem item= (SemanticHighlightingColorListItem) fSemanticHighlightingColorList.get(i);
			overlayKeys.add(new OverlayPreferenceStore.OverlayKey(OverlayPreferenceStore.STRING, item.getColorKey()));
			overlayKeys.add(new OverlayPreferenceStore.OverlayKey(OverlayPreferenceStore.BOOLEAN, item.getBoldKey()));
			overlayKeys.add(new OverlayPreferenceStore.OverlayKey(OverlayPreferenceStore.BOOLEAN, item.getItalicKey()));
			overlayKeys.add(new OverlayPreferenceStore.OverlayKey(OverlayPreferenceStore.BOOLEAN, item.getEnableKey()));
		}
		
		OverlayPreferenceStore.OverlayKey[] keys= new OverlayPreferenceStore.OverlayKey[overlayKeys.size()];
		overlayKeys.toArray(keys);
		return keys;
	}

	/**
	 * Creates page for hover preferences.
	 * 
	 * @param parent the parent composite
	 * @return the control for the preference page
	 */
	public Control createControl(Composite parent) {
		initializeDialogUnits(parent);
		return createSyntaxPage(parent);
	}
	
	/**
     * Returns the number of pixels corresponding to the width of the given
     * number of characters.
     * <p>
     * This method may only be called after <code>initializeDialogUnits</code>
     * has been called.
     * </p>
     * <p>
     * Clients may call this framework method, but should not override it.
     * </p>
     * 
     * @param chars
     *            the number of characters
     * @return the number of pixels
     */
    private int convertWidthInCharsToPixels(int chars) {
        // test for failure to initialize for backward compatibility
        if (fFontMetrics == null)
            return 0;
        return Dialog.convertWidthInCharsToPixels(fFontMetrics, chars);
    }

	/**
     * Returns the number of pixels corresponding to the height of the given
     * number of characters.
     * <p>
     * This method may only be called after <code>initializeDialogUnits</code>
     * has been called.
     * </p>
     * <p>
     * Clients may call this framework method, but should not override it.
     * </p>
     * 
     * @param chars
     *            the number of characters
     * @return the number of pixels
     */
    private int convertHeightInCharsToPixels(int chars) {
        // test for failure to initialize for backward compatibility
        if (fFontMetrics == null)
            return 0;
        return Dialog.convertHeightInCharsToPixels(fFontMetrics, chars);
    }
    
	public void initialize() {
		super.initialize();
		
		for (int i= 0, n= fSyntaxColorListModel.length; i < n; i++)
			fHighlightingColorList.add(new HighlightingColorListItem (fSyntaxColorListModel[i][0], fSyntaxColorListModel[i][1], fSyntaxColorListModel[i][1] + BOLD, fSyntaxColorListModel[i][1] + ITALIC));
		fHighlightingColorList.addAll(fSemanticHighlightingColorList);
		fHighlightingColorListViewer.setInput(fHighlightingColorList);
		fHighlightingColorListViewer.setSelection(new StructuredSelection(fHighlightingColorListViewer.getElementAt(0)));
	}

	public void performDefaults() {
		super.performDefaults();
		
		handleSyntaxColorListSelection();

		uninstallSemanticHighlighting();
		installSemanticHighlighting();

		fPreviewViewer.invalidateTextPresentation();
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.preferences.IPreferenceConfigurationBlock#dispose()
	 */
	public void dispose() {
		uninstallSemanticHighlighting();
		fColorManager.dispose();
		
		super.dispose();
	}

	private void handleSyntaxColorListSelection() {
		HighlightingColorListItem item= getHighlightingColorListItem();
		if (item == null)
			return;
		RGB rgb= PreferenceConverter.getColor(getPreferenceStore(), item.getColorKey());
		fSyntaxForegroundColorEditor.setColorValue(rgb);		
		fBoldCheckBox.setSelection(getPreferenceStore().getBoolean(item.getBoldKey()));
		fItalicCheckBox.setSelection(getPreferenceStore().getBoolean(item.getItalicKey()));
		if (item instanceof SemanticHighlightingColorListItem) {
			fEnableCheckbox.setEnabled(true);
			boolean enable= getPreferenceStore().getBoolean(((SemanticHighlightingColorListItem) item).getEnableKey());
			fEnableCheckbox.setSelection(enable);
			fSyntaxForegroundColorEditor.getButton().setEnabled(enable);
			fBoldCheckBox.setEnabled(enable);
			fItalicCheckBox.setEnabled(enable);
		} else {
			fSyntaxForegroundColorEditor.getButton().setEnabled(true);
			fBoldCheckBox.setEnabled(true);
			fItalicCheckBox.setEnabled(true);
			fEnableCheckbox.setEnabled(false);
			fEnableCheckbox.setSelection(true);
		}
	}

	private Control createSyntaxPage(Composite parent) {
		
		Composite colorComposite= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		colorComposite.setLayout(layout);
	
		Label label;
		label= new Label(colorComposite, SWT.LEFT);
		label.setText(PreferencesMessages.getString("JavaEditorPreferencePage.coloring.element")); //$NON-NLS-1$
		label.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
	
		Composite editorComposite= new Composite(colorComposite, SWT.NONE);
		layout= new GridLayout();
		layout.numColumns= 2;
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		editorComposite.setLayout(layout);
		GridData gd= new GridData(GridData.FILL_BOTH);
		editorComposite.setLayoutData(gd);		
	
		fHighlightingColorListViewer= new TableViewer(editorComposite, SWT.SINGLE | SWT.V_SCROLL | SWT.BORDER | SWT.FULL_SELECTION);
		fHighlightingColorListViewer.setLabelProvider(new ColorListLabelProvider());
		fHighlightingColorListViewer.setContentProvider(new ColorListContentProvider());
		fHighlightingColorListViewer.setSorter(new WorkbenchViewerSorter());
		gd= new GridData(SWT.BEGINNING, SWT.FILL, false, true);
		gd.heightHint= convertHeightInCharsToPixels(5);
		fHighlightingColorListViewer.getControl().setLayoutData(gd);
						
		Composite stylesComposite= new Composite(editorComposite, SWT.NONE);
		layout= new GridLayout();
		layout.marginHeight= 0;
		layout.marginWidth= 0;
		layout.numColumns= 2;
		stylesComposite.setLayout(layout);
		stylesComposite.setLayoutData(new GridData(GridData.FILL_BOTH));
		
		fEnableCheckbox= new Button(stylesComposite, SWT.CHECK);
		fEnableCheckbox.setText(PreferencesMessages.getString("JavaEditorPreferencePage.enable")); //$NON-NLS-1$
		gd= new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalAlignment= GridData.BEGINNING;
		gd.horizontalSpan= 2;
		fEnableCheckbox.setLayoutData(gd);
		
		label= new Label(stylesComposite, SWT.LEFT);
		label.setText(PreferencesMessages.getString("JavaEditorPreferencePage.color")); //$NON-NLS-1$
		gd= new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
		gd.horizontalIndent= 20;
		label.setLayoutData(gd);
	
		fSyntaxForegroundColorEditor= new ColorEditor(stylesComposite);
		Button foregroundColorButton= fSyntaxForegroundColorEditor.getButton();
		gd= new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
		foregroundColorButton.setLayoutData(gd);
		
		fBoldCheckBox= new Button(stylesComposite, SWT.CHECK);
		fBoldCheckBox.setText(PreferencesMessages.getString("JavaEditorPreferencePage.bold")); //$NON-NLS-1$
		gd= new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
		gd.horizontalIndent= 20;
		gd.horizontalSpan= 2;
		fBoldCheckBox.setLayoutData(gd);
		
		fItalicCheckBox= new Button(stylesComposite, SWT.CHECK);
		fItalicCheckBox.setText(PreferencesMessages.getString("JavaEditorPreferencePage.italic")); //$NON-NLS-1$
		gd= new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
		gd.horizontalIndent= 20;
		gd.horizontalSpan= 2;
		fItalicCheckBox.setLayoutData(gd);
		
		label= new Label(colorComposite, SWT.LEFT);
		label.setText(PreferencesMessages.getString("JavaEditorPreferencePage.preview")); //$NON-NLS-1$
		label.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		Control previewer= createPreviewer(colorComposite);
		gd= new GridData(GridData.FILL_BOTH);
		gd.widthHint= convertWidthInCharsToPixels(20);
		gd.heightHint= convertHeightInCharsToPixels(5);
		previewer.setLayoutData(gd);
		
		fHighlightingColorListViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				handleSyntaxColorListSelection();
			}
		});
		
		foregroundColorButton.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
				// do nothing
			}
			public void widgetSelected(SelectionEvent e) {
				HighlightingColorListItem item= getHighlightingColorListItem();
				PreferenceConverter.setValue(getPreferenceStore(), item.getColorKey(), fSyntaxForegroundColorEditor.getColorValue());
			}
		});
	
		fBoldCheckBox.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
				// do nothing
			}
			public void widgetSelected(SelectionEvent e) {
				HighlightingColorListItem item= getHighlightingColorListItem();
				getPreferenceStore().setValue(item.getBoldKey(), fBoldCheckBox.getSelection());
			}
		});
				
		fItalicCheckBox.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
				// do nothing
			}
			public void widgetSelected(SelectionEvent e) {
				HighlightingColorListItem item= getHighlightingColorListItem();
				getPreferenceStore().setValue(item.getItalicKey(), fItalicCheckBox.getSelection());
			}
		});
				
		fEnableCheckbox.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
				// do nothing
			}
			public void widgetSelected(SelectionEvent e) {
				HighlightingColorListItem item= getHighlightingColorListItem();
				if (item instanceof SemanticHighlightingColorListItem) {
					boolean enable= fEnableCheckbox.getSelection();
					getPreferenceStore().setValue(((SemanticHighlightingColorListItem) item).getEnableKey(), enable);
					fEnableCheckbox.setSelection(enable);
					fSyntaxForegroundColorEditor.getButton().setEnabled(enable);
					fBoldCheckBox.setEnabled(enable);
					fItalicCheckBox.setEnabled(enable);
					uninstallSemanticHighlighting();
					installSemanticHighlighting();
				}
			}
		});
		
		colorComposite.layout(false);
				
		return colorComposite;
	}

	private Control createPreviewer(Composite parent) {
		
		IPreferenceStore generalTextStore= EditorsUI.getPreferenceStore();
		IPreferenceStore store= new ChainedPreferenceStore(new IPreferenceStore[] { getPreferenceStore(), new PreferencesAdapter(createTemporaryCorePreferenceStore()), generalTextStore });
		fPreviewViewer= new JavaSourceViewer(parent, null, null, false, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER, store);
		JavaSourceViewerConfiguration configuration= new JavaSourceViewerConfiguration(fColorManager, store, null, IJavaPartitions.JAVA_PARTITIONING);
		fPreviewViewer.configure(configuration);
		Font font= JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT);
		fPreviewViewer.getTextWidget().setFont(font);
		new JavaSourcePreviewerUpdater(fPreviewViewer, configuration, store);
		fPreviewViewer.setEditable(false);
		
		String content= loadPreviewContentFromFile("ColorSettingPreviewCode.txt"); //$NON-NLS-1$
		IDocument document= new Document(content);
		JavaPlugin.getDefault().getJavaTextTools().setupJavaDocumentPartitioner(document, IJavaPartitions.JAVA_PARTITIONING);
		fPreviewViewer.setDocument(document);
	
		installSemanticHighlighting();
		
		return fPreviewViewer.getControl();
	}


	private Preferences createTemporaryCorePreferenceStore() {
		Preferences result= new Preferences();
		
		result.setValue(COMPILER_TASK_TAGS, "TASK,TODO"); //$NON-NLS-1$
		
		return result;
	}


	private String loadPreviewContentFromFile(String filename) {
		String line;
		String separator= System.getProperty("line.separator"); //$NON-NLS-1$
		StringBuffer buffer= new StringBuffer(512);
		BufferedReader reader= null;
		try {
			reader= new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(filename)));
			while ((line= reader.readLine()) != null) {
				buffer.append(line);
				buffer.append(separator);
			}
		} catch (IOException io) {
			JavaPlugin.log(io);
		} finally {
			if (reader != null) {
				try { reader.close(); } catch (IOException e) {}
			}
		}
		return buffer.toString();
	}


	/**
	 * Install Semantic Highlighting on the previewer
	 * 
	 * @since 3.0
	 */
	private void installSemanticHighlighting() {
		if (fSemanticHighlightingManager == null) {
			fSemanticHighlightingManager= new SemanticHighlightingManager();
			fSemanticHighlightingManager.install(fPreviewViewer, fColorManager, getPreferenceStore(), createPreviewerRanges());
		}
	}


	/**
	 * Uninstall Semantic Highlighting from the previewer
	 * 
	 * @since 3.0
	 */
	private void uninstallSemanticHighlighting() {
		if (fSemanticHighlightingManager != null) {
			fSemanticHighlightingManager.uninstall();
			fSemanticHighlightingManager= null;
		}
	}


	/**
	 * Create the hard coded previewer ranges
	 * 
	 * @return the hard coded previewer ranges
	 * @since 3.0
	 */
	private SemanticHighlightingManager.HighlightedRange[][] createPreviewerRanges() {
		return new SemanticHighlightingManager.HighlightedRange[][] {
			{ createHighlightedRange(6, 13, 9, SemanticHighlightings.DEPRECATED_MEMBER) },
			{ createHighlightedRange(7, 26, 8, SemanticHighlightings.STATIC_FINAL_FIELD), createHighlightedRange(7, 26, 8, SemanticHighlightings.STATIC_FIELD), createHighlightedRange(7, 26, 8, SemanticHighlightings.FIELD) },
			{ createHighlightedRange(9, 20, 11, SemanticHighlightings.STATIC_FIELD), createHighlightedRange(9, 20, 11, SemanticHighlightings.FIELD) },
			{ createHighlightedRange(11, 16, 5, SemanticHighlightings.FIELD) },
			{ createHighlightedRange(13, 12, 3, SemanticHighlightings.METHOD_DECLARATION) },
			{ createHighlightedRange(13, 20, 9, SemanticHighlightings.PARAMETER_VARIABLE) },
			{ createHighlightedRange(14, 2, 14, SemanticHighlightings.ABSTRACT_METHOD_INVOCATION) },
			{ createHighlightedRange(15, 6, 5, SemanticHighlightings.LOCAL_VARIABLE_DECLARATION) },
			{ createHighlightedRange(15, 16, 8, SemanticHighlightings.INHERITED_METHOD_INVOCATION) },
			{ createHighlightedRange(16, 2, 12, SemanticHighlightings.STATIC_METHOD_INVOCATION) },
			{ createHighlightedRange(17, 13, 5, SemanticHighlightings.LOCAL_VARIABLE) }
		};
	}


	/**
	 * Create a highlighted range on the previewers document with the given line, column, length and key.
	 * 
	 * @param line the line
	 * @param column the column
	 * @param length the length
	 * @param key the key
	 * @return the highlighted range
	 * @since 3.0
	 */
	private HighlightedRange createHighlightedRange(int line, int column, int length, String key) {
		try {
			IDocument document= fPreviewViewer.getDocument();
			int offset= document.getLineOffset(line) + column;
			return new HighlightedRange(offset, length, key);
		} catch (BadLocationException x) {
			JavaPlugin.log(x);
		}
		return null;
	}


	/**
	 * Returns the current highlighting color list item.
	 * 
	 * @return the current highlighting color list item
	 * @since 3.0
	 */
	private HighlightingColorListItem getHighlightingColorListItem() {
		IStructuredSelection selection= (IStructuredSelection) fHighlightingColorListViewer.getSelection();
		return (HighlightingColorListItem) selection.getFirstElement();
	}
	
	/**
     * Initializes the computation of horizontal and vertical dialog units based
     * on the size of current font.
     * <p>
     * This method must be called before any of the dialog unit based conversion
     * methods are called.
     * </p>
     * 
     * @param testControl
     *            a control from which to obtain the current font
     */
    private void initializeDialogUnits(Control testControl) {
        // Compute and store a font metric
        GC gc = new GC(testControl);
        gc.setFont(JFaceResources.getDialogFont());
        fFontMetrics = gc.getFontMetrics();
        gc.dispose();
    }
}
