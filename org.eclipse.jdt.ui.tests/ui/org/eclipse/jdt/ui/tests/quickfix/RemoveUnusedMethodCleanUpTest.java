/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Contributors to the Eclipse Foundation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.jdt.ui.tests.core.rules.ProjectTestSetup;
import org.eclipse.jdt.internal.ui.fix.RemoveUnusedMethodCleanUp;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for the RemoveUnusedMethodCleanUp multi-file cleanup.
 * <p>
 * This test demonstrates a concrete use case: removing an unused method
 * from both a class implementation and its interface declaration.
 * </p>
 */
public class RemoveUnusedMethodCleanUpTest extends CleanUpTestCase {

	@Rule
	public ProjectTestSetup projectSetup = new ProjectTestSetup();

	@Override
	protected IJavaProject getProject() {
		return projectSetup.getProject();
	}

	@Override
	protected IClasspathEntry[] getDefaultClasspath() throws CoreException {
		return projectSetup.getDefaultClasspath();
	}

	/**
	 * Test the complete scenario: Interface, Implementation, and Main class.
	 * The unused method should be removed from both interface and implementation.
	 */
	@Test
	public void testRemoveUnusedMethodWithInterface() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test", false, null); //$NON-NLS-1$

		// 1. Create interface with two method declarations
		String interfaceInput = """
				package test;
				
				public interface IService {
					void usedMethod();
					void unusedMethod();
				}
				""";

		// 2. Create implementation class
		String implementationInput = """
				package test;
				
				public class ServiceImpl implements IService {
					@Override
					public void usedMethod() {
						System.out.println("This method is used");
					}
					
					@Override
					public void unusedMethod() {
						System.out.println("This method is never called");
					}
				}
				""";

		// 3. Create main class that only calls usedMethod
		String mainInput = """
				package test;
				
				public class Main {
					public static void main(String[] args) {
						IService service = new ServiceImpl();
						service.usedMethod();  // Only this method is called
						// service.unusedMethod() is never called
					}
				}
				""";

		ICompilationUnit interfaceCU = pack.createCompilationUnit("IService.java", interfaceInput, false, null); //$NON-NLS-1$
		ICompilationUnit implCU = pack.createCompilationUnit("ServiceImpl.java", implementationInput, false, null); //$NON-NLS-1$
		ICompilationUnit mainCU = pack.createCompilationUnit("Main.java", mainInput, false, null); //$NON-NLS-1$

		// Apply the multi-file cleanup
		ICleanUp cleanup = new RemoveUnusedMethodCleanUp();
		CleanUpOptions options = new CleanUpOptions();
		options.setOption(RemoveUnusedMethodCleanUp.CLEANUP_REMOVE_UNUSED_METHOD, CleanUpOptions.TRUE);
		cleanup.setOptions(options);

		// Perform cleanup on all three files
		Change[] changes = performCleanUp(cleanup, new ICompilationUnit[] { interfaceCU, implCU, mainCU });

		assertNotNull("Changes should not be null", changes); //$NON-NLS-1$
		assertTrue("Should have changes", changes.length > 0); //$NON-NLS-1$

		// Verify the results
		String interfaceResult = interfaceCU.getSource();
		String implResult = implCU.getSource();
		String mainResult = mainCU.getSource();

		// Interface should no longer have unusedMethod
		assertTrue("Interface should still have usedMethod", interfaceResult.contains("usedMethod")); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue("Interface should not have unusedMethod", !interfaceResult.contains("unusedMethod")); //$NON-NLS-1$ //$NON-NLS-2$

		// Implementation should no longer have unusedMethod
		assertTrue("Implementation should still have usedMethod", implResult.contains("usedMethod")); //$NON-NLS-1$ //$NON-NLS-2$
		assertTrue("Implementation should not have unusedMethod", !implResult.contains("unusedMethod")); //$NON-NLS-1$ //$NON-NLS-2$

		// Main should remain unchanged
		assertTrue("Main should still call usedMethod", mainResult.contains("service.usedMethod()")); //$NON-NLS-1$ //$NON-NLS-2$

		// Print results for verification
		System.out.println("=== Interface after cleanup ==="); //$NON-NLS-1$
		System.out.println(interfaceResult);
		System.out.println("\n=== Implementation after cleanup ==="); //$NON-NLS-1$
		System.out.println(implResult);
		System.out.println("\n=== Main (unchanged) ==="); //$NON-NLS-1$
		System.out.println(mainResult);
	}

	/**
	 * Test case where all methods are used - no cleanup should occur.
	 */
	@Test
	public void testNoCleanupWhenAllMethodsUsed() throws Exception {
		IPackageFragment pack = fSourceFolder.createPackageFragment("test2", false, null); //$NON-NLS-1$

		String interfaceInput = """
				package test2;
				
				public interface ICalculator {
					int add(int a, int b);
					int multiply(int a, int b);
				}
				""";

		String implementationInput = """
				package test2;
				
				public class Calculator implements ICalculator {
					@Override
					public int add(int a, int b) {
						return a + b;
					}
					
					@Override
					public int multiply(int a, int b) {
						return a * b;
					}
				}
				""";

		String mainInput = """
				package test2;
				
				public class MathApp {
					public static void main(String[] args) {
						ICalculator calc = new Calculator();
						System.out.println(calc.add(2, 3));
						System.out.println(calc.multiply(4, 5));
					}
				}
				""";

		ICompilationUnit interfaceCU = pack.createCompilationUnit("ICalculator.java", interfaceInput, false, null); //$NON-NLS-1$
		ICompilationUnit implCU = pack.createCompilationUnit("Calculator.java", implementationInput, false, null); //$NON-NLS-1$
		ICompilationUnit mainCU = pack.createCompilationUnit("MathApp.java", mainInput, false, null); //$NON-NLS-1$

		ICleanUp cleanup = new RemoveUnusedMethodCleanUp();
		CleanUpOptions options = new CleanUpOptions();
		options.setOption(RemoveUnusedMethodCleanUp.CLEANUP_REMOVE_UNUSED_METHOD, CleanUpOptions.TRUE);
		cleanup.setOptions(options);

		Change[] changes = performCleanUp(cleanup, new ICompilationUnit[] { interfaceCU, implCU, mainCU });

		// Should have no changes since all methods are used
		assertTrue("Should have no changes or null", changes == null || changes.length == 0); //$NON-NLS-1$
	}

	private Change[] performCleanUp(ICleanUp cleanUp, ICompilationUnit[] units) throws Exception {
		org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring refactoring = new org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring();
		refactoring.addCleanUp(cleanUp);

		for (ICompilationUnit unit : units) {
			refactoring.addCompilationUnit(unit);
		}

		RefactoringStatus status = refactoring.checkAllConditions(null);
		if (status.hasFatalError()) {
			return null;
		}

		Change change = refactoring.createChange(null);
		if (change == null) {
			return null;
		}

		change.perform(null);

		if (change instanceof CompositeChange) {
			return ((CompositeChange) change).getChildren();
		} else {
			return new Change[] { change };
		}
	}
}
