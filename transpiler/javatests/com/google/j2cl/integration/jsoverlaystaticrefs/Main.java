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
package com.google.j2cl.integration.jsoverlaystaticrefs;

import static com.google.j2cl.integration.testing.Asserts.assertTrue;

import jsinterop.annotations.JsOverlay;
import jsinterop.annotations.JsType;

public class Main {

  @JsType(isNative = true, namespace = "test.foo")
  static class NativeTypeWithStaticOverlay {
    @JsOverlay public static Object staticField = new Object();

    @JsOverlay
    private static final Object getStaticField() {
      return staticField;
    }
  }

  @JsType(isNative = true, namespace = "test.foo")
  static class NativeTypeWithInstanceOverlay {
    @JsOverlay public static Object staticField = new Object();

    @JsOverlay
    final Object getStaticField() {
      return staticField;
    }
  }

  public static void testNativeJsWithOverlay() {
    assertTrue(NativeTypeWithStaticOverlay.getStaticField() != null);

    assertTrue(new NativeTypeWithInstanceOverlay().getStaticField() != null);
  }

  public static void main(String... args) {
    testNativeJsWithOverlay();
  }
}
