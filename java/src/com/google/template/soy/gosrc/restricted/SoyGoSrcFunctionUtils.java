/*
 * Copyright 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.template.soy.gosrc.restricted;

import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;


/**
 * Utilities for implementing {@link SoyGoSrcFunction}s.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 * <p> Feel free to static import these helpers in your function implementation classes.
 *
 * @author Kai Huang
 */
public class SoyGoSrcFunctionUtils {

  private SoyGoSrcFunctionUtils() {}


  /**
   * Creates a new GoExpr with the given exprText and type BooleanData. 
   * @param exprText The Go expression text.
   */
  public static GoExpr toBooleanGoExpr(String exprText) {
    return new GoExpr(exprText, BooleanData.class, Integer.MAX_VALUE);
  }


  /**
   * Creates a new GoExpr with the given exprText and type IntegerData. 
   * @param exprText The Go expression text.
   */
  public static GoExpr toIntegerGoExpr(String exprText) {
    return new GoExpr(exprText, IntegerData.class, Integer.MAX_VALUE);
  }


  /**
   * Creates a new GoExpr with the given exprText and type FloatData.
   * @param exprText The Go expression text.
   */
  public static GoExpr toFloatGoExpr(String exprText) {
    return new GoExpr(exprText, FloatData.class, Integer.MAX_VALUE);
  }


  /**
   * Creates a new GoExpr with the given exprText and type NumberData.
   * @param exprText The Go expression text.
   */
  public static GoExpr toNumberGoExpr(String exprText) {
    return new GoExpr(exprText, NumberData.class, Integer.MAX_VALUE);
  }


  /**
   * Creates a new GoExpr with the given exprText and type StringData.
   * @param exprText The Go expression text.
   */
  public static GoExpr toStringGoExpr(String exprText) {
    return new GoExpr(exprText, StringData.class, Integer.MAX_VALUE);
  }


  /**
   * Creates a new GoExpr with the given exprText and type SoyData.
   * @param exprText The Go expression text.
   */
  public static GoExpr toUnknownGoExpr(String exprText) {
    return new GoExpr(exprText, SoyData.class, Integer.MAX_VALUE);
  }

}
