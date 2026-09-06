"""Generate diagnostic coverage around the unchanged annotation-assist tests."""
from pathlib import Path

source = r'''/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jdt.ui.tests.quickfix;

import java.io.File;
import java.util.List;
import java.util.stream.IntStream;
import java.util.zip.ZipFile;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;

/** Repeats the original complete class in one Eclipse VM and workspace. */
@RunWith(Parameterized.class)
public class AnnotateAssistRepeatProbe extends AnnotateAssistTest1d8 {
    @Parameter
    public int iteration;

    @Parameters(name="repetition={0}")
    public static List<Object[]> repetitions() {
        return IntStream.range(0, 50).mapToObj(i -> new Object[] {i}).toList();
    }

    @Override
    public List<ICompletionProposal> collectAnnotateProposals(JavaEditor editor, int offset) throws CoreException {
        try {
            return super.collectAnnotateProposals(editor, offset);
        } catch (AssertionError failure) {
            // Inspect only after the original assertion failed; do not retry or suppress it.
            System.err.println("ANNOTATION_SOURCE_PROBE repetition=" + iteration + " offset=" + offset);
            try {
                IClassFile classFile = ((IClassFileEditorInput) editor.getEditorInput()).getClassFile();
                IPackageFragmentRoot root = (IPackageFragmentRoot) classFile.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
                System.err.println("classFile=" + classFile.getPath());
                System.err.println("attachment=" + root.getSourceAttachmentPath());
                System.err.println("attachmentRoot=" + root.getSourceAttachmentRootPath());
                System.err.println("rawClasspath=" + java.util.Arrays.toString(fJProject1.getRawClasspath()));
                for (String name : new String[] {"lib.jar", "lib.zip"}) {
                    File file = fJProject1.getProject().getLocation().append(name).toFile();
                    System.err.println(name + " exists=" + file.isFile() + " bytes=" + file.length() + " modified=" + file.lastModified());
                    if (file.isFile()) {
                        try (ZipFile zip = new ZipFile(file)) {
                            zip.stream().limit(20).forEach(entry -> System.err.println("  " + entry.getName() + " bytes=" + entry.getSize()));
                        }
                    }
                }
            } catch (Exception diagnosticFailure) {
                failure.addSuppressed(diagnosticFailure);
            }
            throw failure;
        }
    }
}
'''
path = Path('org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/quickfix/AnnotateAssistRepeatProbe.java')
if path.exists():
    raise SystemExit('Refusing to overwrite an existing source file')
path.write_text(source, encoding='utf-8')
print(path)
