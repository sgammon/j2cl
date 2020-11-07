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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.j2cl.common.visitor.Processor;
import com.google.j2cl.common.visitor.Visitable;
import org.apache.commons.text.StringEscapeUtils;

/** String literal node. */
@Visitable
public class StringLiteral extends Literal {
  private final String value;

  public StringLiteral(String value) {
    this.value = checkNotNull(value);
  }

  public String getEscapedValue() {
    return "\"" + escapeJavaString(value) + "\"";
  }

  private static String escapeJavaString(String string) {
    // NOTE: StringEscapeUtils.escapeJava does not escape unprintable character 127 (delete).
    return StringEscapeUtils.escapeJava(string).replace("\u007f", "\\u007F");
  }

  @Override
  public boolean isNonNullString() {
    return true;
  }

  @Override
  public Node accept(Processor processor) {
    return Visitor_StringLiteral.visit(processor, this);
  }

  @Override
  public TypeDescriptor getTypeDescriptor() {
    return TypeDescriptors.get().javaLangString;
  }

  @Override
  public StringLiteral clone() {
    // String literals are value types do not need to actually clone.
    return this;
  }
}
