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

package com.google.template.soy.gosrc.internal;

import com.google.inject.Inject;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.gosrc.SoyGoSrcOptions;
import com.google.template.soy.gosrc.internal.GenGoExprsVisitor.GenGoExprsVisitorFactory;
import com.google.template.soy.gosrc.internal.TranslateToGoExprVisitor.TranslateToGoExprVisitorFactory;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.UTILS_LIB;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genFunctionCall;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genMaybeCast;
import com.google.template.soy.gosrc.restricted.GoExpr;
import com.google.template.soy.gosrc.restricted.GoExprUtils;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.CallParamValueNode;

import java.util.Deque;
import java.util.List;
import java.util.Map;


/**
 * Utilities for generating Go code for calls.
 *
 */
class GenCallCodeUtils {


  /** The options for generating Go source code. */
  private final SoyGoSrcOptions goSrcOptions;

  /** The IsComputableAsGoExprsVisitor used by this instance. */
  private final IsComputableAsGoExprsVisitor isComputableAsGoExprsVisitor;

  /** Factory for creating an instance of GenGoExprsVisitor. */
  private final GenGoExprsVisitorFactory genGoExprsVisitorFactory;

  /** Factory for creating an instance of TranslateToGoExprVisitor. */
  private final TranslateToGoExprVisitorFactory translateToGoExprVisitorFactory;


  /**
   * @param goSrcOptions The options for generating Go source code.
   * @param isComputableAsGoExprsVisitor The IsComputableAsGoExprsVisitor to be used.
   * @param genGoExprsVisitorFactory Factory for creating an instance of GenGoExprsVisitor.
   * @param translateToGoExprVisitorFactory Factory for creating an instance of
   *     TranslateToGoExprVisitor.
   */
  @Inject
  GenCallCodeUtils(SoyGoSrcOptions goSrcOptions,
                   IsComputableAsGoExprsVisitor isComputableAsGoExprsVisitor,
                   GenGoExprsVisitorFactory genGoExprsVisitorFactory,
                   TranslateToGoExprVisitorFactory translateToGoExprVisitorFactory) {
    this.goSrcOptions = goSrcOptions;
    this.isComputableAsGoExprsVisitor = isComputableAsGoExprsVisitor;
    this.genGoExprsVisitorFactory = genGoExprsVisitorFactory;
    this.translateToGoExprVisitorFactory = translateToGoExprVisitorFactory;
  }


  /**
   * Generates the Go expression for a given call (the version that doesn't pass a StringBuilder).
   *
   * <p> Important: If there are CallParamContentNode children whose contents are not computable as
   * Go expressions, then this function assumes that, elsewhere, code has been generated to define
   * their respective 'param<n>' temporary variables.
   *
   * <p> Here are five example calls:
   * <pre>
   *   {call some.func data="all" /}
   *   {call some.func data="$boo.foo" /}
   *   {call some.func}
   *     {param goo = $moo /}
   *   {/call}
   *   {call some.func data="$boo"}
   *     {param goo}Blah{/param}
   *   {/call}
   *   {call some.func}
   *     {param goo}
   *       {for $i in range(3)}{$i}{/for}
   *     {/param}
   *   {/call}
   * </pre>
   * Their respective generated calls might be the following:
   * <pre>
   *   some.func(opt_data)
   *   some.func(opt_data.boo.foo)
   *   some.func({goo: opt_data.moo})
   *   some.func(soy.$$augmentData(opt_data.boo, {goo: 'Blah'}))
   *   some.func({goo: param65})
   * </pre>
   * Note that in the last case, the param content is not computable as Go expressions, so we
   * assume that code has been generated to define the temporary variable 'param<n>'.
   *
   * @param callNode The call to generate code for.
   * @param localVarTranslations The current stack of replacement Go expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   * @return The Go expression for the call (the version that doesn't pass a StringBuilder).
   */
  public GoExpr genCallExpr(
      CallNode callNode, Deque<Map<String, GoExpr>> localVarTranslations) {

    GoExpr objToPass = genObjToPass(callNode, localVarTranslations);
    return new GoExpr(
        ((CallBasicNode)callNode).getCalleeName().replace('.', '$') + "(" + objToPass.getText() + ")",
        String.class, Integer.MAX_VALUE);
  }


  /**
   * Generates the Go expression for the object to pass in a given call.
   *
   * <p> Important: If there are CallParamContentNode children whose contents are not computable as
   * Go expressions, then this function assumes that, elsewhere, code has been generated to define
   * their respective 'param<n>' temporary variables.
   *
   * <p> Here are five example calls:
   * <pre>
   *   {call some.func data="all" /}
   *   {call some.func data="$boo.foo" /}
   *   {call some.func}
   *     {param goo = $moo /}
   *   {/call}
   *   {call some.func data="$boo"}
   *     {param goo}Blah{/param}
   *   {/call}
   *   {call some.func}
   *     {param goo}
   *       {for $i in range(3)}{$i}{/for}
   *     {/param}
   *   {/call}
   * </pre>
   * Their respective objects to pass might be the following:
   * <pre>
   *   opt_data
   *   opt_data.boo.foo
   *   {goo: opt_data.moo}
   *   soy.$$augmentData(opt_data.boo, {goo: 'Blah'})
   *   {goo: param65}
   * </pre>
   * Note that in the last case, the param content is not computable as Go expressions, so we
   * assume that code has been generated to define the temporary variable 'param<n>'.
   *
   * @param callNode The call to generate code for.
   * @param localVarTranslations The current stack of replacement Go expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   * @return The Go expression for the object to pass in the call.
   */
  public GoExpr genObjToPass(
      CallNode callNode, Deque<Map<String, GoExpr>> localVarTranslations) {

    TranslateToGoExprVisitor ttjev =
        translateToGoExprVisitorFactory.create(localVarTranslations);

    // ------ Generate the expression for the original data to pass ------
    GoExpr dataToPass;
    if (callNode.isPassingAllData()) {
      dataToPass = new GoExpr("data", SoyMapData.class, Integer.MAX_VALUE);
    } else if (callNode.isPassingData()) {
      dataToPass = new GoExpr(
          genMaybeCast(ttjev.exec(callNode.getExpr()), SoyMapData.class),
          SoyMapData.class, Integer.MAX_VALUE);
    } else {
      dataToPass = new GoExpr("nil", SoyMapData.class, Integer.MAX_VALUE);
    }

    // ------ Case 1: No additional params ------
    if (callNode.numChildren() == 0) {
      return dataToPass;
    }

    // ------ Build an object literal containing the additional params ------
    StringBuilder paramsObjSb = new StringBuilder();
    paramsObjSb.append("soyutil.NewSoyMapDataFromArgs(");

    boolean isFirst = true;
    for (CallParamNode child : callNode.getChildren()) {

      if (isFirst) {
        isFirst = false;
      } else {
        paramsObjSb.append(", ");
      }

      String key = child.getKey();
      paramsObjSb.append("\"").append(key).append("\", ");

      if (child instanceof CallParamValueNode) {
        CallParamValueNode cpvn = (CallParamValueNode) child;
        GoExpr valueGoExpr = ttjev.exec(cpvn.getValueExprUnion().getExpr());
        paramsObjSb.append(valueGoExpr.getText());

      } else {
        CallParamContentNode cpcn = (CallParamContentNode) child;

        if (isComputableAsGoExprsVisitor.exec(cpcn)) {
          List<GoExpr> cpcnGoExprs =
              genGoExprsVisitorFactory.create(localVarTranslations).exec(cpcn);
          GoExpr valueGoExpr = GoExprUtils.concatGoExprs(cpcnGoExprs);
          paramsObjSb.append(valueGoExpr.getText());

        } else {
          // This is a param with content that cannot be represented as Go expressions, so we
          // assume that code has been generated to define the temporary variable 'param<n>'.
          if (goSrcOptions.getCodeStyle() == SoyGoSrcOptions.CodeStyle.STRINGBUILDER) {
            paramsObjSb.append("param").append(cpcn.getId()).append(".String()");
          } else {
            paramsObjSb.append("param").append(cpcn.getId());
          }
        }
      }
    }

    paramsObjSb.append(")");

    // ------ Cases 2 and 3: Additional params with and without original data to pass ------
    if (callNode.isPassingData()) {
      return new GoExpr(
          genFunctionCall(
              UTILS_LIB + ".AugmentData", dataToPass.getText(), paramsObjSb.toString()),
          SoyMapData.class, Integer.MAX_VALUE);
    } else {
      return new GoExpr(paramsObjSb.toString(), SoyMapData.class, Integer.MAX_VALUE);
    }
  }

}
