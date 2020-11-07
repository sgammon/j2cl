// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

/**
 * Impl hand rolled.
 */
goog.module('vmbootstrap.Comparables$impl');

let Boolean = goog.forwardDeclare('java.lang.Boolean$impl');
let Comparable = goog.forwardDeclare('java.lang.Comparable$impl');
let Double = goog.forwardDeclare('java.lang.Double$impl');
let String = goog.forwardDeclare('java.lang.String$impl');


/**
 * Provides devirtualized method implementations for Comparable.
 */
class Comparables {
  /**
   * @param {Comparable|string|number|boolean} a
   * @param {*} b
   * @return {number}
   * @public
   */
  static m_compareTo__java_lang_Comparable__java_lang_Object(a, b) {
    Comparables.$clinit();
    var type = typeof a;
    // Dispatch through the corresponding devirtualized 'bridge' method.
    if (type == 'number') {
      return Double.m_compareTo__java_lang_Object(
          /**@type {number} */ (a), /**@type {number} */ (b));
    } else if (type == 'boolean') {
      return Boolean.m_compareTo__java_lang_Object(
          /**@type {boolean} */ (a), /**@type {boolean} */ (b));
    } else if (type == 'string') {
      return String.m_compareTo__java_lang_Object(
          /**@type {string} */ (a), /**@type {string} */ (b));
    }
    return a.m_compareTo__java_lang_Object(b);
  }

  /**
   * Runs inline static field initializers.
   * @public
   */
  static $clinit() {
    Comparables.$clinit = function() {};
    Boolean = goog.module.get('java.lang.Boolean$impl');
    Comparable = goog.module.get('java.lang.Comparable$impl');
    Double = goog.module.get('java.lang.Double$impl');
    String = goog.module.get('java.lang.String$impl');
  }
}


/**
 * Exported class.
 */
exports = Comparables;
