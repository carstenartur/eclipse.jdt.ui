/*******************************************************************************
 * Copyright (c) 2021, 2025 Fabrice TIERCELIN and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Fabrice TIERCELIN - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.fix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;

import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFix;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.jdt.ui.text.java.IProblemLocation;

import org.eclipse.jdt.internal.ui.fix.SimplifyBooleanIfElseCleanUpCore.SimplifyStatus;

/**
 * A fix that removes useless indentation when the opposite workflow falls through:
 * <ul>
 * <li>When several blocks fall through, reduce the block with the greatest indentation.</li>
 * </ul>
 */
public class ReduceIndentationCleanUp extends AbstractMultiFix {
	private static final class IndentationVisitor extends ASTVisitor {
		private int indentation;

		public int getIndentation() {
			return indentation;
		}

		@Override
		public boolean visit(final IfStatement visited) {
			computeGreatestIndentation(visited.getThenStatement());

			if (visited.getElseStatement() != null) {
				computeGreatestIndentation(visited.getElseStatement());
			}

			return false;
		}

		@Override
		public boolean visit(final WhileStatement visited) {
			computeGreatestIndentation(visited.getBody());
			return false;
		}

		@Override
		public boolean visit(final DoStatement visited) {
			computeGreatestIndentation(visited.getBody());
			return false;
		}

		@Override
		public boolean visit(final ForStatement visited) {
			computeGreatestIndentation(visited.getBody());
			return false;
		}

		@Override
		public boolean visit(final EnhancedForStatement visited) {
			computeGreatestIndentation(visited.getBody());
			return false;
		}

		@Override
		public boolean visit(final TryStatement visited) {
			computeGreatestIndentation(visited.getBody());

			for (Object object : visited.catchClauses()) {
				CatchClause clause= (CatchClause) object;
				computeGreatestIndentation(clause.getBody());
			}

			if (visited.getFinally() != null) {
				computeGreatestIndentation(visited.getFinally());
			}

			if (visited.getFinally() != null) {
				computeGreatestIndentation(visited.getFinally());
			}

			return false;
		}

		@Override
		public boolean visit(final Block visited) {
			computeGreatestIndentation(visited);
			return false;
		}

		private void computeGreatestIndentation(final Statement statements) {
			for (Statement statement : ASTNodes.asList(statements)) {
				IndentationVisitor visitor= new IndentationVisitor();

				statement.accept(visitor);

				indentation= Math.max(indentation, visitor.getIndentation() + 1);
			}
		}
	}

	public ReduceIndentationCleanUp() {
		this(Collections.emptyMap());
	}

	public ReduceIndentationCleanUp(final Map<String, String> options) {
		super(options);
	}

	@Override
	public CleanUpRequirements getRequirements() {
		boolean requireAST= isEnabled(CleanUpConstants.REDUCE_INDENTATION);
		return new CleanUpRequirements(requireAST, false, false, null);
	}

	@Override
	public String[] getStepDescriptions() {
		if (isEnabled(CleanUpConstants.REDUCE_INDENTATION)) {
			return new String[] { MultiFixMessages.CodeStyleCleanUp_ReduceIndentation_description };
		}

		return new String[0];
	}

	@Override
	public String getPreview() {
		StringBuilder bld= new StringBuilder();
		bld.append("if (i > 0) {\n"); //$NON-NLS-1$
		bld.append("    return 0;\n"); //$NON-NLS-1$

		if (isEnabled(CleanUpConstants.REDUCE_INDENTATION)) {
			bld.append("}\n"); //$NON-NLS-1$
			bld.append("i = i + 1;\n\n"); //$NON-NLS-1$
		} else {
			bld.append("} else {\n"); //$NON-NLS-1$
			bld.append("    i = i + 1;\n"); //$NON-NLS-1$
			bld.append("}\n"); //$NON-NLS-1$
		}

		return bld.toString();
	}

	@Override
	protected ICleanUpFix createFix(final CompilationUnit unit) throws CoreException {
		if (!isEnabled(CleanUpConstants.REDUCE_INDENTATION)) {
			return null;
		}

		final List<CompilationUnitRewriteOperationWithSourceRange> rewriteOperations= new ArrayList<>();

		unit.accept(new ASTVisitor() {
			@Override
			public boolean visit(final IfStatement visited) {
				return processIfStatement(visited, null);
			}

			private boolean processIfStatement(final IfStatement visited, ReduceIndentationElseOperation currentOp) {
				// The parsing crashes when there are two embedded lone ifs with an end of line comment at the right of the statement
				// So we disable the rule on double lone if
				if (currentOp == null && !(visited.getElseStatement() instanceof Block)
						&& !ASTNodes.canHaveSiblings(visited)) {
					return true;
				}

				if (isEnabled(CleanUpConstants.SIMPLIFY_BOOLEAN_IF_ELSE)) {
					if (SimplifyBooleanIfElseCleanUpCore.verifyBooleanIfElse(visited) != SimplifyStatus.INVALID) {
						return true;
					}
				}

				if (visited.getElseStatement() != null && (currentOp != null || !ASTNodes.isInElse(visited))) {
					if (ASTNodes.fallsThrough(visited.getThenStatement())) {
						if (ASTNodes.fallsThrough(visited.getElseStatement())) {
							if (ASTNodes.getNextSiblings(visited).isEmpty()) {
								int thenIndentation= getIndentation(visited.getThenStatement());
								int elseIndentation= getIndentation(visited.getElseStatement());

								if (thenIndentation <= elseIndentation || visited.getElseStatement() instanceof IfStatement) {
									if (currentOp != null) {
										currentOp.addNewIfStmt(visited);
									} else {
										currentOp= new ReduceIndentationElseOperation(visited);
										rewriteOperations.add(currentOp);
									}
									if (visited.getElseStatement() instanceof IfStatement innerIf) {
										processIfStatement(innerIf, currentOp);
									}
								} else {
									rewriteOperations.add(new ReduceIndentationThenOperation(visited));
								}

								return false;
							}
						} else if (!ASTNodes.hasVariableConflict(visited, visited.getElseStatement())) {
							rewriteOperations.add(new ReduceIndentationElseOperation(visited));
							return false;
						}
					} else if (ASTNodes.fallsThrough(visited.getElseStatement())
							&& !ASTNodes.hasVariableConflict(visited, visited.getThenStatement())
							&& !(visited.getElseStatement() instanceof IfStatement)) {
						rewriteOperations.add(new ReduceIndentationThenOperation(visited));
						return false;
					}
				}

				return true;
			}

			private int getIndentation(final Statement statementInIf) {
				IndentationVisitor visitor= new IndentationVisitor();
				statementInIf.accept(visitor);
				return visitor.getIndentation() + (statementInIf instanceof Block ? -1 : 0);
			}
		});

		if (rewriteOperations.isEmpty()) {
			return null;
		}

		return new CompilationUnitRewriteOperationsFix(MultiFixMessages.CodeStyleCleanUp_ReduceIndentation_description, unit,
				rewriteOperations.toArray(new CompilationUnitRewriteOperationWithSourceRange[0]));
	}

	@Override
	public boolean canFix(final ICompilationUnit compilationUnit, final IProblemLocation problem) {
		return false;
	}

	@Override
	protected ICleanUpFix createFix(final CompilationUnit unit, final IProblemLocation[] problems) throws CoreException {
		return null;
	}

	private static class ReduceIndentationThenOperation extends CompilationUnitRewriteOperationWithSourceRange {
		private final IfStatement visited;

		public ReduceIndentationThenOperation(final IfStatement visited) {
			this.visited= visited;
		}

		@Override
		public void rewriteASTInternal(final CompilationUnitRewrite cuRewrite, final LinkedProposalModelCore linkedModel) throws CoreException {
			ASTRewrite rewrite= cuRewrite.getASTRewrite();
			AST ast= cuRewrite.getRoot().getAST();
			TextEditGroup group= createTextEditGroup(MultiFixMessages.CodeStyleCleanUp_ReduceIndentation_description, cuRewrite);

			List<Statement> statementsToMove= ASTNodes.asList(visited.getThenStatement());

			ASTNode moveTarget= null;
			if (statementsToMove.size() == 1) {
				moveTarget= ASTNodes.createMoveTarget(rewrite, statementsToMove.get(0));
			} else if (!statementsToMove.isEmpty()) {
				ListRewrite listRewrite= rewrite.getListRewrite(statementsToMove.get(0).getParent(), (ChildListPropertyDescriptor) statementsToMove.get(0).getLocationInParent());
				moveTarget= listRewrite.createMoveTarget(statementsToMove.get(0), statementsToMove.get(statementsToMove.size() - 1));
			}

			rewrite.replace(visited.getExpression(), ASTNodeFactory.negateAndUnwrap(ast, rewrite, visited.getExpression(), true, true), group);
			ASTNodes.replaceButKeepComment(rewrite, visited.getThenStatement(), ASTNodes.createMoveTarget(rewrite, visited.getElseStatement()), group);

			if (!statementsToMove.isEmpty()) {
				if (ASTNodes.canHaveSiblings(visited)) {
					ListRewrite targetListRewrite= rewrite.getListRewrite(visited.getParent(), (ChildListPropertyDescriptor) visited.getLocationInParent());
					targetListRewrite.insertAfter(moveTarget, visited, group);
					rewrite.remove(visited.getElseStatement(), group);
				} else {
					Block newBlock= ast.newBlock();
					newBlock.statements().add(ASTNodes.createMoveTarget(rewrite, visited));
					newBlock.statements().add(moveTarget);
					ASTNodes.replaceButKeepComment(rewrite, visited, newBlock, group);
				}
			}
		}
	}

	private static class ReduceIndentationElseOperation extends CompilationUnitRewriteOperationWithSourceRange {
		private final List<IfStatement> ifStmtList= new ArrayList<>();

		public ReduceIndentationElseOperation(final IfStatement visited) {
			this.ifStmtList.add(visited);
		}

		public void addNewIfStmt(final IfStatement visited) {
			ifStmtList.add(visited);
		}

		@Override
		public void rewriteASTInternal(final CompilationUnitRewrite cuRewrite, final LinkedProposalModelCore linkedModel) throws CoreException {
			ASTRewrite rewrite= cuRewrite.getASTRewrite();
			AST ast= cuRewrite.getRoot().getAST();
			TextEditGroup group= createTextEditGroup(MultiFixMessages.CodeStyleCleanUp_ReduceIndentation_description, cuRewrite);

			if (ifStmtList.size() == 1) {
				IfStatement visited= ifStmtList.get(0);
				List<Statement> statementsToMove= ASTNodes.asList(visited.getElseStatement());

				ASTNode moveTarget= null;
				if (statementsToMove.size() == 1) {
					moveTarget= ASTNodes.createMoveTarget(rewrite, statementsToMove.get(0));
				} else if (!statementsToMove.isEmpty()) {
					ListRewrite listRewrite= rewrite.getListRewrite(statementsToMove.get(0).getParent(), (ChildListPropertyDescriptor) statementsToMove.get(0).getLocationInParent());
					moveTarget= listRewrite.createMoveTarget(statementsToMove.get(0), statementsToMove.get(statementsToMove.size() - 1));
				}

				if (!statementsToMove.isEmpty()) {
					if (ASTNodes.canHaveSiblings(visited)) {
						ListRewrite targetListRewrite= rewrite.getListRewrite(visited.getParent(), (ChildListPropertyDescriptor) visited.getLocationInParent());
						targetListRewrite.insertAfter(moveTarget, visited, group);
					} else {
						Block newBlock= ast.newBlock();
						newBlock.statements().add(ASTNodes.createMoveTarget(rewrite, visited));
						newBlock.statements().add(moveTarget);

						ASTNodes.replaceButKeepComment(rewrite, visited, newBlock, group);
					}
				}

				rewrite.remove(visited.getElseStatement(), group);
			} else {
				Block block= null;
				ListRewrite listRewrite= null;
				IfStatement originalIf= ifStmtList.get(0);
				int index= 0;
				if (ifStmtList.get(0).getLocationInParent() != Block.STATEMENTS_PROPERTY) {
					block= ast.newBlock();
					listRewrite= rewrite.getListRewrite(block, Block.STATEMENTS_PROPERTY);
				} else {
					listRewrite= rewrite.getListRewrite(originalIf.getParent(), Block.STATEMENTS_PROPERTY);
					List<Statement> originalList= listRewrite.getOriginalList();
					for (int i= 0; i < originalList.size(); ++i) {
						if (originalList.get(i).equals(originalIf)) {
							index= i + 1;
							break;
						}
					}
				}

				for (IfStatement ifStmt : ifStmtList) {
					IfStatement newIfStmt= ast.newIfStatement();
					newIfStmt.setExpression((Expression) rewrite.createCopyTarget(ifStmt.getExpression()));
					newIfStmt.setThenStatement((Statement) rewrite.createCopyTarget(ifStmt.getThenStatement()));
					listRewrite.insertAt(newIfStmt, index++, group);
				}
				IfStatement lastIfStatement= ifStmtList.get(ifStmtList.size() - 1);
				Statement lastStatement= lastIfStatement.getElseStatement();
				List<Statement> lastStatements= null;
				if (lastStatement instanceof Block b) {
					lastStatements= b.statements();
				} else {
					lastStatements= new ArrayList<>();
					lastStatements.add(lastStatement);
				}
				for (Statement stmt : lastStatements) {
					Statement copy= (Statement) rewrite.createCopyTarget(stmt);
					listRewrite.insertAt(copy, index++, group);
				}

				if (block != null) {
					ASTNodes.replaceButKeepComment(rewrite, ifStmtList.get(0), block, group);
				} else {
					ASTNodes.removeButKeepComment(rewrite, ifStmtList.get(0), group);
				}
			}
			final IDocument document= new Document(cuRewrite.getCu().getBuffer().getContents());

			try {
				TextEdit t = rewrite.rewriteAST(document, null);
				t.apply(document);
				System.out.println(document.get(0, document.getLength()));
			} catch (BadLocationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
