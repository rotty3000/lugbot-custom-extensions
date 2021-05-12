/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.liferay.lugbot.custom.extension;

import com.liferay.ide.upgrade.plan.core.UpgradeProblem;
import com.liferay.ide.upgrade.problems.core.AutoFileMigrator;
import com.liferay.ide.upgrade.problems.core.AutoFileMigratorException;
import com.liferay.ide.upgrade.problems.core.FileMigrator;
import com.liferay.ide.upgrade.problems.core.FileSearchResult;
import com.liferay.ide.upgrade.problems.core.JavaFile;

import java.io.File;

import java.nio.file.Files;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

import org.osgi.service.component.annotations.Component;

/**
 * @author Liferay
 */
@Component(
	name = "spring-mvc-portlet-migrator",
	property = {
		"file.extensions=java", "problem.title=Spring MVC Portlet Fix", "problem.summary=Spring MVC Portlet Fix",
		"problem.tickets=LPS-0", "problem.section=#", "auto.correct=import", "version=7.0"
	},
	service = {AutoFileMigrator.class, FileMigrator.class}
)
public class SpringMVCPortletMigrator extends AbstractFileMigrator<JavaFile> implements AutoFileMigrator {

	public SpringMVCPortletMigrator() {
		super(JavaFile.class);
	}

	@Override
	@SuppressWarnings("unchecked")
	public int correctProblems(File file, Collection<UpgradeProblem> upgradeProblems) throws AutoFileMigratorException {
		int problemsFixed = 0;

		try {
			JavaFile javaFile = createFileService(JavaFile.class, file, "java");

			for (UpgradeProblem problem : upgradeProblems) {
				final MethodDeclaration[] methodDeclaration = new MethodDeclaration[1];

				CompilationUnit cu = javaFile.getCU();

				AST ast = cu.getAST();

				cu.accept(
					new ASTVisitor() {

						public boolean visit(MethodDeclaration node) {
							SimpleName methodNameNode = node.getName();

							if ((problem.getStartOffset() == methodNameNode.getStartPosition()) &&
								(problem.getEndOffset() ==
									(methodNameNode.getStartPosition() + methodNameNode.getLength()))) {

								methodDeclaration[0] = node;
							}

							return false;
						}

					});

				if (methodDeclaration[0] != null) {
					MethodInvocation methodInvocation = ast.newMethodInvocation();

					// System.out.println("Hello, World")

					QualifiedName qName = ast.newQualifiedName(ast.newSimpleName("System"), ast.newSimpleName("out"));

					methodInvocation.setExpression(qName);

					methodInvocation.setName(ast.newSimpleName("println"));

					StringLiteral literal = ast.newStringLiteral();

					literal.setLiteralValue("Hello, World");

					List<Object> arguments = methodInvocation.arguments();

					arguments.add(literal);

					ASTRewrite rewriter = ASTRewrite.create(ast);

					ListRewrite lrw = rewriter.getListRewrite(
						methodDeclaration[0].getBody(), Block.STATEMENTS_PROPERTY);

					lrw.insertLast(ast.newExpressionStatement(methodInvocation), null);

					Document document = new Document(new String(Files.readAllBytes(file.toPath())));

					TextEdit edits = rewriter.rewriteAST(document, null);

					edits.apply(document);

					String content = document.get();

					Files.write(file.toPath(), content.getBytes());

					javaFile.reload(file);

					problemsFixed++;
				}
			}
		}
		catch (Exception e) {
			throw new AutoFileMigratorException("Failure in correcting problems", e);
		}

		return problemsFixed;
	}

	@Override
	public List<FileSearchResult> searchFile(File file, JavaFile javaFile) {
		List<FileSearchResult> searchResults = new ArrayList<>();

		CompilationUnit cu = javaFile.getCU();

		cu.accept(
			new ASTVisitor() {

				@Override
				public boolean visit(MethodDeclaration methodDeclaration) {
					SimpleName methodNameNode = methodDeclaration.getName();

					String methodName = methodNameNode.getIdentifier();

					if (Objects.equals("foobar", methodName)) {
						int startLine = cu.getLineNumber(methodNameNode.getStartPosition());
						int startOffset = methodNameNode.getStartPosition();
						int endLine = cu.getLineNumber(methodNameNode.getStartPosition() + methodNameNode.getLength());
						int endOffset = methodNameNode.getStartPosition() + methodNameNode.getLength();

						searchResults.add(
							new FileSearchResult(file, methodName, startOffset, endOffset, startLine, endLine, true));
					}

					return false;
				}

			});

		return searchResults;
	}

}