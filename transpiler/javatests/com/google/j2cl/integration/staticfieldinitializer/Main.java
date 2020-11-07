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
package com.google.j2cl.integration.staticfieldinitializer;

import static com.google.j2cl.integration.testing.Asserts.assertTrue;

/**
 * Test static field initializer.
 */
public class Main {
  public static int simpleValue = 5;

  public static int calculatedValue = simpleValue * 5;

  public static int someStaticMethod() {
    return simpleValue * calculatedValue;
  }

  public static void main(String... args) {
    assertTrue(Main.simpleValue == 5);
    assertTrue(Main.calculatedValue == 25);
    assertTrue(Main.someStaticMethod() == 125);
  }
}
