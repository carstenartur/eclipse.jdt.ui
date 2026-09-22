from pathlib import Path
import os
import subprocess

root = Path(os.environ['SDK_ROOT'])
patches = root / 'patches'
patches.mkdir(exist_ok=True)
header = '''/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
'''

def replace(path, before, after):
    file = root / path
    text = file.read_text()
    assert before in text, (path, before)
    file.write_text(text.replace(before, after))

(root / 'debug/org.eclipse.jdt.debug.tests/tests/org/eclipse/jdt/debug/tests/ui/TemplateRegistryTests.java').write_text(header + '''package org.eclipse.jdt.debug.tests.ui;

import org.eclipse.jdt.internal.debug.ui.contentassist.CurrentFrameContext;
import org.eclipse.jdt.internal.debug.ui.contentassist.JavaDebugContentAssistProcessor;
import org.eclipse.jdt.internal.debug.ui.snippeteditor.JavaSnippetCompletionProcessor;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Version;
import org.osgi.framework.VersionRange;

import junit.framework.TestCase;

/**
 * Checks the registry linkage used when the debugger creates content assist.
 */
public class TemplateRegistryTests extends TestCase {

	public void testDebugContentAssistCanBeCreated() {
		JavaDebugContentAssistProcessor processor = new JavaDebugContentAssistProcessor(new CurrentFrameContext());
		assertNotNull(processor.getContextInformationValidator());
		assertNull(processor.getErrorMessage());
	}

	public void testSnippetContentAssistCanBeCreated() {
		// The editor is not used until completion is requested. Registry linkage
		// must already succeed when the processor is constructed.
		JavaSnippetCompletionProcessor processor = new JavaSnippetCompletionProcessor(null);
		assertNotNull(processor.getContextInformationValidator());
		assertNull(processor.getErrorMessage());
	}

	public void testRequiresJdtWithCoreRegistryAccessors() throws Exception {
		Bundle bundle = FrameworkUtil.getBundle(JavaDebugContentAssistProcessor.class);
		ManifestElement[] required = ManifestElement.parseHeader(Constants.REQUIRE_BUNDLE,
				bundle.getHeaders().get(Constants.REQUIRE_BUNDLE));
		for (ManifestElement dependency : required) {
			if ("org.eclipse.jdt.ui".equals(dependency.getValue())) {
				VersionRange range = new VersionRange(dependency.getAttribute(Constants.BUNDLE_VERSION_ATTRIBUTE));
				assertFalse("Must reject the JDT version without the Core accessors", range.includes(new Version("3.40.0")));
				assertTrue("Must accept the version introducing the Core accessors", range.includes(new Version("3.40.100")));
				return;
			}
		}
		fail("Missing dependency on org.eclipse.jdt.ui");
	}
}
''')
replace('debug/org.eclipse.jdt.debug.tests/tests/org/eclipse/jdt/debug/tests/AutomatedSuite.java', 'import org.eclipse.jdt.debug.tests.ui.JavaSnippetEditorTest;', 'import org.eclipse.jdt.debug.tests.ui.JavaSnippetEditorTest;\nimport org.eclipse.jdt.debug.tests.ui.TemplateRegistryTests;')
replace('debug/org.eclipse.jdt.debug.tests/tests/org/eclipse/jdt/debug/tests/AutomatedSuite.java', 'addTest(new TestSuite(JavaSnippetEditorTest.class));', 'addTest(new TestSuite(JavaSnippetEditorTest.class));\n\t\taddTest(new TestSuite(TemplateRegistryTests.class));')
(root / 'pde/ui/org.eclipse.pde.ui.tests/src/org/eclipse/pde/ui/tests/runtime/E4TemplateCompletionTest.java').write_text(header + '''package org.eclipse.pde.ui.tests.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.osgi.util.ManifestElement;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.framework.VersionRange;

/**
 * Exercises the executable extension that JDT instantiates for E4 content assist.
 */
public class E4TemplateCompletionTest {

	private static final String BUNDLE_ID = "org.eclipse.e4.tools.jdt.templates";

	@Test
	public void createsE4CompletionComputerThroughExtensionRegistry() throws Exception {
		for (IConfigurationElement element : Platform.getExtensionRegistry()
				.getConfigurationElementsFor("org.eclipse.jdt.ui.javaCompletionProposalComputer")) {
			if (BUNDLE_ID.equals(element.getContributor().getName())
					&& "javaCompletionProposalComputer".equals(element.getName())) {
				IJavaCompletionProposalComputer computer = assertInstanceOf(IJavaCompletionProposalComputer.class,
						element.createExecutableExtension("class"));
				computer.sessionStarted();
				try {
					// Construction initializes all three contributed E4 template contexts.
					// Without a compilation unit there must be no proposals or errors.
					assertTrue(computer.computeCompletionProposals(new JavaContentAssistInvocationContext((ICompilationUnit) null),
							new NullProgressMonitor()).isEmpty());
				} finally {
					computer.sessionEnded();
				}
				return;
			}
		}
		fail("E4 template completion extension is missing from the test runtime");
	}

	@Test
	public void requiresJdtWithCoreRegistryAccessors() throws Exception {
		Bundle bundle = Platform.getBundle(BUNDLE_ID);
		assertNotNull(bundle);
		ManifestElement[] required = ManifestElement.parseHeader(Constants.REQUIRE_BUNDLE,
				bundle.getHeaders().get(Constants.REQUIRE_BUNDLE));
		for (ManifestElement dependency : required) {
			if ("org.eclipse.jdt.ui".equals(dependency.getValue())) {
				VersionRange range = new VersionRange(dependency.getAttribute(Constants.BUNDLE_VERSION_ATTRIBUTE));
				assertFalse(range.includes(new Version("3.40.0")), "Must reject JDT without the Core accessors");
				assertTrue(range.includes(new Version("3.40.100")), "Must accept the version introducing the Core accessors");
				return;
			}
		}
		fail("Missing dependency on org.eclipse.jdt.ui");
	}
}
''')
replace('pde/ui/org.eclipse.pde.ui.tests/src/org/eclipse/pde/ui/tests/runtime/AllPDERuntimeTests.java', '@SelectClasses({ LocalModelTest.class })', '@SelectClasses({ LocalModelTest.class, E4TemplateCompletionTest.class })')
replace('pde/ui/org.eclipse.pde.ui.tests/META-INF/MANIFEST.MF', 'Require-Bundle: org.eclipse.pde.ui,', 'Require-Bundle: org.eclipse.pde.ui,\n org.eclipse.e4.tools.jdt.templates,')
for name, scope in [('debug', 'org.eclipse.jdt.debug.tests'), ('pde', 'ui/org.eclipse.pde.ui.tests')]:
    subprocess.run(['git', '-C', str(root/name), 'add', '-N', '.'], check=True)
    (patches / (name + '-tests.patch')).write_bytes(subprocess.check_output(['git', '-C', str(root/name), 'diff', '--', scope]))

replace('jdt-ui/org.eclipse.jdt.ui/META-INF/MANIFEST.MF', 'Bundle-Version: 3.40.0.qualifier', 'Bundle-Version: 3.40.100.qualifier')
replace('jdt-ui/org.eclipse.jdt.ui/pom.xml', '<version>3.40.0-SNAPSHOT</version>', '<version>3.40.100-SNAPSHOT</version>')
for path in ['contentassist/JavaDebugContentAssistProcessor.java', 'snippeteditor/JavaSnippetCompletionProcessor.java', 'actions/ToggleBreakpointAdapter.java']:
    replace('debug/org.eclipse.jdt.debug.ui/ui/org/eclipse/jdt/internal/debug/ui/' + path, '.getTemplateContextRegistry()', '.getTemplateContextRegistryCore()')
replace('debug/org.eclipse.jdt.debug.ui/META-INF/MANIFEST.MF', 'org.eclipse.jdt.ui;bundle-version="[3.33.0,4.0.0)"', 'org.eclipse.jdt.ui;bundle-version="[3.40.100,4.0.0)"')
replace('debug/org.eclipse.jdt.debug.ui/META-INF/MANIFEST.MF', 'Bundle-Version: 3.15.600.qualifier', 'Bundle-Version: 3.15.700.qualifier')
replace('debug/org.eclipse.jdt.debug.ui/pom.xml', '<version>3.15.600-SNAPSHOT</version>', '<version>3.15.700-SNAPSHOT</version>')
replace('pde/e4tools/bundles/org.eclipse.e4.tools.jdt.templates/src/org/eclipse/e4/internal/tools/jdt/templates/E4TemplateCompletionProposalComputer.java', '.getTemplateContextRegistry()', '.getTemplateContextRegistryCore()')
replace('pde/e4tools/bundles/org.eclipse.e4.tools.jdt.templates/META-INF/MANIFEST.MF', 'org.eclipse.jdt.ui;bundle-version="3.20.0"', 'org.eclipse.jdt.ui;bundle-version="[3.40.100,4.0.0)"')
replace('pde/e4tools/bundles/org.eclipse.e4.tools.jdt.templates/META-INF/MANIFEST.MF', 'Bundle-Version: 4.11.200.qualifier', 'Bundle-Version: 4.11.300.qualifier')
for name in ['jdt-ui', 'debug', 'pde']:
    subprocess.run(['git', '-C', str(root/name), 'diff', '--check'], check=True)
    (patches / (name + '.patch')).write_bytes(subprocess.check_output(['git', '-C', str(root/name), 'diff', '--binary']))
    subprocess.run(['git', '-C', str(root/name), 'diff', '--stat'], check=True)
