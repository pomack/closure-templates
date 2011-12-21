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

import com.google.template.soy.base.SoySyntaxException;
import com.google.template.soy.internal.base.Pair;
import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.gosrc.SoyGoSrcOptions.CodeStyle;
import com.google.template.soy.gosrc.restricted.GoExpr;
import com.google.template.soy.gosrc.restricted.GoExprUtils;
import static com.google.template.soy.gosrc.restricted.GoCodeUtils.genCoerceString;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;


/**
 * A class for building Go code.
 *
 * TODO: fix doc
 * <p> Usage example that demonstrates most of the methods:
 * <pre>
 *   GoCodeBuilder jcb = new GoCodeBuilder(CodeStyle.STRINGBUILDER);
 *   jcb.appendLine("story.title = function(opt_data) {");
 *   jcb.increaseIndent();
 *   jcb.pushOutputVar("output");
 *   jcb.initOutputVarIfNecessary();
 *   jcb.pushOutputVar("temp");
 *   jcb.addToOutputVar(Lists.newArrayList(
 *       new GoExpr("'Snow White and the '", Integer.MAX_VALUE),
 *       new GoExpr("opt_data.numDwarfs", Integer.MAX_VALUE));
 *   jcb.popOutputVar();
 *   jcb.addToOutputVar(Lists.newArrayList(
 *       new GoExpr("temp", Integer.MAX_VALUE),
 *       new GoExpr("' Dwarfs'", Integer.MAX_VALUE));
 *   jcb.indent().append("return ").appendOutputVarName().append(".toString();\n");
 *   jcb.popOutputVar();
 *   jcb.decreaseIndent();
 *   String THE_END = "the end";
 *   jcb.appendLine("}  // ", THE_END);
 * </pre>
 * The above example builds the following Go code:
 * <pre>
 * story.title = function(opt_data) {
 *   var output = new soy.StringBuilder();
 *   var temp = new soy.StringBuilder('Snow White and the ', opt_data.numDwarfs);
 *   output.append(temp, ' Dwarfs');
 *   return output.toString();
 * }  // the end
 * </pre>
 *
 * @author Kai Huang
 */
class GoCodeBuilder {


  /** Used by {@code increaseIndent()} and {@code decreaseIndent()}. */
  private static final String SPACES = "                    ";  // 20 spaces


  /** A buffer to accumulate the generated code. */
  private final StringBuilder code;
  
  private final List<Pair<String, String>> allCode;

  /** The {@code OutputCodeGenerator} to use. */
  private final CodeStyle codeStyle;

  /** The current indent (some even number of spaces). */
  private String indent;

  /** The current stack of output variables. */
  private Deque<Pair<String, Boolean>> outputVars;

  /** The current output variable name. */
  private String currOutputVarName;

  /** Whether the current output variable is initialized. */
  private boolean currOutputVarIsInited;


  /**
   * Constructs a new instance. At the start, the code is empty and the indent is 0 spaces.
   *
   * @param codeStyle The code style to use.
   */
  public GoCodeBuilder(CodeStyle codeStyle) {
    this.codeStyle = codeStyle;
    code = new StringBuilder();
    allCode = new ArrayList<Pair<String, String>>();
    indent = "";
    outputVars = new ArrayDeque<Pair<String, Boolean>>();
    currOutputVarName = null;
    currOutputVarIsInited = false;
  }


  /**
   * Increases the current indent by two spaces.
   * @throws SoySyntaxException If the new indent depth would be greater than 20.
   */
  public void increaseIndent() throws SoySyntaxException {
    int newIndentDepth = indent.length() + 2;
    if (newIndentDepth > 20) {
      throw new SoySyntaxException("Indent is more than 20 spaces!");
    }
    indent = SPACES.substring(0, newIndentDepth);
  }


  /**
   * Decreases the current indent by two spaces.
   * @throws SoySyntaxException If the new indent depth would be less than 0.
   */
  public void decreaseIndent() throws SoySyntaxException {
    int newIndentDepth = indent.length() - 2;
    if (newIndentDepth < 0) {
      throw new SoySyntaxException("Indent is less than 0 spaces!");
    }
    indent = SPACES.substring(0, newIndentDepth);
  }


  /**
   * Pushes on a new current output variable.
   * @param outputVarName The new output variable name.
   */
  public void pushOutputVar(String outputVarName) {
    outputVars.push(Pair.of(outputVarName, false));
    currOutputVarName = outputVarName;
    currOutputVarIsInited = false;
  }


  /**
   * Pops off the current output variable. The previous output variable again becomes the current.
   */
  public void popOutputVar() {
    outputVars.pop();
    Pair<String, Boolean> topPair = outputVars.peek();  // null if outputVars is now empty
    if (topPair != null) {
      currOutputVarName = topPair.getFirst();
      currOutputVarIsInited = topPair.getSecond();
    } else {
      currOutputVarName = null;
      currOutputVarIsInited = false;
    }
  }


  /**
   * Tells this GoCodeBuilder that the current output variable has already been initialized. This
   * causes {@code initOutputVarIfNecessary} and {@code addToOutputVar} to not add initialization
   * code even on the first use of the variable.
   */
  public void setOutputVarInited() {
    outputVars.pop();
    outputVars.push(Pair.of(currOutputVarName, true));
    currOutputVarIsInited = true;
  }


  /**
   * Appends the current indent to the generated code.
   * @return This GoCodeBuilder (for stringing together operations).
   */
  public GoCodeBuilder indent() {
    code.append(indent);
    return this;
  }


  /**
   * Appends one or more strings to the generated code.
   * @param goCodeFragments The code string(s) to append.
   * @return This GoCodeBuilder (for stringing together operations).
   */
  public GoCodeBuilder append(String... goCodeFragments) {
    for (String goCodeFragment : goCodeFragments) {
      code.append(goCodeFragment);
    }
    return this;
  }


  /**
   * Equvalent to goCodeBuilder.indent().append(goCodeFragments).append("\n");
   * @param goCodeFragments The code string(s) to append.
   * @return This GoCodeBuilder (for stringing together operations).
   */
  public GoCodeBuilder appendLine(String... goCodeFragments) {
    indent();
    append(goCodeFragments);
    code.append("\n");
    return this;
  }


  /**
   * Appends the name of the current output variable.
   * @return This GoCodeBuilder (for stringing together operations).
   */
  public GoCodeBuilder appendOutputVarName() {
    code.append(currOutputVarName);
    return this;
  }


  /**
   * Appends a full line/statement for initializing the current output variable.
   */
  public void initOutputVarIfNecessary() {

    if (currOutputVarIsInited) {
      // Nothing to do since it's already initialized.
      return;
    }

    if (codeStyle == CodeStyle.STRINGBUILDER) {
      // StringBuilder output = new StringBuilder();
      appendLine(currOutputVarName, " := bytes.NewBuffer(make([]byte, 0, DEFAULT_BUFFER_SIZE_IN_BYTES))");
    } else {
      // String output = "";
      appendLine(currOutputVarName, " := \"\"");
    }
    setOutputVarInited();
  }


  /**
   * Appends a line/statement with the concatenation of the given Go expressions saved to the
   * current output variable.
   * @param goExprs One or more Go expressions to compute output.
   */
  public void addToOutputVar(List<GoExpr> goExprs) {

    if (codeStyle == CodeStyle.STRINGBUILDER) {
      if (!currOutputVarIsInited) {
        // StringBuilder output = (new StringBuilder()).append(AAA).append(BBB);
        appendLine(currOutputVarName, " := bytes.NewBuffer(make([]byte, 0, DEFAULT_BUFFER_SIZE_IN_BYTES))");
        setOutputVarInited();
      }
      for (GoExpr goExpr : goExprs) {
        appendLine(currOutputVarName, ".WriteString(", genCoerceString(goExpr), ")");
      }

    } else {  // CodeStyle.CONCAT
      GoExpr concatenatedGoExprs = GoExprUtils.concatGoExprs(goExprs);

      if (currOutputVarIsInited) {
        // output += AAA + BBB + CCC;
        appendLine(currOutputVarName, " += ", concatenatedGoExprs.getText());
      } else {
        // String output = AAA + BBB + CCC;
        appendLine(currOutputVarName, " := ", concatenatedGoExprs.getText());
        setOutputVarInited();
      }
    }
  }
  
  public void doneWithFile(SoyFileNode node) {
    allCode.add(new Pair<String, String>(node.getNamespace(), code.toString()));
    code.setLength(0);
  }


  /**
   * @return The generated code.
   */
  public List<Pair<String, String>> getCode() {
    return allCode;
  }

}
