/*
 * Copyright 2018 Google Inc.
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
package com.google.j2cl.ast.visitors;

import com.google.common.collect.ImmutableList;
import com.google.j2cl.ast.AbstractRewriter;
import com.google.j2cl.ast.Block;
import com.google.j2cl.ast.CompilationUnit;
import com.google.j2cl.ast.RuntimeMethods;
import com.google.j2cl.ast.Statement;
import com.google.j2cl.ast.SynchronizedStatement;
import com.google.j2cl.common.SourcePosition;

/** Replaces synchronized statements with the corresponding method call to the runtime. */
public class ImplementSynchronizedStatements extends NormalizationPass {

  @Override
  public void applyTo(CompilationUnit compilationUnit) {
    compilationUnit.accept(
        new AbstractRewriter() {
          @Override
          public Statement rewriteSynchronizedStatement(
              SynchronizedStatement synchronizedStatement) {
            SourcePosition sourcePosition = synchronizedStatement.getSourcePosition();
            return new Block(
                sourcePosition,
                ImmutableList.<Statement>builder()
                    .add(
                        RuntimeMethods.createUtilMethodCall(
                                "$synchronized",
                                ImmutableList.of(synchronizedStatement.getExpression()))
                            .makeStatement(sourcePosition))
                    .addAll(synchronizedStatement.getBody().getStatements())
                    .build());
          }
        });
  }
}
