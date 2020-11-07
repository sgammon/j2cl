/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.j2cl.transpiler.passes;

import com.google.j2cl.transpiler.ast.AbstractRewriter;
import com.google.j2cl.transpiler.ast.Block;
import com.google.j2cl.transpiler.ast.CompilationUnit;
import com.google.j2cl.transpiler.ast.DoWhileStatement;
import com.google.j2cl.transpiler.ast.ForStatement;
import com.google.j2cl.transpiler.ast.IfStatement;
import com.google.j2cl.transpiler.ast.Statement;
import com.google.j2cl.transpiler.ast.WhileStatement;

/** Makes sure that body of conditional are always blocks (except in the else if case). */
public class ControlStatementFormatter extends NormalizationPass {
  @Override
  public void applyTo(CompilationUnit compilationUnit) {
    compilationUnit.accept(
        new AbstractRewriter() {
          @Override
          public IfStatement rewriteIfStatement(IfStatement ifStatement) {
            Statement thenStatement = ifStatement.getThenStatement();
            Statement elseStatement = ifStatement.getElseStatement();
            if (thenStatement instanceof Block
                && (elseStatement == null
                    || elseStatement instanceof Block
                    || elseStatement instanceof IfStatement)) {
              return ifStatement;
            }

            thenStatement =
                thenStatement instanceof Block
                    ? thenStatement
                    : Block.newBuilder()
                        .setSourcePosition(thenStatement.getSourcePosition())
                        .setStatements(thenStatement)
                        .build();
            elseStatement =
                elseStatement == null
                        || elseStatement instanceof Block
                        || elseStatement instanceof IfStatement
                    ? elseStatement
                    : Block.newBuilder()
                        .setSourcePosition(elseStatement.getSourcePosition())
                        .setStatements(elseStatement)
                        .build();
            return IfStatement.newBuilder()
                .setSourcePosition(ifStatement.getSourcePosition())
                .setConditionExpression(ifStatement.getConditionExpression())
                .setThenStatement(thenStatement)
                .setElseStatement(elseStatement)
                .build();
          }

          @Override
          public ForStatement rewriteForStatement(ForStatement forStatement) {
            Statement body = forStatement.getBody();
            if (body instanceof Block) {
              return forStatement;
            }

            return ForStatement.newBuilder()
                .setConditionExpression(forStatement.getConditionExpression())
                .setBody(body)
                .setInitializers(forStatement.getInitializers())
                .setUpdates(forStatement.getUpdates())
                .build();
          }

          @Override
          public DoWhileStatement rewriteDoWhileStatement(DoWhileStatement doWhileStatement) {
            Statement body = doWhileStatement.getBody();
            if (body instanceof Block) {
              return doWhileStatement;
            }

            return DoWhileStatement.newBuilder()
                .setSourcePosition(doWhileStatement.getSourcePosition())
                .setConditionExpression(doWhileStatement.getConditionExpression())
                .setBody(
                    Block.newBuilder()
                        .setSourcePosition(body.getSourcePosition())
                        .setStatements(body)
                        .build())
                .build();
          }

          @Override
          public WhileStatement rewriteWhileStatement(WhileStatement whileStatement) {
            Statement body = whileStatement.getBody();
            if (body instanceof Block) {
              return whileStatement;
            }

            return WhileStatement.newBuilder()
                .setSourcePosition(whileStatement.getSourcePosition())
                .setConditionExpression(whileStatement.getConditionExpression())
                .setBody(
                    Block.newBuilder()
                        .setSourcePosition(body.getSourcePosition())
                        .setStatements(body)
                        .build())
                .build();
          }
        });
  }

}
