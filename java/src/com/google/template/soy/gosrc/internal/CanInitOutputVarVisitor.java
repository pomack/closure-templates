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

import com.google.inject.Inject;
import com.google.template.soy.gosrc.SoyGoSrcOptions;
import com.google.template.soy.gosrc.SoyGoSrcOptions.CodeStyle;
import com.google.template.soy.soytree.AbstractReturningSoyNodeVisitor;
import com.google.template.soy.soytree.CallNode;
import com.google.template.soy.soytree.SoyNode;


/**
 * Visitor for determining whether the code generated from a given node's subtree can be made to
 * also initialize the current variable (if not already initialized).
 *
 * <p> Precondition: MsgNode should not exist in the tree.
 *
 */
class CanInitOutputVarVisitor extends AbstractReturningSoyNodeVisitor<Boolean> {


  /** The options for generating Go source code. */
  private final SoyGoSrcOptions goSrcOptions;

  /** The IsComputableAsGoExprsVisitor used by this instance (when needed). */
  private final IsComputableAsGoExprsVisitor computableAsGoExprsVisitor;


  /**
   * @param goSrcOptions The options for generating Go source code.
   * @param computableAsGoExprsVisitor The IsComputableAsGoExprsVisitor used by this instance
   *     (when needed).
   */
  @Inject
  public CanInitOutputVarVisitor(SoyGoSrcOptions goSrcOptions,
                                 IsComputableAsGoExprsVisitor computableAsGoExprsVisitor) {
    this.goSrcOptions = goSrcOptions;
    this.computableAsGoExprsVisitor = computableAsGoExprsVisitor;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for concrete classes.


  @Override protected Boolean visitCallNode(CallNode node) {
    // If we're generating code in the 'concat' style, then the call is a Go expression that
    // returns its output as a string. However, if we're generating code in the 'stringbuilder'
    // style, then the call is a full statement that returns no value (instead, the output is
    // directly appended to the StringBuilder we pass to the callee).
    return goSrcOptions.getCodeStyle() == CodeStyle.CONCAT;
  }


  // -----------------------------------------------------------------------------------------------
  // Implementations for interfaces.


  @Override protected Boolean visitSoyNode(SoyNode node) {
    // For the vast majority of nodes, the return value of this visitor should be the same as the
    // return value of IsComputableAsGoExprsVisitor.
    return computableAsGoExprsVisitor.exec(node);
  }

}
