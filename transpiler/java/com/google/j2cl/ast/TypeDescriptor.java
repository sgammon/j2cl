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
package com.google.j2cl.ast;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.j2cl.ast.processors.Visitable;

import java.util.Collections;
import java.util.List;

/**
 * A reference to a type.
 *
 * <p>
 * This class is mostly a bag of precomputed properties, and the details of how those properties are
 * created lives at in several creation functions in TypeDescriptors.
 *
 * <p>
 * A couple of properties are lazily calculated via the TypeDescriptorFactory and
 * MethodDescriptorFactory interfaces, since eagerly calculating them would lead to infinite loops
 * of TypeDescriptor creation.
 */
@Visitable
public class TypeDescriptor extends Node implements Comparable<TypeDescriptor>, HasJsName {

  /**
   * Builder for a TypeDescriptor.
   */
  public static class Builder {

    public static Builder from(final TypeDescriptor typeDescriptor) {
      Builder builder = new Builder();
      TypeDescriptor newTypeDescriptor = builder.newTypeDescriptor;

      newTypeDescriptor.binaryName = typeDescriptor.getBinaryName();
      newTypeDescriptor.classComponents = typeDescriptor.getClassComponents();
      newTypeDescriptor.binaryClassName = typeDescriptor.getBinaryClassName();
      newTypeDescriptor.componentTypeDescriptor = typeDescriptor.getComponentTypeDescriptor();
      newTypeDescriptor.concreteJsFunctionMethodDescriptorFactory =
          new MethodDescriptorFactory() {
            @Override
            public MethodDescriptor create() {
              return typeDescriptor.getConcreteJsFunctionMethodDescriptor();
            }
          };
      newTypeDescriptor.dimensions = typeDescriptor.getDimensions();
      newTypeDescriptor.enclosingTypeDescriptorFactory =
          new TypeDescriptorFactory() {
            @Override
            public TypeDescriptor create() {
              return typeDescriptor.getEnclosingTypeDescriptor();
            }
          };
      newTypeDescriptor.interfacesTypeDescriptorsFactory =
          new TypeDescriptorsFactory() {
            @Override
            public List<TypeDescriptor> create() {
              return typeDescriptor.getInterfacesTypeDescriptors();
            }
          };
      newTypeDescriptor.isArray = typeDescriptor.isArray();
      newTypeDescriptor.isEnumOrSubclass = typeDescriptor.isEnumOrSubclass();
      newTypeDescriptor.isExtern = typeDescriptor.isExtern();
      newTypeDescriptor.isGlobal = typeDescriptor.isGlobal();
      newTypeDescriptor.isInstanceMemberClass = typeDescriptor.isInstanceMemberClass();
      newTypeDescriptor.isInstanceNestedClass = typeDescriptor.isInstanceNestedClass();
      newTypeDescriptor.isInterface = typeDescriptor.isInterface();
      newTypeDescriptor.isJsFunction = typeDescriptor.isJsFunctionInterface();
      newTypeDescriptor.isJsFunctionImplementation = typeDescriptor.isJsFunctionImplementation();
      newTypeDescriptor.isJsType = typeDescriptor.isJsType();
      newTypeDescriptor.isLocal = typeDescriptor.isLocal();
      newTypeDescriptor.isNative = typeDescriptor.isNative();
      newTypeDescriptor.isNullable = typeDescriptor.isNullable();
      newTypeDescriptor.isPrimitive = typeDescriptor.isPrimitive();
      newTypeDescriptor.isRaw = typeDescriptor.isRaw();
      newTypeDescriptor.isRawType = typeDescriptor.isRawType();
      newTypeDescriptor.isTypeVariable = typeDescriptor.isTypeVariable();
      newTypeDescriptor.isUnion = typeDescriptor.isUnion();
      newTypeDescriptor.isWildCard = typeDescriptor.isWildCard();
      newTypeDescriptor.jsFunctionMethodDescriptorFactory =
          new MethodDescriptorFactory() {
            @Override
            public MethodDescriptor create() {
              return typeDescriptor.getJsFunctionMethodDescriptor();
            }
          };
      newTypeDescriptor.jsName = typeDescriptor.getJsName();
      newTypeDescriptor.jsNamespace = typeDescriptor.getJsNamespace();
      newTypeDescriptor.leafTypeDescriptor = typeDescriptor.getLeafTypeDescriptor();
      newTypeDescriptor.packageComponents = typeDescriptor.getPackageComponents();
      newTypeDescriptor.packageName = typeDescriptor.getPackageName();
      newTypeDescriptor.qualifiedName = typeDescriptor.getQualifiedName();
      newTypeDescriptor.rawTypeDescriptorFactory =
          new TypeDescriptorFactory() {
            @Override
            public TypeDescriptor create() {
              return typeDescriptor.getRawTypeDescriptor();
            }
          };
      newTypeDescriptor.simpleName = typeDescriptor.getSimpleName();
      newTypeDescriptor.sourceName = typeDescriptor.getSourceName();
      newTypeDescriptor.subclassesJsConstructorClass =
          typeDescriptor.subclassesJsConstructorClass();
      newTypeDescriptor.superTypeDescriptorFactory =
          new TypeDescriptorFactory() {
            @Override
            public TypeDescriptor create() {
              return typeDescriptor.getSuperTypeDescriptor();
            }
          };
      newTypeDescriptor.unionedTypeDescriptors = typeDescriptor.getUnionedTypeDescriptors();
      newTypeDescriptor.typeArgumentDescriptors = typeDescriptor.getTypeArgumentDescriptors();
      newTypeDescriptor.uniqueId = typeDescriptor.getUniqueId();
      newTypeDescriptor.visibility = typeDescriptor.getVisibility();

      return builder;
    }

    private TypeDescriptor newTypeDescriptor = new TypeDescriptor();

    public TypeDescriptor build() {
      return newTypeDescriptor;
    }

    public Builder setBinaryClassName(String binaryClassName) {
      newTypeDescriptor.binaryClassName = binaryClassName;
      return this;
    }

    public Builder setBinaryName(String binaryName) {
      newTypeDescriptor.binaryName = binaryName;
      return this;
    }

    public Builder setClassComponents(List<String> classComponents) {
      newTypeDescriptor.classComponents = classComponents;
      return this;
    }

    public Builder setComponentTypeDescriptor(TypeDescriptor componentTypeDescriptor) {
      newTypeDescriptor.componentTypeDescriptor = componentTypeDescriptor;
      return this;
    }

    public Builder setConcreteJsFunctionMethodDescriptorFactory(
        MethodDescriptorFactory concreteJsFunctionMethodDescriptorFactory) {
      newTypeDescriptor.concreteJsFunctionMethodDescriptorFactory =
          concreteJsFunctionMethodDescriptorFactory;
      return this;
    }

    public Builder setDimensions(int dimensions) {
      newTypeDescriptor.dimensions = dimensions;
      return this;
    }

    public Builder setEnclosingTypeDescriptorFactory(
        TypeDescriptorFactory enclosingTypeDescriptorFactory) {
      newTypeDescriptor.enclosingTypeDescriptorFactory = enclosingTypeDescriptorFactory;
      return this;
    }

    public Builder setInterfacesTypeDescriptorsFactory(
        TypeDescriptorsFactory interfacesTypeDescriptorsFactory) {
      newTypeDescriptor.interfacesTypeDescriptorsFactory = interfacesTypeDescriptorsFactory;
      return this;
    }

    public Builder setIsArray(boolean isArray) {
      newTypeDescriptor.isArray = isArray;
      return this;
    }

    public Builder setIsEnumOrSubclass(boolean isEnumOrSubclass) {
      newTypeDescriptor.isEnumOrSubclass = isEnumOrSubclass;
      return this;
    }

    public Builder setIsExtern(boolean isExtern) {
      newTypeDescriptor.isExtern = isExtern;
      return this;
    }

    public Builder setIsGlobal(boolean isGlobal) {
      newTypeDescriptor.isGlobal = isGlobal;
      return this;
    }

    public Builder setIsInstanceMemberClass(boolean isInstanceMemberClass) {
      newTypeDescriptor.isInstanceMemberClass = isInstanceMemberClass;
      return this;
    }

    public Builder setIsInstanceNestedClass(boolean isInstanceNestedClass) {
      newTypeDescriptor.isInstanceNestedClass = isInstanceNestedClass;
      return this;
    }

    public Builder setIsInterface(boolean isInterface) {
      newTypeDescriptor.isInterface = isInterface;
      return this;
    }

    public Builder setIsJsFunction(boolean isJsFunction) {
      newTypeDescriptor.isJsFunction = isJsFunction;
      return this;
    }

    public Builder setIsJsFunctionImplementation(boolean isJsFunctionImplementation) {
      newTypeDescriptor.isJsFunctionImplementation = isJsFunctionImplementation;
      return this;
    }

    public Builder setIsJsType(boolean isJsType) {
      newTypeDescriptor.isJsType = isJsType;
      return this;
    }

    public Builder setIsLocal(boolean isLocal) {
      newTypeDescriptor.isLocal = isLocal;
      return this;
    }

    public Builder setIsNative(boolean isNative) {
      newTypeDescriptor.isNative = isNative;
      return this;
    }

    public Builder setIsNullable(boolean isNullable) {
      newTypeDescriptor.isNullable = isNullable;
      return this;
    }

    public Builder setIsPrimitive(boolean isPrimitive) {
      newTypeDescriptor.isPrimitive = isPrimitive;
      return this;
    }

    public Builder setIsRaw(boolean isRaw) {
      newTypeDescriptor.isRaw = isRaw;
      return this;
    }

    public Builder setIsRawType(boolean isRawType) {
      newTypeDescriptor.isRawType = isRawType;
      return this;
    }

    public Builder setIsTypeVariable(boolean isTypeVariable) {
      newTypeDescriptor.isTypeVariable = isTypeVariable;
      return this;
    }

    public Builder setIsUnion(boolean isUnion) {
      newTypeDescriptor.isUnion = isUnion;
      return this;
    }

    public Builder setIsWildCard(boolean isWildCard) {
      newTypeDescriptor.isWildCard = isWildCard;
      return this;
    }

    public Builder setJsFunctionMethodDescriptorFactory(
        MethodDescriptorFactory jsFunctionMethodDescriptorFactory) {
      newTypeDescriptor.jsFunctionMethodDescriptorFactory = jsFunctionMethodDescriptorFactory;
      return this;
    }

    public Builder setJsName(String jsName) {
      newTypeDescriptor.jsName = jsName;
      return this;
    }

    public Builder setJsNamespace(String jsNamespace) {
      newTypeDescriptor.jsNamespace = jsNamespace;
      return this;
    }

    public Builder setLeafTypeDescriptor(TypeDescriptor leafTypeDescriptor) {
      newTypeDescriptor.leafTypeDescriptor = leafTypeDescriptor;
      return this;
    }

    public Builder setPackageComponents(List<String> packageComponents) {
      newTypeDescriptor.packageComponents = packageComponents;
      return this;
    }

    public Builder setPackageName(String packageName) {
      newTypeDescriptor.packageName = packageName;
      return this;
    }

    public Builder setQualifiedName(String qualifiedName) {
      newTypeDescriptor.qualifiedName = qualifiedName;
      return this;
    }

    public Builder setRawTypeDescriptorFactory(TypeDescriptorFactory rawTypeDescriptorFactory) {
      newTypeDescriptor.rawTypeDescriptorFactory = rawTypeDescriptorFactory;
      return this;
    }

    public Builder setSimpleName(String simpleName) {
      newTypeDescriptor.simpleName = simpleName;
      return this;
    }

    public Builder setSourceName(String sourceName) {
      newTypeDescriptor.sourceName = sourceName;
      return this;
    }

    public Builder setSubclassesJsConstructorClass(boolean subclassesJsConstructorClass) {
      newTypeDescriptor.subclassesJsConstructorClass = subclassesJsConstructorClass;
      return this;
    }

    public Builder setSuperTypeDescriptorFactory(TypeDescriptorFactory superTypeDescriptorFactory) {
      newTypeDescriptor.superTypeDescriptorFactory = superTypeDescriptorFactory;
      return this;
    }

    public Builder setTypeArgumentDescriptors(List<TypeDescriptor> typeArgumentDescriptors) {
      newTypeDescriptor.typeArgumentDescriptors = typeArgumentDescriptors;
      return this;
    }

    public Builder setUnionedTypeDescriptors(List<TypeDescriptor> unionedTypeDescriptors) {
      newTypeDescriptor.unionedTypeDescriptors = unionedTypeDescriptors;
      return this;
    }

    public Builder setUniqueId(String uniqueId) {
      newTypeDescriptor.uniqueId = uniqueId;
      return this;
    }

    public Builder setVisibility(Visibility visibility) {
      newTypeDescriptor.visibility = visibility;
      return this;
    }
  }

  /**
   * Enables delayed MethodDescriptor creation.
   */
  public interface MethodDescriptorFactory {
    MethodDescriptor create();
  }

  /**
   * Enables delayed TypeDescriptor creation.
   */
  public interface TypeDescriptorFactory {
    TypeDescriptor create();
  }

  /**
   * Enables delayed TypeDescriptor creation.
   */
  public interface TypeDescriptorsFactory {
    List<TypeDescriptor> create();
  }


  private String binaryClassName;
  private String binaryName;
  private List<String> classComponents = Collections.emptyList();
  private TypeDescriptor componentTypeDescriptor;
  private MethodDescriptorFactory concreteJsFunctionMethodDescriptorFactory;
  private int dimensions;
  private TypeDescriptorFactory enclosingTypeDescriptorFactory;
  private TypeDescriptorsFactory interfacesTypeDescriptorsFactory;
  private boolean isArray;
  private boolean isEnumOrSubclass;
  private boolean isExtern;
  private boolean isGlobal;
  private boolean isInstanceMemberClass;
  private boolean isInstanceNestedClass;
  private boolean isInterface;
  private boolean isJsFunction;
  private boolean isJsFunctionImplementation;
  private boolean isJsType;
  private boolean isLocal;
  private boolean isNative;
  private boolean isNullable;
  private boolean isPrimitive;
  private boolean isRaw;
  private boolean isRawType;
  private boolean isTypeVariable;
  private boolean isUnion;
  private boolean isWildCard;
  private MethodDescriptorFactory jsFunctionMethodDescriptorFactory;
  private String jsName;
  private String jsNamespace;
  private TypeDescriptor leafTypeDescriptor;
  private List<String> packageComponents = Collections.emptyList();
  private String packageName;
  private String qualifiedName;
  private TypeDescriptorFactory rawTypeDescriptorFactory;
  private String simpleName;
  private String sourceName;
  private boolean subclassesJsConstructorClass;
  private TypeDescriptorFactory superTypeDescriptorFactory;
  private List<TypeDescriptor> typeArgumentDescriptors = Collections.emptyList();
  private List<TypeDescriptor> unionedTypeDescriptors = Collections.emptyList();
  private String uniqueId;
  private Visibility visibility;

  private TypeDescriptor() {}

  @Override
  public Node accept(Processor processor) {
    return Visitor_TypeDescriptor.visit(processor, this);
  }

  @Override
  public int compareTo(TypeDescriptor that) {
    return getUniqueId().compareTo(that.getUniqueId());
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof TypeDescriptor) {
      return getUniqueId().equals(((TypeDescriptor) o).getUniqueId());
    }
    return false;
  }

  public boolean equalsIgnoreNullability(TypeDescriptor other) {
    // Don't try to convert type variables to non-nullable.
    TypeDescriptor right = other.isTypeVariable() ? other : TypeDescriptors.toNullable(other);
    TypeDescriptor left = this.isTypeVariable() ? this : TypeDescriptors.toNullable(this);

    return left.equals(right);
  }

  /**
   * Returns the unqualified binary name like "Outer$Inner".
   */
  public String getBinaryClassName() {
    return binaryClassName;
  }

  /**
   * Returns the fully package qualified binary name like "com.google.common.Outer$Inner".
   */
  public String getBinaryName() {
    return binaryName;
  }

  /**
   * Returns a list of Strings representing the current type's simple name and enclosing type simple
   * names. For example for "com.google.foo.Outer" the class components are ["Outer"] and for
   * "com.google.foo.Outer.Inner" the class components are ["Outer", "Inner"].
   */
  public List<String> getClassComponents() {
    return classComponents;
  }

  public TypeDescriptor getComponentTypeDescriptor() {
    return componentTypeDescriptor;
  }

  public MethodDescriptor getConcreteJsFunctionMethodDescriptor() {
    if (concreteJsFunctionMethodDescriptorFactory == null) {
      return null;
    }
    return concreteJsFunctionMethodDescriptorFactory.create();
  }

  public int getDimensions() {
    return dimensions;
  }

  public TypeDescriptor getEnclosingTypeDescriptor() {
    if (enclosingTypeDescriptorFactory == null) {
      return null;
    }
    return enclosingTypeDescriptorFactory.create();
  }

  public ImmutableList<TypeDescriptor> getInterfacesTypeDescriptors() {
    if (interfacesTypeDescriptorsFactory == null) {
      return ImmutableList.of();
    }
    return ImmutableList.copyOf(interfacesTypeDescriptorsFactory.create());
  }

  public MethodDescriptor getJsFunctionMethodDescriptor() {
    if (jsFunctionMethodDescriptorFactory == null) {
      return null;
    }
    return jsFunctionMethodDescriptorFactory.create();
  }

  @Override
  public String getJsName() {
    return jsName;
  }

  @Override
  public String getJsNamespace() {
    return jsNamespace;
  }

  public TypeDescriptor getLeafTypeDescriptor() {
    return leafTypeDescriptor;
  }

  public List<String> getPackageComponents() {
    return packageComponents;
  }

  /**
   * Returns the fully package qualified name like "com.google.common".
   */
  public String getPackageName() {
    return packageName;
  }

  public String getQualifiedName() {
    return qualifiedName;
  }

  /**
   * Returns the erasure type (see definition of erasure type at
   * http://help.eclipse.org/luna/index.jsp) with an empty type arguments list.
   */
  public TypeDescriptor getRawTypeDescriptor() {
    if (rawTypeDescriptorFactory == null) {
      return null;
    }
    return rawTypeDescriptorFactory.create();
  }

  /**
   * Returns the unqualified and unenclosed simple name like "Inner".
   */
  public String getSimpleName() {
    return simpleName;
  }

  /**
   * Returns the fully package qualified source name like "com.google.common.Outer.Inner".
   */
  public String getSourceName() {
    return sourceName;
  }

  public TypeDescriptor getSuperTypeDescriptor() {
    if (superTypeDescriptorFactory == null) {
      return null;
    }
    return superTypeDescriptorFactory.create();
  }

  public List<TypeDescriptor> getTypeArgumentDescriptors() {
    return typeArgumentDescriptors;
  }

  public List<TypeDescriptor> getUnionedTypeDescriptors() {
    return unionedTypeDescriptors;
  }

  public String getUniqueId() {
    return uniqueId;
  }

  public Visibility getVisibility() {
    return visibility;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(getUniqueId());
  }

  /**
   * Returns whether the described type is an array.
   */
  public boolean isArray() {
    return isArray;
  }

  public boolean isEnumOrSubclass() {
    return isEnumOrSubclass;
  }

  public boolean isExtern() {
    return isExtern;
  }

  public boolean isGlobal() {
    return isGlobal;
  }

  public boolean isInstanceMemberClass() {
    return isInstanceMemberClass;
  }

  public boolean isInstanceNestedClass() {
    return isInstanceNestedClass;
  }

  public boolean isInterface() {
    return isInterface;
  }

  public boolean isJsFunctionImplementation() {
    return isJsFunctionImplementation;
  }

  public boolean isJsFunctionInterface() {
    return isJsFunction;
  }

  public boolean isJsType() {
    return isJsType;
  }

  /**
   * Returns whether the described type is a nested type (i.e. it is defined inside the body of some
   * enclosing type) but is not a member type because it's location in the body is not in the
   * declaration scope of the enclosing type. For example:
   *
   * <code>
   * class Foo {
   *   void bar() {
   *     class Baz {}
   *   }
   * }
   * </code>
   *
   * or
   *
   * <code>
   * class Foo {
   *   void bar() {
   *     Comparable comparable = new Comparable() { ... }
   *   }
   * }
   * </code>
   */
  public boolean isLocal() {
    return isLocal;
  }

  public boolean isNative() {
    return isNative;
  }

  public boolean isNullable() {
    return isNullable;
  }

  public boolean isParameterizedType() {
    return !getTypeArgumentDescriptors().isEmpty();
  }

  public boolean isPrimitive() {
    return isPrimitive;
  }

  /**
   * Returns whether this is a Raw reference. Raw references are not mangled in the output and thus
   * can be used to describe reference to JS apis.
   */
  public boolean isRaw() {
    return isRaw;
  }

  public boolean isRawType() {
    return isRawType;
  }

  public boolean isTypeVariable() {
    return isTypeVariable;
  }

  /**
   * Returns whether the described type is a union.
   */
  public boolean isUnion() {
    return isUnion;
  }

  public boolean isWildCard() {
    return isWildCard;
  }

  public boolean subclassesJsConstructorClass() {
    return subclassesJsConstructorClass;
  }

  @Override
  public String toString() {
    return getUniqueId();
  }
}
