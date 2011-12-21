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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.data.restricted.StringData;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.exprtree.Operator;
import com.google.template.soy.internal.base.CharEscapers;
import com.google.template.soy.gosrc.SoyGoSrcOptions;
import com.google.template.soy.gosrc.internal.TranslateToGoExprVisitor.TranslateToGoExprVisitorFactory;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genCoerceBoolean;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genMaybeProtect;
import com.google.template.soy.gosrc.restricted.GoExpr;
import com.google.template.soy.gosrc.restricted.GoExprUtils;
import com.google.template.soy.gosrc.restricted.SoyGoSrcPrintDirective;
import com.google.template.soy.shared.SoyCssRenamingMap;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CssNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.PrintDirectiveNode;
import com.google.template.soy.soytree.PrintNode;
import com.google.template.soy.soytree.RawTextNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateNode;

import java.util.Deque;
import java.util.List;
import java.util.Map;


/**
 * Visitor for generating Go expressions for parse tree nodes.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * <p> Precondition: MsgNode should not exist in the tree.
 *
 * @author Kai Huang
 */
public class GenGoExprsVisitor extends AbstractSoyNodeVisitor<List<GoExpr>> {


  /**
   * Injectable factory for creating an instance of this class.
   */
  public static interface GenGoExprsVisitorFactory {

    /**
     * @param localVarTranslations The current stack of replacement Go expressions for the local
     *     variables (and foreach-loop special functions) current in scope.
     */
    public GenGoExprsVisitor create(Deque<Map<String, GoExpr>> localVarTranslations);
  }


  /** Map of all SoyGoSrcPrintDirectives (name to directive). */
  Map<String, SoyGoSrcPrintDirective> soyGoSrcDirectivesMap;

  /** The options for generating Java source code. */
  private final SoyGoSrcOptions goSrcOptions;

  /** Instance of GenCallCodeUtils to use. */
  private final GenCallCodeUtils genCallCodeUtils;

  /** The IsComputableAsGoExprsVisitor used by this instance (when needed). */
  private final IsComputableAsGoExprsVisitor isComputableAsGoExprsVisitor;

  /** Factory for creating an instance of GenGoExprsVisitor. */
  private final GenGoExprsVisitorFactory genGoExprsVisitorFactory;

  /** Factory for creating an instance of TranslateToGoExprVisitor. */
  private final TranslateToGoExprVisitorFactory translateToGoExprVisitorFactory;

  /** The current stack of replacement Go expressions for the local variables (and foreach-loop
   *  special functions) current in scope. */
  private final Deque<Map<String, GoExpr>> localVarTranslations;

  /** List to collect the results. */
  private List<GoExpr> goExprs;


  /**
   * @param soyGoSrcDirectivesMap Map of all SoyGoSrcPrintDirectives (name to directive).
   * @param goSrcOptions The options for generating Go source code.
   * @param genCallCodeUtils Instance of GenCallCodeUtils to use.
   * @param isComputableAsGoExprsVisitor The IsComputableAsGoExprsVisitor used by this instance
   *     (when needed).
   * @param genGoExprsVisitorFactory Factory for creating an instance of GenGoExprsVisitor.
   * @param translateToGoExprVisitorFactory Factory for creating an instance of
   *     TranslateToGoExprVisitor.
   * @param localVarTranslations The current stack of replacement Go expressions for the local
   *     variables (and foreach-loop special functions) current in scope.
   */
  @AssistedInject
  GenGoExprsVisitor(
      Map<String, SoyGoSrcPrintDirective> soyGoSrcDirectivesMap,
      SoyGoSrcOptions goSrcOptions,
      GenCallCodeUtils genCallCodeUtils,
      IsComputableAsGoExprsVisitor isComputableAsGoExprsVisitor,
      GenGoExprsVisitorFactory genGoExprsVisitorFactory,
      TranslateToGoExprVisitorFactory translateToGoExprVisitorFactory,
      @Assisted Deque<Map<String, GoExpr>> localVarTranslations) {
    this.soyGoSrcDirectivesMap = soyGoSrcDirectivesMap;
    this.goSrcOptions = goSrcOptions;
    this.genCallCodeUtils = genCallCodeUtils;
    this.isComputableAsGoExprsVisitor = isComputableAsGoExprsVisitor;
    this.genGoExprsVisitorFactory = genGoExprsVisitorFactory;
    this.translateToGoExprVisitorFactory = translateToGoExprVisitorFactory;
    this.localVarTranslations = localVarTranslations;
  }


  @Override public List<GoExpr> exec(SoyNode node) {
    Preconditions.checkArgument(isComputableAsGoExprsVisitor.exec(node));
    goExprs = Lists.newArrayList();
    visit(node);
    return goExprs;
  }



  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  @Override protected void visitTemplateNode(TemplateNode node) {
    visitChildren(node);
  }


  /**
   * Example:
   * <pre>
   *   I'm feeling lucky!
   * </pre>
   * generates
   * <pre>
   *   'I\'m feeling lucky!'
   * </pre>
   */
  @Override protected void visitRawTextNode(RawTextNode node) {

    goExprs.add(new GoExpr(
        '"' + CharEscapers.goStringEscaper().escape(node.getRawText()) + '"',
        String.class, Integer.MAX_VALUE));
  }


  /**
   * Example:
   * <pre>
   *   {$boo.foo}
   *   {$goo.moo + 5}
   * </pre>
   * might generate
   * <pre>
   *   opt_data.boo.foo
   *   gooData4.moo + 5
   * </pre>
   */
  @Override protected void visitPrintNode(PrintNode node) {

    TranslateToGoExprVisitor ttjev =
        translateToGoExprVisitorFactory.create(localVarTranslations);

    GoExpr goExpr = ttjev.exec(node.getExprUnion().getExpr());

    // Process directives.
    for (PrintDirectiveNode directiveNode : node.getChildren()) {

      // Get directive.
      SoyGoSrcPrintDirective directive = soyGoSrcDirectivesMap.get(directiveNode.getName());
      if (directive == null) {
        throw new SoySyntaxException(
            "Failed to find SoyGoSrcPrintDirective with name '" + directiveNode.getName() + "'" +
            " (tag " + node.toSourceString() +")");
      }

      // Get directive args.
      List<ExprRootNode<?>> args = directiveNode.getArgs();
      if (! directive.getValidArgsSizes().contains(args.size())) {
        throw new SoySyntaxException(
            "Print directive '" + directiveNode.getName() + "' used with the wrong number of" +
            " arguments (tag " + node.toSourceString() + ").");
      }

      // Translate directive args.
      List<GoExpr> argsGoExprs = Lists.newArrayListWithCapacity(args.size());
      for (ExprRootNode<?> arg : args) {
        argsGoExprs.add(ttjev.exec(arg));
      }

      // Apply directive.
      goExpr = directive.applyForGoSrc(goExpr, argsGoExprs);
    }

    goExprs.add(goExpr);
  }


  /**
   * Example:
   * <pre>
   *   {if $boo}
   *     AAA
   *   {elseif $foo}
   *     BBB
   *   {else}
   *     CCC
   *   {/if}
   * </pre>
   * might generate
   * <pre>
   *   (opt_data.boo) ? AAA : (opt_data.foo) ? BBB : CCC
   * </pre>
   */
  @Override protected void visitIfNode(IfNode node) {

    // Create another instance of this visitor class for generating Go expressions from children.
    GenGoExprsVisitor genGoExprsVisitor =
        genGoExprsVisitorFactory.create(localVarTranslations);

    StringBuilder goExprTextSb = new StringBuilder();

    boolean hasElse = false;
    for (SoyNode child : node.getChildren()) {

      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;

        GoExpr condGoExpr =
            translateToGoExprVisitorFactory.create(localVarTranslations)
            	.exec(icn.getExprUnion().getExpr());
        goExprTextSb.append("(").append(genCoerceBoolean(condGoExpr)).append(") ? ");

        List<GoExpr> condBlockGoExprs = genGoExprsVisitor.exec(icn);
        goExprTextSb.append(
            genMaybeProtect(GoExprUtils.concatGoExprs(condBlockGoExprs),
                            Operator.CONDITIONAL.getPrecedence() + 1));

        goExprTextSb.append(" : ");

      } else if (child instanceof IfElseNode) {
        hasElse = true;
        IfElseNode ien = (IfElseNode) child;

        List<GoExpr> elseBlockGoExprs = genGoExprsVisitor.exec(ien);
        goExprTextSb.append(
            genMaybeProtect(GoExprUtils.concatGoExprs(elseBlockGoExprs),
                            Operator.CONDITIONAL.getPrecedence() + 1));

      } else {
        throw new AssertionError();
      }
    }

    if (!hasElse) {
      goExprTextSb.append("\"\"");
    }

    goExprs.add(new GoExpr(
        goExprTextSb.toString(), String.class, Operator.CONDITIONAL.getPrecedence()));
  }


  @Override protected void visitIfCondNode(IfCondNode node) {
    visitChildren(node);
  }


  @Override protected void visitIfElseNode(IfElseNode node) {
    visitChildren(node);
  }


  /**
   * Example:
   * <pre>
   *   {call name="some.func" data="all" /}
   *   {call name="some.func" data="$boo.foo" /}
   *   {call name="some.func"}
   *     {param key="goo" value="$moo" /}
   *   {/call}
   *   {call name="some.func" data="$boo"}
   *     {param key="goo"}Blah{/param}
   *   {/call}
   * </pre>
   * might generate
   * <pre>
   *   some.func(opt_data)
   *   some.func(opt_data.boo.foo)
   *   some.func({goo: opt_data.moo})
   *   some.func(soy.$$augmentData(opt_data.boo, {goo: 'Blah'}))
   * </pre>
   */
  @Override protected void visitCallNode(CallNode node) {
    goExprs.add(genCallCodeUtils.genCallExpr(node, localVarTranslations));
  }


  @Override protected void visitCallParamContentNode(CallParamContentNode node) {
    visitChildren(node);
  }

  /**
   * CSS can be rewritten in two ways : at compile time or at run time.
   * If the css renaming hints knows how to rename selector text, it returns a non-null string.
   * If the CSS hints rom the javasrc options renames "Alex" to "Axel" then the Soy
   * <xmp>
   *   <div class="{css Alex}">
   * </xmp>
   * will compile to the java
   * <xmp>
   *   output.append("<div class=\"Axel\">
   * </xmp>.
   * But if the renaming hints returns null for "Chris", then we have to use a runtime renamer.
   * Then, the Soy
   * <xmp>
   *   <div class="Chris">
   * </xmp>
   * is rewritten to Java with a method call to dynamically rewrite the CSS:
   * <xmp>
   *   output.append("<div class=\"").append(this.$$renameCss("Chris")).append("\">")
   * </xmp>
   */
  @Override protected void visitCssNode(CssNode node) {
    ExprRootNode<?> componentNameExpr = node.getComponentNameExpr();
    if (componentNameExpr != null) {
      // Append the result of componentNameExpr and a dash
      TranslateToGoExprVisitor ttjev =
        translateToGoExprVisitorFactory.create(localVarTranslations);

      goExprs.add(ttjev.exec(componentNameExpr));
      goExprs.add(new GoExpr("\"-\"", StringData.class, Integer.MAX_VALUE));
    }


    String selectorText = node.getSelectorText();

    // If we can rename at compile time, do so.
    SoyCssRenamingMap cssRenamingHints = goSrcOptions.getCssRenamingHints();
    String renamedSelectorText = cssRenamingHints.get(selectorText);
    if (renamedSelectorText != null && renamedSelectorText.length() != 0) {
      String goRenamedSelectorText = (
          '"' + CharEscapers.goStringEscaper().escape(renamedSelectorText) + '"');
      goExprs.add(new GoExpr(goRenamedSelectorText, StringData.class, Integer.MAX_VALUE));

    } else {
      // TODO AALOK fix visitCssNode to generate Go expressions, not Java Expressions
      // We can't rename at compile time, so do it dynamically.
      String goSelectorText = '"' + CharEscapers.goStringEscaper().escape(selectorText) + '"';
      goExprs.add(new GoExpr(
          "this.$$renameCss(" + goSelectorText + ")", StringData.class, Integer.MAX_VALUE));
    }
  }


}
