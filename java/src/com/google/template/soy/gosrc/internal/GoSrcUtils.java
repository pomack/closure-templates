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

import java.io.File;

import javax.annotation.Nullable;

import com.google.template.soy.soytree.SoyFileNode;
import com.google.template.soy.soytree.SoyNode;
import com.google.template.soy.soytree.TemplateNode;


/**
 * Shared utilities specific to the Go Src backend.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 * @author Kai Huang
 */
public class GoSrcUtils {


  public static final String PKG_DATA = "com.google.template.soy.data.";

  public static final String PKG_DATA_INTERNAL = "com.google.template.soy.data.internal.";
  
  public static String finalNamespace(String namespace) {
    if(namespace == null || namespace.indexOf('.') < 0)
      return namespace;
    return namespace.substring(namespace.lastIndexOf('.'));
  }
  
  public static String publicize(String value) {
    if(value == null || value.isEmpty())
      return value;
    return Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }
  
  public static String privatize(String value) {
    if(value == null || value.isEmpty())
      return value;
    return Character.toLowerCase(value.charAt(0)) + value.substring(1);
  }
  
  public static String libraryNameToPackageName(String libraryName) {
    return "ct_" + libraryName.replace(".", "_");
  }
  
  public static String callNameToTemplateName(String callName) {
    int lastDotIndex = callName.lastIndexOf('.');
    if(lastDotIndex < 0) {
      return publicize(callName);
    }
    return libraryNameToPackageName(callName.substring(0, lastDotIndex)) + "." + publicize(callName.substring(lastDotIndex + 1));
  }
  
  public static SoyFileNode fileNodeFromNode(SoyNode node) {
    do {
      if(node instanceof SoyFileNode) {
        SoyFileNode sfn = (SoyFileNode)node;
        return sfn;
      }
      node = node.getParent();
    } while(node != null);
    return null;
  }
  
  public static String namespaceFromNode(SoyNode node) {
    SoyFileNode sfn = fileNodeFromNode(node);
    return (sfn == null) ? "" : sfn.getNamespace();
  }
  
  public static TemplateNode templateNodeFromName(SoyNode node, String name) {
    SoyFileNode sfn = fileNodeFromNode(node);
    if(sfn == null)
      return null;
    int lastDotIndex = name.lastIndexOf('.');
    String unqualifiedName = name;
    if(lastDotIndex >= 0) {
      unqualifiedName = name.substring(lastDotIndex + 1);
    }
    for(TemplateNode tn : sfn.getChildren()) {
      String tnName = tn.getTemplateName();
      if(tnName.equals(name)) {
        return tn;
      }
      lastDotIndex = tnName.lastIndexOf('.');
      if(lastDotIndex >= 0) {
        tnName = tnName.substring(lastDotIndex + 1);
        if(tnName.equals(unqualifiedName)) {
          return tn;
        }
      }
    }
    return null;
  }


  /**
   * Builds a specific file path given a path format and the info needed for replacing placeholders.
   *
   * @param filePathFormat The format string defining how to build the file path.
   * @param locale The locale for the file path, or null if not applicable.
   * @param inputFilePath Only applicable if you need to replace the placeholders {INPUT_DIRECTORY},
   *     {INPUT_FILE_NAME}, and {INPUT_FILE_NAME_NO_EXT} (otherwise pass null). This is the full
   *     path of the input file (including the input path prefix).
   * @param inputPathPrefix The input path prefix, or empty string if none.
   * @return The output file path corresponding to the given input file path.
   */
  public static String buildFilePath(String filePathFormat, @Nullable String locale,
                                     @Nullable String inputFilePath, String inputPathPrefix) {

    String path = filePathFormat;

    if (locale != null) {
      path = path.replace("{LOCALE}", locale);
      path = path.replace("{LOCALE_LOWER_CASE}", locale.toLowerCase().replace('-', '_'));
    }

    path = path.replace("{INPUT_PREFIX}", inputPathPrefix);

    if (inputFilePath != null) {
      // Remove the prefix (if any) from the input file path.
      inputFilePath = inputFilePath.substring(inputPathPrefix.length());

      // Compute directory and file name.
      int lastSlashIndex = inputFilePath.lastIndexOf(File.separatorChar);
      String directory = inputFilePath.substring(0, lastSlashIndex + 1);
      String fileName = inputFilePath.substring(lastSlashIndex + 1);

      // Compute file name without extension.
      int lastDotIndex = fileName.lastIndexOf('.');
      if (lastDotIndex == -1) {
        lastDotIndex = fileName.length();
      }
      String fileNameNoExt = fileName.substring(0, lastDotIndex);

      // Substitute placeholders.
      path = path.replace("{INPUT_DIRECTORY}", directory);
      path = path.replace("{INPUT_FILE_NAME}", fileName);
      path = path.replace("{INPUT_FILE_NAME_NO_EXT}", fileNameNoExt);
    }

    return path;
  }

}
