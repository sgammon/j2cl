/*
 * Copyright 2020 Google Inc.
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
package com.google.j2cl.transpiler.backend.wasm;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.google.common.collect.ImmutableSet;
import com.google.j2cl.common.OutputUtils;
import com.google.j2cl.common.Problems;
import com.google.j2cl.transpiler.ast.AbstractVisitor;
import com.google.j2cl.transpiler.ast.CompilationUnit;
import com.google.j2cl.transpiler.ast.DeclaredTypeDescriptor;
import com.google.j2cl.transpiler.ast.Field;
import com.google.j2cl.transpiler.ast.Method;
import com.google.j2cl.transpiler.ast.Type;
import com.google.j2cl.transpiler.ast.TypeDeclaration;
import com.google.j2cl.transpiler.ast.TypeDescriptors;
import com.google.j2cl.transpiler.ast.Variable;
import com.google.j2cl.transpiler.backend.common.SourceBuilder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Generates a WASM module containing all the code for the application. */
public class WasmModuleGenerator {
  private final Problems problems;
  private final Path outputPath;
  private final Set<String> pendingEntryPoints;
  private final SourceBuilder builder = new SourceBuilder();
  private final GenerationEnvironment environment = new GenerationEnvironment();
  /**
   * Maps type declarations to the corresponding type objects to allow access to the implementations
   * of super classes.
   */
  private Map<TypeDeclaration, Type> typesByTypeDeclaration;

  public WasmModuleGenerator(Path outputPath, ImmutableSet<String> entryPoints, Problems problems) {
    this.outputPath = outputPath;
    this.pendingEntryPoints = new HashSet<>(entryPoints);
    this.problems = problems;
  }

  public void generateOutputs(List<CompilationUnit> compilationUnits) {
    builder.append(";;; Code generated by J2WASM");
    // Collect the implementations for all types
    typesByTypeDeclaration =
        compilationUnits.stream()
            .flatMap(cu -> cu.getTypes().stream())
            .collect(toImmutableMap(Type::getDeclaration, Function.identity()));
    for (CompilationUnit j2clCompilationUnit : compilationUnits) {
      builder.newLine();
      builder.append(
          ";;; Code for "
              + j2clCompilationUnit.getPackageName()
              + "."
              + j2clCompilationUnit.getName());
      for (Type type : j2clCompilationUnit.getTypes()) {
        renderType(type);
      }
    }
    OutputUtils.writeToFile(outputPath.resolve("module.wat"), builder.build(), problems);
    if (!pendingEntryPoints.isEmpty()) {
      problems.error("Entry points %s not found.", pendingEntryPoints);
    }
  }

  private void renderType(Type type) {
    builder.newLine();
    builder.newLine();
    builder.append(";;; " + type.getKind() + "  " + type.getReadableDescription());
    renderStaticFields(type);
    renderTypeStruct(type);
    renderTypeMethods(type);
  }

  private void renderStaticFields(Type type) {
    builder.newLine();
    for (Field field : type.getStaticFields()) {
      builder.newLine();
      builder.append(
          "(global "
              + environment.getFieldName(field)
              + " "
              + environment.getWasmType(field.getDescriptor().getTypeDescriptor())
              + " ");
      ExpressionTranspiler.render(
          field.getDescriptor().getTypeDescriptor().getDefaultValue(), builder, environment);
      builder.append(")");
    }
  }

  private void renderTypeMethods(Type type) {
    for (Method method : type.getMethods()) {
      renderMethod(method);
    }
  }

  private void renderMethod(Method method) {
    builder.newLine();
    builder.newLine();
    builder.append(";;; " + method.getReadableDescription());
    builder.newLine();
    builder.append("(func " + environment.getMethodImplementationName(method.getDescriptor()));

    if (pendingEntryPoints.remove(method.getQualifiedBinaryName())) {
      if (!method.isStatic()) {
        problems.error("Entry point [%s] is not a static method.", method.getQualifiedBinaryName());
      }
      builder.append(" (export \"" + method.getDescriptor().getName() + "\")");
    }

    // Emit parameters
    builder.indent();
    for (Variable parameter : method.getParameters()) {
      builder.newLine();
      builder.append(
          "(param "
              + environment.getVariableName(parameter)
              + " "
              + environment.getWasmType(parameter.getTypeDescriptor())
              + ")");
    }
    // Emit return type
    if (!TypeDescriptors.isPrimitiveVoid(method.getDescriptor().getReturnTypeDescriptor())) {
      builder.newLine();
      builder.append(
          "(result "
              + environment.getWasmType(method.getDescriptor().getReturnTypeDescriptor())
              + ")");
    }
    // Emit locals.
    for (Variable variable : collectLocals(method)) {
      builder.newLine();
      builder.append(
          "(local "
              + environment.getVariableName(variable)
              + " "
              + environment.getWasmType(variable.getTypeDescriptor())
              + ")");
    }
    builder.newLine();
    new StatementTranspiler(builder, environment).renderStatement(method.getBody());
    builder.unindent();
    builder.newLine();
    // TODO(rluble): remove the dummy return value to keep WASM happy until the return statement
    // is properly implemented.
    if (!TypeDescriptors.isPrimitiveVoid(method.getDescriptor().getReturnTypeDescriptor())) {
      builder.newLine();
      ExpressionTranspiler.render(
          method.getDescriptor().getReturnTypeDescriptor().getDefaultValue(), builder, environment);
      builder.newLine();
    }
    builder.append(")");
  }

  private static List<Variable> collectLocals(Method method) {
    List<Variable> locals = new ArrayList<>();
    method
        .getBody()
        .accept(
            new AbstractVisitor() {
              @Override
              public void exitVariable(Variable variable) {
                locals.add(variable);
              }
            });
    return locals;
  }

  private void renderTypeStruct(Type type) {
    builder.newLine();
    builder.append("(type " + environment.getWasmTypeName(type.getTypeDescriptor()) + " (struct");
    builder.indent();
    renderTypeFields(type);
    builder.unindent();
    builder.newLine();
    builder.append("))");
  }

  private void renderTypeFields(Type type) {
    DeclaredTypeDescriptor superTypeDescriptor = type.getSuperTypeDescriptor();
    if (superTypeDescriptor != null) {
      Type supertype = typesByTypeDeclaration.get(superTypeDescriptor.getTypeDeclaration());
      if (supertype == null) {
        builder.newLine();
        builder.append(";; Missing supertype " + superTypeDescriptor.getReadableDescription());
      } else {
        renderTypeFields(typesByTypeDeclaration.get(superTypeDescriptor.getTypeDeclaration()));
      }
    }
    for (Field field : type.getFields()) {
      if (field.isStatic()) {
        continue;
      }
      builder.newLine();
      builder.append(
          "(field "
              + environment.getFieldName(field)
              + " "
              + environment.getWasmType(field.getDescriptor().getTypeDescriptor())
              + ")");
    }
  }
}
