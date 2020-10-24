/*******************************************************************************
 * Copyright (c) 2008, 2020 IBM Corporation and others.
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
package org.eclipse.jdt.ui.tests.refactoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;

import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.MoveRefactoring;
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.JavaRefactoringDescriptor;
import org.eclipse.jdt.core.refactoring.descriptors.MoveDescriptor;
import org.eclipse.jdt.core.refactoring.descriptors.MoveStaticMembersDescriptor;
import org.eclipse.jdt.core.refactoring.descriptors.RenameJavaElementDescriptor;
import org.eclipse.jdt.core.search.SearchMatch;

import org.eclipse.jdt.internal.core.refactoring.descriptors.RefactoringSignatureDescriptorFactory;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.ParameterInfo;
import org.eclipse.jdt.internal.corext.refactoring.base.ReferencesInBinaryContext;
import org.eclipse.jdt.internal.corext.refactoring.code.InlineMethodRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.structure.ASTNodeSearchUtil;
import org.eclipse.jdt.internal.corext.refactoring.structure.ChangeSignatureProcessor;
import org.eclipse.jdt.internal.corext.refactoring.structure.MoveInstanceMethodProcessor;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;

public class BinaryReferencesTests {
	private static final boolean BUG_226660= true;

	@Rule
	public BinaryReferencesTestSetup fgTestSetup= new BinaryReferencesTestSetup();

	private static void assertContainsMatches(List<SearchMatch> matches, String[] expectedHandleIdentifiers) {
		int matchCount= matches.size();
		assertTrue("match count too small: " + matchCount, matchCount >= expectedHandleIdentifiers.length);

		List<String> actual= new ArrayList<>();
		for (int i= 0; i < matchCount; i++) {
			SearchMatch match= matches.get(i);
			String handleIdentifier= ((IJavaElement) match.getElement()).getHandleIdentifier();
			actual.add(handleIdentifier);
		}
		List<String> expected= new ArrayList<>(Arrays.asList(expectedHandleIdentifiers));
		expected.removeAll(actual);
		if (!expected.isEmpty())
			assertEquals("not all expected matches", expected.toString(), actual.toString());
	}

	private IType findType(String typeName) throws JavaModelException {
		return fgTestSetup.getSourceProject().findType(typeName);
	}

	private static IMethod findMethod(IType type, String methodName) throws JavaModelException {
		IMethod method= type.getMethod(methodName, new String[0]);
		if (! method.exists()) {
			for (IMethod m : type.getMethods()) {
				if (m.getElementName().equals(methodName)) {
					method= m;
					break;
				}
			}
		}
		return method;
	}

	private static List<SearchMatch> doRefactoring(JavaRefactoringDescriptor descriptor) throws CoreException {
		RefactoringStatus status= new RefactoringStatus();
		Refactoring refactoring= descriptor.createRefactoring(status);
		assertTrue(status.isOK());

		return doCheckConditions(refactoring);
	}

	private static List<SearchMatch> doCheckConditions(Refactoring refactoring) throws CoreException {
		CheckConditionsOperation op= new CheckConditionsOperation(refactoring, CheckConditionsOperation.ALL_CONDITIONS);
		op.run(null);
		RefactoringStatus validationStatus= op.getStatus();
		assertFalse(validationStatus.hasFatalError());
		assertTrue(validationStatus.hasError());
		assertEquals(1, validationStatus.getEntries().length);

		ReferencesInBinaryContext context= (ReferencesInBinaryContext) validationStatus.getEntryAt(0).getContext();
		return context.getMatches();
	}

	@Test
	public void testRenameType01() throws Exception {
		RenameJavaElementDescriptor descriptor= RefactoringSignatureDescriptorFactory.createRenameJavaElementDescriptor(IJavaRefactorings.RENAME_TYPE);
		descriptor.setJavaElement(findType("source.BaseClass"));
		descriptor.setNewName("RenamedBaseClass");
		descriptor.setUpdateReferences(true);

		List<SearchMatch> matches= doRefactoring(descriptor);
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(SubClass.class[SubClass",
				"=BinaryReference/binary<ref(SubClass.class[SubClass",
				"=BinaryReference/binary<ref(SubClass.class[SubClass~compareTo~Lsource.BaseClass;"
		});
	}

	@Test
	public void testRenameType02() throws Exception {
		RenameJavaElementDescriptor descriptor= RefactoringSignatureDescriptorFactory.createRenameJavaElementDescriptor(IJavaRefactorings.RENAME_TYPE);
		descriptor.setJavaElement(findType("source.Color"));
		descriptor.setNewName("Colour");
		descriptor.setUpdateSimilarDeclarations(true);
		descriptor.setMatchStrategy(RenameJavaElementDescriptor.STRATEGY_SUFFIX);
		descriptor.setUpdateReferences(true);

		RefactoringStatus status= new RefactoringStatus();
		Refactoring refactoring= descriptor.createRefactoring(status);
		assertTrue(status.isOK());

		CheckConditionsOperation op= new CheckConditionsOperation(refactoring, CheckConditionsOperation.ALL_CONDITIONS);
		op.run(null);
		RefactoringStatus validationStatus= op.getStatus();
		assertFalse(validationStatus.hasFatalError());
		assertTrue(validationStatus.hasError());
		assertEquals(2, validationStatus.getEntries().length);

		ReferencesInBinaryContext context= (ReferencesInBinaryContext) validationStatus.getEntryAt(0).getContext();
		List<SearchMatch> matches= context.getMatches();
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(SubClass.class[SubClass",
				"=BinaryReference/binary<ref(SubClass.class[SubClass~paintColor~Lsource.Color;"
		});

		context= (ReferencesInBinaryContext) validationStatus.getEntryAt(1).getContext();
		matches= context.getMatches();
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass~main~\\[Ljava.lang.String;"
		});
	}

	private List<SearchMatch> doRenameMethod(String typeName, String methodName) throws CoreException {
		RenameJavaElementDescriptor descriptor= RefactoringSignatureDescriptorFactory.createRenameJavaElementDescriptor(IJavaRefactorings.RENAME_METHOD);
		IMethod method= findMethod(findType(typeName), methodName);
		descriptor.setJavaElement(method);
		descriptor.setNewName("newName");
		descriptor.setUpdateReferences(true);

		return doRefactoring(descriptor);
	}

	@Test
	public void testRenameVirtualMethod01() throws Exception {
		List<SearchMatch> matches= doRenameMethod("source.BaseClass", "baseMethod");
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(SubClass.class[SubClass~baseMethod"
		});
	}

	@Test
	public void testRenameVirtualMethod02() throws Exception {
		List<SearchMatch> matches= doRenameMethod("source.BaseClass", "compareTo");
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(SubClass.class[SubClass~compareTo~Lsource.BaseClass;"
		});
	}

	@Test
	public void testRenameVirtualMethod03() throws Exception {
		List<SearchMatch> matches= doRenameMethod("source.BaseClass", "referencedVirtualMethod");
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass~main~\\[Ljava.lang.String;",
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass~main~\\[Ljava.lang.String;",
				"=BinaryReference/binary<ref(SubClass.class[SubClass~baseMethod"
		});
	}

	@Test
	public void testRenameNonVirtualMethod01() throws Exception {
		List<SearchMatch> matches= doRenameMethod("source.BaseClass", "referencedMethod");
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass~main~\\[Ljava.lang.String;"
		});
	}

	@Test
	public void testRenameNonVirtualMethod02() throws Exception {
		List<SearchMatch> matches= doRenameMethod("source.BaseClass", "referencedStaticMethod");
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass~main~\\[Ljava.lang.String;"
		});
	}

	private List<SearchMatch> doRenameField(String typeName, String fieldName) throws CoreException {
		IField field= findType(typeName).getField(fieldName);
		String refactoringID= field.isEnumConstant() ? IJavaRefactorings.RENAME_ENUM_CONSTANT : IJavaRefactorings.RENAME_FIELD;
		RenameJavaElementDescriptor descriptor= RefactoringSignatureDescriptorFactory.createRenameJavaElementDescriptor(refactoringID);
		descriptor.setJavaElement(field);
		descriptor.setNewName(field.isEnumConstant() ? "BLA" : "newName");
		descriptor.setUpdateReferences(true);

		return doRefactoring(descriptor);
	}

	@Test
	public void testRenameField01() throws Exception {
		List<SearchMatch> matches= doRenameField("source.BaseClass", "fProtected");
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(SubClass.class[SubClass~SubClass~I"
		});
	}

	@Test
	public void testRenameField02() throws Exception {
		List<SearchMatch> matches= doRenameField("source.BaseClass", "fPublic");
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass~main~\\[Ljava.lang.String;"
		});
	}

	@Test
	public void testRenameField03() throws Exception {
		List<SearchMatch> matches= doRenameField("source.Color", "RED");
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass~main~\\[Ljava.lang.String;"
		});
	}

	@Test
	public void testRenameField04() throws Exception {
		assumeFalse(BUG_226660); // https://bugs.eclipse.org/bugs/show_bug.cgi?id=226660
		List<SearchMatch> matches= doRenameField("source.Color", "GREEN");
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass~main~\\[Ljava.lang.String;"
		});
	}

	@Test
	public void testRenamePackage01() throws Exception {
		RenameJavaElementDescriptor descriptor= RefactoringSignatureDescriptorFactory.createRenameJavaElementDescriptor(IJavaRefactorings.RENAME_PACKAGE);
		IPackageFragment pack= findType("source.BaseClass").getPackageFragment();
		descriptor.setJavaElement(pack);
		descriptor.setNewName("newName");
		descriptor.setUpdateReferences(true);
		descriptor.setUpdateHierarchy(false);

		List<SearchMatch> matches= doRefactoring(descriptor);
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass",
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass",
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass",
				"=BinaryReference/binary<ref(SubClass.class[SubClass",
				"=BinaryReference/binary<ref(SubClass.class[SubClass",
		});
	}

	@Test
	public void testRenamePackage02() throws Exception {
		RenameJavaElementDescriptor descriptor= RefactoringSignatureDescriptorFactory.createRenameJavaElementDescriptor(IJavaRefactorings.RENAME_PACKAGE);
		IPackageFragment pack= findType("source.BaseClass").getPackageFragment();
		descriptor.setJavaElement(pack);
		descriptor.setNewName("newName");
		descriptor.setUpdateReferences(true);
		descriptor.setUpdateHierarchy(true);

		RefactoringStatus status= new RefactoringStatus();
		Refactoring refactoring= descriptor.createRefactoring(status);
		assertTrue(status.isOK());
		CheckConditionsOperation op= new CheckConditionsOperation(refactoring, CheckConditionsOperation.ALL_CONDITIONS);
		op.run(null);
		RefactoringStatus validationStatus= op.getStatus();
		assertFalse(validationStatus.hasFatalError());
		assertTrue(validationStatus.hasError());
		assertEquals(2, validationStatus.getEntries().length);

		ReferencesInBinaryContext context= (ReferencesInBinaryContext) validationStatus.getEntryAt(0).getContext();
		List<SearchMatch> matches= context.getMatches();
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass",
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass",
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass",
				"=BinaryReference/binary<ref(SubClass.class[SubClass",
				"=BinaryReference/binary<ref(SubClass.class[SubClass",
		});

		context= (ReferencesInBinaryContext) validationStatus.getEntryAt(1).getContext();
		matches= context.getMatches();
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass~main~\\[Ljava.lang.String;",
				"=BinaryReference/binary<ref(SubClass.class[SubClass"
		});
	}

	private List<SearchMatch> doChangeSignature(String typeName, String methodName) throws JavaModelException, Exception, CoreException {
		IMethod method= findMethod(findType(typeName), methodName);
		ChangeSignatureProcessor processor= new ChangeSignatureProcessor(method);

		String[] newNames= { "x" };
		String[] newTypes= { "int" };
		String[] newDefaultValues= { "0" };
		ParameterInfo[] newParamInfos= ChangeSignatureTests.createNewParamInfos(newTypes, newNames, newDefaultValues);
		int[] newIndices= { 0 };
		ChangeSignatureTests.addInfos(processor.getParameterInfos(), newParamInfos, newIndices);

		Refactoring refactoring= new ProcessorBasedRefactoring(processor);

		return doCheckConditions(refactoring);
	}

	@Test
	public void testChangeSignature01() throws Exception {
		List<SearchMatch> matches= doChangeSignature("source.BaseClass", "baseMethod");
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(SubClass.class[SubClass~baseMethod",
				"=BinaryReference/binary<ref(SubClass.class[SubClass~baseMethod"
		});
	}

	@Test
	public void testChangeSignature02() throws Exception {
		List<SearchMatch> matches= doChangeSignature("source.BaseClass", "compareTo");
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(SubClass.class[SubClass~compareTo~Lsource.BaseClass;"
		});
	}

	@Test
	public void testChangeSignature03() throws Exception {
		List<SearchMatch> matches= doChangeSignature("source.BaseClass", "referencedMethod");
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass~main~\\[Ljava.lang.String;"
		});
	}

	@Test
	public void testChangeConstructorSignature01() throws Exception {
		List<SearchMatch> matches= doChangeSignature("source.BaseClass", "BaseClass");
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(SubClass.class[SubClass~SubClass~I",
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass~main~\\[Ljava.lang.String;"
		});
	}

	private List<SearchMatch> doInlineMethod(String typeName, String methodName) throws JavaModelException, Exception, CoreException {
		IMethod method= findMethod(findType(typeName), methodName);
		ICompilationUnit cu= method.getCompilationUnit();
		CompilationUnit node= new RefactoringASTParser(IASTSharedValues.SHARED_AST_LEVEL).parse(cu, true);
		ISourceRange nameRange= method.getNameRange();
		Refactoring refactoring= InlineMethodRefactoring.create(cu, node, nameRange.getOffset(), nameRange.getLength());
		return doCheckConditions(refactoring);
	}

	@Test
	public void testInlineMethod01() throws Exception {
		List<SearchMatch> matches= doInlineMethod("source.BaseClass", "referencedMethod");
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass~main~\\[Ljava.lang.String;"
		});
	}

	@Test
	public void testInlineMethod02() throws Exception {
		// no error if inlining only selected reference from source
		IMethod baseMethod= findMethod(findType("source.BaseClass"), "baseMethod");
		ICompilationUnit cu= baseMethod.getCompilationUnit();

		CompilationUnit node= new RefactoringASTParser(IASTSharedValues.SHARED_AST_LEVEL).parse(cu, true);
		MethodDeclaration baseDecl= ASTNodeSearchUtil.getMethodDeclarationNode(baseMethod, node);
		ExpressionStatement methodStmt= (ExpressionStatement) baseDecl.getBody().statements().get(0);

		Refactoring refactoring= InlineMethodRefactoring.create(cu, node, methodStmt.getStartPosition(), methodStmt.getLength());
		CheckConditionsOperation op= new CheckConditionsOperation(refactoring, CheckConditionsOperation.ALL_CONDITIONS);
		op.run(null);
		RefactoringStatus validationStatus= op.getStatus();
		assertFalse(validationStatus.hasError());
	}

	private List<SearchMatch> doMoveType(String typeName, String newPackageName) throws CoreException {
		IType type= findType(typeName);
		IPackageFragmentRoot root= JavaModelUtil.getPackageFragmentRoot(type);

		MoveDescriptor descriptor= (MoveDescriptor) RefactoringCore.getRefactoringContribution(IJavaRefactorings.MOVE).createDescriptor();
		descriptor.setMoveResources(new IFile[0], new IFolder[0], new ICompilationUnit[] { type.getCompilationUnit() });
		descriptor.setDestination(root.getPackageFragment(newPackageName));
		descriptor.setUpdateReferences(true);

		return doRefactoring(descriptor);
	}

	@Test
	public void testMoveType01() throws Exception {
		List<SearchMatch> matches= doMoveType("source.BaseClass", "source.sub");
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(SubClass.class[SubClass",
				"=BinaryReference/binary<ref(SubClass.class[SubClass",
				"=BinaryReference/binary<ref(SubClass.class[SubClass~compareTo~Lsource.BaseClass;",
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass",
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass~main~\\[Ljava.lang.String;",
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass~main~\\[Ljava.lang.String;", // + more of these...
		});
	}

	private List<SearchMatch> doMoveStaticMembers(IMember[] members, String targetTypeName) throws CoreException {
		IType targetType= findType(targetTypeName);

		MoveStaticMembersDescriptor descriptor= (MoveStaticMembersDescriptor) RefactoringCore.getRefactoringContribution(IJavaRefactorings.MOVE_STATIC_MEMBERS).createDescriptor();
		descriptor.setDestinationType(targetType);
		descriptor.setMembers(members);

		return doRefactoring(descriptor);
	}

	@Test
	public void testMoveStaticMember01() throws Exception {
		IType type= findType("source.BaseClass");
		IMethod method= findMethod(type, "referencedStaticMethod");
		IField field= type.getField("CONST");
		List<SearchMatch> matches= doMoveStaticMembers(new IMember[] { method, field }, "source.sub.InSubPack");
		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass~main~\\[Ljava.lang.String;",
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass~main~\\[Ljava.lang.String;"
		});
	}

	@Test
	public void testMoveMethod01() throws Exception {
		IType type= findType("source.BaseClass");
		IMethod method= findMethod(type, "referencedMethod");

		MoveInstanceMethodProcessor processor= new MoveInstanceMethodProcessor(method, JavaPreferencesSettings.getCodeGenerationSettings(method.getJavaProject()));
		Refactoring ref= new MoveRefactoring(processor);
		RefactoringStatus preconditionResult= ref.checkInitialConditions(new NullProgressMonitor());
		assertTrue("activation was supposed to be successful", preconditionResult.isOK());

		MoveInstanceMethodTests.chooseNewTarget(processor, MoveInstanceMethodTests.PARAMETER, "c");
		List<SearchMatch> matches= doCheckConditions(ref);

		assertContainsMatches(matches, new String[] {
				"=BinaryReference/binary<ref(ReferenceClass.class[ReferenceClass~main~\\[Ljava.lang.String;"
		});
	}
}
