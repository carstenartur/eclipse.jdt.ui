/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.text.tests.formatter.comment;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextUtilities;

import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

import org.eclipse.jdt.internal.corext.Assert; // TODO: replace with org.eclipse.jdt.internal.core.Assert
import org.eclipse.jdt.internal.corext.text.comment.CommentFormatterPreferenceConstants;
import org.eclipse.jdt.internal.corext.text.comment.CommentFormatterConstants;
import org.eclipse.jdt.internal.corext.text.comment.ITextMeasurement;
import org.eclipse.jdt.internal.corext.text.comment.ToolFactory;

/**
 * Utilities for the comment formatter.
 * 
 * @since 3.0
 */
public class CommentFormatterUtil {

	/**
	 * Comment formatter preference keys.
	 * @since 3.1
	 */
	private static final String[] PREFERENCE_KEYS= new String[] { 
		CommentFormatterPreferenceConstants.FORMATTER_COMMENT_FORMAT, 
		CommentFormatterPreferenceConstants.FORMATTER_COMMENT_FORMATHEADER, 
		CommentFormatterPreferenceConstants.FORMATTER_COMMENT_FORMATSOURCE, 
		CommentFormatterPreferenceConstants.FORMATTER_COMMENT_INDENTPARAMETERDESCRIPTION, 
		CommentFormatterPreferenceConstants.FORMATTER_COMMENT_INDENTROOTTAGS, 
		CommentFormatterPreferenceConstants.FORMATTER_COMMENT_NEWLINEFORPARAMETER, 
		CommentFormatterPreferenceConstants.FORMATTER_COMMENT_SEPARATEROOTTAGS, 
		CommentFormatterPreferenceConstants.FORMATTER_COMMENT_LINELENGTH, 
		CommentFormatterPreferenceConstants.FORMATTER_COMMENT_CLEARBLANKLINES, 
		CommentFormatterPreferenceConstants.FORMATTER_COMMENT_FORMATHTML
	};
	
	/**
	 * Formats the source string as a comment region of the specified kind.
	 * <p>
	 * Both offset and length must denote a valid comment partition, that is
	 * to say a substring that starts and ends with the corresponding
	 * comment delimiter tokens.
	 * 
	 * @param kind the kind of the comment
	 * @param source the source string to format
	 * @param offset the offset relative to the source string where to
	 *                format
	 * @param length the length of the region in the source string to format
	 * @param preferences preferences for the comment formatter
	 * @param textMeasurement optional text measurement for font specific
	 *                formatting, can be <code>null</code>
	 * @return the formatted source string
	 */
	public static String format(int kind, String source, int offset, int length, Map preferences, ITextMeasurement textMeasurement) {
		Assert.isTrue(kind == CommentFormatterConstants.K_JAVA_DOC || kind == CommentFormatterConstants.K_MULTI_LINE_COMMENT || kind == CommentFormatterConstants.K_SINGLE_LINE_COMMENT);

		Assert.isNotNull(source);
		Assert.isNotNull(preferences);

		Assert.isTrue(offset >= 0);
		Assert.isTrue(length <= source.length());

		IDocument document= new Document(source);
		
		TextEdit edit;
		try {
			int indentOffset= document.getLineOffset(document.getLineOfOffset(offset));
			int indentationLevel= inferIndentationLevel(document.get(indentOffset, offset - indentOffset), getTabSize(preferences), textMeasurement);
			edit= ToolFactory.createCommentFormatter(textMeasurement, preferences).format(kind, source, offset, length, indentationLevel, TextUtilities.getDefaultLineDelimiter(document));
		} catch (BadLocationException x) {
			throw new RuntimeException(x);
		}
		
		try {
			if (edit != null)
				edit.apply(document);
		} catch (MalformedTreeException x) {
			throw new RuntimeException(x);
		} catch (BadLocationException x) {
			throw new RuntimeException(x);
		}
		return document.get();
	}
	
	/**
	 * Infer the indentation level based on the given reference indentation,
	 * tab size and text measurement.
	 * 
	 * @param reference the reference indentation
	 * @param tabSize the tab size
	 * @param textMeasurement the text measurement
	 * @return the inferred indentation level
	 * @since 3.1
	 */
	private static int inferIndentationLevel(String reference, int tabSize, ITextMeasurement textMeasurement) {
		StringBuffer expanded= expandTabs(reference, tabSize);
		
		int spaceWidth, referenceWidth;
		if (textMeasurement != null) {
			spaceWidth= textMeasurement.computeWidth(" "); //$NON-NLS-1$
			referenceWidth= textMeasurement.computeWidth(expanded.toString());
		} else {
			spaceWidth= 1;
			referenceWidth= expanded.length();
		}
		
		int level= referenceWidth / (tabSize * spaceWidth);
		if (referenceWidth % (tabSize * spaceWidth) > 0)
			level++;
		return level;
	}
	
	/**
	 * Expands the given string's tabs according to the given tab size.
	 * 
	 * @param string the string
	 * @param tabSize the tab size
	 * @return the expanded string
	 * @since 3.1
	 */
	private static StringBuffer expandTabs(String string, int tabSize) {
		StringBuffer expanded= new StringBuffer();
		for (int i= 0, n= string.length(), chars= 0; i < n; i++) {
			char ch= string.charAt(i);
			if (ch == '\t') {
				for (; chars < tabSize; chars++)
					expanded.append(' ');
				chars= 0;
			} else {
				expanded.append(ch);
				chars++;
				if (chars >= tabSize)
					chars= 0;
			}
		
		}
		return expanded;
	}

	/**
	 * Returns the value of {@link DefaultCodeFormatterConstants#FORMATTER_TAB_SIZE}
	 * from the given preferences.
	 * 
	 * @param preferences the preferences
	 * @return the value of {@link DefaultCodeFormatterConstants#FORMATTER_TAB_SIZE}
	 *         from the given preferences
	 * @since 3.1
	 */
	private static int getTabSize(Map preferences) {
		if (preferences.containsKey(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE))
			try {
				return Integer.parseInt((String) preferences.get(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE));
			} catch (NumberFormatException e) {
				// use default
			}
		return 4;
	}
	
	/**
	 * @param key the preference key
	 * @return <code>true</code> iff the corresponding preference is of boolean type
	 * @since 3.1
	 */
	private static boolean isBooleanPreference(String key) {
		return !key.equals(CommentFormatterPreferenceConstants.FORMATTER_COMMENT_LINELENGTH);
	}

	/**
	 * @param key the preference key
	 * @return <code>true</code> iff the corresponding preference is of integer type
	 * @since 3.1
	 */
	private static boolean isIntegerPreference(String key) {
		return key.equals(CommentFormatterPreferenceConstants.FORMATTER_COMMENT_LINELENGTH);
	}

	/**
	 * Creates a formatting options with all default options and the given custom user options.
	 * 
	 * @param user the custom user options
	 * @return the formatting options
	 * @since 3.1
	 */
	public static Map createOptions(Map user) {
		final Map map= new HashMap();
		final String[] keys= PREFERENCE_KEYS;

		for (int index= 0; index < keys.length; index++) {

			if (isBooleanPreference(keys[index]))
				map.put(keys[index], Boolean.toString(true));
			else if (isIntegerPreference(keys[index]))
				map.put(keys[index], "80"); //$NON-NLS-1$
		}

		if (user != null) {

			for (final Iterator iterator= user.keySet().iterator(); iterator.hasNext();) {

				Object key= iterator.next();
				map.put(key, user.get(key));
			}
		}
		return map;
	}
}
