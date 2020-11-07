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
import com.google.j2cl.transpiler.ast.AstUtils;
import com.google.j2cl.transpiler.ast.CompilationUnit;
import com.google.j2cl.transpiler.ast.Method;
import com.google.j2cl.transpiler.ast.MethodDescriptor;
import com.google.j2cl.transpiler.ast.Type;
import com.google.j2cl.transpiler.ast.TypeDescriptor;
import com.google.j2cl.transpiler.ast.TypeDescriptors;

/** Creates the devirtualized methods for devirtualized boxed types. */
public class DevirtualizeBoxedTypesAndJsFunctionImplementations extends NormalizationPass {
  @Override
  public void applyTo(CompilationUnit compilationUnit) {
    compilationUnit.accept(
        new AbstractRewriter() {
          @Override
          public boolean shouldProcessType(Type type) {
            // Creates devirtualized static methods for the boxed types (Boolean, Double, String).
            return TypeDescriptors.isBoxedTypeAsJsPrimitives(type.getTypeDescriptor())
                || type.getDeclaration().isJsFunctionImplementation();
          }

          @Override
          public Method rewriteMethod(Method method) {
            if (!shouldDevirtualize(method)) {
              return method;
            }

            return AstUtils.devirtualizeMethod(
                method, method.getDescriptor().getEnclosingTypeDescriptor());
          }

          private boolean shouldDevirtualize(Method method) {
            MethodDescriptor methodDescriptor = method.getDescriptor();
            if (!methodDescriptor.isInstanceMember()) {
              return false;
            }

            if (isActualJsFunction(methodDescriptor)) {
              return false;
            }
            return true;
          }
        });
  }

  private static boolean isActualJsFunction(MethodDescriptor methodDescriptor) {
    // If the user specialized the JsFunction, then the JsFunction method will have a different
    // signature from the overridden method from the JsFunction interface.
    // In this case there will be a bridge that will become the actual JsFunction implementation
    // that delegates to the user written JsFunction method. Both methods are marked as JsFunction
    // (it would be better if the bridge creator removed the JsFunction property on the specialized
    // method). The user written specialized would need to be devirtualized.
    TypeDescriptor enclosingTypeDescriptor = methodDescriptor.getEnclosingTypeDescriptor();
    return methodDescriptor.isJsFunction()
        && methodDescriptor
            .getMangledName()
            .equals(
                enclosingTypeDescriptor
                    .getFunctionalInterface()
                    .getJsFunctionMethodDescriptor()
                    .getDeclarationDescriptor()
                    .getMangledName());
  }
}
