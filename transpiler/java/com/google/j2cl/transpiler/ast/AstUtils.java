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
package com.google.j2cl.transpiler.ast;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.j2cl.common.SourcePosition;
import com.google.j2cl.transpiler.ast.FieldDescriptor.FieldOrigin;
import com.google.j2cl.transpiler.ast.MethodDescriptor.MethodOrigin;
import com.google.j2cl.transpiler.ast.MethodDescriptor.ParameterDescriptor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Utility functions to manipulate J2CL AST. */
public class AstUtils {
  private static final String ENCLOSING_INSTANCE_NAME = "this";

  /** Returns the loadModules method descriptor for a particular type */
  public static MethodDescriptor getLoadModulesDescriptor(DeclaredTypeDescriptor typeDescriptor) {
    return MethodDescriptor.newBuilder()
        .setStatic(true)
        .setEnclosingTypeDescriptor(typeDescriptor)
        .setName(MethodDescriptor.LOAD_MODULES_METHOD_NAME)
        .setOrigin(MethodOrigin.SYNTHETIC_CLASS_INITIALIZER)
        .setJsInfo(typeDescriptor.isNative() ? JsInfo.RAW_OVERLAY : JsInfo.RAW)
        .build();
  }

  /** Create default constructor MethodDescriptor. */
  public static MethodDescriptor createImplicitConstructorDescriptor(
      DeclaredTypeDescriptor enclosingTypeDescriptor) {
    JsInfo jsInfo =
        isImplicitJsConstructor(enclosingTypeDescriptor.getTypeDeclaration())
            ? JsInfo.newBuilder().setJsMemberType(JsMemberType.CONSTRUCTOR).build()
            : JsInfo.NONE;
    return MethodDescriptor.newBuilder()
        .setVisibility(
            getImplicitConstructorVisibility(enclosingTypeDescriptor.getTypeDeclaration()))
        .setEnclosingTypeDescriptor(enclosingTypeDescriptor)
        .setConstructor(true)
        .setJsInfo(jsInfo)
        .build();
  }

  private static Visibility getImplicitConstructorVisibility(TypeDeclaration typeDeclaration) {
    return typeDeclaration.isEnum() ? Visibility.PRIVATE : typeDeclaration.getVisibility();
  }

  /** Return true if the synthetic implicit default constructor is a JsConstructor. */
  private static boolean isImplicitJsConstructor(TypeDeclaration typeDeclaration) {
    return typeDeclaration.isJsType()
        && (typeDeclaration.isNative()
            || getImplicitConstructorVisibility(typeDeclaration).isPublic());
  }

  /**
   * Returns the constructor invocation (super call or this call) in a specified constructor, or
   * returns null if it does not have one.
   */
  public static ExpressionStatement getConstructorInvocationStatement(Method method) {
    checkArgument(method.isConstructor());
    for (Statement statement : method.getBody().getStatements()) {
      if (!(statement instanceof ExpressionStatement)) {
        continue;
      }
      Expression expression = ((ExpressionStatement) statement).getExpression();
      if (!(expression instanceof MethodCall)) {
        continue;
      }
      MethodCall methodCall = (MethodCall) expression;
      if (methodCall.getTarget().isConstructor()) {
        return (ExpressionStatement) statement;
      }
    }
    return null;
  }

  /**
   * Returns the constructor invocation (super call or this call) in a specified constructor, or
   * returns null if it does not have one.
   */
  public static MethodCall getConstructorInvocation(Method method) {
    ExpressionStatement statement = getConstructorInvocationStatement(method);
    if (statement == null) {
      return null;
    }
    checkArgument(statement.getExpression() instanceof MethodCall);
    MethodCall methodCall = (MethodCall) statement.getExpression();
    checkArgument(methodCall.getTarget().isConstructor());
    return methodCall;
  }

  /** Returns whether the specified constructor has a this() call. */
  public static boolean hasThisCall(Method method) {
    MethodCall constructorInvocation = getConstructorInvocation(method);
    return constructorInvocation != null
        && constructorInvocation.getTarget().inSameTypeAs(method.getDescriptor());
  }

  /** Returns whether the specified constructor has a super() call. */
  public static boolean hasSuperCall(Method method) {
    MethodCall constructorInvocation = getConstructorInvocation(method);
    return constructorInvocation != null
        && !constructorInvocation.getTarget().inSameTypeAs(method.getDescriptor());
  }

  /** Returns whether the specified constructor has a this() or a super() call. */
  public static boolean hasConstructorInvocation(Method method) {
    MethodCall constructorInvocation = getConstructorInvocation(method);
    return constructorInvocation != null;
  }

  /**
   * Returns the superclass constructor method descriptor this constructor delegates to if any or
   * {@code null} otherwise.
   */
  public static MethodDescriptor getDelegatedSuperConstructorDescriptor(Method method) {
    checkArgument(method.isConstructor());
    if (!hasConstructorInvocation(method)) {
      return method
          .getDescriptor()
          .getEnclosingTypeDescriptor()
          .getSuperTypeDescriptor()
          .getDefaultConstructorMethodDescriptor();
    }
    MethodDescriptor delegatedMethodDescriptor = getConstructorInvocation(method).getTarget();

    return delegatedMethodDescriptor.inSameTypeAs(method.getDescriptor())
        ? null
        : delegatedMethodDescriptor;
  }

  /** Returns the added field descriptor corresponding to the captured variable. */
  public static FieldDescriptor getFieldDescriptorForCapture(
      DeclaredTypeDescriptor enclosingTypeDescriptor, Variable capturedVariable) {
    return FieldDescriptor.newBuilder()
        .setEnclosingTypeDescriptor(enclosingTypeDescriptor)
        .setName(capturedVariable.getName())
        .setTypeDescriptor(capturedVariable.getTypeDescriptor())
        .setStatic(false)
        .setFinal(true)
        .setSynthetic(true)
        .setOrigin(FieldOrigin.SYNTHETIC_CAPTURE_FIELD)
        .build();
  }

  /** Returns the added field corresponding to the enclosing instance. */
  public static FieldDescriptor getFieldDescriptorForEnclosingInstance(
      DeclaredTypeDescriptor enclosingClassDescriptor, TypeDescriptor fieldTypeDescriptor) {
    return FieldDescriptor.newBuilder()
        .setEnclosingTypeDescriptor(enclosingClassDescriptor)
        .setName(ENCLOSING_INSTANCE_NAME)
        .setFinal(true)
        .setSynthetic(true)
        .setTypeDescriptor(fieldTypeDescriptor)
        .setOrigin(FieldOrigin.SYNTHETIC_OUTER_FIELD)
        .build();
  }

  /**
   * Creates forwarding method {@code fromMethodDescriptor} that delegates to {@code
   * toMethodDescriptor}, e.g.
   *
   * <p>fromMethodDescriptor (args) { return this.toMethodDescriptor(args); }
   */
  public static Method createForwardingMethod(
      SourcePosition sourcePosition,
      Expression qualifier,
      MethodDescriptor fromMethodDescriptor,
      MethodDescriptor toMethodDescriptor,
      String jsDocDescription) {
    return createForwardingMethod(
        sourcePosition,
        qualifier,
        fromMethodDescriptor,
        toMethodDescriptor,
        jsDocDescription,
        /* isStaticDispatch */ false);
  }

  private static Method createForwardingMethod(
      SourcePosition sourcePosition,
      Expression qualifier,
      MethodDescriptor fromMethodDescriptor,
      MethodDescriptor toMethodDescriptor,
      String jsDocDescription,
      boolean isStaticDispatch) {
    checkArgument(!fromMethodDescriptor.getEnclosingTypeDescriptor().isInterface());

    List<Variable> parameters =
        createParameterVariables(fromMethodDescriptor.getParameterTypeDescriptors());

    Statement statement =
        createForwardingStatement(
            sourcePosition,
            qualifier,
            toMethodDescriptor,
            isStaticDispatch,
            parameters.stream().map(Variable::getReference).collect(Collectors.toList()),
            fromMethodDescriptor.getReturnTypeDescriptor());
    return Method.newBuilder()
        .setMethodDescriptor(fromMethodDescriptor)
        .setParameters(parameters)
        .addStatements(statement)
        .setJsDocDescription(jsDocDescription)
        .setSourcePosition(sourcePosition)
        .build();
  }

  public static List<Variable> createParameterVariables(List<TypeDescriptor> parameterTypes) {
    List<Variable> parameters = new ArrayList<>();
    for (int i = 0; i < parameterTypes.size(); i++) {
      parameters.add(
          Variable.newBuilder()
              .setName("arg" + i)
              .setTypeDescriptor(parameterTypes.get(i))
              .setParameter(true)
              .build());
    }
    return parameters;
  }

  public static Statement createForwardingStatement(
      SourcePosition sourcePosition,
      Expression qualifier,
      MethodDescriptor toMethodDescriptor,
      boolean isStaticDispatch,
      List<Expression> arguments,
      TypeDescriptor returnTypeDescriptor) {
    // TODO(rluble): Casts are probably needed on arguments if the types differ between the
    // targetMethodDescriptor and its declarationMethodDescriptor.
    Expression forwardingMethodCall =
        MethodCall.Builder.from(toMethodDescriptor)
            .setQualifier(qualifier)
            .setArguments(maybePackageVarargs(toMethodDescriptor, arguments))
            .setStaticDispatch(isStaticDispatch)
            .build();

    return createReturnOrExpressionStatement(
        sourcePosition, forwardingMethodCall, returnTypeDescriptor);
  }

  /**
   * Boxes {@code expression} using the valueOf() method of the corresponding boxed type. e.g.
   * expression => Integer.valueOf(expression).
   */
  public static Expression box(Expression expression) {
    PrimitiveTypeDescriptor primitiveType =
        (PrimitiveTypeDescriptor) expression.getTypeDescriptor();
    checkArgument(!TypeDescriptors.isPrimitiveVoid(primitiveType));
    checkArgument(!TypeDescriptors.isPrimitiveBooleanOrDouble(primitiveType));
    DeclaredTypeDescriptor boxType = primitiveType.toBoxedType();

    MethodDescriptor valueOfMethodDescriptor =
        boxType.getMethodDescriptor(MethodDescriptor.VALUE_OF_METHOD_NAME, primitiveType);
    return MethodCall.Builder.from(valueOfMethodDescriptor).setArguments(expression).build();
  }

  /**
   * Unboxes {expression} using the ***Value() method of the corresponding boxed type. e.g
   * expression => expression.intValue().
   */
  public static Expression unbox(Expression expression) {
    DeclaredTypeDescriptor boxType =
        (DeclaredTypeDescriptor) expression.getTypeDescriptor().toRawTypeDescriptor();
    checkArgument(TypeDescriptors.isBoxedType(boxType));
    PrimitiveTypeDescriptor primitiveType = boxType.toUnboxedType();

    MethodDescriptor valueMethodDescriptor =
        boxType.getMethodDescriptor(
            primitiveType.getSimpleSourceName() + MethodDescriptor.VALUE_METHOD_SUFFIX);

    MethodCall methodCall =
        MethodCall.Builder.from(valueMethodDescriptor).setQualifier(expression).build();

    if (TypeDescriptors.isBoxedBooleanOrDouble(boxType)) {
      methodCall = devirtualizeMethodCall(methodCall, boxType);
    }
    return methodCall;
  }

  public static boolean isDelegatedConstructorCall(
      MethodCall methodCall, DeclaredTypeDescriptor targetTypeDescriptor) {
    if (methodCall == null || !methodCall.getTarget().isConstructor()) {
      return false;
    }
    return methodCall.getTarget().isMemberOf(targetTypeDescriptor);
  }

  /**
   * See JLS 5.2.
   *
   * <p>Note that compound assignments are excluded here. The assignment context arising from
   * compound assignments requires the expression to be rewritten into a plain assignment.
   */
  public static boolean matchesAssignmentContext(BinaryOperator binaryOperator) {
    return binaryOperator == BinaryOperator.ASSIGN;
  }

  /** See JLS 5.4. */
  public static boolean matchesStringContext(BinaryExpression binaryExpression) {
    BinaryOperator operator = binaryExpression.getOperator();
    return (operator == BinaryOperator.PLUS_ASSIGN || operator == BinaryOperator.PLUS)
        && TypeDescriptors.isJavaLangString(binaryExpression.getTypeDescriptor());
  }

  /** See JLS 5.6.1. */
  public static boolean matchesUnaryNumericPromotionContext(PrefixExpression prefixExpression) {
    PrefixOperator operator = prefixExpression.getOperator();
    return TypeDescriptors.isBoxedOrPrimitiveType(prefixExpression.getTypeDescriptor())
        && (operator == PrefixOperator.PLUS
            || operator == PrefixOperator.MINUS
            || operator == PrefixOperator.COMPLEMENT);
  }

  /** See JLS 5.6.1. */
  public static boolean matchesUnaryNumericPromotionContext(TypeDescriptor returnTypeDescriptor) {
    return TypeDescriptors.isBoxedOrPrimitiveType(returnTypeDescriptor);
  }

  /** See JLS 5.6.2. */
  public static boolean matchesBinaryNumericPromotionContext(BinaryExpression binaryExpression) {
    // Both operands must be boxed or primitive.
    Expression leftOperand = binaryExpression.getLeftOperand();
    Expression rightOperand = binaryExpression.getRightOperand();
    BinaryOperator operator = binaryExpression.getOperator();

    if (!TypeDescriptors.isBoxedOrPrimitiveType(leftOperand.getTypeDescriptor())
        || !TypeDescriptors.isBoxedOrPrimitiveType(rightOperand.getTypeDescriptor())) {
      return false;
    }

    boolean leftIsPrimitive = leftOperand.getTypeDescriptor().isPrimitive();
    boolean rightIsPrimitive = rightOperand.getTypeDescriptor().isPrimitive();

    switch (operator.isCompoundAssignment() ? operator.getUnderlyingBinaryOperator() : operator) {
      case TIMES:
      case DIVIDE:
      case REMAINDER:
      case PLUS:
      case MINUS:
      case LESS:
      case GREATER:
      case LESS_EQUALS:
      case GREATER_EQUALS:
      case BIT_XOR:
      case BIT_AND:
      case BIT_OR:
        return true; // Both numerics and booleans get these operators.
      case EQUALS:
      case NOT_EQUALS:
        return leftIsPrimitive || rightIsPrimitive; // Equality is sometimes instance comparison.
      default:
        return false;
    }
  }

  /** See JLS 5.1. */
  public static boolean matchesBooleanConversionContext(BinaryOperator operator) {
    switch (operator) {
      case CONDITIONAL_AND:
      case CONDITIONAL_OR:
        return true; // Booleans get these operators.
      default:
        return false;
    }
  }

  /** See JLS 5.1. */
  public static boolean matchesBooleanConversionContext(PrefixOperator operator) {
    return operator == PrefixOperator.NOT;
  }

  /** JsEnum comparison context. */
  public static boolean matchesJsEnumBoxingConversionContext(BinaryExpression binaryExpression) {
    BinaryOperator operator = binaryExpression.getOperator();
    // Use the declaration descriptor to ignore the specialization of type variables to JsEnums
    // to understand whether the (returned) enum was boxed or unboxed.
    TypeDescriptor lhsTypeDescriptor =
        binaryExpression.getLeftOperand().getDeclaredTypeDescriptor();
    TypeDescriptor rhsTypeDescriptor =
        binaryExpression.getRightOperand().getDeclaredTypeDescriptor();

    return operator.isRelationalOperator()
        && (isNonNativeJsEnum(lhsTypeDescriptor) || isNonNativeJsEnum(rhsTypeDescriptor))
        // DO NOT use hasSameRawType, since intersections that have JsEnums have the same raw type
        // as the JsEnum but behave differently w.r.t JsEnum boxing conversions. Intersection types
        // are always boxed.
        && !lhsTypeDescriptor.isSameBaseType(rhsTypeDescriptor);
  }

  /** JsEnum comparison context. */
  public static boolean matchesJsEnumBoxingConversionContext(
      InstanceOfExpression instanceOfExpression) {
    TypeDescriptor expressionTypeDescriptor =
        instanceOfExpression.getExpression().getTypeDescriptor();
    TypeDescriptor testTypeDescriptor = instanceOfExpression.getTestTypeDescriptor();
    return isNonNativeJsEnum(expressionTypeDescriptor)
        // DO NOT use hasSameRawType, since intersections that have JsEnums have the same raw type
        // as the JsEnum but behave differently w.r.t JsEnum boxing conversions. Intersection types
        // are always boxed.
        && !expressionTypeDescriptor.isSameBaseType(testTypeDescriptor);
  }

  /**
   * Returns the type of the numeric binary expression, which is the wider type of its operands, or
   * int if it is wider.
   *
   * <p>See JLS 5.6.2.
   */
  public static PrimitiveTypeDescriptor getNumericBinaryExpressionTypeDescriptor(
      PrimitiveTypeDescriptor thisTypeDescriptor, PrimitiveTypeDescriptor thatTypeDescriptor) {
    if (TypeDescriptors.isPrimitiveDouble(thisTypeDescriptor)
        || TypeDescriptors.isPrimitiveDouble(thatTypeDescriptor)) {
      return PrimitiveTypes.DOUBLE;
    }

    if (TypeDescriptors.isPrimitiveFloat(thisTypeDescriptor)
        || TypeDescriptors.isPrimitiveFloat(thatTypeDescriptor)) {
      return PrimitiveTypes.FLOAT;
    }

    if (TypeDescriptors.isPrimitiveLong(thisTypeDescriptor)
        || TypeDescriptors.isPrimitiveLong(thatTypeDescriptor)) {
      return PrimitiveTypes.LONG;
    }

    return PrimitiveTypes.INT;
  }

  /**
   * Returns the type of the numeric unary expression, which is the type of its operand, or int if
   * it is wider.
   *
   * <p>See JLS 5.6.1.
   */
  public static PrimitiveTypeDescriptor getNumericUnaryExpressionTypeDescriptor(
      PrimitiveTypeDescriptor thisTypeDescriptor) {
    if (PrimitiveTypes.INT.isWiderThan(thisTypeDescriptor)) {
      return PrimitiveTypes.INT;
    }

    return thisTypeDescriptor;
  }

  /**
   * Returns explicit qualifier for member reference (field access or method call).
   *
   * <p>If {@code qualifier} is null, returns EnclosingTypeDescriptor as the qualifier for static
   * method/field and 'this' reference as the qualifier for instance method/field.
   */
  static Expression getExplicitQualifier(Expression qualifier, MemberDescriptor memberDescriptor) {
    checkNotNull(memberDescriptor);
    if (qualifier != null) {
      return qualifier;
    }
    DeclaredTypeDescriptor enclosingTypeDescriptor = memberDescriptor.getEnclosingTypeDescriptor();
    return memberDescriptor.isStatic()
        ? new JavaScriptConstructorReference(enclosingTypeDescriptor.getTypeDeclaration())
        : new ThisReference(enclosingTypeDescriptor);
  }

  /** Returns true if the qualifier of the given member reference is 'this' reference. */
  public static boolean hasThisReferenceAsQualifier(MemberReference memberReference) {
    Expression qualifier = memberReference.getQualifier();
    return qualifier instanceof ThisReference
        && memberReference.getTarget().isMemberOf(((ThisReference) qualifier).getTypeDescriptor());
  }

  /**
   * Generates the following code:
   *
   * <p>$Util.$makeLambdaFunction(Type.prototype.m_equal, $instance, Type.$copy);
   */
  public static MethodCall createLambdaInstance(
      DeclaredTypeDescriptor lambdaType, Expression instance) {
    checkArgument(lambdaType.isJsFunctionImplementation());

    // Use the method from the interface instead instead of the implementation method, since it is
    // the appropriate semantic behaviour. The function might be called(directly as a function) from
    // as a @JsFunction which might require dispatch through the bridge.
    MethodDescriptor jsFunctionMethodDescriptor =
        lambdaType.getFunctionalInterface().getJsFunctionMethodDescriptor();

    // Class.prototype.apply
    String functionalMethodMangledName = jsFunctionMethodDescriptor.getMangledName();

    FieldAccess applyFunctionFieldAccess =
        FieldAccess.Builder.from(
                FieldDescriptor.newBuilder()
                    .setEnclosingTypeDescriptor(lambdaType)
                    .setName(functionalMethodMangledName)
                    .setTypeDescriptor(TypeDescriptors.get().nativeFunction)
                    .setJsInfo(JsInfo.RAW_FIELD)
                    .build())
            .setQualifier(
                new JavaScriptConstructorReference(lambdaType.getTypeDeclaration())
                    .getPrototypeFieldAccess())
            .build();

    FieldAccess copyFunctionFieldAccess =
        FieldAccess.Builder.from(
                FieldDescriptor.newBuilder()
                    .setEnclosingTypeDescriptor(lambdaType)
                    .setName("$copy")
                    .setTypeDescriptor(TypeDescriptors.get().nativeFunction)
                    .setJsInfo(JsInfo.RAW_FIELD)
                    .setDeprecated(lambdaType.isDeprecated())
                    .build())
            .setQualifier(new JavaScriptConstructorReference(lambdaType.getTypeDeclaration()))
            .build();

    return RuntimeMethods.createUtilMethodCall(
        "$makeLambdaFunction", applyFunctionFieldAccess, instance, copyFunctionFieldAccess);
  }

  /** Returns a field declaration statement. */
  public static Statement declarationStatement(Field field, SourcePosition sourcePosition) {
    boolean isPublic = field.getDescriptor().getOrigin() != FieldOrigin.SYNTHETIC_BACKING_FIELD;

    Expression declarationExpression =
        FieldAccess.newBuilder()
            .setTargetFieldDescriptor(field.getDescriptor())
            .setSourcePosition(field.getNameSourcePosition())
            .build();

    if (field.getInitializer() != null) {
      declarationExpression =
          BinaryExpression.Builder.asAssignmentTo(declarationExpression)
              .setRightOperand(field.getInitializer())
              .build();
    }

    return FieldDeclarationStatement.newBuilder()
        .setExpression(declarationExpression)
        .setFieldDescriptor(field.getDescriptor())
        .setPublic(isPublic)
        .setConst(field.isCompileTimeConstant())
        .setDeprecated(field.getDescriptor().isDeprecated())
        .setSourcePosition(
            field.isCompileTimeConstant() ? field.getSourcePosition() : sourcePosition)
        .build();
  }

  public static String getSimpleSourceName(List<String> classComponents) {
    String simpleName = Iterables.getLast(classComponents);
    // Prefix anonymous numbered classes with a string to to make their names less odd.
    return startsWithNumber(simpleName) ? "$" + simpleName : simpleName;
  }

  private static boolean startsWithNumber(String string) {
    return !string.isEmpty() && Character.isDigit(string.charAt(0));
  }

  /** Clones a list of expressions */
  @SuppressWarnings("unchecked")
  public static <T extends Cloneable<?>> List<T> clone(List<T> nodes) {
    return nodes.stream().map(node -> (T) node.clone()).collect(toImmutableList());
  }

  /** Clones a cloneable or returns null */
  @SuppressWarnings("unchecked")
  public static <T extends Cloneable<?>> T clone(T node) {
    return node != null ? (T) node.clone() : null;
  }

  /**
   * Replaces references to variables in {@code fromVariables} to reference to variables in {@code
   * toVariables}.
   */
  public static <T extends Node> List<T> replaceVariables(
      List<Variable> fromVariables, List<Variable> toVariable, List<T> nodes) {
    List<T> result = new ArrayList<>();
    for (T node : nodes) {
      result.add(replaceVariables(fromVariables, toVariable, node));
    }
    return result;
  }

  /**
   * Replaces references to variables in {@code fromVariables} to reference to variables in {@code
   * toVariables}.
   */
  @SuppressWarnings("unchecked")
  public static <T extends Node> T replaceVariables(
      List<Variable> fromVariables, List<Variable> toVariable, T node) {
    class VariableReplacer extends AbstractRewriter {
      Map<Variable, Variable> toVariableByFromVariable = new HashMap<>();

      public VariableReplacer(List<Variable> fromVariables, List<Variable> toVariables) {
        checkArgument(fromVariables.size() == toVariables.size());
        for (int i = 0; i < fromVariables.size(); i++) {
          toVariableByFromVariable.put(fromVariables.get(i), toVariables.get(i));
        }
      }

      @Override
      public Variable rewriteVariable(Variable variable) {
        Variable toVariable = toVariableByFromVariable.get(variable);
        return toVariable == null ? variable : toVariable;
      }

      @Override
      public Expression rewriteVariableReference(VariableReference variableReference) {
        Variable toVariable = toVariableByFromVariable.get(variableReference.getTarget());
        if (toVariable != null) {
          return toVariable.getReference();
        }
        return variableReference;
      }
    }

    return (T) node.accept(new VariableReplacer(fromVariables, toVariable));
  }

  /** Get a list of references for {@code variables}. */
  public static List<Expression> getReferences(List<Variable> variables) {
    return variables.stream().map(Variable::getReference).collect(toImmutableList());
  }

  /** Creates an implicit constructor that forwards to a specific super constructor. */
  public static Method createImplicitAnonymousClassConstructor(
      SourcePosition sourcePosition,
      MethodDescriptor constructorDescriptor,
      MethodDescriptor superConstructorDescriptor) {

    // If the super qualifier is explicit then it is passed as the first parameter and the super
    // constructor has 1 parameter less than the constructor being build.
    boolean firstParameterIsSuperQualifier =
        constructorDescriptor.getParameterTypeDescriptors().size()
            != superConstructorDescriptor.getParameterTypeDescriptors().size();

    List<Variable> constructorParameters = new ArrayList<>();
    int index = 0;
    for (TypeDescriptor parameterTypeDescriptor :
        constructorDescriptor.getParameterTypeDescriptors()) {
      String parameterName =
          firstParameterIsSuperQualifier && index == 0 ? "$super_outer_this" : "$_" + index;
      constructorParameters.add(
          Variable.newBuilder()
              .setName(parameterName)
              .setTypeDescriptor(parameterTypeDescriptor)
              .build());
      index++;
    }

    Expression qualifier =
        firstParameterIsSuperQualifier ? constructorParameters.get(0).getReference() : null;

    List<Expression> superConstructorArguments =
        (firstParameterIsSuperQualifier
                ? constructorParameters.subList(1, constructorParameters.size())
                : constructorParameters)
            .stream()
            .map(Variable::getReference)
            .collect(toImmutableList());

    return Method.newBuilder()
        .setMethodDescriptor(constructorDescriptor)
        .setParameters(constructorParameters)
        .addStatements(
            MethodCall.Builder.from(superConstructorDescriptor)
                .setQualifier(qualifier)
                .setArguments(superConstructorArguments)
                .build()
                .makeStatement(sourcePosition))
        .setSourcePosition(sourcePosition)
        .build();
  }

  public static Expression removeJsDocCastIfPresent(Expression expression) {
    if (expression instanceof JsDocCastExpression) {
      return ((JsDocCastExpression) expression).getExpression();
    }
    return expression;
  }

  /**
   * Create a return statement if the return type is not void; otherwise create an expression
   * statement
   */
  public static Statement createReturnOrExpressionStatement(
      SourcePosition sourcePosition,
      Expression expression,
      TypeDescriptor methodReturnTypeDescriptor) {

    if (TypeDescriptors.isPrimitiveVoid(methodReturnTypeDescriptor)) {
      return expression.makeStatement(sourcePosition);
    }

    return ReturnStatement.newBuilder()
        .setExpression(expression)
        .setTypeDescriptor(methodReturnTypeDescriptor)
        .setSourcePosition(sourcePosition)
        .build();
  }

  /**
   * Returns a TypeDescriptor to refer to the enclosing name that represents the member namespace;
   * this will transform a namespace=a.b.c on an JsMethod into a fictitious TypeDescriptor for
   * namespace=a.b and name=c.
   */
  public static DeclaredTypeDescriptor getNamespaceAsTypeDescriptor(
      MemberDescriptor memberDescriptor) {
    String memberJsNamespace = memberDescriptor.getJsNamespace();
    if (JsUtils.isGlobal(memberJsNamespace)) {
      return TypeDescriptors.get().globalNamespace;
    }

    List<String> components = Splitter.on('.').omitEmptyStrings().splitToList(memberJsNamespace);

    String jsNamespace = buildQualifiedName(components.subList(0, components.size() - 1).stream());
    String jsName = Iterables.getLast(components);
    return TypeDescriptors.createNativeTypeDescriptor(jsNamespace, jsName);
  }

  /** Returns a qualified name, ignoring empty and {@code null} {@code parts}. */
  public static String buildQualifiedName(String... parts) {
    return buildQualifiedName(Arrays.stream(parts));
  }

  /** Returns a qualified name, ignoring empty and {@code null} {@code parts}. */
  public static String buildQualifiedName(Stream<String> parts) {
    return parts
        .filter(Predicates.notNull())
        .filter(Predicates.not(String::isEmpty))
        .collect(Collectors.joining("."));
  }

  /**
   * Creates a static method in {@code targetTypeDescriptor} with the same signature and body as
   * {@code method}.
   */
  public static Method createStaticOverlayMethod(
      Method method, DeclaredTypeDescriptor targetTypeDescriptor) {
    MethodDescriptor methodDescriptor = method.getDescriptor();
    checkArgument(methodDescriptor.isStatic());

    return Method.Builder.from(method)
        .setMethodDescriptor(
            MethodDescriptor.Builder.from(methodDescriptor)
                .setJsInfo(methodDescriptor.isJsAsync() ? JsInfo.NONE_ASYNC : JsInfo.NONE)
                .setEnclosingTypeDescriptor(targetTypeDescriptor)
                .removeParameterOptionality()
                .build())
        .build();
  }

  /**
   * Devirtualizes {@code Method} by making {@code this} into an explicit argument and placing the
   * resulting method in {@code enclosingTypeDescriptor}.
   */
  public static Method devirtualizeMethod(
      Method method, DeclaredTypeDescriptor enclosingTypeDescriptor) {
    return devirtualizeMethod(method, enclosingTypeDescriptor, null);
  }

  /**
   * Devirtualizes {@code Method} by making {@code this} into an explicit argument and placing the
   * resulting method in {@code enclosingTypeDescriptor} and adding {@code postfix} to its name to
   * avoid possible name collisions with already existing static methods.
   */
  public static Method devirtualizeMethod(Method method, String postfix) {
    checkArgument(!postfix.isEmpty());
    return devirtualizeMethod(method, method.getDescriptor().getEnclosingTypeDescriptor(), postfix);
  }

  /**
   * Devirtualizes {@code Method} by making {@code this} into an explicit argument and placing the
   * resulting method in according to {@code devirtualizedMethodDescriptor}.
   */
  public static Method devirtualizeMethod(
      Method method, DeclaredTypeDescriptor enclosingTypeDescriptor, String postfix) {
    checkArgument(
        !method.getDescriptor().isJsPropertyGetter()
            && !method.getDescriptor().isJsPropertySetter(),
        "JsPropery getter and setters should never be devirtualized %s",
        method.getReadableDescription());
    checkArgument(method.getDescriptor().isInstanceMember());
    checkArgument(!method.getDescriptor().isInit(), "Do not devirtualize init().");

    MethodDescriptor devirtualizedMethodDescriptor =
        devirtualizeMethodDescriptor(
            method.getDescriptor(), enclosingTypeDescriptor, Optional.ofNullable(postfix));

    final Variable thisArg =
        Variable.newBuilder()
            .setName("$thisArg")
            .setTypeDescriptor(method.getDescriptor().getEnclosingTypeDescriptor().toNonNullable())
            .setParameter(true)
            .setFinal(true)
            .build();

    // Replace all 'this' references in the method with a reference to the newly introduced
    // parameter. There should not be any super references in devirtualized methods; but if there
    // were they should also be replaced with the parameter reference but more importantly, the
    // method call or field access they are the qualifier of needs to be resolved statically.
    method.accept(
        new AbstractRewriter() {
          @Override
          public Node rewriteThisReference(ThisReference thisReference) {
            return thisArg.getReference();
          }

          @Override
          public Node rewriteSuperReference(SuperReference superReference) {
            checkState(false, "super should not appear in devirtualized methods.");
            return superReference;
          }
        });

    // Add the static method to current type.
    return Method.newBuilder()
        .setMethodDescriptor(devirtualizedMethodDescriptor)
        .setParameters(
            ImmutableList.<Variable>builder().add(thisArg).addAll(method.getParameters()).build())
        .addStatements(method.getBody().getStatements())
        .setSourcePosition(method.getSourcePosition())
        .build();
  }

  /**
   * Creates devirtualized method call of {@code methodCall} as method call to the static method in
   * {@code targetTypeDescriptor} with the {@code instanceTypeDescriptor} as the first parameter
   * type.
   *
   * <p>instance.instanceMethod(a, b) => TargetType.staticMethod(instance, a, b)
   */
  public static MethodCall devirtualizeMethodCall(
      MethodCall methodCall, DeclaredTypeDescriptor targetTypeDescriptor) {
    return devirtualizeMethodCall(methodCall, targetTypeDescriptor, null);
  }

  /**
   * Creates devirtualized method call of {@code methodCall} as method call to the corresponding
   * static method in the same type with the {@code instanceTypeDescriptor} as the first parameter
   * type. Since the devirtualized method is in the same type a {@code postfix} is used to make sure
   * there is no collision with an already existing static method.
   *
   * <p>instance.instanceMethod(a, b) => staticMethod_postfix(instance, a, b)
   */
  public static MethodCall devirtualizeMethodCall(MethodCall methodCall, String postfix) {
    checkArgument(!postfix.isEmpty());
    return devirtualizeMethodCall(
        methodCall, methodCall.getTarget().getEnclosingTypeDescriptor(), postfix);
  }

  public static MethodCall devirtualizeMethodCall(
      MethodCall methodCall, DeclaredTypeDescriptor targetTypeDescriptor, String postfix) {
    MethodDescriptor devirtualizedMethodDescriptor =
        devirtualizeMethodDescriptor(
            methodCall.getTarget(), targetTypeDescriptor, Optional.ofNullable(postfix));

    // Call the method like Objects.foo(instance, ...)
    List<Expression> arguments =
        ImmutableList.<Expression>builder()
            // Turn the instance into now a first parameter to the devirtualized method.
            .add(checkNotNull(methodCall.getQualifier()))
            .addAll(methodCall.getArguments())
            .build();
    return MethodCall.Builder.from(devirtualizedMethodDescriptor).setArguments(arguments).build();
  }

  /**
   * Creates a devirtualized static MethodDescriptor from an instance MethodDescriptor.
   *
   * <p>The static MethodDescriptor has an extra parameter as its first parameter whose type is the
   * enclosing class of {@code methodDescriptor}.
   */
  private static MethodDescriptor devirtualizeMethodDescriptor(
      MethodDescriptor methodDescriptor,
      DeclaredTypeDescriptor targetTypeDescriptor,
      Optional<String> postfix) {
    checkArgument(methodDescriptor.isInstanceMember());

    DeclaredTypeDescriptor enclosingTypeDescriptor = methodDescriptor.getEnclosingTypeDescriptor();

    String devirtualizedMethodName = methodDescriptor.getName() + postfix.orElse("");
    MethodDescriptor.Builder methodBuilder =
        MethodDescriptor.Builder.from(methodDescriptor)
            .setName(devirtualizedMethodName)
            .setEnclosingTypeDescriptor(targetTypeDescriptor)
            .setParameterDescriptors(
                ImmutableList.<ParameterDescriptor>builder()
                    .add(
                        ParameterDescriptor.newBuilder()
                            // the instance ($thisArg) parameter is assumed non nullable for
                            // the typing perspective.
                            .setTypeDescriptor(enclosingTypeDescriptor.toNonNullable())
                            .build())
                    .addAll(methodDescriptor.getParameterDescriptors())
                    .build())
            .setTypeParameterTypeDescriptors(
                ImmutableList.<TypeVariable>builder()
                    .addAll(methodDescriptor.getTypeParameterTypeDescriptors())
                    .addAll(
                        enclosingTypeDescriptor.getTypeDeclaration().getTypeParameterDescriptors())
                    .build())
            .setStatic(true)
            .setAbstract(false)
            .setDefaultMethod(false)
            .setJsInfo(methodDescriptor.isJsAsync() ? JsInfo.NONE_ASYNC : JsInfo.NONE)
            .removeParameterOptionality();

    if (!methodDescriptor.isDeclaration()) {
      methodBuilder.setDeclarationDescriptor(
          devirtualizeMethodDescriptor(
              methodDescriptor.getDeclarationDescriptor(), targetTypeDescriptor, postfix));
    }
    return methodBuilder.build();
  }

  /** Returns an Optional.empty() if optionalSourcePosition is empty or unnamed */
  public static SourcePosition removeUnnamedSourcePosition(SourcePosition sourcePosition) {
    return sourcePosition.getName() == null ? SourcePosition.NONE : sourcePosition;
  }

  /** Returns the arguments with the vararg arguments packaged together as an array if necessary. */
  public static List<Expression> maybePackageVarargs(
      MethodDescriptor methodDescriptor, List<Expression> arguments) {
    if (!shouldPackageVarargs(methodDescriptor, arguments)) {
      return arguments;
    }

    int parameterLength = methodDescriptor.getParameterDescriptors().size();
    Expression packagedVarargs = getPackagedVarargs(methodDescriptor, arguments);
    List<Expression> result = new ArrayList<>();
    result.addAll(arguments.subList(0, parameterLength - 1));
    result.add(packagedVarargs);
    return result;
  }

  /** Returns if a method call is invoked with varargs that are not in an explicit array format. */
  private static boolean shouldPackageVarargs(
      MethodDescriptor methodDescriptor, List<Expression> arguments) {
    int parametersLength = methodDescriptor.getParameterDescriptors().size();
    if (!methodDescriptor.isVarargs()) {
      return false;
    }
    if (arguments.size() != parametersLength) {
      return true;
    }
    Expression lastArgument = arguments.get(parametersLength - 1);
    ParameterDescriptor lastParameter =
        methodDescriptor.getParameterDescriptors().get(parametersLength - 1);

    return !(lastArgument instanceof NullLiteral)
        && !lastArgument.getTypeDescriptor().isAssignableTo(lastParameter.getTypeDescriptor());
  }

  /** Returns the package the varargs argument as an array. */
  private static Expression getPackagedVarargs(
      MethodDescriptor methodDescriptor, List<Expression> arguments) {
    checkArgument(methodDescriptor.isVarargs());
    int parametersLength = methodDescriptor.getParameterDescriptors().size();
    ParameterDescriptor varargsParameterDescriptor =
        Iterables.getLast(methodDescriptor.getParameterDescriptors());
    ArrayTypeDescriptor varargsTypeDescriptor =
        (ArrayTypeDescriptor) varargsParameterDescriptor.getTypeDescriptor();

    if (AstUtils.isNonNativeJsEnum(varargsTypeDescriptor.getComponentTypeDescriptor())) {
      // TODO(b/118615488): remove this when BoxedLightEnums are surfaces to J2CL.
      //
      // Here we create an array using the bound of declarated type T[] instead of the actual
      // inferred type JsEnum[] since non-native JsEnum arrays are forbidden.
      // We have chosen this workaround instead of banning T[] when T is inferred to be a non-native
      // JsEnum. It is quite uncommon to have code that observes the implications of using a
      // array of the supertype in the implicit array creation due to varargs, instead of an array
      // of the inferred subtype. Making this choice allows the use of common varargs APIs such as
      // Arrays.asList() with JsEnum values.
      varargsTypeDescriptor =
          (ArrayTypeDescriptor)
              Iterables.getLast(
                      methodDescriptor.getDeclarationDescriptor().getParameterDescriptors())
                  .getTypeDescriptor()
                  .toRawTypeDescriptor();
    }
    if (arguments.size() < parametersLength) {
      // no argument for the varargs, add an empty array.
      return new ArrayLiteral(varargsTypeDescriptor);
    }
    List<Expression> valueExpressions = new ArrayList<>();
    for (int i = parametersLength - 1; i < arguments.size(); i++) {
      valueExpressions.add(
          // Wrap isDoNotAutobox arguments in a JsDocCastExpression so that they don't get converted
          // by passes based on ContextRewriter.
          varargsParameterDescriptor.isDoNotAutobox()
              ? JsDocCastExpression.newBuilder()
                  .setCastType(varargsTypeDescriptor.getComponentTypeDescriptor())
                  .setExpression(arguments.get(i))
                  .build()
              : arguments.get(i));
    }
    return new ArrayLiteral(varargsTypeDescriptor, valueExpressions);
  }

  /** Whether the function is the identity function. */
  public static boolean isIdentityFunction(Function<?, ?> function) {
    return function == Function.identity();
  }

  /** Returns the field descriptor for the const field that holds the ordinal value. */
  public static FieldDescriptor getEnumOrdinalConstantFieldDescriptor(
      FieldDescriptor fieldDescriptor) {
    checkArgument(fieldDescriptor.isStatic());
    return FieldDescriptor.Builder.from(fieldDescriptor)
        .setCompileTimeConstant(true)
        .setSynthetic(true)
        .setTypeDescriptor(PrimitiveTypes.INT)
        .setJsInfo(JsInfo.NONE)
        .setOrigin(FieldOrigin.SYNTHETIC_ORDINAL_FIELD)
        .build();
  }

  /** Returns the value field descriptor for a JsEnum */
  private static FieldDescriptor getJsEnumValueFieldDescriptor(TypeDeclaration typeDeclaration) {
    checkState(typeDeclaration.isJsEnum());
    return typeDeclaration.getDeclaredFieldDescriptors().stream()
        .filter(AstUtils::isJsEnumCustomValueField)
        .findFirst()
        .orElse(null);
  }

  public static boolean isJsEnumCustomValueField(MemberDescriptor memberDescriptor) {
    return memberDescriptor.isField()
        && memberDescriptor.getName().equals("value")
        && memberDescriptor.getEnclosingTypeDescriptor().isJsEnum();
  }

  /** Returns the value field for a JsEnum. */
  public static TypeDescriptor getJsEnumValueFieldType(TypeDeclaration typeDeclaration) {
    FieldDescriptor valueFieldDescriptor = getJsEnumValueFieldDescriptor(typeDeclaration);
    return valueFieldDescriptor == null
        ? PrimitiveTypes.INT
        : valueFieldDescriptor.getTypeDescriptor();
  }

  /** Returns the type to use for instanceof checking for a native JsEnum. */
  public static DeclaredTypeDescriptor getJsEnumValueFieldInstanceCheckType(
      TypeDeclaration typeDeclaration) {
    TypeDescriptor valueTypeDescriptor =
        getJsEnumValueFieldDescriptor(typeDeclaration).getTypeDescriptor();
    // The JavaScript types are either number, boolean or string. As a shortcut we use the
    // instanceof checks for Double, Boolean and String respectively, instead of duplicating the
    // code.
    // Note that this is not equivalent to use .toBoxedType since for example [int].toBoxedType()
    // will actually be java.lang.Integer not the runtime type for an unboxed number enum.
    if (TypeDescriptors.isNumericPrimitive(valueTypeDescriptor)) {
      return TypeDescriptors.get().javaLangDouble;
    } else if (TypeDescriptors.isPrimitiveBoolean(valueTypeDescriptor)) {
      return TypeDescriptors.get().javaLangBoolean;
    } else {
      return (DeclaredTypeDescriptor) valueTypeDescriptor;
    }
  }

  /**
   * Returns true if {@code typeDescriptor} is a non native JsEnum, i.e. a JsEnum that requires
   * boxing.
   */
  public static boolean isNonNativeJsEnum(TypeDescriptor typeDescriptor) {
    return typeDescriptor.isJsEnum() && !typeDescriptor.isNative();
  }

  /** Returns true if {@code typeDescriptor} is a non native JsEnum array. */
  public static boolean isNonNativeJsEnumArray(TypeDescriptor typeDescriptor) {
    return typeDescriptor.isArray()
        && isNonNativeJsEnum(((ArrayTypeDescriptor) typeDescriptor).getLeafTypeDescriptor());
  }

  /** Retuns a list of null values. */
  public static List<Expression> createListOfNullValues(int size) {
    return Collections.nCopies(size, TypeDescriptors.get().javaLangObject.getNullValue());
  }

  /** Pads the expression list with null literals to the requested {@code size}. */
  public static void addNullPadding(List<Expression> expressionList, int size) {
    while (expressionList.size() < size) {
      expressionList.add(TypeDescriptors.get().javaLangObject.getNullValue());
    }
  }
}
