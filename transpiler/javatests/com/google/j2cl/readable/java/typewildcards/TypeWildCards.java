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
package typewildcards;

import java.util.List;
import java.util.function.Function;

class GenericType<T> {}

public class TypeWildCards {
  public void unbounded(GenericType<?> g) {}

  public void upperBound(GenericType<? extends TypeWildCards> g) {}

  public void lowerBound(GenericType<? super TypeWildCards> g) {}

  public void test() {
    unbounded(new GenericType<TypeWildCards>());
    upperBound(new GenericType<TypeWildCards>());
    lowerBound(new GenericType<TypeWildCards>());
  }

  private interface X {
    void m();
  }

  private interface Y {
    void n();
  }

  private static class A implements X {
    int f;

    @Override
    public void m() {}
  }

  public static <T extends A> void testBoundedTypeMemberAccess(T t) {
    int i = t.f;
    t.m();
  }

  public static <T extends A & Y> void testIntersectionBoundedTypeMemberAccess(T t) {
    int i = t.f;
    t.m();
    t.n();
  }

  public interface IntegerSupplier {
    Integer get();
  }

  public interface HasKey {
    String getKey();
  }

  public abstract class Element implements HasKey, IntegerSupplier {}

  public abstract class OtherElement implements IntegerSupplier, HasKey {}

  public abstract class SubOtherElement extends OtherElement implements HasKey {}

  private static <F, V> List<V> transform(List<F> from, Function<? super F, ? extends V> function) {
    return null;
  }

  private static <E> List<E> concat(List<? extends E> a, List<? extends E> b) {
    return null;
  }

  public void testInferredGenericIntersection() {
    List<Element> elements = null;
    List<SubOtherElement> otherElements = null;
    // This is a rather complicated way to make sure the inference ends with a wildcard extending
    // multiple bounds. This is type of code that  exposes b/67858399.
    List<Integer> integers =
        transform(
            concat(elements, otherElements),
            a -> {
              a.getKey();
              return a.get();
            });
  }

  class Foo extends GenericType<Foo> {}

  private static void takesRecursiveGeneric(GenericType<Foo> foo) {}

  public void testRecursiveGeneric() {
    takesRecursiveGeneric(new Foo());
  }
}
