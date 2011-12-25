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

import com.google.common.base.Joiner;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.TemplateNode;

import java.util.regex.Pattern;


/**
 * Utilities for building code for the Go Source backend.
 *
 * <p> Important: This class may only be used in implementing plugins (e.g. functions, directives).
 *
 * @author Kai Huang
 */
public class GoCodeUtils {

  private GoCodeUtils() {}


  public static final String UTILS_LIB =
      "soyutil";

  // group(1) is the number literal.
  private static final Pattern NUMBER_IN_PARENS = Pattern.compile("^[(]([0-9]+(?:[.][0-9]+)?)[)]$");

  
  public static String finalNamespace(String namespace) {
    if(namespace == null || namespace.indexOf('.') < 0)
      return namespace;
    return namespace.substring(namespace.lastIndexOf('.') + 1);
  }
  
  public static String publicize(String value) {
    if(value == null || value.isEmpty())
      return value;
    int lastNamespace = value.lastIndexOf('.') + 1;
    return value.substring(0, lastNamespace) + Character.toUpperCase(value.charAt(lastNamespace)) + value.substring(lastNamespace+1);
  }
  
  public static String privatize(String value) {
    if(value == null || value.isEmpty())
      return value;
    int lastNamespace = value.lastIndexOf('.') + 1;
    return value.substring(0, lastNamespace) + Character.toLowerCase(value.charAt(lastNamespace)) + value.substring(lastNamespace+1);
  }
  
  public static String goTemplateName(ParentSoyNode<? extends SoyNode> node, String templateName) {
    while(node != null && !(node instanceof SoyFileNode)) {
      node = node.getParent();
    }
    if(node == null) {
      return templateName;
    }
    boolean hasDot = (templateName.indexOf('.') >= 0);
    for(TemplateNode templateNode : ((SoyFileNode)node).getChildren()) {
      String compareTemplateName = templateNode.getTemplateName();
      if(!hasDot) {
        compareTemplateName = compareTemplateName.substring(compareTemplateName.lastIndexOf('.')  + 1);
      }
      if(templateName.equals(compareTemplateName)) {
        if(templateNode.isPrivate()) {
          return privatize(finalNamespace(templateName));
        }
        return publicize(finalNamespace(templateName));
      }
    }
    return publicize(templateName);
  }

  public static String genMaybeProtect(GoExpr expr, int minSafePrecedence) {
    return (expr.getPrecedence() >= minSafePrecedence) ?
           expr.getText() : "(" + expr.getText() + ")";
  }


  public static String genNewBooleanData(String innerExprText) {
    return "soyutil.NewBooleanData(" + innerExprText + ")";
  }


  public static String genNewIntegerData(String innerExprText) {
    return "soyutil.NewIntegerData(" + innerExprText + ")";
  }


  public static String genNewFloatData(String innerExprText) {
    return "soyutil.NewFloat64Data(" + innerExprText + ")";
  }


  public static String genNewStringData(String innerExprText) {
    return "soyutil.NewStringData(" + innerExprText + ")";
  }


  public static String genNewListData(String innerExprText) {
    return "soyutil.NewSoyListDataFromArgs(" + innerExprText + ")";
  }


  public static String genNewMapData(String innerExprText) {
    return "soyutil.NewSoyMapDataFromArgs(" + innerExprText + ")";
  }


  public static String genNewSanitizedContent(
      String innerExprText, SanitizedContent.ContentKind contentKind) {
    return "soyutil.NewSanitizedContent(" + innerExprText + ", " +
        "soyutil.CONTENT_KIND_" + contentKind.name() + ")";
  }

  
  public static int matchingParenthesis(String s, int startIndex) {
    int openCount = 0;
    char[] dst = new char[s.length()];
    s.getChars(0, s.length(), dst, 0);
    for(int i=startIndex; i<dst.length; ++i) {
      char c = dst[i];
      switch(c) {
      case '(':
        openCount++;
        break;
      case ')':
        if(openCount == 0)
          return i;
        openCount--;
      }
    }
    return -1;
  }
  
  private static String stripWithParenthesis(String text, String removeWithParenthesis) {
    int startIndex = removeWithParenthesis.length();
    int lastIndex = matchingParenthesis(text, startIndex);
    if(lastIndex >= 0) {
      return text.substring(startIndex, lastIndex) + text.substring(lastIndex + 1);
    }
    return text.substring(removeWithParenthesis.length()-1);
  }


  public static String genCoerceBoolean(GoExpr expr) {

    // Special case: If the expr is wrapped by "new BooleanData()", remove the "new BooleanData"
    // instead of generating silly code. We leave the parentheses because we don't know whether the
    // inner expression needs protection.
    String exprText = expr.getText();
    if (exprText.startsWith("soyutil.NewBooleanData(")) {
      return stripWithParenthesis(exprText, "soyutil.NewBooleanData(");
    }

    // Normal case.
    return genMaybeProtect(expr, Integer.MAX_VALUE) + ".Bool()";
  }


  public static String genCoerceString(GoExpr expr) {

    // Special case: If the expr is wrapped by "new StringData()", remove the "new StringData"
    // instead of generating silly code. We leave the parentheses because we don't know whether the
    // inner expression needs protection.
    String exprText = expr.getText();
    if (exprText.startsWith("soyutil.NewStringData(")) {
      return stripWithParenthesis(exprText, "soyutil.NewStringData(");
    }
    if (expr.getType() == String.class) {
      return exprText;
    }

    // Normal case.
    return genMaybeProtect(expr, Integer.MAX_VALUE) + ".String()";
  }


  public static String genCoerceNumber(GoExpr expr) {

    // Special case: If the expr is wrapped by "new StringData()", remove the "new StringData"
    // instead of generating silly code. We leave the parentheses because we don't know whether the
    // inner expression needs protection.
    String exprText = expr.getText();
    if (exprText.startsWith("soyutil.NewIntegerData(")) {
      return stripWithParenthesis(exprText, "soyutil.NewIntegerData(");
    }
    if (exprText.startsWith("soyutil.NewFloat64Data(")) {
      return stripWithParenthesis(exprText, "soyutil.NewFloat64Data(");
    }
    if (expr.getType() == Float.class || expr.getType() == Integer.class || expr.getType() == Double.class) {
      return exprText;
    }

    // Normal case.
    return genMaybeProtect(expr, Integer.MAX_VALUE) + ".NumberValue()";
  }


  public static String genBooleanValue(GoExpr expr) {

    // Special case: If the expr is wrapped by "new BooleanData()", remove the "new BooleanData"
    // instead of generating silly code. We leave the parentheses because we don't know whether the
    // inner expression needs protection.
    String exprText = expr.getText();
    if (exprText.startsWith("soyutil.NewBooleanData(")) {
      return stripWithParenthesis(exprText, "soyutil.NewBooleanData(");
    }

    // Normal case.
    return genMaybeProtect(expr, Integer.MAX_VALUE) + ".BooleanValue()";
  }


  public static String genIntegerValue(GoExpr expr) {

    // Special case: If the expr is wrapped by "new IntegerData()", remove the "new IntegerData"
    // instead of generating silly code. We leave the parentheses because we don't know whether the
    // inner expression needs protection.
    String exprText = expr.getText();
    if (exprText.startsWith("soyutil.NewIntegerData(")) {
      String result = stripWithParenthesis(exprText, "soyutil.NewIntegerData(");
      if (NUMBER_IN_PARENS.matcher(result).matches()) {
        result = result.substring(1, result.length() - 1);
      }
      return result;
    }

    // Normal case.
    return genMaybeProtect(expr, Integer.MAX_VALUE) + ".IntegerValue()";
  }


  public static String genFloatValue(GoExpr expr) {

    // Special case: If the expr is wrapped by "new FloatData()", remove the "new FloatData"
    // instead of generating silly code. We leave the parentheses because we don't know whether the
    // inner expression needs protection.
    String exprText = expr.getText();
    if (exprText.startsWith("soyutil.NewFloat64Data(")) {
      String result = stripWithParenthesis(exprText, "soyutil.NewFloat64Data(");
      if (NUMBER_IN_PARENS.matcher(result).matches()) {
        result = result.substring(1, result.length() - 1);
      }
      return result;
    }

    // Normal case.
    return genMaybeProtect(expr, Integer.MAX_VALUE) + ".Float64Value()";
  }


  public static String genNumberValue(GoExpr expr) {

    // Special case: If the expr is wrapped by "new IntegerData" or "new FloatData()", remove the
    // "new IntegerData" or "new FloatData" instead of generating silly code. We leave the
    // parentheses because we don't know whether the inner expression needs protection.
    String exprText = expr.getText();
    String result = null;
    if (exprText.startsWith("soyutil.NewIntegerData(")) {
      result = stripWithParenthesis(exprText, "soyutil.NewIntegerData(");
    }
    if (exprText.startsWith("soyutil.NewFloat64Data(")) {
      result = stripWithParenthesis(exprText, "soyutil.NewFloat64Data(");
    }
    if (result != null) {
      if (NUMBER_IN_PARENS.matcher(result).matches()) {
        result = result.substring(1, result.length() - 1);
      }
      return result;
    }

    // Normal case.
    return genMaybeProtect(expr, Integer.MAX_VALUE) + ".NumberValue()";
  }


  public static String genStringValue(GoExpr expr) {

    // Special case: If the expr is wrapped by "new StringData()", remove the "new StringData"
    // instead of generating silly code. We leave the parentheses because we don't know whether the
    // inner expression needs protection.
    String exprText = expr.getText();
    if (exprText.startsWith("soyutil.NewStringData(")) {
      return stripWithParenthesis(exprText, "soyutil.NewStringData(");
    }

    // Normal case.
    return genMaybeProtect(expr, Integer.MAX_VALUE) + ".StringValue()";
  }


  public static String genMaybeCast(GoExpr expr, Class<? extends SoyData> class0) {
    if (class0.isAssignableFrom(expr.getType())) {
      return expr.getText();
    } else {
      String className = class0.getName();
      String convertFunc;
      if("com.google.template.soy.data.restricted.CollectionData".equals(className)) {
        convertFunc = "soyutil.ToSoyMapData";
      } else if("com.google.template.soy.data.restricted.NumberData".equals(className)) {
        convertFunc = "soyutil.ToSoyDataNoErr";
      } else if("com.google.template.soy.data.restricted.NullData".equals(className)) {
        convertFunc = "soyutil.NilData";
      } else if("com.google.template.soy.data.restricted.PrimitiveData".equals(className)) {
        convertFunc = "soyutil.ToSoyData";
      } else if("com.google.template.soy.data.SoyListData".equals(className)) {
        convertFunc = "soyutil.ToSoyListData";
      } else if("com.google.template.soy.data.SoyMapData".equals(className)) {
        convertFunc = "soyutil.ToSoyMapData";
      } else if("com.google.template.soy.data.SoyData".equals(className)) {
        convertFunc = "soyutil.ToSoyDataNoErr";
      } else if(className.startsWith("com.google.template.soy.data.restricted.")) {
        convertFunc = "soyutil.To" + className.substring(className.lastIndexOf('.') + 1);
      } else {
        convertFunc = className;
      }
      return convertFunc + "(" + expr.getText() + ")";
    }
  }


  public static String genConditional(
      String condExprText, String thenExprText, String elseExprText) {
    return "[" + elseExprText + ", " + thenExprText + "][soyutil.BoolToInt(" + condExprText + ")]";
  }


  public static boolean isAlwaysInteger(GoExpr expr) {
    return IntegerData.class.isAssignableFrom(expr.getType());
  }


  public static boolean isAlwaysFloat(GoExpr expr) {
    return FloatData.class.isAssignableFrom(expr.getType());
  }


  public static boolean isAlwaysString(GoExpr expr) {
    return StringData.class.isAssignableFrom(expr.getType());
  }


  public static boolean isAlwaysNumber(GoExpr expr) {
    return NumberData.class.isAssignableFrom(expr.getType());
  }


  public static boolean isAlwaysTwoIntegers(GoExpr expr0, GoExpr expr1) {
    return isAlwaysInteger(expr0) && isAlwaysInteger(expr1);
  }


  public static boolean isAlwaysTwoFloatsOrOneFloatOneInteger(GoExpr expr0, GoExpr expr1) {
    return (isAlwaysFloat(expr0) && isAlwaysNumber(expr1)) ||
           (isAlwaysFloat(expr1) && isAlwaysNumber(expr0));
  }


  public static boolean isAlwaysAtLeastOneFloat(GoExpr expr0, GoExpr expr1) {
    return isAlwaysFloat(expr0) || isAlwaysFloat(expr1);
  }


  public static boolean isAlwaysAtLeastOneString(GoExpr expr0, GoExpr expr1) {
    return isAlwaysString(expr0) || isAlwaysString(expr1);
  }


  public static String genUnaryOp(String operatorExprText, String operandExprText) {
    return operatorExprText + "(" + operandExprText + ")";
  }


  public static String genBinaryOp(
      String operatorExprText, String operand0ExprText, String operand1ExprText) {
    return operand0ExprText + " " + operatorExprText + " " + operand1ExprText;
  }


  public static String genFunctionCall(
      String functionNameExprText, String... functionArgsExprTexts) {
    return functionNameExprText + "(" + Joiner.on(", ").join(functionArgsExprTexts) + ")";
  }


  public static GoExpr genGoExprForNumberToNumberBinaryFunction(
      String goFunctionName, String utilsLibFunctionName, GoExpr arg0, GoExpr arg1) {

    if (isAlwaysTwoIntegers(arg0, arg1)) {
      String exprText = genNewIntegerData(genFunctionCall(
          goFunctionName, genIntegerValue(arg0), genIntegerValue(arg1)));
      return new GoExpr(exprText, IntegerData.class, Integer.MAX_VALUE);

    } else if (isAlwaysAtLeastOneFloat(arg0, arg1)) {
      String exprText = genNewFloatData(genFunctionCall(
          goFunctionName, genFloatValue(arg0), genFloatValue(arg1)));
      return new GoExpr(exprText, FloatData.class, Integer.MAX_VALUE);

    } else {
      String exprText = genFunctionCall(
          UTILS_LIB + "." + utilsLibFunctionName,
          genMaybeCast(arg0, NumberData.class),
          genMaybeCast(arg1, NumberData.class));
      return new GoExpr(exprText, NumberData.class, Integer.MAX_VALUE);
    }
  }

}
