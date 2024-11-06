/*******************************************************************************
 * Copyright (c) 2000, 2020 IBM Corporation and others.
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
package org.eclipse.jdt.ui.tests.refactoring.typeconstraints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.types.TType;
import org.eclipse.jdt.internal.corext.refactoring.typeconstraints.types.TypeEnvironment;

import org.eclipse.jdt.ui.tests.refactoring.infra.AbstractJunit4CUTestCase;
import org.eclipse.jdt.ui.tests.refactoring.infra.RefactoringTestPlugin;
import org.eclipse.jdt.ui.tests.refactoring.rules.RefactoringTestSetup;

public class TypeEnvironmentTests extends AbstractJunit4CUTestCase {

	private static final boolean BUG_83616_core_wildcard_assignments= true;

	private static class MyTestSetup extends RefactoringTestSetup {
		private static IPackageFragment fSignaturePackage;
		private static IPackageFragment fGenericPackage;

		@Override
		public void beforeEach(ExtensionContext context) throws Exception {
			super.beforeEach(context);
			fSignaturePackage= getDefaultSourceFolder().createPackageFragment("signature", true, null);
			fGenericPackage= getDefaultSourceFolder().createPackageFragment("generic", true, null);
		}
		public static IPackageFragment getSignaturePackage() {
			return fSignaturePackage;
		}
		public static IPackageFragment getGenericPackage() {
			return fGenericPackage;
		}
	}

	private static class CreationChecker extends HierarchicalASTVisitor {
		private final TypeEnvironment fTypeEnvironment;
		public CreationChecker() {
			fTypeEnvironment= new TypeEnvironment();
		}
		@Override
		public boolean visit(SimpleName node) {
			IBinding binding= node.resolveBinding();
			if (!(binding instanceof ITypeBinding))
				return true;
			checkTypeBinding(binding);
			return true;
		}
		private void checkTypeBinding(IBinding binding) {
			ITypeBinding type= (ITypeBinding)binding;
			if (!type.isPrimitive() || !"void".equals(type.getName())) {
				TType refType= fTypeEnvironment.create(type);
				assertNotNull(refType, "Refactoring type is null");
				assertEquals(type.getName(), refType.getName(), "Not same name");
				assertEquals(PrettySignatures.get(type), refType.getPrettySignature(), "Not same signature");
				assertSame(refType, fTypeEnvironment.create(type), "Not same type");
			}
		}
		@Override
		public boolean visit(org.eclipse.jdt.core.dom.Type node) {
			checkTypeBinding(node.resolveBinding());
			return true;
		}
	}

	private static class TypeBindingCollector extends ASTVisitor {
		private final List<ITypeBinding> fResult= new ArrayList<>();
		private final List<ITypeBinding> fWildcards= new ArrayList<>();
		@Override
		public boolean visit(FieldDeclaration node) {
			List<VariableDeclarationFragment> fragments= node.fragments();
			VariableDeclarationFragment fragment= fragments.get(0);
			if ("NullType".equals(fragment.getName().getIdentifier())) {
				fResult.add(fragment.getInitializer().resolveTypeBinding());
			} else {
				fResult.add(fragment.resolveBinding().getType());
			}
			return false;
		}
		@Override
		public void endVisit(CompilationUnit node) {
			for (ITypeBinding binding : fResult) {
				if (binding.isParameterizedType()) {
					for (ITypeBinding arg : binding.getTypeArguments()) {
						if (arg.isWildcardType()) {
							fWildcards.add(arg);
						}
					}
				}
			}
		}
		public ITypeBinding[] getResult() {
			return fResult.toArray(new ITypeBinding[fResult.size()]);
		}
		public ITypeBinding[] getWildcards() {
			return fWildcards.toArray(new ITypeBinding[fWildcards.size()]);
		}
	}

	private static class CaptureTypeBindingCollector extends ASTVisitor {
		private final List<ITypeBinding> fResult= new ArrayList<>();
		@Override
		public boolean visit(Assignment node) {
			Expression expression= node.getRightHandSide();
			ITypeBinding typeBinding= expression.resolveTypeBinding();
			fResult.add(typeBinding);
			collectTypeArgumentBindings(typeBinding, fResult);
			return false;
		}
		private void collectTypeArgumentBindings(ITypeBinding typeBinding, List<ITypeBinding> result) {
			if (! typeBinding.isParameterizedType())
				return;
			for (ITypeBinding typeArgument : typeBinding.getTypeArguments()) {
				if (BUG_83616_core_wildcard_assignments && typeArgument.isParameterizedType() && typeArgument.getTypeArguments()[0].isWildcardType())
					continue;
				result.add(typeArgument);
				collectTypeArgumentBindings(typeArgument, result);
			}
		}
		public ITypeBinding[] getResult() {
			return fResult.toArray(new ITypeBinding[fResult.size()]);
		}
	}

	@RegisterExtension
	public MyTestSetup mts=new MyTestSetup();

	@Override
	protected InputStream getFileInputStream(String fileName) throws IOException {
		return RefactoringTestPlugin.getDefault().getTestResourceStream(fileName);
	}

	@Override
	protected String getResourceLocation() {
		return "TypeEnvironment/TestProject/";
	}

	@Override
	protected String adaptName(String name) {
		return Character.toUpperCase(name.charAt(0)) + name.substring(1) + ".java";
	}

	private ASTNode createAST(IPackageFragment pack) throws Exception {
		IJavaProject project= pack.getJavaProject();
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setProject(project);
		parser.setResolveBindings(true);
		ICompilationUnit unit= createCU(pack, getName());
		parser.setSource(unit);
		return parser.createAST(null);
	}

	//---- creation ----------------------------------------------------------

	private void performCreationTest() throws Exception {
		createAST(MyTestSetup.getSignaturePackage()).accept(new CreationChecker());
	}

	@Test
	public void testArrays() throws Exception {
		performCreationTest();
	}

	@Test
	public void testStandardTypes() throws Exception {
		performCreationTest();
	}

	@Test
	public void testRawTypes() throws Exception {
		performCreationTest();
	}

	@Test
	public void testGenericTypes() throws Exception {
		performCreationTest();
	}

	@Test
	public void testWildcardTypes() throws Exception {
		performCreationTest();
	}

	@Test
	public void testPrimitiveTypes() throws Exception {
		performCreationTest();
	}

	@Test
	public void testTypeVariables() throws Exception {
		performCreationTest();
	}

	//---- generic assigment test ----------------------------------------------

	private void performGenericAssignmentTest() throws Exception {
		ASTNode node= createAST(MyTestSetup.getGenericPackage());
		TypeBindingCollector collector= new TypeBindingCollector();
		node.accept(collector);
		testBindings(collector.getResult());
		testAssignment(collector.getWildcards());
	}

	private void testBindings(ITypeBinding[] bindings) throws Exception {
		TType[] types= new TType[bindings.length];
		TypeEnvironment environment= new TypeEnvironment();
		for (int i= 0; i < bindings.length; i++) {
			types[i]= environment.create(bindings[i]);
			assertEquals(bindings[i].getName(), types[i].getName(), "Not same name");
			assertEquals(PrettySignatures.get(bindings[i]), types[i].getPrettySignature(), "Not same signature");
			assertEquals(bindings[i].getModifiers(), types[i].getModifiers(), "Not same modifiers");
			testFlags(bindings[i], types[i]);
			assertTrue(types[i].getErasure().isEqualTo(bindings[i].getErasure()), "Not same erasure");
			assertTrue(types[i].getTypeDeclaration().isEqualTo(bindings[i].getTypeDeclaration()), "Not same type declaration");
			assertSame(types[i], environment.create(bindings[i]), "Not same type");

		}
		for (int o= 0; o < bindings.length; o++) {
			for (int i= 0; i < bindings.length; i++) {
				checkCanAssignTo(bindings[o], bindings[i], types[o], types[i]);
			}
		}
		TypeEnvironment secondEnvironment= new TypeEnvironment();
		for (int i= 0; i < bindings.length; i++) {
			assertEquals(types[i], secondEnvironment.create(bindings[i]), "Equal to second environment");
		}
		ITypeBinding[] restoredBindings= TypeEnvironment.createTypeBindings(types, mts.getProject());
		assertEquals(restoredBindings.length, bindings.length, "Not same length");
		for (int i= 0; i < restoredBindings.length; i++) {
			assertTrue(bindings[i].isEqualTo(restoredBindings[i]), "Not same binding");
		}
	}

	private void checkCanAssignTo(ITypeBinding rhsBinding, ITypeBinding lhsBinding, TType rhs, TType lhs) {
		boolean coreResult= rhsBinding.isAssignmentCompatible(lhsBinding);
		boolean uiResult= rhs.canAssignTo(lhs);
		assertEquals(coreResult, uiResult, "Different assignment rule(" + PrettySignatures.get(lhsBinding) + "= " + PrettySignatures.get(rhsBinding) + "): ");
	}

	private void testAssignment(ITypeBinding[] bindings) {
		TType[] types= new TType[bindings.length];
		TypeEnvironment environment= new TypeEnvironment();
		for (int i= 0; i < bindings.length; i++) {
			types[i]= environment.create(bindings[i]);
		}
		for (int o= 0; o < bindings.length; o++) {
			for (int i= 0; i < bindings.length; i++) {
				ITypeBinding oBinding= bindings[o];
				ITypeBinding iBinding= bindings[i];
				boolean coreResult= oBinding.isAssignmentCompatible(iBinding);
				TType oType= types[o];
				TType iType= types[i];
				boolean uiResult= oType.canAssignTo(iType);
				if (!BUG_83616_core_wildcard_assignments) {
					if (coreResult != uiResult && !oType.isWildcardType()) {
						System.out.println("Different assignment rule(" +
								PrettySignatures.get(iBinding) + "= " + PrettySignatures.get(oBinding) +
								"): Bindings<" + coreResult +
								"> TType<" + uiResult + ">");
					}
				}
			}
		}
	}

	private void testFlags(ITypeBinding binding, TType type) {
		assertEquals(binding.isClass(), type.isClass(), "Different class flag");
		assertEquals(binding.isEnum(), type.isEnum(), "Different enum flag");
		assertEquals(binding.isInterface(), type.isInterface(), "Different interface  flag");
		assertEquals(binding.isAnnotation(), type.isAnnotation(), "Different annotation flag");

		assertEquals(binding.isTopLevel(), type.isTopLevel(), "Different top level flag");
		assertEquals(binding.isNested(), type.isNested(), "Different nested flag");
		assertEquals(binding.isLocal(), type.isLocal(), "Different local flag");
		assertEquals(binding.isMember(), type.isMember(), "Different member flag");
		assertEquals(binding.isAnonymous(), type.isAnonymous(), "Different anonymous flag");
	}

	@Test
	public void testStandardAssignments() throws Exception {
		performGenericAssignmentTest();
	}

	@Test
	public void testWildcardAssignments() throws Exception {
		performGenericAssignmentTest();
	}

	@Test
	public void testTypeVariableAssignments() throws Exception {
		performGenericAssignmentTest();
	}

	@Test
	public void testCaptureAssignments() throws Exception {
		ASTNode node= createAST(MyTestSetup.getGenericPackage());
		CaptureTypeBindingCollector collector= new CaptureTypeBindingCollector();
		node.accept(collector);
		testBindings(collector.getResult());
	}

	public void _testAssignment() throws Exception {
		ASTNode node= createAST(MyTestSetup.getGenericPackage());
		TypeBindingCollector collector= new TypeBindingCollector();
		node.accept(collector);
		ITypeBinding[] bindings= collector.getResult();
		TType[] types= new TType[bindings.length];
		TypeEnvironment environment= new TypeEnvironment();
		for (int i= 0; i < bindings.length; i++) {
			types[i]= environment.create(bindings[i]);
		}
		System.out.println(PrettySignatures.get(bindings[0]) + "= " + PrettySignatures.get(bindings[1]) +
			": " + bindings[1].isAssignmentCompatible(bindings[0]));
		// types[1].canAssignTo(types[0]);
	}

	public void _testParameterizedToGeneric() throws Exception {
		ASTNode node= createAST(MyTestSetup.getGenericPackage());
		TypeBindingCollector collector= new TypeBindingCollector();
		node.accept(collector);
		ITypeBinding[] bindings= collector.getResult();
		bindings[0]= bindings[0].getTypeDeclaration();
		System.out.println(PrettySignatures.get(bindings[0]) + "= " + PrettySignatures.get(bindings[1]) +
			": " + bindings[1].isAssignmentCompatible(bindings[0]));
		System.out.println(PrettySignatures.get(bindings[0]) + "= " + PrettySignatures.get(bindings[0]) +
			": " + bindings[0].isAssignmentCompatible(bindings[0]));
		bindings[1]= bindings[1].getTypeDeclaration();
		System.out.println(PrettySignatures.get(bindings[0]) + "= " + PrettySignatures.get(bindings[1]) +
			": " + bindings[1].isAssignmentCompatible(bindings[0]));
	}
}
