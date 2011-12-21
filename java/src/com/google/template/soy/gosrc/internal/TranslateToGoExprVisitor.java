/*
 * Copyright 2008 Google Inc.
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

package com.google.template.soy.gosrc.internal;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.SoyListData;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.CollectionData;
import com.google.template.soy.data.restricted.FloatData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.data.restricted.NullData;
import com.google.template.soy.data.restricted.NumberData;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.exprtree.BooleanNode;
import com.google.template.soy.exprtree.DataRefIndexNode;
import com.google.template.soy.exprtree.DataRefKeyNode;
import com.google.template.soy.exprtree.DataRefNode;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprNode.OperatorNode;
import com.google.template.soy.exprtree.ExprNode.ParentExprNode;
import com.google.template.soy.exprtree.AbstractReturningExprNodeVisitor;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.FloatNode;
import com.google.template.soy.exprtree.FunctionNode;
import com.google.template.soy.exprtree.GlobalNode;
import com.google.template.soy.exprtree.IntegerNode;
import com.google.template.soy.exprtree.ListLiteralNode;
import com.google.template.soy.exprtree.MapLiteralNode;
import com.google.template.soy.exprtree.NullNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.exprtree.OperatorNodes.AndOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ConditionalOpNode;
import com.google.template.soy.exprtree.OperatorNodes.DivideByOpNode;
import com.google.template.soy.exprtree.OperatorNodes.EqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.GreaterThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOpNode;
import com.google.template.soy.exprtree.OperatorNodes.LessThanOrEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.MinusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.ModOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NegativeOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotEqualOpNode;
import com.google.template.soy.exprtree.OperatorNodes.NotOpNode;
import com.google.template.soy.exprtree.OperatorNodes.OrOpNode;
import com.google.template.soy.exprtree.OperatorNodes.PlusOpNode;
import com.google.template.soy.exprtree.OperatorNodes.TimesOpNode;
import com.google.template.soy.exprtree.StringNode;
import com.google.template.soy.internal.base.CharEscapers;
import com.google.template.soy.shared.internal.NonpluginFunction;

import static com.google.template.soy.gosrc.restricted.GoCodeUtils.UTILS_LIB;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genBinaryOp;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genCoerceBoolean;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genCoerceString;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genFloatValue;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genFunctionCall;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genIntegerValue;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genMaybeCast;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genMaybeProtect;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genNewBooleanData;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genNewFloatData;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genNewIntegerData;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genNewListData;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genNewMapData;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genNewStringData;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genNumberValue;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genUnaryOp;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.isAlwaysAtLeastOneFloat;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.isAlwaysAtLeastOneString;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.isAlwaysFloat;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.isAlwaysInteger;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.isAlwaysTwoFloatsOrOneFloatOneInteger;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.isAlwaysTwoIntegers;

import com.google.template.soy.gosrc.restricted.GoExpr;
import com.google.template.soy.gosrc.restricted.SoyGoSrcFunction;

import java.util.Deque;
import java.util.List;
import java.util.Map;


/**
 * Visitor for translating a Soy expression (in the form of an {@code ExprNode}) into an
 * equivalent Go expression.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class TranslateToGoExprVisitor extends AbstractReturningExprNodeVisitor<GoExpr> {


  /**
   * Injectable factory for creating an instance of this class.
   */
  public static interface TranslateToGoExprVisitorFactory {

    /**
     * @param localVarTranslations The current stack of replacement Go expressions for the local
     *     variables (and foreach-loop special functions) current in scope.
     */
    public TranslateToGoExprVisitor create(Deque<Map<String, GoExpr>> localVarTranslations);
  }


  /** Map of all SoyGoSrcFunctions (name to function). */
  private final Map<String, SoyGoSrcFunction> soyGoSrcFunctionsMap;

  /** The current stack of replacement Go expressions for the local variables (and foreach-loop
   *  special functions) current in scope. */
  private final Deque<Map<String, GoExpr>> localVarTranslations;


  /**
   * @param soyGoSrcFunctionsMap Map of all SoyGoSrcFunctions (name to function).
   * @param localVarTranslations The current stack of replacement Go expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   */
  @AssistedInject
  TranslateToGoExprVisitor(
      Map<String, SoyGoSrcFunction> soyGoSrcFunctionsMap,
      @Assisted Deque<Map<String, GoExpr>> localVarTranslations) {
    this.soyGoSrcFunctionsMap = soyGoSrcFunctionsMap;
    this.localVarTranslations = localVarTranslations;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementation for a dummy root node.


  @Override protected GoExpr visitExprRootNode(ExprRootNode<? extends ExprNode> node) {
    return visit(node.getChild(0));
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for primitives and data references (concrete classes).


  @Override protected GoExpr visitNullNode(NullNode node) {
    return new GoExpr(
        "soyutil.NilDataInstance",
        NullData.class, Integer.MAX_VALUE);
  }


  @Override protected GoExpr visitBooleanNode(BooleanNode node) {
    // Soy boolean literals have same form as Go 'boolean' literals.
	return convertBooleanResult(genNewBooleanData(node.toSourceString()));
  }


  @Override protected GoExpr visitIntegerNode(IntegerNode node) {
    // Soy integer literals have same form as Go 'int' literals.
	return convertIntegerResult(genNewIntegerData(node.toSourceString()));
  }


  @Override protected GoExpr visitFloatNode(FloatNode node) {
    // Soy float literals have same form as Go 'double' literals.
    return convertFloatResult(genNewFloatData(node.toSourceString()));
  }


  @Override protected GoExpr visitStringNode(StringNode node) {
    return convertStringResult(genNewStringData(
        '"' + CharEscapers.goStringEscaper().escape(node.getValue()) + '"'));
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for collections.


  @Override protected GoExpr visitListLiteralNode(ListLiteralNode node) {
    return convertListResult(genNewListData(buildCommaSepChildrenListHelper(node)));
  }


  @Override protected GoExpr visitMapLiteralNode(MapLiteralNode node) {
    return convertMapResult(genNewMapData(buildCommaSepChildrenListHelper(node)));
  }


  /**
   * Private helper for visitListLiteralNode() and visitMapLiteralNode() to build a
   * comma-separated list of children expression texts.
   * @param node The parent node whose children should be visited and then the resulting expression
   *     texts joined into a comma-separated list.
   * @return A comma-separated list of children expression texts.
   */
  private String buildCommaSepChildrenListHelper(ParentExprNode node) {

    StringBuilder resultSb = new StringBuilder();
    boolean isFirst = true;
    for (ExprNode child : node.getChildren()) {
      if (isFirst) {
        isFirst = false;
      } else {
        resultSb.append(", ");
      }
      resultSb.append(visit(child).getText());
    }
    return resultSb.toString();
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for data references.


  @Override protected GoExpr visitDataRefNode(DataRefNode node) {

    if (node.isIjDataRef()) {
      // Case 1: $ij data reference.
      return convertUnknownResult(genFunctionCall(
          UTILS_LIB + "GetIjData", buildKeyStringExprText(node, 0)));

    } else {
      GoExpr translation = getLocalVarTranslation(node.getFirstKey());
      if (translation != null) {
        // Case 2: In-scope local var.
        if (node.numChildren() == 1) {
          return translation;
        } else {
          return convertUnknownResult(genFunctionCall(
              UTILS_LIB + ".GetData",
              genMaybeCast(translation, CollectionData.class), buildKeyStringExprText(node, 1)));
        }

      } else {
        // Case 3: Data reference.
        return convertUnknownResult(genFunctionCall(
            UTILS_LIB + ".GetData", "data", buildKeyStringExprText(node, 0)));
      }
    }
  }


  /**
   * Private helper for visitInternal(DataRefNode).
   * @param node -
   * @param startIndex -
   */
  private String buildKeyStringExprText(DataRefNode node, int startIndex) {

    List<String> keyStrParts = Lists.newArrayList();
    StringBuilder currStringLiteralPart = new StringBuilder();

    for (int i = startIndex; i < node.numChildren(); i++) {
      ExprNode child = node.getChild(i);

      if (i != startIndex) {
        currStringLiteralPart.append(".");
      }

      if (child instanceof DataRefKeyNode) {
        currStringLiteralPart.append(
            CharEscapers.goStringEscaper().escape(((DataRefKeyNode) child).getKey()));
      } else if (child instanceof DataRefIndexNode) {
        currStringLiteralPart.append(Integer.toString(((DataRefIndexNode) child).getIndex()));
      } else {
        GoExpr childGoExpr = visit(child);
        keyStrParts.add("\"" + currStringLiteralPart.toString() + "\"");
        keyStrParts.add(genMaybeProtect(childGoExpr, Integer.MAX_VALUE) + ".String()");
        currStringLiteralPart = new StringBuilder();
      }
    }

    if (currStringLiteralPart.length() > 0) {
      keyStrParts.add("\"" + currStringLiteralPart.toString() + "\"");
    }

    return Joiner.on(" + ").join(keyStrParts);
  }


  @Override protected GoExpr visitGlobalNode(GlobalNode node) {
    throw new UnsupportedOperationException();
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for operators (concrete classes).


  @Override protected GoExpr visitNegativeOpNode(NegativeOpNode node) {

    GoExpr operand = visit(node.getChild(0));

    String integerComputation = genNewIntegerData(genUnaryOp("-", genIntegerValue(operand)));
    String floatComputation = genNewFloatData(genUnaryOp("-", genFloatValue(operand)));

    if (isAlwaysInteger(operand)) {
      return convertIntegerResult(integerComputation);
    } else if (isAlwaysFloat(operand)) {
      return convertFloatResult(floatComputation);
    } else {
      return convertNumberResult(genFunctionCall(UTILS_LIB + ".Negative", operand.getText()));
    }
  }


  @Override protected GoExpr visitNotOpNode(NotOpNode node) {

    GoExpr operand = visit(node.getChild(0));
    return convertBooleanResult(genNewBooleanData(genUnaryOp("!", genCoerceBoolean(operand))));
  }


  @Override protected GoExpr visitTimesOpNode(TimesOpNode node) {
    return visitNumberToNumberBinaryOpHelper(node, "*", "Times");
  }


  @Override protected GoExpr visitDivideByOpNode(DivideByOpNode node) {

    GoExpr operand0 = visit(node.getChild(0));
    GoExpr operand1 = visit(node.getChild(1));

    // Note: Soy always performs floating-point division, even on two integers (like GoScript).
    return convertFloatResult(genNewFloatData(genBinaryOp(
        "/", genFloatValue(operand0), genFloatValue(operand1))));
  }


  @Override protected GoExpr visitModOpNode(ModOpNode node) {

    GoExpr operand0 = visit(node.getChild(0));
    GoExpr operand1 = visit(node.getChild(1));

    return convertNumberResult(genNewIntegerData(genBinaryOp(
        "%", genIntegerValue(operand0), genIntegerValue(operand1))));
  }


  @Override protected GoExpr visitPlusOpNode(PlusOpNode node) {

    GoExpr operand0 = visit(node.getChild(0));
    GoExpr operand1 = visit(node.getChild(1));

    String stringComputation = genNewStringData(genBinaryOp(
        "+", genCoerceString(operand0), genCoerceString(operand1)));
    String integerComputation = genNewIntegerData(genBinaryOp(
        "+", genIntegerValue(operand0), genIntegerValue(operand1)));
    String floatComputation = genNewFloatData(genBinaryOp(
        "+", genNumberValue(operand0), genNumberValue(operand1)));

    if (isAlwaysTwoIntegers(operand0, operand1)) {
      return convertIntegerResult(integerComputation);
    } else if (isAlwaysAtLeastOneString(operand0, operand1)) {
      return convertStringResult(stringComputation);
    } else if (isAlwaysTwoFloatsOrOneFloatOneInteger(operand0, operand1)) {
      return convertFloatResult(floatComputation);
    } else {
      return convertNumberResult(genFunctionCall(
          UTILS_LIB + ".Plus", operand0.getText(), operand1.getText()));
    }
  }


  @Override protected GoExpr visitMinusOpNode(MinusOpNode node) {
    return visitNumberToNumberBinaryOpHelper(node, "-", "Minus");
  }


  @Override protected GoExpr visitLessThanOpNode(LessThanOpNode node) {
    return visitNumberToBooleanBinaryOpHelper(node, "<", "LessThan");
  }


  @Override protected GoExpr visitGreaterThanOpNode(GreaterThanOpNode node) {
    return visitNumberToBooleanBinaryOpHelper(node, ">", "GreaterThan");
  }


  @Override protected GoExpr visitLessThanOrEqualOpNode(LessThanOrEqualOpNode node) {
    return visitNumberToBooleanBinaryOpHelper(node, "<=", "LessThanOrEqual");
  }


  @Override protected GoExpr visitGreaterThanOrEqualOpNode(GreaterThanOrEqualOpNode node) {
    return visitNumberToBooleanBinaryOpHelper(node, ">=", "GreaterThanOrEqual");
  }


  @Override protected GoExpr visitEqualOpNode(EqualOpNode node) {

    GoExpr operand0 = visit(node.getChild(0));
    GoExpr operand1 = visit(node.getChild(1));

    return convertBooleanResult(genNewBooleanData(
        genMaybeProtect(operand0, Integer.MAX_VALUE) + ".Equals(" + operand1.getText() + ")"));
  }


  @Override protected GoExpr visitNotEqualOpNode(NotEqualOpNode node) {

    GoExpr operand0 = visit(node.getChild(0));
    GoExpr operand1 = visit(node.getChild(1));

    return convertBooleanResult(genNewBooleanData(
        "! " + genMaybeProtect(operand0, Integer.MAX_VALUE) + ".Equals(" +
        operand1.getText() + ")"));
  }


  @Override protected GoExpr visitAndOpNode(AndOpNode node) {
    return visitBooleanToBooleanBinaryOpHelper(node, "&&");
  }


  @Override protected GoExpr visitOrOpNode(OrOpNode node) {
    return visitBooleanToBooleanBinaryOpHelper(node, "||");
  }


  @Override protected GoExpr visitConditionalOpNode(ConditionalOpNode node) {

    GoExpr operand0 = visit(node.getChild(0));
    GoExpr operand1 = visit(node.getChild(1));
    GoExpr operand2 = visit(node.getChild(2));
    
    Class<?> type1 = operand1.getType();
    Class<?> type2 = operand2.getType();
    // Set result type to nearest common ancestor of type1 and type2.
    Class<?> resultType = null;
    for (Class<?> type = type1; type != null; type = type.getSuperclass()) {
      if (type.isAssignableFrom(type2)) {
        resultType = type;
        break;
      }
    }
    if (resultType == null) {
      throw new AssertionError();
    }

    return new GoExpr(
        UTILS_LIB + ".Conditional(" + genCoerceBoolean(operand0) + ", " +
        genMaybeCast(operand1, SoyData.class) + ", " +
        genMaybeCast(operand2, SoyData.class) + ")",
        resultType, Operator.CONDITIONAL.getPrecedence());
  }


  // -----------------------------------------------------------------------------------------------
  // Implementation for functions.


  @Override protected GoExpr visitFunctionNode(FunctionNode node) {

    String fnName = node.getFunctionName();
    int numArgs = node.numChildren();

    // Handle nonplugin functions.
    NonpluginFunction nonpluginFn = NonpluginFunction.forFunctionName(fnName);
    if (nonpluginFn != null) {
      if (numArgs != nonpluginFn.getNumArgs()) {
        throw new SoySyntaxException(
            "Function '" + fnName + "' called with the wrong number of arguments" +
            " (function call \"" + node.toSourceString() + "\").");
      }
      switch (nonpluginFn) {
        case IS_FIRST:
          return visitIsFirstFunction(node);
        case IS_LAST:
          return visitIsLastFunction(node);
        case INDEX:
          return visitIndexFunction(node);
        case HAS_DATA:
          return visitHasDataFunction();
        default:
          throw new AssertionError();
      }
    }

    // Handle plugin functions.
    SoyGoSrcFunction fn = soyGoSrcFunctionsMap.get(fnName);
    if (fn != null) {
      if (! fn.getValidArgsSizes().contains(numArgs)) {
        throw new SoySyntaxException(
            "Function '" + fnName + "' called with the wrong number of arguments" +
            " (function call \"" + node.toSourceString() + "\").");
      }
      List<GoExpr> args = visitChildren(node);
      try {
        return fn.computeForGoSrc(args);
      } catch (Exception e) {
        throw new SoySyntaxException(
            "Error in function call \"" + node.toSourceString() + "\": " + e.getMessage(), e);
      }
    }

    throw new SoySyntaxException(
        "Failed to find SoyGoSrcFunction with name '" + fnName + "'" +
        " (function call \"" + node.toSourceString() + "\").");
  }


  private GoExpr visitIsFirstFunction(FunctionNode node) {
    String varName = ((DataRefKeyNode) ((DataRefNode) node.getChild(0)).getChild(0)).getKey();
    return getLocalVarTranslation(varName + "__isFirst");
  }


  private GoExpr visitIsLastFunction(FunctionNode node) {
    String varName = ((DataRefKeyNode) ((DataRefNode) node.getChild(0)).getChild(0)).getKey();
    return getLocalVarTranslation(varName + "__isLast");
  }


  private GoExpr visitIndexFunction(FunctionNode node) {
    String varName = ((DataRefKeyNode) ((DataRefNode) node.getChild(0)).getChild(0)).getKey();
    return getLocalVarTranslation(varName + "__index");
  }


  private GoExpr visitHasDataFunction() {
    return convertBooleanResult(genNewBooleanData("data != nil"));
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  // -----------------------------------------------------------------------------------------------
  // Private helpers.


  /**
   * Private helper to push a BooleanData expression onto the result stack.
   * @param exprText The expression text that computes a BooleanData.
   */
  private GoExpr convertBooleanResult(String exprText) {
    return new GoExpr(exprText, BooleanData.class, Integer.MAX_VALUE);
  }


  /**
   * Private helper to push an IntegerData expression onto the result stack.
   * @param exprText The expression text that computes an IntegerData.
   */
  private GoExpr convertIntegerResult(String exprText) {
    return new GoExpr(exprText, IntegerData.class, Integer.MAX_VALUE);
  }


  /**
   * Private helper to push a FloatData expression onto the result stack.
   * @param exprText The expression text that computes a DoubleData.
   */
  private GoExpr convertFloatResult(String exprText) {
    return new GoExpr(exprText, FloatData.class, Integer.MAX_VALUE);
  }


  private GoExpr convertNumberResult(String exprText) {
    return new GoExpr(exprText, NumberData.class, Integer.MAX_VALUE);
  }


  /**
   * Private helper to push a StringData expression onto the result stack.
   * @param exprText The expression text that computes a StringData.
   */
  private GoExpr convertStringResult(String exprText) {
    return new GoExpr(exprText, StringData.class, Integer.MAX_VALUE);
  }


  private GoExpr convertListResult(String exprText) {
    return new GoExpr(exprText, SoyListData.class, Integer.MAX_VALUE);
  }


  private GoExpr convertMapResult(String exprText) {
    return new GoExpr(exprText, SoyMapData.class, Integer.MAX_VALUE);
  }


  private GoExpr convertUnknownResult(String exprText) {
    return new GoExpr(exprText, SoyData.class, Integer.MAX_VALUE);
  }


  private GoExpr visitBooleanToBooleanBinaryOpHelper(OperatorNode node, String goOpToken) {

    GoExpr operand0 = visit(node.getChild(0));
    GoExpr operand1 = visit(node.getChild(1));

    return convertBooleanResult(genNewBooleanData(genBinaryOp(
        goOpToken, genCoerceBoolean(operand0), genCoerceBoolean(operand1))));
  }


  private GoExpr visitNumberToNumberBinaryOpHelper(
      OperatorNode node, String goOpToken, String utilsLibFnName) {

    GoExpr operand0 = visit(node.getChild(0));
    GoExpr operand1 = visit(node.getChild(1));

    String integerComputation = genNewIntegerData(genBinaryOp(
        goOpToken, genIntegerValue(operand0), genIntegerValue(operand1)));
    String floatComputation = genNewFloatData(genBinaryOp(
        goOpToken, genNumberValue(operand0), genNumberValue(operand1)));

    if (isAlwaysTwoIntegers(operand0, operand1)) {
      return convertIntegerResult(integerComputation);
    } else if (isAlwaysAtLeastOneFloat(operand0, operand1)) {
    	return convertFloatResult(floatComputation);
    } else {
    	return convertNumberResult(genFunctionCall(
          UTILS_LIB + "." + utilsLibFnName,
          genMaybeCast(operand0, NumberData.class), genMaybeCast(operand1, NumberData.class)));
    }
  }


  private GoExpr visitNumberToBooleanBinaryOpHelper(
      OperatorNode node, String goOpToken, String utilsLibFnName) {

    GoExpr operand0 = visit(node.getChild(0));
    GoExpr operand1 = visit(node.getChild(1));

    String integerComputation = genNewBooleanData(genBinaryOp(
        goOpToken, genIntegerValue(operand0), genIntegerValue(operand1)));
    String floatComputation = genNewBooleanData(genBinaryOp(
        goOpToken, genNumberValue(operand0), genNumberValue(operand1)));

    if (isAlwaysTwoIntegers(operand0, operand1)) {
      return convertBooleanResult(integerComputation);
    } else if (isAlwaysAtLeastOneFloat(operand0, operand1)) {
      return convertBooleanResult(floatComputation);
    } else {
      return convertBooleanResult(genFunctionCall(
          UTILS_LIB + "." + utilsLibFnName,
          genMaybeCast(operand0, NumberData.class), genMaybeCast(operand1, NumberData.class)));
    }
  }


  /**
   * Gets the translated expression for an in-scope local variable (or special "variable" derived
   * from a foreach-loop var), or null if not found.
   * @param ident The Soy local variable to translate.
   * @return The translated expression for the given variable, or null if not found.
   */
  private GoExpr getLocalVarTranslation(String ident) {

    for (Map<String, GoExpr> localVarTranslationsFrame : localVarTranslations) {
      GoExpr translation = localVarTranslationsFrame.get(ident);
      if (translation != null) {
        return translation;
      }
    }

    return null;
  }

}
