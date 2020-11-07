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
package com.google.j2cl.integration.casts;

import static com.google.j2cl.integration.testing.Asserts.assertThrowsClassCastException;
import static com.google.j2cl.integration.testing.Asserts.assertTrue;

import java.io.Serializable;
import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsType;

public class Main {

  public static void main(String... args) {
    testCasts_basics();
    testCasts_generics();
    testCasts_typeVariableWithNativeBound();
    testCasts_parameterizedNativeType();
    testCasts_exceptionMessages();
    testArrayCasts_basics();
    testArrayCasts_differentDimensions();
    testArrayCasts_sameDimensions();
    testArrayCasts_erasureCastsOnArrayAccess_fromArrayOfT();
    testArrayCasts_erasureCastsOnArrayAccess_fromT();
    testArrayCasts_boxedTypes();
    testDevirtualizedCasts_object();
    testDevirtualizedCasts_number();
    testDevirtualizedCasts_comparable();
    testDevirtualizedCasts_charSequence();
    testDevirtualizedCasts_void();
  }

  public interface Interface {}

  private static void testCasts_basics() {
    Object o = null;
    String s = (String) o;

    Serializable serializable = new Serializable() {};
    Serializable unusedSerializable = (Serializable) serializable;
    assertThrowsClassCastException(
        () -> {
          RuntimeException unused = (RuntimeException) serializable;
        });

    Interface intf = new Interface() {};
    Interface unusedInterface = (Interface) intf;
    assertThrowsClassCastException(
        () -> {
          Serializable unused = (Serializable) intf;
        });
  }

  private static void testArrayCasts_basics() {
    // Cast null to Object[]
    Object o = (Object[]) null;

    // Cast null to Object[][]
    o = (Object[][]) null;

    // Cast JS "[]" to Object[]
    o = new Object[] {}; // Actually emits as the JS array literal "[]".
    o = (Object[]) o;

    // Cast JS "$Arrays.$init([], Object, 2))" to Object[][]
    o = new Object[][] {};
    o = (Object[][]) o;
  }

  private static void testArrayCasts_sameDimensions() {
    Object o = null;

    Object[] objects = new Object[0];
    String[] strings = new String[0];
    CharSequence[] charSequences = new CharSequence[0];

    o = (Object[]) objects;
    o = (Object[]) strings;
    o = (String[]) strings;
    o = (CharSequence[]) strings;
    o = (Object[]) charSequences;
    o = (CharSequence[]) charSequences;

    assertThrowsClassCastException(
        () -> {
          Object unused = (String[]) objects;
        },
        String[].class);

    assertThrowsClassCastException(
        () -> {
          Object unused = (CharSequence[]) objects;
        },
        CharSequence[].class);

    assertThrowsClassCastException(
        () -> {
          Object unused = (String[]) charSequences;
        },
        String[].class);
  }

  private static void testArrayCasts_differentDimensions() {
    Object object = new Object[10][10];

    // These are fine.
    Object[] object1d = (Object[]) object;
    Object[][] object2d = (Object[][]) object;

    // A 2d array cannot be cast to a 3d array.
    assertThrowsClassCastException(
        () -> {
          Object[][][] unused = (Object[][][]) object2d;
        },
        Object[][][].class);

    // A non-array cannot be cast to an array.
    assertThrowsClassCastException(
        () -> {
          Object[] unused = (Object[]) new Object();
        },
        Object[].class);
  }

  private static void testArrayCasts_erasureCastsOnArrayAccess_fromArrayOfT() {
    // Array of the right type.
    ArrayContainer<String> stringArrayInArrayContainer = new ArrayContainer<>(new String[1]);
    String unusedString = stringArrayInArrayContainer.data[0];
    int len = stringArrayInArrayContainer.data.length;
    assertTrue(len == 1);

    // Array of the wrong type.
    ArrayContainer<String> objectArrayInArrayContainer = new ArrayContainer<>(new Object[1]);
    assertThrowsClassCastException(
        () -> {
          String unused = objectArrayInArrayContainer.data[0];
        },
        String[].class);
    // Make sure access to the length field performs the right cast. The length field
    // has special handling in CompilationUnitBuider.
    assertThrowsClassCastException(
        () -> {
          int unused = objectArrayInArrayContainer.data.length;
        },
        String[].class);

    // Not even an array.
    assertThrowsClassCastException(
        () -> {
          ArrayContainer<String> container = new ArrayContainer<>(new Object());
        },
        Object[].class);
  }

  private static class ArrayContainer<T> {
    ArrayContainer(Object array) {
      this.data = (T[]) array;
    }

    T[] data;
  }

  private static void testArrayCasts_erasureCastsOnArrayAccess_fromT() {
    // Array of the right type.
    Container<String[]> stringArrayInContainer = new Container<>(new String[1]);
    String unusedString = stringArrayInContainer.data[0];
    int len = stringArrayInContainer.data.length;
    assertTrue(len == 1);

    // Array of the wrong type.
    Container<String[]> objectArrayInContainer = new Container<>(new Object[1]);
    assertThrowsClassCastException(
        () -> {
          String unused = objectArrayInContainer.data[0];
        },
        String[].class);
    assertThrowsClassCastException(
        () -> {
          int unused = objectArrayInContainer.data.length;
        },
        String[].class);

    // Not even an array.
    Container<String[]> notAnArrayInContainer = new Container<>(new Object());
    assertThrowsClassCastException(
        () -> {
          String unused = notAnArrayInContainer.data[0];
        },
        String[].class);
    assertThrowsClassCastException(
        () -> {
          int unused = notAnArrayInContainer.data.length;
        },
        String[].class);
  }

  private static class Container<T> {
    Container(Object array) {
      this.data = (T) array;
    }

    T data;
  }

  private static void testArrayCasts_boxedTypes() {
    Object b = new Byte((byte) 1);
    Byte unusedB = (Byte) b;
    Number unusedN = (Number) b;
    castToDoubleException(b);
    castToFloatException(b);
    castToIntegerException(b);
    castToLongException(b);
    castToShortException(b);
    castToCharacterException(b);
    castToBooleanException(b);

    Object d = new Double(1.0);
    Double unusedD = (Double) d;
    unusedN = (Number) d;
    castToByteException(d);
    castToFloatException(d);
    castToIntegerException(d);
    castToLongException(d);
    castToShortException(d);
    castToCharacterException(d);
    castToBooleanException(d);

    Object f = new Float(1.0f);
    Float unusedF = (Float) f;
    unusedN = (Number) f;
    castToByteException(f);
    castToDoubleException(f);
    castToIntegerException(f);
    castToLongException(f);
    castToShortException(f);
    castToCharacterException(f);
    castToBooleanException(f);

    Object i = new Integer(1);
    Integer unusedI = (Integer) i;
    unusedN = (Number) i;
    castToByteException(i);
    castToDoubleException(i);
    castToFloatException(i);
    castToLongException(i);
    castToShortException(i);
    castToCharacterException(i);
    castToBooleanException(i);

    Object l = new Long(1L);
    Long unusedL = (Long) l;
    unusedN = (Number) l;
    castToByteException(l);
    castToDoubleException(l);
    castToFloatException(l);
    castToIntegerException(l);
    castToShortException(l);
    castToCharacterException(l);
    castToBooleanException(l);

    Object s = new Short((short) 1);
    Short unusedS = (Short) s;
    unusedN = (Number) s;
    castToByteException(s);
    castToDoubleException(s);
    castToFloatException(s);
    castToIntegerException(s);
    castToLongException(s);
    castToCharacterException(s);
    castToBooleanException(s);

    Object c = new Character('a');
    Character unusedC = (Character) c;
    castToByteException(c);
    castToDoubleException(c);
    castToFloatException(c);
    castToIntegerException(c);
    castToLongException(c);
    castToShortException(c);
    castToNumberException(c);
    castToBooleanException(c);

    Object bool = new Boolean(true);
    Boolean unusedBool = (Boolean) bool;
    castToByteException(bool);
    castToDoubleException(bool);
    castToFloatException(bool);
    castToIntegerException(bool);
    castToLongException(bool);
    castToShortException(bool);
    castToNumberException(bool);
    castToCharacterException(bool);

    Object sn = new SubNumber();
    unusedN = (Number) sn;
  }

  private static void castToByteException(Object o) {
    assertThrowsClassCastException(
        () -> {
          Byte b = (Byte) o;
        });
  }

  private static void castToDoubleException(Object o) {
    assertThrowsClassCastException(
        () -> {
          Double d = (Double) o;
        });
  }

  private static void castToFloatException(Object o) {
    assertThrowsClassCastException(
        () -> {
          Float f = (Float) o;
        });
  }

  private static void castToIntegerException(Object o) {
    assertThrowsClassCastException(
        () -> {
          Integer i = (Integer) o;
        });
  }

  private static void castToLongException(Object o) {
    assertThrowsClassCastException(
        () -> {
          Long l = (Long) o;
        });
  }

  private static void castToShortException(Object o) {
    assertThrowsClassCastException(
        () -> {
          Short s = (Short) o;
        });
  }

  private static void castToCharacterException(Object o) {
    assertThrowsClassCastException(
        () -> {
          Character c = (Character) o;
        });
  }

  private static void castToBooleanException(Object o) {
    assertThrowsClassCastException(
        () -> {
          Boolean b = (Boolean) o;
        });
  }

  private static void castToNumberException(Object o) {
    assertThrowsClassCastException(
        () -> {
          Number n = (Number) o;
        });
  }

  private static class SubNumber extends Number {
    @Override
    public int intValue() {
      return 0;
    }

    @Override
    public long longValue() {
      return 0;
    }

    @Override
    public float floatValue() {
      return 0;
    }

    @Override
    public double doubleValue() {
      return 0;
    }
  }

  private static void testDevirtualizedCasts_object() {
    Object unusedObject = null;

    // All these casts should succeed.
    unusedObject = (Object) "";
    unusedObject = (Object) new Double(0);
    unusedObject = (Object) new Boolean(false);
    unusedObject = (Object) new Object[] {};
  }

  private static void testDevirtualizedCasts_number() {
    Number unusedNumber = null;

    // This casts should succeed.
    unusedNumber = (Number) new Double(0);
  }

  private static void testDevirtualizedCasts_comparable() {
    Comparable<?> unusedComparable = null;

    // All these casts should succeed.
    unusedComparable = (Comparable) "";
    unusedComparable = (Comparable) new Double(0);
    unusedComparable = (Comparable) new Boolean(false);
  }

  private static void testDevirtualizedCasts_charSequence() {
    CharSequence unusedCharSequence = null;

    // This casts should succeed.
    unusedCharSequence = (CharSequence) "";
  }

  private static void testDevirtualizedCasts_void() {
    Void unusedVoid = null;

    // This casts should succeed.
    unusedVoid = (Void) null;
  }

  @SuppressWarnings({"unused", "unchecked"})
  private static <T, E extends Number> void testCasts_generics() {
    Object o = new Integer(1);
    E e = (E) o; // cast to type variable with bound, casting Integer instance to Number
    T t = (T) o; // cast to type variable without bound, casting Integer instance to Object

    assertThrowsClassCastException(
        () -> {
          Object error = new Error();
          E unused = (E) error; // casting Error instance to Number, exception.
        });

    class Pameterized<T, E extends Number> {}

    Object c = new Pameterized<Number, Number>();
    Pameterized<Error, Number> cc = (Pameterized<Error, Number>) c; // cast to parameterized type.

    Object[] is = new Integer[1];
    Object[] os = new Object[1];
    E[] es = (E[]) is;
    T[] ts = (T[]) is;
    assertThrowsClassCastException(
        () -> {
          E[] ees = (E[]) os;
        });
    T[] tts = (T[]) os;
  }

  @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Map")
  private static class NativeMap<K, V> {}

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static <T extends NativeMap<?, ?>> void testCasts_typeVariableWithNativeBound() {
    {
      Object o = new Object[] {new Object()};
      T[] unusedArray = (T[]) o; // cast to T[].
      assertThrowsClassCastException(
          () -> {
            T unused = (T) o;
          });
    }
    {
      Object o = new NativeMap();
      T unused = (T) o;
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void testCasts_parameterizedNativeType() {
    Object a = new NativeMap<String, Object>();

    NativeMap e = (NativeMap) a;
    assertTrue(e == a);
    NativeMap<String, Object> f = (NativeMap<String, Object>) a;
    assertTrue(f == a);
    assertTrue(a instanceof NativeMap);

    Object os = new NativeMap[] {e};
    NativeMap[] g = (NativeMap[]) os;
    assertTrue(g[0] == e);
    NativeMap<String, Object>[] h = (NativeMap<String, Object>[]) os;
    assertTrue(h[0] == e);
    assertTrue(os instanceof NativeMap[]);
  }

  private static class Foo {}

  private static class Bar {}

  @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "String")
  private static class Baz {}

  @JsFunction
  interface Qux {
    String m(String s);
  }

  @SuppressWarnings("unused")
  private static void testCasts_exceptionMessages() {
    Object object = new Foo();
    assertThrowsClassCastException(
        () -> {
          Bar bar = (Bar) object;
        },
        Bar.class);
    assertThrowsClassCastException(
        () -> {
          Bar[] bars = (Bar[]) object;
        },
        Bar[].class);
    assertThrowsClassCastException(
        () -> {
          String string = (String) object;
        },
        String.class);
    assertThrowsClassCastException(
        () -> {
          Void aVoid = (Void) object;
        },
        Void.class);
    assertThrowsClassCastException(
        () -> {
          Baz baz = (Baz) object;
        },
        "String");
    assertThrowsClassCastException(
        () -> {
          Qux qux = (Qux) object;
        },
        "<native function>");
  }
}