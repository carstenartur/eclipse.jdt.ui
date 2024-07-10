/*******************************************************************************
 * Copyright (c) 2008, 2024 IBM Corporation and others.
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
 *     Stephan Herrmann - Contribution for Bug 403917 - [1.8] Render TYPE_USE annotations in Javadoc hover/view
 *     Jozef Tomek - add styling enhancements (issue 1073)
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.viewsupport;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;

import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationAdapter;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.LocationListener;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

import org.eclipse.core.runtime.ListenerList;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;

import org.eclipse.jdt.core.BindingKey;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.IBinding;

import org.eclipse.jdt.internal.core.manipulation.util.Strings;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaUIMessages;


/**
 * Links inside Javadoc hovers and Javadoc view.
 *
 * @since 3.4
 */
public class JavaElementLinks {

	private static final String CHECKBOX_FORMATTING_ID = "formattingSwitch"; //$NON-NLS-1$
	// browser on windows changes single quotes to double quotes (other platforms don't do that) thus we use / primarily search for this one
	private static final String CHECKBOX_FORMATTING_ID_ATTR_DQUOTES= "id=\"" + CHECKBOX_FORMATTING_ID + "\""; //$NON-NLS-1$ //$NON-NLS-2$
	private static final String CHECKBOX_FORMATTING_ID_ATTR_SQUOTES= "id='" + CHECKBOX_FORMATTING_ID + "'"; //$NON-NLS-1$ //$NON-NLS-2$

	private static final String PREFERENCE_KEY_POSTFIX_TYPE_PARAMETERS_REFERENCES_COLORING= "javadocElementsStyling.typeParamsReferencesColoring"; //$NON-NLS-1$

	private static final String PREFERENCE_KEY_ENABLED= "javadocElementsStyling.enabled"; //$NON-NLS-1$
	private static final String PREFERENCE_KEY_DARK_MODE_DEFAULT_COLORS= "javadocElementsStyling.darkModeDefaultColors"; //$NON-NLS-1$
	// uses 1-based indexing
	private static final String PREFERENCE_KEY_PREFIX_TYPE_PARAMETERS_REFERENCE_COLOR= "javadocElementsStyling.typesParamsReferenceColor_"; //$NON-NLS-1$
	/**
	 * Maximum number of type parameters references for which we support setting custom color
	 */
	private static final int MAX_COLOR_INDEX= 16;


	private static final String CSS_CLASS_SWITCH_PARENT= "styleSwitchParent"; //$NON-NLS-1$
	// both use 1-based indexing
	private static final String CSS_CLASS_TYPE_PARAMETERS_REFERENCE_PREFIX= "typeParamsReference typeParamsReferenceNo"; //$NON-NLS-1$

	private static final String CSS_SECTION_START_TYPE_PARAMETERS_REFERENCES= "/* Start of dynamic type parameters references styling section (do not edit this line) */"; //$NON-NLS-1$
	private static final String CSS_SECTION_END_TYPE_PARAMETERS_REFERENCES= "/* End of dynamic type parameters references styling section (do not edit this line) */"; //$NON-NLS-1$
	private static final String CSS_PLACEHOLDER_INDEX= "-INDEX-"; //$NON-NLS-1$
	private static final String CSS_PLACEHOLDER_COLOR= "-COLOR-"; //$NON-NLS-1$


	private static String[] CSS_FRAGMENTS_CACHE_TYPE_PARAMETERS_REFERENCES= new String[4];
	private static final ReentrantLock CSS_FRAGMENTS_CACHE_LOCK= new ReentrantLock();
	private static final IPropertyChangeListener ENHANCEMENTS_PROPERTIES_CHANGE_LISTENER= JavaElementLinks::enhancementsSettingsChangeListener;
	private static final IPropertyChangeListener COLOR_PROPERTIES_CHANGE_LISTENER= JavaElementLinks::cssFragmentsCacheResetListener;
	private static final ListenerList<IStylingConfigurationListener> CONFIG_LISTENERS = new ListenerList<>();

//	private static boolean

	/**
	 * A handler is asked to handle links to targets.
	 *
	 * @see JavaElementLinks#createLocationListener(JavaElementLinks.ILinkHandler)
	 */
	public interface ILinkHandler {

		/**
		 * Handle normal kind of link to given target.
		 *
		 * @param target the target to show
		 */
		void handleInlineJavadocLink(IJavaElement target);

		/**
		 * Handle link to given target to open in javadoc view.
		 *
		 * @param target the target to show
		 */
		void handleJavadocViewLink(IJavaElement target);

		/**
		 * Handle link to given target to open its declaration
		 *
		 * @param target the target to show
		 */
		void handleDeclarationLink(IJavaElement target);

		/**
		 * Handle link to given URL to open in browser.
		 *
		 * @param url the url to show
		 * @param display the current display
		 * @return <code>true</code> if the handler could open the link <code>false</code> if the
		 *         browser should follow the link
		 */
		boolean handleExternalLink(URL url, Display display);

		/**
		 * Informs the handler that the text of the browser was set.
		 */
		void handleTextSet();
	}

	static class JavaElementLinkedLabelComposer extends JavaElementLabelComposer {
		private final IJavaElement fElement;
		private final boolean noEnhancements;
		private final boolean enableFormatting;
		private final boolean enableTypeParamsColoring;

		private boolean appendHoverParent= true;
		private int nextNestingLevel= 1;
		private Map<String, Integer> typesIds= new TreeMap<>();
		private int nextTypeNo= 1;
		private int nextParamNo= 1;
		private boolean appendingMethodQualification= false;
		private boolean typeStyleClassApplied= false;
		private boolean inBoundedTypeParam= false;

		public JavaElementLinkedLabelComposer(IJavaElement member, StringBuffer buf) {
			this(member, buf, false);
		}

		public JavaElementLinkedLabelComposer(IJavaElement member, StringBuffer buf, boolean useEnhancements) {
			super(buf);
			if (member instanceof IPackageDeclaration) {
				fElement= member.getAncestor(IJavaElement.PACKAGE_FRAGMENT);
			} else {
				fElement= member;
			}
			if (getStylingEnabledPreference() && useEnhancements) {
				noEnhancements= false;
				enableFormatting= true;
				enableTypeParamsColoring= getPreferenceForTypeParamsColoring();
			} else {
				noEnhancements= true;
				enableFormatting= enableTypeParamsColoring= false;
			}
		}

		@Override
		public String getElementName(IJavaElement element) {
			if (element instanceof IPackageFragment || element instanceof IPackageDeclaration) {
				return getPackageFragmentElementName(element);
			}

			String elementName= element.getElementName();
			return getElementName(element, elementName);
		}

		private String getElementName(IJavaElement element, String elementName) {
			if (element.equals(fElement)) { // linking to the member itself would be a no-op
				return elementName;
			}
			if (elementName.length() == 0) { // anonymous or lambda
				return elementName;
			}
			try {
				String uri= createURI(JAVADOC_SCHEME, element);
				return createHeaderLink(uri, elementName);
			} catch (URISyntaxException e) {
				JavaPlugin.log(e);
				return elementName;
			}
		}

		private String getPackageFragmentElementName(IJavaElement javaElement) {
			IPackageFragmentRoot root= (IPackageFragmentRoot) javaElement.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
			String javaElementName= javaElement.getElementName();
			String packageName= null;
			StringBuilder strBuffer= new StringBuilder();

			for (String lastSegmentName : javaElementName.split("\\.")) { //$NON-NLS-1$
				if (packageName != null) {
					strBuffer.append('.');
					packageName= packageName + '.' + lastSegmentName;
				} else {
					packageName= lastSegmentName;
				}
				IPackageFragment subFragment= root.getPackageFragment(packageName);
				strBuffer.append(getElementName(subFragment, lastSegmentName));
			}

			return strBuffer.toString();
		}

		@Override
		protected void appendGT() {
			if (noEnhancements) {
				fBuffer.append("&gt;"); //$NON-NLS-1$
			} else {
				fBuffer.append("</span>"); // close 'typeParams' span //$NON-NLS-1$
				fBuffer.append("<span class='typeBrackets typeBracketsEnd'>&gt;</span>"); //$NON-NLS-1$
				nextNestingLevel--;
			}

		}

		@Override
		protected void appendLT() {
			if (noEnhancements) {
				fBuffer.append("&lt;"); //$NON-NLS-1$
			} else {
				fBuffer.append("<span class='typeBrackets typeBracketsStart'>&lt;</span>"); //$NON-NLS-1$
				fBuffer.append("<span class='typeParams'>"); //$NON-NLS-1$
				nextNestingLevel++;
			}
		}

		@Override
		protected String getSimpleTypeName(IJavaElement enclosingElement, String typeSig) {
			String typeName= super.getSimpleTypeName(enclosingElement, typeSig);

			String title= ""; //$NON-NLS-1$
			String qualifiedName= Signature.toString(Signature.getTypeErasure(typeSig));
			int qualifierLength= qualifiedName.length() - typeName.length() - 1;
			if (qualifierLength > 0) {
				if (qualifiedName.endsWith(typeName)) {
					title= qualifiedName.substring(0, qualifierLength);
					title= Messages.format(JavaUIMessages.JavaElementLinks_title, title);
				} else {
					title= qualifiedName; // Not expected. Just show the whole qualifiedName.
				}
			}

			String retVal= typeName;
			try {
				String uri= createURI(JAVADOC_SCHEME, enclosingElement, qualifiedName, null, null);
				retVal= createHeaderLink(uri, typeName, title);
			} catch (URISyntaxException e) {
				JavaPlugin.log(e);
			}

			if (!noEnhancements && !inBoundedTypeParam) {
				if ((Signature.getTypeSignatureKind(typeSig) == Signature.TYPE_VARIABLE_SIGNATURE && !typeStyleClassApplied)
						|| (Signature.getTypeSignatureKind(typeSig) == Signature.CLASS_TYPE_SIGNATURE && nextNestingLevel > 1)) {
					return wrapWithTypeClass(typeName, retVal);
				} else {
					return retVal;
				}
			} else {
				return retVal;
			}
		}

		private String wrapWithTypeClass(String typeName, String value) {
			return "<span class='" //$NON-NLS-1$
					+ getTypeStylingClass(typeName) + "'>" //$NON-NLS-1$
					+ value
					+ "</span>"; //$NON-NLS-1$
		}

		private String getTypeStylingClass(String typeName) {
			Integer typeId;
			if ((typeId= typesIds.putIfAbsent(typeName, nextTypeNo)) == null) {
				typeId= nextTypeNo++;
			}
			return CSS_CLASS_TYPE_PARAMETERS_REFERENCE_PREFIX + typeId;
		}

		@Override
		protected String getMemberName(IJavaElement enclosingElement, String typeName, String memberName) {
			try {
				String uri= createURI(JAVADOC_SCHEME, enclosingElement, typeName, memberName, null);
				return createHeaderLink(uri, memberName);
			} catch (URISyntaxException e) {
				JavaPlugin.log(e);
				return memberName;
			}
		}

		@Override
		protected void appendAnnotationLabels(IAnnotation[] annotations, long flags) throws JavaModelException {
			fBuffer.append("<span style='font-weight:normal;'>"); //$NON-NLS-1$
			super.appendAnnotationLabels(annotations, flags);
			fBuffer.append("</span>"); //$NON-NLS-1$
		}

		@Override
		public void appendElementLabel(IJavaElement element, long flags) {
			if (noEnhancements) {
				super.appendElementLabel(element, flags);
				return;
			}
			if (appendingMethodQualification) {
				// method label contains nested method label (eg. lambdas), we need to end method qualification <span> if started
				fBuffer.append("</span>"); //$NON-NLS-1$
				appendingMethodQualification= false;
			}
			if (appendHoverParent) {
				appendHoverParent= false;

				// formatting checkbox
				fBuffer.append("<input type='checkbox' " + CHECKBOX_FORMATTING_ID_ATTR_DQUOTES + " "); //$NON-NLS-1$ //$NON-NLS-2$
				if (enableFormatting) {
					fBuffer.append("checked=true "); //$NON-NLS-1$
				}
				fBuffer.append("style='position: absolute; top: 18px; left: -23px;'/>"); //$NON-NLS-1$

				// typeParametersColoring checkbox
				fBuffer.append("<input type='checkbox' id='typeParamsRefsColoringSwitch' "); //$NON-NLS-1$
				if (enableTypeParamsColoring) {
					fBuffer.append("checked=true "); //$NON-NLS-1$
				}
				fBuffer.append("style='position: absolute; top: 32px; left: -23px;'/>"); //$NON-NLS-1$

				// encompassing <span> for everything styled based on checkboxes checked state
				fBuffer.append("<span class='" + CSS_CLASS_SWITCH_PARENT + "'>"); //$NON-NLS-1$ //$NON-NLS-2$

				// actual signature content
				super.appendElementLabel(element, flags);

				fBuffer.append("</span>"); //$NON-NLS-1$
				appendHoverParent= true;
			} else {
				super.appendElementLabel(element, flags);
			}
		}

		@Override
		protected void appendMethodPrependedTypeParams(IMethod method, long flags, BindingKey resolvedKey, String resolvedSignature) throws JavaModelException {
			if (noEnhancements) {
				super.appendMethodPrependedTypeParams(method, flags, resolvedKey, resolvedSignature);
			} else {
				fBuffer.append("<span class='methodPrependTypeParams'>"); //$NON-NLS-1$
				super.appendMethodPrependedTypeParams(method, flags, resolvedKey, resolvedSignature);
				fBuffer.append("</span>"); //$NON-NLS-1$
			}
		}

		@Override
		protected void appendMethodPrependedReturnType(IMethod method, long flags, String resolvedSignature) throws JavaModelException {
			if (noEnhancements) {
				super.appendMethodPrependedReturnType(method, flags, resolvedSignature);
			} else {
				fBuffer.append("<span class='methodReturn'>"); //$NON-NLS-1$
				super.appendMethodPrependedReturnType(method, flags, resolvedSignature);
				fBuffer.append("</span>"); //$NON-NLS-1$
			}
		}

		@Override
		protected void appendMethodQualification(IMethod method, long flags) {
			if (noEnhancements) {
				super.appendMethodQualification(method, flags);
			} else {
				appendingMethodQualification= true;
				fBuffer.append("<span class='methodQualifier'>"); //$NON-NLS-1$
				super.appendMethodQualification(method, flags);
				if (appendingMethodQualification) {
					fBuffer.append("</span>"); //$NON-NLS-1$
					appendingMethodQualification= false;
				}
			}
		}

		@Override
		protected void appendMethodName(IMethod method) {
			if (noEnhancements) {
				super.appendMethodName(method);
			} else {
				fBuffer.append("<span class='methodName'>"); //$NON-NLS-1$
				super.appendMethodName(method);
				fBuffer.append("</span>"); //$NON-NLS-1$
			}
		}

		@Override
		protected void appendMethodParams(IMethod method, long flags, String resolvedSignature) throws JavaModelException {
			if (noEnhancements) {
				super.appendMethodParams(method, flags, resolvedSignature);
			} else {
				fBuffer.append("<span class='methodParams'>"); //$NON-NLS-1$
				super.appendMethodParams(method, flags, resolvedSignature);
				fBuffer.append("</span>"); //$NON-NLS-1$
				nextParamNo= 1;
			}
		}

		@Override
		protected void appendMethodParam(IMethod method, long flags, IAnnotation[] annotations, String paramSignature, String name, boolean renderVarargs, boolean isLast) throws JavaModelException {
			if (noEnhancements) {
				super.appendMethodParam(method, flags, annotations, paramSignature, name, renderVarargs, isLast);
			} else {
				fBuffer.append("<span class='methodParam'>"); //$NON-NLS-1$
				super.appendMethodParam(method, flags, annotations, paramSignature, name, renderVarargs, isLast);
				fBuffer.append("</span>"); //$NON-NLS-1$
			}
		}

		@Override
		protected void appendMethodParamName(String name) {
			if (noEnhancements) {
				super.appendMethodParamName(name);
			} else {
				fBuffer.append("<span class='methodParamName methodParamNo"); //$NON-NLS-1$
				fBuffer.append(String.valueOf(nextParamNo++));
				fBuffer.append("'>"); //$NON-NLS-1$
				super.appendMethodParamName(name);
				fBuffer.append("</span>"); //$NON-NLS-1$
			}
		}

		@Override
		protected void appendTypeParameterWithBounds(ITypeParameter typeParameter, long flags) throws JavaModelException {
			if (noEnhancements || nextNestingLevel == 1) {
				super.appendTypeParameterWithBounds(typeParameter, flags);
			} else {
				fBuffer.append("<span class='"); //$NON-NLS-1$
				fBuffer.append(getTypeStylingClass(typeParameter.getElementName()));
				fBuffer.append("'>"); //$NON-NLS-1$
				inBoundedTypeParam= true;
				super.appendTypeParameterWithBounds(typeParameter, flags);
				inBoundedTypeParam= false;
				fBuffer.append("</span>"); //$NON-NLS-1$
			}
		}

		@Override
		protected void appendWildcardTypeSignature(String prefix, IJavaElement enclosingElement, String typeSignature, long flags) {
			if (noEnhancements) {
				super.appendWildcardTypeSignature(prefix, enclosingElement, typeSignature, flags);
			} else {
				int sigKind= Signature.getTypeSignatureKind(typeSignature);
				if (sigKind == Signature.TYPE_VARIABLE_SIGNATURE || sigKind == Signature.CLASS_TYPE_SIGNATURE) {
					typeStyleClassApplied= true;
					String typeName= super.getSimpleTypeName(enclosingElement, typeSignature);
					fBuffer.append("<span class='"); //$NON-NLS-1$
					fBuffer.append(getTypeStylingClass(typeName));
					fBuffer.append("'>"); //$NON-NLS-1$
				}
				super.appendWildcardTypeSignature(prefix, enclosingElement, typeSignature, flags);
				if (typeStyleClassApplied) {
					fBuffer.append("</span>"); //$NON-NLS-1$
					typeStyleClassApplied= false;
				}
			}
		}

		@Override
		protected void appendTypeArgumentSignaturesLabel(IJavaElement enclosingElement, String[] typeArgsSig, long flags) {
			if (noEnhancements || !inBoundedTypeParam) {
				super.appendTypeArgumentSignaturesLabel(enclosingElement, typeArgsSig, flags);
			} else {
				inBoundedTypeParam= false;
				super.appendTypeArgumentSignaturesLabel(enclosingElement, typeArgsSig, flags);
				inBoundedTypeParam= true;
			}
		}

		@Override
		protected void appendTypeParameterSignatureLabel(String typeVariableName) {
			if (noEnhancements) {
				super.appendTypeParameterSignatureLabel(typeVariableName);
			} else {
				super.appendTypeParameterSignatureLabel(wrapWithTypeClass(typeVariableName, typeVariableName));
			}
		}
	}

	public static final String OPEN_LINK_SCHEME= CoreJavaElementLinks.OPEN_LINK_SCHEME;
	public static final String JAVADOC_SCHEME= CoreJavaElementLinks.JAVADOC_SCHEME;
	public static final String JAVADOC_VIEW_SCHEME= CoreJavaElementLinks.JAVADOC_VIEW_SCHEME;

	public static void initDefaultPreferences(IPreferenceStore store) {
		initDefaultColors(store);
		store.setDefault(PREFERENCE_KEY_ENABLED, true);
		store.setDefault(PREFERENCE_KEY_POSTFIX_TYPE_PARAMETERS_REFERENCES_COLORING, true);
		store.addPropertyChangeListener(COLOR_PROPERTIES_CHANGE_LISTENER);
		store.addPropertyChangeListener(ENHANCEMENTS_PROPERTIES_CHANGE_LISTENER);
	}

	public static void initDefaultColors(IPreferenceStore store) {
		if (store.getBoolean(PREFERENCE_KEY_DARK_MODE_DEFAULT_COLORS)) {
			var color= new RGB(177, 102, 218); // semanticHighlighting.typeArgument.color in css\e4-dark_jdt_syntaxhighlighting.css
			PreferenceConverter.setDefault(store, getColorPreferenceKey(PREFERENCE_KEY_PREFIX_TYPE_PARAMETERS_REFERENCE_COLOR, 1), color);

			color= new RGB(255, 140, 0); // CSS 'DarkOrange'
			PreferenceConverter.setDefault(store, getColorPreferenceKey(PREFERENCE_KEY_PREFIX_TYPE_PARAMETERS_REFERENCE_COLOR, 2), color);

			color= new RGB(144, 238, 144); // CSS 'LightGreen'
			PreferenceConverter.setDefault(store, getColorPreferenceKey(PREFERENCE_KEY_PREFIX_TYPE_PARAMETERS_REFERENCE_COLOR, 3), color);

			color= new RGB(0, 191, 255); // CSS 'DeepSkyBlue'
			PreferenceConverter.setDefault(store, getColorPreferenceKey(PREFERENCE_KEY_PREFIX_TYPE_PARAMETERS_REFERENCE_COLOR, 4), color);
		} else {
			// slightly brighter than SemanticHighlightings.TypeArgumentHighlighting's default color to work better on yellow-ish background
			var color= new RGB(60, 179, 113); // CSS 'MediumSeaGreen'
			PreferenceConverter.setDefault(store, getColorPreferenceKey(PREFERENCE_KEY_PREFIX_TYPE_PARAMETERS_REFERENCE_COLOR, 1), color);

			color= new RGB(255, 140, 0); // CSS 'DarkOrange'
			PreferenceConverter.setDefault(store, getColorPreferenceKey(PREFERENCE_KEY_PREFIX_TYPE_PARAMETERS_REFERENCE_COLOR, 2), color);

			color= new RGB(153, 50, 204); // CSS 'DarkOrchid'
			PreferenceConverter.setDefault(store, getColorPreferenceKey(PREFERENCE_KEY_PREFIX_TYPE_PARAMETERS_REFERENCE_COLOR, 3), color);

			color= new RGB(65, 105, 225); // CSS 'RoyalBlue'
			PreferenceConverter.setDefault(store, getColorPreferenceKey(PREFERENCE_KEY_PREFIX_TYPE_PARAMETERS_REFERENCE_COLOR, 4), color);
		}
	}

	private static void enhancementsSettingsChangeListener(PropertyChangeEvent event) {
		if (PREFERENCE_KEY_DARK_MODE_DEFAULT_COLORS.equals(event.getProperty())) {
			// taking advantage of PREFERENCE_KEY_DARK_MODE_DEFAULT_COLORS change instead of more complicated OSGi event listener
			initDefaultColors(preferenceStore());
			cssFragmentsCacheResetListener(null);
		} else if (PREFERENCE_KEY_ENABLED.equals(event.getProperty())) {
			CONFIG_LISTENERS.forEach(l -> l.stylingStateChanged((Boolean) event.getNewValue()));
		} else if (event.getProperty().startsWith(PREFERENCE_KEY_POSTFIX_TYPE_PARAMETERS_REFERENCES_COLORING)) {
			CONFIG_LISTENERS.forEach(l -> l.parametersColoringStateChanged((Boolean) event.getNewValue()));
		} else if (event.getProperty().startsWith(PREFERENCE_KEY_PREFIX_TYPE_PARAMETERS_REFERENCE_COLOR)) {
			CONFIG_LISTENERS.forEach(IStylingConfigurationListener::parametersColorChanged);
		}
	}

	private static void cssFragmentsCacheResetListener(PropertyChangeEvent event) {
		if (event == null || event.getProperty().startsWith(PREFERENCE_KEY_PREFIX_TYPE_PARAMETERS_REFERENCE_COLOR)) {
			try {
				if (CSS_FRAGMENTS_CACHE_LOCK.tryLock(500, TimeUnit.MILLISECONDS)) {
					try {
						CSS_FRAGMENTS_CACHE_TYPE_PARAMETERS_REFERENCES= new String[4];
					} finally {
						CSS_FRAGMENTS_CACHE_LOCK.unlock();
					}
				}
			} catch (InterruptedException e) {
				JavaPlugin.log(new RuntimeException("Interrupted while waiting for CSS fragments cache lock, cache reset unsuccessful", e)); //$NON-NLS-1$
			}
		}
	}

	private JavaElementLinks() {
		// static only
	}

	/**
	 * Creates a location listener which uses the given handler
	 * to handle java element links.
	 *
	 * The location listener can be attached to a {@link Browser}
	 *
	 * @param handler the handler to use to handle links
	 * @return a new {@link LocationListener}
	 */
	public static LocationListener createLocationListener(final ILinkHandler handler) {
		return new LocationAdapter() {
			@Override
			public void changing(LocationEvent event) {
				String loc= event.location;

				if ("about:blank".equals(loc) || loc.startsWith("data:")) { //$NON-NLS-1$ //$NON-NLS-2$
					/*
					 * Using the Browser.setText API triggers a location change to "about:blank".
					 * XXX: remove this code once https://bugs.eclipse.org/bugs/show_bug.cgi?id=130314 is fixed
					 */
					// The check for "data:" is due to Edge browser issuing a location change with a URL using the data: protocol
					// that contains the Base64 encoded version of the text whenever setText is called on the browser.
					// See issue: https://github.com/eclipse-jdt/eclipse.jdt.ui/issues/248

					//input set with setText
					handler.handleTextSet();
					return;
				}

				event.doit= false;

				if (loc.startsWith("about:")) { //$NON-NLS-1$
					// Relative links should be handled via head > base tag.
					// If no base is available, links just won't work.
					return;
				}

				URI uri= null;
				try {
					uri= new URI(loc);
				} catch (URISyntaxException e) {
					JavaPlugin.log(e); // log bad URL, but proceed in the hope that handleExternalLink(..) can deal with it
				}

				String scheme= uri == null ? null : uri.getScheme();
				boolean nomatch= false;
				if (scheme != null) switch (scheme) {
				case JavaElementLinks.JAVADOC_VIEW_SCHEME:
					{
						IJavaElement linkTarget= JavaElementLinks.parseURI(uri);
						if (linkTarget == null)
							return;
						handler.handleJavadocViewLink(linkTarget);
						break;
					}
				case JavaElementLinks.JAVADOC_SCHEME:
					{
						IJavaElement linkTarget= JavaElementLinks.parseURI(uri);
						if (linkTarget == null)
							return;
						handler.handleInlineJavadocLink(linkTarget);
						break;
					}
				case JavaElementLinks.OPEN_LINK_SCHEME:
					{
						IJavaElement linkTarget= JavaElementLinks.parseURI(uri);
						if (linkTarget == null)
							return;
						handler.handleDeclarationLink(linkTarget);
						break;
					}
				default:
					nomatch= true;
					break;
				}
				if (nomatch) {
					try {
						if (!(loc.startsWith("data:")) && handler.handleExternalLink(new URL(loc), event.display)) //$NON-NLS-1$
							return;
						event.doit= true;
					} catch (MalformedURLException e) {
						JavaPlugin.log(e);
					}
				}
			}
		};
	}

	/**
	 * Creates an {@link URI} with the given scheme for the given element.
	 *
	 * @param scheme the scheme
	 * @param element the element
	 * @return an {@link URI}, encoded as {@link URI#toASCIIString() ASCII} string, ready to be used
	 *         as <code>href</code> attribute in an <code>&lt;a&gt;</code> tag
	 * @throws URISyntaxException if the arguments were invalid
	 */
	public static String createURI(String scheme, IJavaElement element) throws URISyntaxException {
		return CoreJavaElementLinks.createURI(scheme, element);
	}

	/**
	 * Creates an {@link URI} with the given scheme based on the given element.
	 * The additional arguments specify a member referenced from the given element.
	 *
	 * @param scheme a scheme
	 * @param element the declaring element
	 * @param refTypeName a (possibly qualified) type or package name, can be <code>null</code>
	 * @param refMemberName a member name, can be <code>null</code>
	 * @param refParameterTypes a (possibly empty) array of (possibly qualified) parameter type
	 *            names, can be <code>null</code>
	 * @return an {@link URI}, encoded as {@link URI#toASCIIString() ASCII} string, ready to be used
	 *         as <code>href</code> attribute in an <code>&lt;a&gt;</code> tag
	 * @throws URISyntaxException if the arguments were invalid
	 */
	public static String createURI(String scheme, IJavaElement element, String refTypeName, String refMemberName, String[] refParameterTypes) throws URISyntaxException {
		return CoreJavaElementLinks.createURI(scheme, element, refTypeName, refMemberName, refParameterTypes);
	}

	public static IJavaElement parseURI(URI uri) {
		return CoreJavaElementLinks.parseURI(uri);
	}

	/**
	 * Creates a link with the given URI and label text.
	 *
	 * @param uri the URI
	 * @param label the label
	 * @return the HTML link
	 * @since 3.6
	 */
	public static String createLink(String uri, String label) {
		return CoreJavaElementLinks.createLink(uri, label);
	}

	/**
	 * Creates a header link with the given URI and label text.
	 *
	 * @param uri the URI
	 * @param label the label
	 * @return the HTML link
	 * @since 3.6
	 */
	public static String createHeaderLink(String uri, String label) {
		return CoreJavaElementLinks.createHeaderLink(uri, label);
	}

	/**
	 * Creates a link with the given URI, label and title text.
	 *
	 * @param uri the URI
	 * @param label the label
	 * @param title the title to be displayed while hovering over the link (can be empty)
	 * @return the HTML link
	 * @since 3.10
	 */
	public static String createHeaderLink(String uri, String label, String title) {
		return CoreJavaElementLinks.createHeaderLink(uri, label, title);
	}

	/**
	 * Returns the label for a Java element with the flags as defined by {@link JavaElementLabels}.
	 * Referenced element names in the label (except the given element's name) are rendered as
	 * header links.
	 *
	 * @param element the element to render
	 * @param flags the rendering flags
	 * @return the label of the Java element
	 * @since 3.5
	 */
	public static String getElementLabel(IJavaElement element, long flags) {
		return getElementLabel(element, flags, false);
	}

	/**
	 * Returns the label for a Java element with the flags as defined by {@link JavaElementLabels}.
	 * Referenced element names in the label are rendered as header links.
	 * If <code>linkAllNames</code> is <code>false</code>, don't link the name of the given element
	 *
	 * @param element the element to render
	 * @param flags the rendering flags
	 * @param linkAllNames if <code>true</code>, link all names; if <code>false</code>, link all names except original element's name
	 * @return the label of the Java element
	 * @since 3.6
	 */
	public static String getElementLabel(IJavaElement element, long flags, boolean linkAllNames) {
		return getElementLabel(element, flags, linkAllNames, false);
	}

	/**
	 * Returns the label for a Java element with the flags as defined by {@link JavaElementLabels}.
	 * Referenced element names in the label are rendered as header links.
	 * If <code>linkAllNames</code> is <code>false</code>, don't link the name of the given element
	 *
	 * @param element the element to render
	 * @param flags the rendering flags
	 * @param linkAllNames if <code>true</code>, link all names; if <code>false</code>, link all names except original element's name
	 * <code>null</code> means no enhanced styling
	 * @return the label of the Java element
	 * @since 3.6
	 */
	public static String getElementLabel(IJavaElement element, long flags, boolean linkAllNames, boolean useEnhancements) {
		StringBuffer buf= new StringBuffer();

		if (!Strings.USE_TEXT_PROCESSOR) {
			new JavaElementLinkedLabelComposer(linkAllNames ? null : element, buf, useEnhancements).appendElementLabel(element, flags);
			return Strings.markJavaElementLabelLTR(buf.toString());
		} else {
			String label= JavaElementLabels.getElementLabel(element, flags);
			return label.replace("<", "&lt;").replace(">", "&gt;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}
	}

	/**
	 * Returns the label for a binding with the flags as defined by {@link JavaElementLabels}.
	 * Referenced element names in the label are rendered as header links.
	 *
	 * @param binding the binding to render
	 * @param element the corresponding Java element, used for javadoc hyperlinks
	 * @param flags the rendering flags
	 * @param haveSource true when looking at an ICompilationUnit which enables the use of short type names
	 * @return the label of the binding
	 * @since 3.11
	 */
	public static String getBindingLabel(IBinding binding, IJavaElement element, long flags, boolean haveSource) {
		return getBindingLabel(binding, element, flags, haveSource, false);
	}

	/**
	 * Returns the label for a binding with the flags as defined by {@link JavaElementLabels}.
	 * Referenced element names in the label are rendered as header links.
	 *
	 * @param binding the binding to render
	 * @param element the corresponding Java element, used for javadoc hyperlinks
	 * @param flags the rendering flags
	 * @param haveSource true when looking at an ICompilationUnit which enables the use of short type names
	 * @param useEnhancements whether to use enhanced styling of HTML content for element labels
	 * @return the label of the binding
	 * @since 3.11
	 */
	public static String getBindingLabel(IBinding binding, IJavaElement element, long flags, boolean haveSource, boolean useEnhancements) {
		StringBuffer buf= new StringBuffer();

		if (!Strings.USE_TEXT_PROCESSOR) {
			new BindingLinkedLabelComposer(element, buf, haveSource, useEnhancements).appendBindingLabel(binding, flags);
			return Strings.markJavaElementLabelLTR(buf.toString());
		} else {
			String label= JavaElementLabels.getElementLabel(element, flags);
			return label.replace("<", "&lt;").replace(">", "&gt;"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}
	}

	public static boolean getStylingEnabledPreference() {
		return preferenceStore().getBoolean(PREFERENCE_KEY_ENABLED);
	}

	public static boolean getPreferenceForTypeParamsColoring() {
		return preferenceStore().getBoolean(PREFERENCE_KEY_POSTFIX_TYPE_PARAMETERS_REFERENCES_COLORING);
	}

	public static void setStylingEnabledPreference(boolean value) {
		preferenceStore().setValue(PREFERENCE_KEY_ENABLED, value);
	}

	public static void setPreferenceForTypeParamsColoring(boolean value) {
		preferenceStore().setValue(PREFERENCE_KEY_POSTFIX_TYPE_PARAMETERS_REFERENCES_COLORING, value);
	}

	public static RGB getColorPreferenceForTypeParamsReference(int referenceIndex) {
		var color= PreferenceConverter.getColor(preferenceStore(), getColorPreferenceKey(PREFERENCE_KEY_PREFIX_TYPE_PARAMETERS_REFERENCE_COLOR, referenceIndex));
		if (PreferenceConverter.COLOR_DEFAULT_DEFAULT == color) {
			// for unconfigured color indexes alternate between first 4 colors
			return PreferenceConverter.getColor(preferenceStore(), getColorPreferenceKey(PREFERENCE_KEY_PREFIX_TYPE_PARAMETERS_REFERENCE_COLOR, 1 + ((referenceIndex + 3) % 4)));
		}
		return color;
	}

	public static void setColorPreferenceForTypeParamsReference(int referenceIndex, RGB color) {
		PreferenceConverter.setValue(preferenceStore(), getColorPreferenceKey(PREFERENCE_KEY_PREFIX_TYPE_PARAMETERS_REFERENCE_COLOR, referenceIndex), color);
	}

	public static boolean isStylingPresent(String browserContent) {
		return browserContent.contains(CHECKBOX_FORMATTING_ID_ATTR_DQUOTES) || browserContent.contains(CHECKBOX_FORMATTING_ID_ATTR_SQUOTES);
	}

	public static String modifyCssStyleSheet(String css, StringBuilder buffer) {
		int startPos= buffer.indexOf(CSS_CLASS_SWITCH_PARENT);
		if (startPos < 0) {
			return css;
		}
		StringBuilder cssContent= new StringBuilder();

		int maxTypeParamNo= getMaxIndexOfStyle(buffer, StringBuilder::indexOf, CSS_CLASS_TYPE_PARAMETERS_REFERENCE_PREFIX);

		var locked= false;
		try {
			locked= CSS_FRAGMENTS_CACHE_LOCK.tryLock(100, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return css;
		}
		try {
			if (locked) {
				if (CSS_FRAGMENTS_CACHE_TYPE_PARAMETERS_REFERENCES.length < maxTypeParamNo) {
					CSS_FRAGMENTS_CACHE_TYPE_PARAMETERS_REFERENCES= Arrays.copyOf(CSS_FRAGMENTS_CACHE_TYPE_PARAMETERS_REFERENCES, maxTypeParamNo);
				}
			}
			var processedUntil= processColoringSection(css, cssContent, maxTypeParamNo, locked);
			cssContent.append(css, processedUntil, css.length());
			return cssContent.toString();
		} catch (Exception e) {
			JavaPlugin.log(e);
			return css;
		} finally {
			if (locked) {
				CSS_FRAGMENTS_CACHE_LOCK.unlock();
			}
		}
	}

	private static int processColoringSection(String cssTemplate, StringBuilder outputCss, int iterations, boolean fragmentsCacheLock) {
		var sectionStart= cssTemplate.indexOf(CSS_SECTION_START_TYPE_PARAMETERS_REFERENCES);
		outputCss.append(cssTemplate, 0, sectionStart);

		sectionStart += CSS_SECTION_START_TYPE_PARAMETERS_REFERENCES.length();
		var sectionEnd= cssTemplate.indexOf(CSS_SECTION_END_TYPE_PARAMETERS_REFERENCES, sectionStart);
		for (int i= iterations - 1; i >= 0 ;  i--) {
			if (fragmentsCacheLock && CSS_FRAGMENTS_CACHE_TYPE_PARAMETERS_REFERENCES.length > i && CSS_FRAGMENTS_CACHE_TYPE_PARAMETERS_REFERENCES[i] != null) {
				// re-use cached fragment
				outputCss.append(CSS_FRAGMENTS_CACHE_TYPE_PARAMETERS_REFERENCES[i]);
			} else {
				var section= cssTemplate.substring(sectionStart, sectionEnd);
				var sectionBuf= new StringBuilder(section);
				int pos;
				int index = i + 1; // color styles in CSS and preference keys are 1-based
				while ((pos= sectionBuf.indexOf(CSS_PLACEHOLDER_INDEX)) != -1) {
					sectionBuf.replace(pos, pos + CSS_PLACEHOLDER_INDEX.length(), String.valueOf(index));
				}
				pos= sectionBuf.indexOf(CSS_PLACEHOLDER_COLOR);
				sectionBuf.replace(pos, pos + CSS_PLACEHOLDER_COLOR.length(), getCssColor(getColorPreferenceForTypeParamsReference(index)));
				if (fragmentsCacheLock && CSS_FRAGMENTS_CACHE_TYPE_PARAMETERS_REFERENCES.length > i) { // cache fragment if possible
					CSS_FRAGMENTS_CACHE_TYPE_PARAMETERS_REFERENCES[i]= sectionBuf.toString();
				}
				outputCss.append(sectionBuf);
			}
		}
		return sectionEnd + CSS_SECTION_END_TYPE_PARAMETERS_REFERENCES.length();
	}

	private static String getCssColor(RGB color) {
		return "rgb(" + color.red + ", " + color.green + ", " + color.blue + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

	public static int getNumberOfTypeParamsReferences(String content) {
		return getMaxIndexOfStyle(content, String::indexOf, CSS_CLASS_TYPE_PARAMETERS_REFERENCE_PREFIX);
	}

	private static <T extends CharSequence> int getMaxIndexOfStyle(T content, BiFunction<T, String, Integer> indexOfGetter, String stylePrefix) {
		int i= 0;
		while (indexOfGetter.apply(content, stylePrefix + ++i) != -1) { /* no-op */ }
		return i - 1;
	}

	public static Integer[] getColorPreferencesIndicesForTypeParamsReference() {
		List<Integer> retVal= new ArrayList<>(MAX_COLOR_INDEX);
		for (int i= 1; i <= MAX_COLOR_INDEX; i++) {
			if (i <= 4) {
				// pretend first 4 colors are always set (since we have defaults for them)
				retVal.add(i);
			} else {
				String key= getColorPreferenceKey(PREFERENCE_KEY_PREFIX_TYPE_PARAMETERS_REFERENCE_COLOR, i);
				if (preferenceStore().contains(key)) {
					retVal.add(i);
				}
			}
		}
		return retVal.toArray(Integer[]::new);
	}

	public static void resetAllColorPreferencesToDefaults() {
		var store= preferenceStore();
		store.removePropertyChangeListener(COLOR_PROPERTIES_CHANGE_LISTENER);
		store.removePropertyChangeListener(ENHANCEMENTS_PROPERTIES_CHANGE_LISTENER);
		try {
			for (int i= 1; i <= MAX_COLOR_INDEX; i++) {
				String key= getColorPreferenceKey(PREFERENCE_KEY_PREFIX_TYPE_PARAMETERS_REFERENCE_COLOR, i);
				if (!store.isDefault(key)) {
					store.setToDefault(key);
				}
			}
			cssFragmentsCacheResetListener(null); // clear CSS fragments cache
		} finally {
			store.addPropertyChangeListener(COLOR_PROPERTIES_CHANGE_LISTENER);
			store.addPropertyChangeListener(ENHANCEMENTS_PROPERTIES_CHANGE_LISTENER);
			CONFIG_LISTENERS.forEach(IStylingConfigurationListener::parametersColorChanged);
		}
	}

	private static String getColorPreferenceKey(String prefix, int index) {
		return prefix + index;
	}

	private static IPreferenceStore preferenceStore() {
		return PreferenceConstants.getPreferenceStore();
	}

	public static void addStylingConfigurationListener(IStylingConfigurationListener listener) {
		CONFIG_LISTENERS.add(listener);
	}

	public static void removeStylingConfigurationListener(IStylingConfigurationListener listener) {
		CONFIG_LISTENERS.remove(listener);
	}

	/**
	 * Styling configuration listener is notified when Javadoc styling enhancements are switched on or off via preference.
	 */
	public static interface IStylingConfigurationListener {
		/**
		 * Called when all Javadoc styling enhancements have been toggled.
		 * @param isEnabled whether all styling enhancements were turned on or off
		 */
		void stylingStateChanged(boolean isEnabled);

		/**
		 * Called when parameters coloring styling enhancement for Javadoc have been toggled.
		 * @param isEnabled whether parameters coloring styling enhancement was turned on or off
		 */
		void parametersColoringStateChanged(boolean isEnabled);

		/**
		 * Called when some color used for parameters coloring styling enhancement for Javadoc has been changed.
		 */
		void parametersColorChanged();
	}

}
