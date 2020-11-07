/*
 * Copyright 2016 Google Inc.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.j2cl.transpiler.ast.AbstractVisitor;
import com.google.j2cl.transpiler.ast.CompilationUnit;
import com.google.j2cl.transpiler.ast.Field;
import com.google.j2cl.transpiler.ast.Member;
import com.google.j2cl.transpiler.ast.Method;
import com.google.j2cl.transpiler.ast.MultiExpression;
import com.google.j2cl.transpiler.ast.Type;

/** Verifies that the AST satisfies the normalization invariants. */
public class VerifyNormalizedUnits extends NormalizationPass {

  @Override
  public void applyTo(CompilationUnit compilationUnit) {
    compilationUnit.accept(
        new AbstractVisitor() {
          @Override
          public void exitType(Type type) {
            // Native and JsFunction types should have been removed from the AST.
            checkState(!type.isNative());
            checkState(!type.isJsFunctionInterface());
          }

          @Override
          public void exitField(Field field) {
            checkState(!field.isNative());
          }

          @Override
          public void exitMethod(Method method) {
            // All native methods should be empty.
            checkState(!method.isNative() || method.getBody().isEmpty());
          }

          @Override
          public void exitMember(Member member) {
            // JsEnum only contains the enum fields.
            if (getCurrentType().isJsEnum()) {
              checkState(member.isField() || member.isStatic());
            }
          }

          @Override
          public void exitMultiExpression(MultiExpression multiExpression) {
            // No empty nor singleton multiexpressions should remain.
            checkState(multiExpression.getExpressions().size() > 1);
          }
        });
  }
}
