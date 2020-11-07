/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.j2cl.integration.interfaces;

import static com.google.j2cl.integration.testing.Asserts.assertEquals;
import static com.google.j2cl.integration.testing.Asserts.assertTrue;

/** Test basic interface functionality. */
@SuppressWarnings("StaticQualifiedUsingExpression")
public class Main {

  public static void main(String[] args) {
    testInterfaceDispatch();
    testInterfaceWithFields();
    testDefaultMethods();
    testSuperCallDefaultMethod();
    testStaticMethods();
    testPrivateMethods();
    testDiamondProperty();
  }

  public interface SomeInterface {
    int run();
  }

  private static class SomeClass implements SomeInterface {
    @Override
    public int run() {
      return 1;
    }
  }

  public static int run(SomeInterface someInterface) {
    return someInterface.run();
  }

  private static void testInterfaceDispatch() {
    SomeClass s = new SomeClass();
    assertTrue(run(s) == 1);
  }

  public interface InterfaceWithFields {
    public int A = 1;
    public static int B = 2;
  }

  private static void testInterfaceWithFields() {
    assertTrue(InterfaceWithFields.A == 1);
    assertTrue(InterfaceWithFields.B == 2);
    InterfaceWithFields i = new InterfaceWithFields() {};
    assertTrue(i.A == 1);
    assertTrue(i.B == 2);
  }

  public static final int COLLECTION_ADD = 1;
  public static final int LIST_ADD = 2;
  public static final int ABSTRACT_COLLECTION_ADD = 3;
  public static final int ANOTHER_STRING_LIST_ADD = 4;
  public static final int ANOTHER_LIST_INTERFACE_ADD = 5;

  interface Collection<T> {
    default int add(T elem) {
      assertTrue(this instanceof Collection);
      return COLLECTION_ADD;
    }
  }

  interface List<T> extends Collection<T> {
    @Override
    default int add(T elem) {
      assertTrue(this instanceof List);
      return LIST_ADD;
    }

    static int returnOne() {
      return 1;
    }
  }

  abstract static class AbstractCollection<T> implements Collection<T> {
    @Override
    public int add(T elem) {
      assertTrue(this instanceof AbstractCollection);
      return ABSTRACT_COLLECTION_ADD;
    }
  }

  static class ACollection<T> implements Collection<T> {}

  abstract static class AbstractList<T> extends AbstractCollection<T> implements List<T> {}

  static class AConcreteList<T> extends AbstractList<T> {}

  static class SomeOtherCollection<T> implements Collection<T> {}

  static class SomeOtherList<T> extends SomeOtherCollection<T> implements List<T> {}

  // Should inherit List.add  even though the interface is not directly declared.
  static class YetAnotherList<T> extends SomeOtherList<T> implements Collection<T> {}

  static class StringList implements List<String> {}

  static class AnotherStringList implements List<String> {
    @Override
    public int add(String elem) {
      return ANOTHER_STRING_LIST_ADD;
    }
  }

  interface AnotherListInterface<T> {
    default int add(T elem) {
      assertTrue(this instanceof AnotherListInterface);
      return ANOTHER_LIST_INTERFACE_ADD;
    }
  }

  static class AnotherCollection<T> implements List<T>, AnotherListInterface<T> {
    @Override
    public int add(T elem) {
      return AnotherListInterface.super.add(elem);
    }
  }

  private static void testDefaultMethods() {
    assertTrue(new ACollection<Object>().add(null) == COLLECTION_ADD);
    assertTrue(new AConcreteList<Object>().add(null) == ABSTRACT_COLLECTION_ADD);
    assertTrue(new SomeOtherCollection<Object>().add(null) == COLLECTION_ADD);
    assertTrue(new SomeOtherList<Object>().add(null) == LIST_ADD);
    assertTrue(new YetAnotherList<Object>().add(null) == LIST_ADD);
    assertTrue(new StringList().add(null) == LIST_ADD);
    assertTrue(new AnotherStringList().add(null) == ANOTHER_STRING_LIST_ADD);
    assertTrue(new AnotherCollection<Object>().add(null) == ANOTHER_LIST_INTERFACE_ADD);
  }

  private static void testStaticMethods() {
    assertTrue(List.returnOne() == 1);
  }

  interface InterfaceWithPrivateMethods {
    default int defaultMethod() {
      return privateInstanceMethod();
    }

    private int privateInstanceMethod() {
      return m();
    }

    private static int privateStaticMethod() {
      return ((InterfaceWithPrivateMethods) () -> 1).privateInstanceMethod() + 1;
    }

    int m();

    static int callPrivateStaticMethod() {
      return privateStaticMethod();
    }
  }

  private static void testPrivateMethods() {
    assertTrue(InterfaceWithPrivateMethods.callPrivateStaticMethod() == 2);
    assertTrue(((InterfaceWithPrivateMethods) () -> 1).defaultMethod() == 1);
  }

  interface InterfaceWithDefaultMethod {
    default String defaultMethod() {
      return "default-method";
    }
  }

  private static void testSuperCallDefaultMethod() {
    abstract class AbstractClass implements InterfaceWithDefaultMethod {}

    class SubClass extends AbstractClass {
      public String defaultMethod() {
        return super.defaultMethod();
      }
    }

    assertEquals("default-method", new SubClass().defaultMethod());
  }

  interface DiamondLeft<T extends DiamondLeft<T>> {
    String NAME = "DiamondLeft";

    default String name(T t) {
      return NAME;
    }
  }

  interface DiamondRight<T extends DiamondRight<T>> {
    String NAME = "DiamondRight";

    default String name(T t) {
      return NAME;
    }
  }

  interface Bottom<T extends Bottom<T>> extends DiamondLeft<T>, DiamondRight<T> {
    String NAME = "Bottom";

    @Override
    default String name(T t) {
      return NAME;
    }
  }

  static class A<T extends DiamondLeft<T>, V extends DiamondRight<V>>
      implements DiamondLeft<T>, DiamondRight<V> {}

  static class B extends A<B, B> implements Bottom<B> {}

  static class C implements Bottom<C> {
    static String NAME = "C";

    @Override
    public String name(C c) {
      return NAME;
    }
  }

  private static void testDiamondProperty() {
    A<? extends DiamondLeft<?>, ? extends DiamondRight<?>> a = new A<>();
    DiamondLeft<?> dl = a;
    assertEquals(DiamondLeft.NAME, dl.name(null));
    DiamondRight<?> dr = a;
    assertEquals(DiamondRight.NAME, dr.name(null));

    a = new B();
    dl = a;
    assertEquals(Bottom.NAME, dl.name(null));
    dr = a;
    assertEquals(Bottom.NAME, dr.name(null));

    C c = new C();
    dl = c;
    assertEquals(C.NAME, dl.name(null));
    dr = c;
    assertEquals(C.NAME, dr.name(null));
    assertEquals(C.NAME, c.name(null));
  }
}
