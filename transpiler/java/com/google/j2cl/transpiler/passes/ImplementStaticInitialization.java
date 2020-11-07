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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Joiner;
import com.google.j2cl.common.SourcePosition;
import com.google.j2cl.transpiler.ast.AbstractRewriter;
import com.google.j2cl.transpiler.ast.AbstractVisitor;
import com.google.j2cl.transpiler.ast.AstUtils;
import com.google.j2cl.transpiler.ast.BinaryExpression;
import com.google.j2cl.transpiler.ast.Block;
import com.google.j2cl.transpiler.ast.CompilationUnit;
import com.google.j2cl.transpiler.ast.DeclaredTypeDescriptor;
import com.google.j2cl.transpiler.ast.Expression;
import com.google.j2cl.transpiler.ast.Field;
import com.google.j2cl.transpiler.ast.FieldAccess;
import com.google.j2cl.transpiler.ast.FieldDescriptor;
import com.google.j2cl.transpiler.ast.FieldDescriptor.FieldOrigin;
import com.google.j2cl.transpiler.ast.FunctionExpression;
import com.google.j2cl.transpiler.ast.Invocation;
import com.google.j2cl.transpiler.ast.JsInfo;
import com.google.j2cl.transpiler.ast.JsMemberType;
import com.google.j2cl.transpiler.ast.LambdaTypeDescriptors;
import com.google.j2cl.transpiler.ast.Member;
import com.google.j2cl.transpiler.ast.MemberDescriptor;
import com.google.j2cl.transpiler.ast.Method;
import com.google.j2cl.transpiler.ast.MethodCall;
import com.google.j2cl.transpiler.ast.MethodDescriptor;
import com.google.j2cl.transpiler.ast.MethodDescriptor.MethodOrigin;
import com.google.j2cl.transpiler.ast.MultiExpression;
import com.google.j2cl.transpiler.ast.PrimitiveTypes;
import com.google.j2cl.transpiler.ast.Statement;
import com.google.j2cl.transpiler.ast.Type;
import com.google.j2cl.transpiler.ast.TypeDeclaration;
import com.google.j2cl.transpiler.ast.TypeDescriptor;
import com.google.j2cl.transpiler.ast.TypeDescriptors;
import com.google.j2cl.transpiler.ast.Variable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Implements static initialization to comply with Java semantics. */
public class ImplementStaticInitialization extends NormalizationPass {

  private final Set<String> privateStaticMembersCalledFromOtherClasses = new HashSet<>();

  @Override
  public void applyTo(CompilationUnit compilationUnit) {
    collectPrivateMemberReferences(compilationUnit);
    for (Type type : compilationUnit.getTypes()) {
      checkState(!type.isNative());
      checkState(!type.isJsFunctionInterface());
      if (type.isJsEnum()) {
        continue;
      }
      insertClinitCalls(type);
      synthesizeSuperClinitCalls(type);
      synthesizeSettersAndGetters(type);
      synthesizeClinitMethod(type);
      synthesizeStaticFieldDeclaration(type);
    }
  }

  private static void synthesizeStaticFieldDeclaration(Type type) {
    type.accept(
        new AbstractRewriter() {
          @Override
          public Field rewriteField(Field field) {
            if (!field.isStatic()) {
              return field;
            }
            // Add the static field initialization to null, 0, false or compile time constant, to
            // be executed at load time after the class definition.
            type.addLoadTimeStatement(
                AstUtils.declarationStatement(field, type.getSourcePosition()));
            // Remove the initialization value from the field, to preserve the AST invariant that
            // the AST is a proper tree, i.e. a node has only one parent.
            return Field.Builder.from(field).setInitializer(null).build();
          }
        });
  }

  /** Collect all private static methods and fields that are accessed from outside its class. */
  private void collectPrivateMemberReferences(CompilationUnit compilationUnit) {
    compilationUnit.accept(
        new AbstractVisitor() {
          @Override
          @SuppressWarnings("ReferenceEquality")
          public void exitInvocation(Invocation invocation) {
            recordMemberReference(getCurrentType().getDeclaration(), invocation.getTarget());
          }

          @Override
          @SuppressWarnings("ReferenceEquality")
          public void exitFieldAccess(FieldAccess fieldAccess) {
            recordMemberReference(getCurrentType().getDeclaration(), fieldAccess.getTarget());
          }
        });
  }

  /** Records access to member {@code targetMember} from type {@code callerEnclosingType}. */
  private void recordMemberReference(
      TypeDeclaration callerEnclosingType, MemberDescriptor targetMember) {
    if (targetMember.isInstanceMember() || !targetMember.getVisibility().isPrivate()) {
      return;
    }

    if (!targetMember.isMemberOf(callerEnclosingType)) {
      privateStaticMembersCalledFromOtherClasses.add(getUniqueIdentifier(targetMember));
    }
  }

  /** Add clinit calls to static methods and (real js) constructors. */
  private void insertClinitCalls(Type type) {
    type.accept(
        new AbstractRewriter() {
          @Override
          public Method rewriteMethod(Method method) {
            if (triggersClinit(method.getDescriptor())) {
              return Method.Builder.from(method)
                  .addStatement(
                      0,
                      createClinitCallStatement(
                          method.getBody().getSourcePosition(),
                          method.getDescriptor().getEnclosingTypeDescriptor()))
                  .build();
            }
            return method;
          }
        });
  }

  /** Synthesize a static initializer block that calls the necessary super type clinits. */
  private static void synthesizeSuperClinitCalls(Type type) {
    Block.Builder staticInitializerBuilder =
        Block.newBuilder().setSourcePosition(type.getSourcePosition());

    if (implementsClinitMethod(type.getSuperTypeDescriptor())) {
      staticInitializerBuilder.addStatement(
          createClinitCallStatement(type.getSourcePosition(), type.getSuperTypeDescriptor()));
    }
    addRequiredSuperInterfacesClinitCalls(
        type.getSourcePosition(), type.getTypeDescriptor(), staticInitializerBuilder);

    Block staticInitiliazerBlock = staticInitializerBuilder.build();
    if (!staticInitiliazerBlock.isEmpty()) {
      type.addStaticInitializerBlock(0, staticInitiliazerBlock);
    }
  }

  /**
   * Synthesize static getters/setter to ensure class initialization is run on static field
   * accesses.
   */
  private void synthesizeSettersAndGetters(Type type) {
    for (Field staticField : type.getStaticFields()) {
      if (!triggersClinit(staticField.getDescriptor())) {
        continue;
      }
      synthesizePropertyGetter(type, staticField);
      if (!staticField.getDescriptor().isFinal()) {
        synthesizePropertySetter(type, staticField);
      }
    }

    // Replace the actual static fields with their backing fields.
    type.accept(
        new AbstractRewriter() {
          @Override
          public Member rewriteField(Field field) {
            if (!triggersClinit(field.getDescriptor())) {
              return field;
            }
            return Field.Builder.from(getBackingFieldDescriptor(field.getDescriptor()))
                .setInitializer(field.getInitializer())
                .setSourcePosition(field.getSourcePosition())
                .build();
          }
        });

    // Replace field access within the class for accesses to the backing field.
    type.accept(
        new AbstractRewriter() {
          @Override
          public FieldAccess rewriteFieldAccess(FieldAccess fieldAccess) {
            FieldDescriptor fieldDescriptor = fieldAccess.getTarget();
            if (!triggersClinit(fieldDescriptor)) {
              return fieldAccess;
            }

            if (!fieldDescriptor.isMemberOf(type.getDeclaration())) {
              return fieldAccess;
            }

            return FieldAccess.Builder.from(getBackingFieldDescriptor(fieldDescriptor)).build();
          }
        });
  }

  /** Returns a string that uniquely identifies a method. */
  private static String getUniqueIdentifier(MemberDescriptor memberDescriptor) {
    return Joiner.on("___")
        .join(
            memberDescriptor.getEnclosingTypeDescriptor().getQualifiedBinaryName(),
            memberDescriptor.getMangledName());
  }

  public void synthesizePropertyGetter(Type type, Field field) {
    FieldDescriptor fieldDescriptor = field.getDescriptor();
    type.addMethod(
        Method.newBuilder()
            .setSourcePosition(field.getSourcePosition())
            .setMethodDescriptor(getGetterMethodDescriptor(fieldDescriptor))
            .addStatements(
                AstUtils.createReturnOrExpressionStatement(
                    field.getSourcePosition(),
                    MultiExpression.newBuilder()
                        .addExpressions(
                            createClinitCallExpression(
                                fieldDescriptor.getEnclosingTypeDescriptor()),
                            FieldAccess.Builder.from(fieldDescriptor).build())
                        .build(),
                    fieldDescriptor.getTypeDescriptor()))
            .build());
  }

  public void synthesizePropertySetter(Type type, Field field) {
    FieldDescriptor fieldDescriptor = field.getDescriptor();
    Variable parameter =
        Variable.newBuilder()
            .setName("value")
            .setTypeDescriptor(fieldDescriptor.getTypeDescriptor())
            .setParameter(true)
            .build();
    type.addMethod(
        Method.newBuilder()
            .setSourcePosition(field.getSourcePosition())
            .setMethodDescriptor(getSetterMethodDescriptor(fieldDescriptor))
            .setParameters(parameter)
            .addStatements(
                MultiExpression.newBuilder()
                    .addExpressions(
                        createClinitCallExpression(fieldDescriptor.getEnclosingTypeDescriptor()),
                        BinaryExpression.Builder.asAssignmentTo(fieldDescriptor)
                            .setRightOperand(parameter)
                            .build())
                    .build()
                    .makeStatement(field.getSourcePosition()))
            .build());
  }

  private static Statement createClinitCallStatement(
      SourcePosition sourcePosition, DeclaredTypeDescriptor typeDescriptor) {
    return createClinitCallExpression(typeDescriptor).makeStatement(sourcePosition);
  }

  private static Expression createClinitCallExpression(DeclaredTypeDescriptor typeDescriptor) {
    return MethodCall.Builder.from(typeDescriptor.getClinitMethodDescriptor()).build();
  }

  private static void addRequiredSuperInterfacesClinitCalls(
      SourcePosition sourcePosition,
      DeclaredTypeDescriptor typeDescriptor,
      Block.Builder staticInitializerBuilder) {
    for (DeclaredTypeDescriptor interfaceTypeDescriptor :
        typeDescriptor.getInterfaceTypeDescriptors()) {
      if (!implementsClinitMethod(interfaceTypeDescriptor)) {
        continue;
      }

      if (interfaceTypeDescriptor.getTypeDeclaration().declaresDefaultMethods()) {
        // The interface declares a default method; invoke its clinit which will initialize
        // the interface and all it superinterfaces that declare default methods.
        staticInitializerBuilder.addStatement(
            createClinitCallStatement(sourcePosition, interfaceTypeDescriptor));
        continue;
      }

      // The interface does not declare a default method so don't call its clinit; instead recurse
      // into its super interface hierarchy to invoke clinits of super interfaces that declare
      // default methods.
      addRequiredSuperInterfacesClinitCalls(
          sourcePosition, interfaceTypeDescriptor, staticInitializerBuilder);
    }
  }

  /**
   * Returns {@code true} if a class initialization (clinit) needs to be called when accessing this
   * member (i.e. calling it if if a method, or referencing it if it is a field)
   */
  private boolean triggersClinit(MemberDescriptor memberDescriptor) {
    if (memberDescriptor.isNative()) {
      // Skip native members.
      return false;
    }

    if (memberDescriptor.isCompileTimeConstant()) {
      // Compile time constants do not trigger clinit.
      return false;
    }

    if (memberDescriptor.getVisibility().isPrivate()
        && !memberDescriptor.isJsMember()
        && !isCalledFromOtherClasses(memberDescriptor)) {
      // This is an effectively private member, which means that when this member is access clinit
      // is already guaranteed to have been called.
      return false;
    }

    return memberDescriptor.isStatic() || memberDescriptor.isJsConstructor();
  }

  private boolean isCalledFromOtherClasses(MemberDescriptor memberDescriptor) {
    return privateStaticMembersCalledFromOtherClasses.contains(
        getUniqueIdentifier(memberDescriptor));
  }

  /** Returns {@code true} if the type implements the class initialization method. */
  private static boolean implementsClinitMethod(TypeDescriptor typeDescriptor) {
    return typeDescriptor != null
        && !typeDescriptor.isNative()
        && !typeDescriptor.isJsFunctionInterface();
  }

  /** Returns the field descriptor for a the field backing the static setter/getters. */
  private static FieldDescriptor getBackingFieldDescriptor(FieldDescriptor fieldDescriptor) {
    checkArgument(
        fieldDescriptor.isStatic()
            && fieldDescriptor.getOrigin() != FieldOrigin.SYNTHETIC_BACKING_FIELD);
    return FieldDescriptor.Builder.from(fieldDescriptor)
        .setJsInfo(JsInfo.NONE)
        .setOrigin(FieldOrigin.SYNTHETIC_BACKING_FIELD)
        .build();
  }

  private static MethodDescriptor getSetterMethodDescriptor(FieldDescriptor fieldDescriptor) {
    return MethodDescriptor.newBuilder()
        .setEnclosingTypeDescriptor(fieldDescriptor.getEnclosingTypeDescriptor())
        .setName(fieldDescriptor.getName())
        .setOrigin(MethodOrigin.SYNTHETIC_PROPERTY_SETTER)
        .setParameterTypeDescriptors(fieldDescriptor.getTypeDescriptor())
        .setReturnTypeDescriptor(PrimitiveTypes.VOID)
        .setVisibility(fieldDescriptor.getVisibility())
        .setJsInfo(
            fieldDescriptor.isJsProperty()
                ? JsInfo.Builder.from(fieldDescriptor.getJsInfo())
                    .setJsMemberType(JsMemberType.SETTER)
                    .setJsName(fieldDescriptor.getName())
                    .build()
                : JsInfo.NONE)
        .setStatic(true)
        .setDeprecated(fieldDescriptor.isDeprecated())
        .build();
  }

  private static MethodDescriptor getGetterMethodDescriptor(FieldDescriptor fieldDescriptor) {
    return MethodDescriptor.newBuilder()
        .setEnclosingTypeDescriptor(fieldDescriptor.getEnclosingTypeDescriptor())
        .setName(fieldDescriptor.getName())
        .setOrigin(MethodOrigin.SYNTHETIC_PROPERTY_GETTER)
        .setReturnTypeDescriptor(fieldDescriptor.getTypeDescriptor())
        .setVisibility(fieldDescriptor.getVisibility())
        .setJsInfo(
            fieldDescriptor.isJsProperty()
                ? JsInfo.Builder.from(fieldDescriptor.getJsInfo())
                    .setJsMemberType(JsMemberType.GETTER)
                    .setJsName(fieldDescriptor.getName())
                    .build()
                : JsInfo.NONE)
        .setStatic(true)
        .setDeprecated(fieldDescriptor.isDeprecated())
        .build();
  }

  /** Implements the static initialization method ($clinit). */
  private static void synthesizeClinitMethod(Type type) {
    SourcePosition sourcePosition = type.getSourcePosition();

    FieldDescriptor clinitMethodFieldDescriptor =
        getClinitFieldDescriptor(type.getTypeDescriptor());

    // Use the underlying JsFunction for the runnable Lambda to get a functional type with
    // no parameters and void return.
    // TODO(b/112308901): remove this hacky code when function types are modeled better.
    DeclaredTypeDescriptor runnableJsFunctionDescriptor =
        LambdaTypeDescriptors.createJsFunctionTypeDescriptor(
            TypeDescriptors.get().javaLangRunnable);

    // Class.$clinit = () => {};
    Statement noopClinitFunction =
        BinaryExpression.Builder.asAssignmentTo(clinitMethodFieldDescriptor)
            .setRightOperand(
                FunctionExpression.newBuilder()
                    .setTypeDescriptor(runnableJsFunctionDescriptor)
                    .setSourcePosition(sourcePosition)
                    .build())
            .build()
            .makeStatement(sourcePosition);

    // Class.$loadModules();
    Statement callLoadModules =
        MethodCall.Builder.from(AstUtils.getLoadModulesDescriptor(type.getTypeDescriptor()))
            .build()
            .makeStatement(sourcePosition);

    // Code from static initializer blocks.
    List<Statement> clinitStatements =
        type.getStaticInitializerBlocks()
            .stream()
            .flatMap(initializerBlock -> initializerBlock.getBlock().getStatements().stream())
            .collect(Collectors.toList());

    type.addMethod(
        Method.newBuilder()
            .setMethodDescriptor(type.getTypeDescriptor().getClinitMethodDescriptor())
            .addStatements(noopClinitFunction, callLoadModules)
            .addStatements(clinitStatements)
            .setSourcePosition(sourcePosition)
            .build());

    type.getMembers().removeIf(member -> member.isInitializerBlock() && member.isStatic());
  }

  /** Returns the class initializer property as a field for a particular type */
  private static FieldDescriptor getClinitFieldDescriptor(DeclaredTypeDescriptor typeDescriptor) {
    return FieldDescriptor.newBuilder()
        .setStatic(true)
        .setEnclosingTypeDescriptor(typeDescriptor)
        .setTypeDescriptor(TypeDescriptors.get().nativeFunction)
        .setName(MethodDescriptor.CLINIT_METHOD_NAME)
        .setJsInfo(typeDescriptor.isNative() ? JsInfo.RAW_OVERLAY : JsInfo.RAW)
        .build();
  }
}
