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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.data.SoyData;
import com.google.template.soy.data.restricted.BooleanData;
import com.google.template.soy.data.restricted.IntegerData;
import com.google.template.soy.exprtree.ExprNode;
import com.google.template.soy.exprtree.ExprRootNode;
import com.google.template.soy.gosrc.SoyGoSrcOptions;
import com.google.template.soy.gosrc.SoyGoSrcOptions.CodeStyle;
import com.google.template.soy.gosrc.internal.GenGoExprsVisitor.GenGoExprsVisitorFactory;
import com.google.template.soy.gosrc.internal.TranslateToGoExprVisitor.TranslateToGoExprVisitorFactory;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genCoerceBoolean;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genIntegerValue;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.publicize;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.privatize;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.finalNamespace;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.goTemplateName;

import com.google.template.soy.gosrc.restricted.GoCodeUtils;
import com.google.template.soy.gosrc.restricted.GoExpr;
import com.google.template.soy.gosrc.restricted.GoExprUtils;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.soytree.AbstractSoyNodeVisitor;
import com.google.template.soy.soytree.CallBasicNode;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.CallParamContentNode;
import com.google.template.soy.soytree.CallParamNode;
import com.google.template.soy.soytree.ForNode;
import com.google.template.soy.soytree.ForeachNode;
import com.google.template.soy.soytree.ForeachNonemptyNode;
import com.google.template.soy.soytree.IfCondNode;
import com.google.template.soy.soytree.IfElseNode;
import com.google.template.soy.soytree.IfNode;
import com.google.template.soy.soytree.LetContentNode;
import com.google.template.soy.soytree.LetValueNode;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyFileSetNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.SoyNode.BlockNode;
import com.google.template.soy.soytree.SoyNode.ParentSoyNode;
import com.google.template.soy.soytree.SwitchCaseNode;
import com.google.template.soy.soytree.SwitchDefaultNode;
import com.google.template.soy.soytree.SwitchNode;
import com.google.template.soy.soytree.TemplateNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;


/**
 * Visitor for generating full Go code (i.e. statements) for parse tree nodes.
 *
 * <p> Precondition: MsgNode should not exist in the tree.
 *
 * <p> {@link #exec} should be called on a full parse tree. Go source code will be generated for
 * all the Soy files. The return value is a list of strings, each string being the content of one
 * generated Go file (corresponding to one Soy file).
 *
 */
class GenGoCodeVisitor extends AbstractSoyNodeVisitor<List<Pair<String, String>>> {


  /** Regex pattern for an integer. */
  private static final Pattern INTEGER = Pattern.compile("-?\\d+");


  /** The options for generating Go source code. */
  private final SoyGoSrcOptions goSrcOptions;

  /** Instance of GenCallCodeUtils to use. */
  private final GenCallCodeUtils genCallCodeUtils;

  /** The IsComputableAsGoExprsVisitor used by this instance. */
  private final IsComputableAsGoExprsVisitor isComputableAsGoExprsVisitor;

  /** The CanInitOutputVarVisitor used by this instance. */
  private final CanInitOutputVarVisitor canInitOutputVarVisitor;

  /** Factory for creating an instance of GenGoExprsVisitor. */
  private final GenGoExprsVisitorFactory genGoExprsVisitorFactory;

  /** Factory for creating an instance of TranslateToGoExprVisitor. */
  private final TranslateToGoExprVisitorFactory translateToGoExprVisitorFactory;

  /** The GenGoExprsVisitor used by this instance. */
  @VisibleForTesting protected GenGoExprsVisitor genGoExprsVisitor;

  /** The GoCodeBuilder to build the Go code. */
  @VisibleForTesting protected GoCodeBuilder goCodeBuilder;

  /** The current stack of replacement Go expressions for the local variables (and foreach-loop
   *  special functions) current in scope. */
  @VisibleForTesting protected Deque<Map<String, GoExpr>> localVarTranslations;


  /**
   * @param goSrcOptions The options for generating Go source code.
   * @param genCallCodeUtils Instance of GenCallCodeUtils to use.
   * @param isComputableAsGoExprsVisitor The IsComputableAsGoExprsVisitor to be used.
   * @param canInitOutputVarVisitor The CanInitOutputVarVisitor to be used.
   * @param genGoExprsVisitorFactory Factory for creating an instance of GenGoExprsVisitor.
   * @param translateToGoExprVisitorFactory Factory for creating an instance of
   *     TranslateToGoExprVisitor.
   */
  @Inject
  GenGoCodeVisitor(SoyGoSrcOptions goSrcOptions, GenCallCodeUtils genCallCodeUtils,
                     IsComputableAsGoExprsVisitor isComputableAsGoExprsVisitor,
                     CanInitOutputVarVisitor canInitOutputVarVisitor,
                     GenGoExprsVisitorFactory genGoExprsVisitorFactory,
                     TranslateToGoExprVisitorFactory translateToGoExprVisitorFactory) {
    this.goSrcOptions = goSrcOptions;
    this.genCallCodeUtils = genCallCodeUtils;
    this.isComputableAsGoExprsVisitor = isComputableAsGoExprsVisitor;
    this.canInitOutputVarVisitor = canInitOutputVarVisitor;
    this.genGoExprsVisitorFactory = genGoExprsVisitorFactory;
    this.translateToGoExprVisitorFactory = translateToGoExprVisitorFactory;
  }


  @Override public List<Pair<String, String>> exec(SoyNode node) {
    goCodeBuilder = new GoCodeBuilder(goSrcOptions.getCodeStyle());
    localVarTranslations = null;
    visit(node);
    return goCodeBuilder.getCode();
  }


  @VisibleForTesting
  @Override protected void visit(SoyNode node) {
    super.visit(node);
  }


  @Override protected void visitChildren(ParentSoyNode<?> node) {

    // If the block is empty or if the first child cannot initilize the output var, we must
    // initialize the output var.
    if (node.numChildren() == 0 || !canInitOutputVarVisitor.exec(node.getChild(0))) {
      goCodeBuilder.initOutputVarIfNecessary();
    }

    List<GoExpr> consecChildrenGoExprs = Lists.newArrayList();

    for (SoyNode child : node.getChildren()) {

      if (isComputableAsGoExprsVisitor.exec(child)) {
        consecChildrenGoExprs.addAll(genGoExprsVisitor.exec(child));

      } else {
        // We've reached a child that is not computable as Go expressions.

        // First add the GoExprs from preceding consecutive siblings that are computable as Go
        // expressions (if any).
        if (consecChildrenGoExprs.size() > 0) {
          goCodeBuilder.addToOutputVar(consecChildrenGoExprs);
          consecChildrenGoExprs.clear();
        }

        // Now append the code for this child.
        visit(child);
      }
    }

    // Add the GoExprs from the last few children (if any).
    if (consecChildrenGoExprs.size() > 0) {
      goCodeBuilder.addToOutputVar(consecChildrenGoExprs);
      consecChildrenGoExprs.clear();
    }
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  @Override protected void visitSoyFileSetNode(SoyFileSetNode node) {
    for (SoyFileNode soyFile : node.getChildren()) {
      try {
        visit(soyFile);
      } catch (SoySyntaxException sse) {
        throw sse.setFilePath(soyFile.getFilePath());
      }
    }
  }


  /**
   * Example:
   * <pre>
   * // -----------------------------------------------------------------------------
   * // The functions below were generated from my_soy_file.soy.
   *
   * ...
   * </pre>
   */
  @Override protected void visitSoyFileNode(SoyFileNode node) {
    String namespace = node.getNamespace();
    String finalNamespace = finalNamespace(namespace);
    goCodeBuilder.appendLine("package ", finalNamespace);
    if(node.numChildren() > 0) {
      goCodeBuilder.appendLine("import \"bytes\"");
      goCodeBuilder.appendLine("import \"closure/template/soyutil\"");
      for(String callString : getAllExternalCallLibraryNames(node)) {
        if(callString.equals(namespace))
          continue;
        String importString = callString.replace(".", "/");
        String packageString = GoSrcUtils.libraryNameToPackageName(callString);
        goCodeBuilder.appendLine("import " + packageString + " \"closure/template/" + importString + "\"");
      }
      goCodeBuilder.appendLine("");
      goCodeBuilder.appendLine("const DEFAULT_BUFFER_SIZE_IN_BYTES = 8192");
    }
    goCodeBuilder.appendLine("// ----------------------------------------------------------------------------- ");
    goCodeBuilder.appendLine("// The functions below were generated from ", node.getFileName(), ".");
    goCodeBuilder.appendLine("");
    
    

    // Add code for each template.
    for (TemplateNode template : node.getChildren()) {
      goCodeBuilder.appendLine().appendLine();
      try {
        visit(template);
      } catch (SoySyntaxException sse) {
        throw sse.setTemplateName(template.getTemplateName());
      }
    }
    goCodeBuilder.doneWithFile(node);
  }
  
  private Set<String> getAllExternalCallLibraryNames(ParentSoyNode<? extends SoyNode> node) {
    Set<String> elems = new HashSet<String>();
    if(node instanceof CallBasicNode) {
      CallBasicNode cn = (CallBasicNode)node;
      String calleeName = cn.getCalleeName();
      int lastDotIndex = calleeName.lastIndexOf('.');
      if(lastDotIndex > 0) {
        elems.add(calleeName.substring(0, lastDotIndex));
      }
    } else {
      for(SoyNode n : node.getChildren()) {
        if(n instanceof ParentSoyNode) {
          elems.addAll(getAllExternalCallLibraryNames((ParentSoyNode<?>)n));
        }
      }
    }
    return elems;
  }
  
  


  /**
   * Example:
   * <pre>
   * my.func = function(opt_data, opt_sb) {
   *   var output = opt_sb || new soy.StringBuilder();
   *   ...
   *   ...
   *   if (!opt_sb) return output.toString();
   * };
   * </pre>
   */
  @Override protected void visitTemplateNode(TemplateNode node) {

    boolean isCodeStyleStringbuilder = goSrcOptions.getCodeStyle() == CodeStyle.STRINGBUILDER;

    localVarTranslations = new ArrayDeque<Map<String, GoExpr>>();
    genGoExprsVisitor = genGoExprsVisitorFactory.create(localVarTranslations);
    
    String templateName = node.getTemplateName();
    String finalTemplateName = templateName.substring(templateName.lastIndexOf('.') + 1);
    String functionName = node.isPrivate() ? privatize(finalTemplateName) : publicize(finalTemplateName);
    boolean shouldReturn;

    if (isCodeStyleStringbuilder && node.isPrivate()) {
  	  goCodeBuilder.appendLine(
  		      "func ", functionName, "(data soyutil.SoyMapData, buf *bytes.Buffer) {");
  	  shouldReturn = false;
    } else {
      if(isCodeStyleStringbuilder) {
	    goCodeBuilder.appendLine(
	        "func ", functionName, "(data soyutil.SoyMapData, buf *bytes.Buffer) string {");
      } else {
        goCodeBuilder.appendLine(
            "func ", functionName, "(data soyutil.SoyMapData) string {");
      }
      shouldReturn = true;
    }
    goCodeBuilder.increaseIndent();
    goCodeBuilder.appendLine("if data == nil {");
    goCodeBuilder.appendLine("  data = soyutil.NewSoyMapData()");
    goCodeBuilder.appendLine("}");
    localVarTranslations.push(Maps.<String, GoExpr>newHashMap());

    if (!isCodeStyleStringbuilder && isComputableAsGoExprsVisitor.exec(node)) {
      // Case 1: The code style is 'concat' and the whole template body can be represented as Go
      // expressions. We specially handle this case because we don't want to generate the variable
      // 'output' at all. We simply concatenate the Go expressions and return the result.

      List<GoExpr> templateBodyGoExprs = genGoExprsVisitor.exec(node);
      GoExpr templateBodyGoExpr = GoExprUtils.concatGoExprs(templateBodyGoExprs);
      goCodeBuilder.appendLine("return ", templateBodyGoExpr.getText(), ";");

    } else {
      // Case 2: Normal case.

      goCodeBuilder.pushOutputVar("output");
      if (isCodeStyleStringbuilder) {
        goCodeBuilder.appendLine("var output *bytes.Buffer");
        goCodeBuilder.appendLine("if buf != nil {");
        goCodeBuilder.appendLine("  output = buf");
        goCodeBuilder.appendLine("} else {");
        goCodeBuilder.appendLine("  output = bytes.NewBuffer(make([]byte, 0, DEFAULT_BUFFER_SIZE_IN_BYTES))");
        goCodeBuilder.appendLine("}");
        goCodeBuilder.setOutputVarInited();
      }

      visitChildren(node);
      
      if (shouldReturn) {
	    if (isCodeStyleStringbuilder) {
	      goCodeBuilder.appendLine("if buf != nil {");
	      goCodeBuilder.appendLine("  return \"\"");
	      goCodeBuilder.appendLine("}");
	      goCodeBuilder.appendLine("return output.String()");
	    } else {
	      goCodeBuilder.appendLine("return output");
	    }
      }
      goCodeBuilder.popOutputVar();
    }

    localVarTranslations.pop();
    goCodeBuilder.decreaseIndent();
    goCodeBuilder.appendLine("}");
  }


  /**
   * Example:
   * <pre>
   *   {let $boo = ...}
   * </pre>
   * might generate
   * <pre>
   *   final com.google.template.soy.data.SoyData boo35 = ...;
   * </pre>
   */
  @Override protected void visitLetValueNode(LetValueNode node) {

    String generatedVarName = node.getUniqueVarName();

    // Generate code to define the local var.
    GoExpr valueGoExpr =
        translateToGoExprVisitorFactory.create(localVarTranslations).exec(node.getValueExpr());
    goCodeBuilder.appendLine(
        generatedVarName, " := ",
        valueGoExpr.getText(), ";");

    // Add a mapping for generating future references to this local var.
    localVarTranslations.peek().put(
        node.getVarName(), new GoExpr(generatedVarName, SoyData.class, Integer.MAX_VALUE));
  }


  /**
   * Example:
   * <pre>
   *   {let $boo}
   *     Hello {$name}
   *   {/let}
   * </pre>
   * might generate
   * <pre>
   *   final com.google.template.soy.data.SoyData boo35 = ...;
   * </pre>
   */
  @Override protected void visitLetContentNode(LetContentNode node) {

    String generatedVarName = node.getUniqueVarName();

    // Generate code to define the local var.
    localVarTranslations.push(Maps.<String, GoExpr>newHashMap());
    if (goSrcOptions.getCodeStyle() == CodeStyle.STRINGBUILDER) {
      goCodeBuilder.pushOutputVar(generatedVarName + "_sb");
    } else {
      goCodeBuilder.pushOutputVar(generatedVarName);
    }

    visitChildren(node);

    goCodeBuilder.popOutputVar();
    localVarTranslations.pop();

    if (goSrcOptions.getCodeStyle() == CodeStyle.STRINGBUILDER) {
      goCodeBuilder.appendLine(
          generatedVarName, " := ", generatedVarName, "_sb.toString();");
    }

    // Add a mapping for generating future references to this local var.
    localVarTranslations.peek().put(
        node.getVarName(), new GoExpr(generatedVarName, SoyData.class, Integer.MAX_VALUE));
  }


  /**
   * Example:
   * <pre>
   *   {if $boo.foo &gt; 0}
   *     ...
   *   {/if}
   * </pre>
   * might generate
   * <pre>
   *   if (opt_data.boo.foo &gt; 0) {
   *     ...
   *   }
   * </pre>
   */
  @Override protected void visitIfNode(IfNode node) {

    //if (isComputableAsGoExprsVisitor.exec(node)) {
    //  goCodeBuilder.addToOutputVar(genGoExprsVisitor.exec(node));
    //  return;
    //}

    // ------ Not computable as Go expressions, so generate full code. ------

    for (SoyNode child : node.getChildren()) {

      if (child instanceof IfCondNode) {
        IfCondNode icn = (IfCondNode) child;

        GoExpr condGoExpr =
            translateToGoExprVisitorFactory.create(localVarTranslations).exec(icn.getExprUnion().getExpr());
        if (icn.getCommandName().equals("if")) {
          goCodeBuilder.appendLine("if ", genCoerceBoolean(condGoExpr), " {");
        } else {  // "elseif" block
          goCodeBuilder.appendLine("} else if ", genCoerceBoolean(condGoExpr), " {");
        }

        goCodeBuilder.increaseIndent();
        visit(icn);
        goCodeBuilder.decreaseIndent();

      } else if (child instanceof IfElseNode) {
        IfElseNode ien = (IfElseNode) child;

        goCodeBuilder.appendLine("} else {");

        goCodeBuilder.increaseIndent();
        visit(ien);
        goCodeBuilder.decreaseIndent();

      } else {
        throw new AssertionError();
      }
    }

    goCodeBuilder.appendLine("}");
  }


  /**
   * Example:
   * <pre>
   *   {switch $boo}
   *     {case 0}
   *       ...
   *     {case 1, 2}
   *       ...
   *     {default}
   *       ...
   *   {/switch}
   * </pre>
   * might generate
   * <pre>
   *   switch (opt_data.boo) {
   *     case 0:
   *       ...
   *       break;
   *     case 1:
   *     case 2:
   *       ...
   *       break;
   *     default:
   *       ...
   *   }
   * </pre>
   */
  @Override protected void visitSwitchNode(SwitchNode node) {

    TranslateToGoExprVisitor ttjev =
        translateToGoExprVisitorFactory.create(localVarTranslations);

    GoExpr switchValueGoExpr = ttjev.exec(node.getExpr());
    String switchValueVarName = "switchValue" + node.getId();
    goCodeBuilder.appendLine(
        switchValueVarName, " := ",
        GoCodeUtils.genMaybeCast(switchValueGoExpr, SoyData.class));
    if(node.getChildren().isEmpty())
      return;
    goCodeBuilder.appendLine("switch {");
    for (SoyNode child : node.getChildren()) {

      if (child instanceof SwitchCaseNode) {
        SwitchCaseNode scn = (SwitchCaseNode) child;

        StringBuilder conditionExprText = new StringBuilder();
        boolean isFirstCaseValue = true;
        for (ExprNode caseExpr : scn.getExprList()) {
          GoExpr caseGoExpr = ttjev.exec(caseExpr);
          if (isFirstCaseValue) {
            isFirstCaseValue = false;
          } else {
            conditionExprText.append(", ");
          }
          conditionExprText.append(switchValueVarName).append(".Equals(")
              .append(caseGoExpr.getText()).append(")");
        }
        goCodeBuilder.appendLine("case ", conditionExprText.toString(), ":");

        goCodeBuilder.increaseIndent();
        visit(scn);
        goCodeBuilder.decreaseIndent();

      } else if (child instanceof SwitchDefaultNode) {
        SwitchDefaultNode sdn = (SwitchDefaultNode) child;

        goCodeBuilder.appendLine("default:");

        goCodeBuilder.increaseIndent();
        visit(sdn);
        goCodeBuilder.decreaseIndent();

      } else {
        throw new AssertionError();
      }
    }

    goCodeBuilder.appendLine("}");
  }


  /**
   * Example:
   * <pre>
   *   {foreach $foo in $boo.foos}
   *     ...
   *   {ifempty}
   *     ...
   *   {/foreach}
   * </pre>
   * might generate
   * <pre>
   *   var fooList2 = opt_data.boo.foos;
   *   var fooListLen2 = fooList2.length;
   *   if (fooListLen2 > 0) {
   *     ...
   *   } else {
   *     ...
   *   }
   * </pre>
   */
  @Override protected void visitForeachNode(ForeachNode node) {

    // Build some local variable names.
    String baseVarName = node.getVarName();
    String nodeId = Integer.toString(node.getId());
    String listVarName = baseVarName + "List" + nodeId;

    // Define list var and list-len var.
    GoExpr dataRefGoExpr =
        translateToGoExprVisitorFactory.create(localVarTranslations).exec(node.getExpr());
    goCodeBuilder.appendLine(
        listVarName,
        " := soyutil.ToSoyListData(", dataRefGoExpr.getText(), ")");

    // If has 'ifempty' node, add the wrapper 'if' statement.
    boolean hasIfemptyNode = node.numChildren() == 2;
    if (hasIfemptyNode) {
      goCodeBuilder.appendLine("if ", listVarName, ".HasElements() {");
      goCodeBuilder.increaseIndent();
    }

    // Generate code for nonempty case.
    visit(node.getChild(0));

    // If has 'ifempty' node, add the 'else' block of the wrapper 'if' statement.
    if (hasIfemptyNode) {
      goCodeBuilder.decreaseIndent();
      goCodeBuilder.appendLine("} else {");
      goCodeBuilder.increaseIndent();

      // Generate code for empty case.
      visit(node.getChild(1));

      goCodeBuilder.decreaseIndent();
      goCodeBuilder.appendLine("}");
    }
  }


  /**
   * Example:
   * <pre>
   *   {foreach $foo in $boo.foos}
   *     ...
   *   {/foreach}
   * </pre>
   * might generate
   * <pre>
   *   for (var fooIndex2 = 0; fooIndex2 &lt; fooListLen2; fooIndex2++) {
   *     var fooData2 = fooList2[fooIndex2];
   *     ...
   *   }
   * </pre>
   */
  @Override protected void visitForeachNonemptyNode(ForeachNonemptyNode node) {

    // Build some local variable names.
    String baseVarName = node.getVarName();
    String foreachNodeId = Integer.toString(node.getForeachNodeId());
    String listVarName = baseVarName + "List" + foreachNodeId;
    String indexVarName = baseVarName + "Index" + foreachNodeId;
    String elementVarName = baseVarName + "Elem" + foreachNodeId;
    String dataVarName = baseVarName + "Data" + foreachNodeId;

    // The start of the Go 'for' loop.
    goCodeBuilder.appendLine("for ", indexVarName, ", ", elementVarName, 
                             " := 0, ", listVarName, ".Front(); ",
                             elementVarName, " != nil ; ",
                             indexVarName, ", ", elementVarName, " = ", 
                             indexVarName, " + 1, ", elementVarName, ".Next() {");
    goCodeBuilder.increaseIndent();
    goCodeBuilder.appendLine(
        dataVarName, " := soyutil.ToSoyDataNoErr(",
        elementVarName, ".Value)");

    // Add a new localVarTranslations frame and populate it with the translations from this node.
    Map<String, GoExpr> newLocalVarTranslationsFrame = Maps.newHashMap();
    newLocalVarTranslationsFrame.put(
        baseVarName,
        new GoExpr(dataVarName,
                     SoyData.class, Integer.MAX_VALUE));
    newLocalVarTranslationsFrame.put(
        baseVarName + "__isFirst",
        new GoExpr("soyutil.NewBooleanData(" +
                     elementVarName + ".Prev() == nil)",
                     BooleanData.class, Integer.MAX_VALUE));
    newLocalVarTranslationsFrame.put(
        baseVarName + "__isLast",
        new GoExpr("soyutil.NewBooleanData(" +
                     elementVarName + ".Next() == nil)",
                     BooleanData.class, Integer.MAX_VALUE));
    newLocalVarTranslationsFrame.put(
        baseVarName + "__index",
        new GoExpr("soyutil.NewIntegerData(" +
                     indexVarName + ")",
                     IntegerData.class, Integer.MAX_VALUE));
    localVarTranslations.push(newLocalVarTranslationsFrame);

    // Generate the code for the loop body.
    visitChildren(node);

    // Remove the localVarTranslations frame that we added above.
    localVarTranslations.pop();

    // The end of the Go 'for' loop.
    goCodeBuilder.decreaseIndent();
    goCodeBuilder.appendLine("}");
  }


  /**
   * Example:
   * <pre>
   *   {for $i in range(1, $boo)}
   *     ...
   *   {/for}
   * </pre>
   * might generate
   * <pre>
   *   var iLimit4 = opt_data.boo;
   *   for (var i4 = 1; i4 &lt; iLimit4; i4++) {
   *     ...
   *   }
   * </pre>
   */
  @Override protected void visitForNode(ForNode node) {

    String varName = node.getVarName();
    String nodeId = Integer.toString(node.getId());

    TranslateToGoExprVisitor ttjev =
        translateToGoExprVisitorFactory.create(localVarTranslations);

    // Get the Go expression text for the init/limit/increment values.
    List<ExprRootNode<?>> rangeArgs = Lists.newArrayList(node.getRangeArgs());
    String incrementGoExprText = (rangeArgs.size() == 3) ?
        genIntegerValue(ttjev.exec(rangeArgs.remove(2))) : "1" /* default */;
    String initGoExprText = (rangeArgs.size() == 2) ?
        genIntegerValue(ttjev.exec(rangeArgs.remove(0))) : "0" /* default */;
    String limitGoExprText = genIntegerValue(ttjev.exec(rangeArgs.get(0)));

    // If any of the Go exprs for init/limit/increment isn't an integer, precompute its value.
    String initCode;
    if (INTEGER.matcher(initGoExprText).matches()) {
      initCode = initGoExprText;
    } else {
      initCode = varName + "Init" + nodeId;
      goCodeBuilder.appendLine(initCode, " := ", initGoExprText);
    }

    String limitCode;
    if (INTEGER.matcher(limitGoExprText).matches()) {
      limitCode = limitGoExprText;
    } else {
      limitCode = varName + "Limit" + nodeId;
      goCodeBuilder.appendLine(limitCode, " := ", limitGoExprText);
    }

    String incrementCode;
    if (INTEGER.matcher(incrementGoExprText).matches()) {
      incrementCode = incrementGoExprText;
    } else {
      incrementCode = varName + "Increment" + nodeId;
      goCodeBuilder.appendLine(incrementCode, " := ", incrementGoExprText);
    }

    // The start of the Go 'for' loop.
    String incrementStmt =
        (incrementCode.equals("1")) ? varName + nodeId + "++"
                                    : varName + nodeId + " += " + incrementCode;
    goCodeBuilder.appendLine("for ",
                             varName, nodeId, " := ", initCode, "; ",
                             varName, nodeId, " < ", limitCode, "; ",
                             incrementStmt,
                             " {");
    goCodeBuilder.increaseIndent();

    // Add a new localVarTranslations frame and populate it with the translations from this node.
    Map<String, GoExpr> newLocalVarTranslationsFrame = Maps.newHashMap();
    newLocalVarTranslationsFrame.put(
        varName,
        new GoExpr("soyutil.NewIntegerData(" +
                     varName + nodeId + ")",
                     IntegerData.class, Integer.MAX_VALUE));
    localVarTranslations.push(newLocalVarTranslationsFrame);

    // Generate the code for the loop body.
    visitChildren(node);

    // Remove the localVarTranslations frame that we added above.
    localVarTranslations.pop();

    // The end of the Go 'for' loop.
    goCodeBuilder.decreaseIndent();
    goCodeBuilder.appendLine("}");
  }


  /**
   * Example:
   * <pre>
   *   {call name="some.func" data="all" /}
   *   {call name="some.func" data="$boo.foo" /}
   *   {call name="some.func"}
   *     {param key="goo" value="88" /}
   *   {/call}
   *   {call name="some.func" data="$boo"}
   *     {param key="goo"}
   *       Hello {$name}
   *     {/param}
   *   {/call}
   * </pre>
   * might generate
   * <pre>
   *   output += some.func(opt_data);
   *   output += some.func(opt_data.boo.foo);
   *   output += some.func({goo: 88});
   *   output += some.func(soy.$$augmentData(opt_data.boo, {goo: 'Hello ' + opt_data.name});
   * </pre>
   */
  @Override protected void visitCallNode(CallNode node) {

    // If this node has any CallParamContentNode children those contents are not computable as Go
    // expressions, visit them to generate code to define their respective 'param<n>' variables.
    for (CallParamNode child : node.getChildren()) {
      if (child instanceof CallParamContentNode && !isComputableAsGoExprsVisitor.exec(child)) {
        visit(child);
      }
    }

    if (goSrcOptions.getCodeStyle() == CodeStyle.STRINGBUILDER) {
      // For 'stringbuilder' code style, pass the current output var to collect the call's output.
      if (! (node instanceof CallBasicNode)) {
        throw new UnsupportedOperationException("Delegates are not supported in GoSrc backend.");
      }
      GoExpr objToPass = genCallCodeUtils.genObjToPass(node, localVarTranslations);
      String templateName;
      String calleeName = ((CallBasicNode) node).getCalleeName();
      int lastDotIndex = calleeName.lastIndexOf('.');
      TemplateNode tn = GoSrcUtils.templateNodeFromName(node, calleeName);
      if(lastDotIndex < 0) {
        templateName = calleeName;
      } else if(lastDotIndex == 0 || GoSrcUtils.namespaceFromNode(node).equals(calleeName.substring(0, lastDotIndex))) {
        templateName = calleeName.substring(lastDotIndex + 1);
        if(tn != null) {
          if(tn.isPrivate()) {
            templateName = GoSrcUtils.privatize(templateName);
          } else {
            templateName = GoSrcUtils.publicize(templateName);
          }
        }
      } else {
        templateName = calleeName.substring(lastDotIndex+1);
        templateName = goTemplateName(node, GoSrcUtils.callNameToTemplateName(calleeName));
      }
      goCodeBuilder.indent().append(
          templateName, "(", objToPass.getText(), ", ")
          .appendOutputVarName().append(")\n");

    } else {
      // For 'concat' code style, we simply add the call's result to the current output var.
      GoExpr callExpr = genCallCodeUtils.genCallExpr(node, localVarTranslations);
      goCodeBuilder.addToOutputVar(ImmutableList.of(callExpr));
    }
  }


  @Override protected void visitCallParamContentNode(CallParamContentNode node) {

    // This node should only be visited when it's not computable as Go expressions, because this
    // method just generates the code to define the temporary 'param<n>' variable.
    if (isComputableAsGoExprsVisitor.exec(node)) {
      throw new AssertionError(
          "Should only define 'param<n>' when not computable as Go expressions.");
    }

    localVarTranslations.push(Maps.<String, GoExpr>newHashMap());
    goCodeBuilder.pushOutputVar("param" + node.getId());

    visitChildren(node);

    goCodeBuilder.popOutputVar();
    localVarTranslations.pop();
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  @Override protected void visitSoyNode(SoyNode node) {

    if (node instanceof ParentSoyNode<?>) {

      if (node instanceof BlockNode) {
        localVarTranslations.push(Maps.<String, GoExpr>newHashMap());
        visitChildren((BlockNode) node);
        localVarTranslations.pop();

      } else {
        visitChildren((ParentSoyNode<?>) node);
      }

      return;
    }

    if (isComputableAsGoExprsVisitor.exec(node)) {
      // Simply generate Go expressions for this node and add them to the current output var.
      goCodeBuilder.addToOutputVar(genGoExprsVisitor.exec(node));

    } else {
      // Need to implement visitInternal() for the specific case.
      throw new UnsupportedOperationException();
    }
  }

}
