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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.MoreCollectors;
import com.google.j2cl.transpiler.ast.AbstractVisitor;
import com.google.j2cl.transpiler.ast.AstUtils;
import com.google.j2cl.transpiler.ast.DeclaredTypeDescriptor;
import com.google.j2cl.transpiler.ast.Method;
import com.google.j2cl.transpiler.ast.MethodCall;
import com.google.j2cl.transpiler.ast.MethodDescriptor;
import com.google.j2cl.transpiler.ast.Type;
import java.util.Optional;

/**
 * Makes the implicit super call in a constructor explicit.
 *
 * <p>The implicit super call invokes the default constructor that has an empty parameter list.
 */
public class InsertExplicitSuperCalls extends NormalizationPass {

  @Override
  public void applyTo(Type type) {
    if (type.isInterface() || type.getSuperTypeDescriptor() == null) {
      return;
    }

    type.accept(
        new AbstractVisitor() {
          @Override
          public void exitMethod(Method method) {
            if (!method.isConstructor()) {
              return;
            }
            if (AstUtils.hasConstructorInvocation(method)) {
              return;
            }
            // Only inserts explicit super() call to a constructor that does not have
            // super() or this() call (provided that the type has a superclass).
            synthesizeSuperCall(method);
          }
        });
  }

  private static void synthesizeSuperCall(Method constructor) {
    DeclaredTypeDescriptor typeDescriptor =
        constructor.getDescriptor().getEnclosingTypeDescriptor();
    MethodDescriptor superConstructor = getDefaultSuperConstructorTarget(typeDescriptor);

    constructor
        .getBody()
        .getStatements()
        .add(
            0,
            MethodCall.Builder.from(superConstructor)
                .setArguments(AstUtils.maybePackageVarargs(superConstructor, ImmutableList.of()))
                .build()
                .makeStatement(constructor.getBody().getSourcePosition()));
  }

  /**
   * Returns the superconstructor method that should be the target of the implicit super() call.
   *
   * <p>Note: Implicit super constructor calls are inserted when no explicit call to super() or
   * this() appear in the constructor (JLS 8.8.7). Such call, super() with no parameters, is treated
   * as if written by the user and is subject to standard overloading rules (JLS 15.12.2.1 to
   * 15.12.2.6). JLS 15.12.2 describes how the match should be made. The rules are rather complex
   * but they become simpler when the method is not an instance method, and boil down to finding the
   * most specific overload.
   *
   * <p>Since the implicit method call has no parameters the only other possible match apart of the
   * 0-parameter constructor is the 1-parameter varargs which could have many overloads.
   *
   * <p>So far this is the only place where J2CL explicitly resolves the target overloaded method,
   * all other cases are resolved by the frontend.
   */
  private static MethodDescriptor getDefaultSuperConstructorTarget(
      DeclaredTypeDescriptor typeDescriptor) {
    DeclaredTypeDescriptor superTypeDescriptor = typeDescriptor.getSuperTypeDescriptor();
    if (typeDescriptor.isEnum()) {
      // Enum is special, in subclasses the constructors have two implicit parameters
      // (name and ordinal) which are omitted. But when looking at the methods in the Enum class,
      // those parameters are explicit.
      // Anyway, the super class here is always Enum, which does not have a varargs constructor,
      // so we can dispatch to the implicit super constructor as before.
      // The anonymous inner enum values ARE NOT handled here since they are created with an
      // explicit super call to the target provided by the frontend..
      return AstUtils.createImplicitConstructorDescriptor(superTypeDescriptor);
    }

    // Get all possible targets of an implicit super() call. The targets can either be a
    // parameterless constructor or if there is no parameterless constructor a varargs constructor
    // that can be called with no parameters.
    Optional<MethodDescriptor> superContructor =
        superTypeDescriptor.getDeclaredMethodDescriptors().stream()
            .filter(MethodDescriptor::isConstructor)
            .filter(m -> m.isVisibleFrom(typeDescriptor))
            .filter(m -> m.getParameterDescriptors().isEmpty())
            .collect(MoreCollectors.toOptional());

    if (superContructor.isPresent()) {
      return superContructor.get();
    }

    // If no 0-argument constructor find a 1-argument varargs constructor. There might be more than
    // 1 varargs constructor, if so apply the more specific overload rule. At this point there
    // should be no ambiguity and a more specific overload is guaranteed, This is because at this
    // point type checking succeed in the frontend and if there were any ambiguity the compile would
    // have produced an error already.
    superContructor =
        superTypeDescriptor.getDeclaredMethodDescriptors().stream()
            .filter(MethodDescriptor::isConstructor)
            .filter(m -> m.isVisibleFrom(typeDescriptor))
            .filter(m -> m.getParameterDescriptors().size() == 1)
            .filter(m -> Iterables.getOnlyElement(m.getParameterDescriptors()).isVarargs())
            .min(InsertExplicitSuperCalls::getParameterSpecificityComparator);

    if (superContructor.isPresent()) {
      return superContructor.get();
    }

    // No appropriate constructor found, it must be the implicit constructor.
    checkState(
        superTypeDescriptor.getDeclaredMethodDescriptors().stream()
            .noneMatch(MethodDescriptor::isConstructor));
    return AstUtils.createImplicitConstructorDescriptor(superTypeDescriptor);
  }

  /**
   * Returns a comparator for methods with exactly 1 parameter where the method with the most
   * specific parameter comes first.
   *
   * <p>Note: Assumes that there is one that is more specific. The assumption is guaranteed by the
   * Java type resolution in this narrow scenario.
   */
  private static int getParameterSpecificityComparator(MethodDescriptor m1, MethodDescriptor m2) {
    return Iterables.getOnlyElement(m1.getParameterTypeDescriptors())
            .isAssignableTo(Iterables.getOnlyElement(m2.getParameterTypeDescriptors()))
        ? -1
        : 1;
  }
}
