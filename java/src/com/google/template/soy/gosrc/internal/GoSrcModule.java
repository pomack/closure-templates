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

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.template.soy.gosrc.SoyGoSrcOptions;
import com.google.template.soy.gosrc.internal.GenGoExprsVisitor.GenGoExprsVisitorFactory;
import com.google.template.soy.gosrc.internal.TranslateToGoExprVisitor.TranslateToGoExprVisitorFactory;
import com.google.template.soy.gosrc.restricted.SoyGoSrcFunction;
import com.google.template.soy.gosrc.restricted.SoyGoSrcPrintDirective;
import com.google.template.soy.gosrc.internal.CanInitOutputVarVisitor;
import com.google.template.soy.gosrc.internal.GenCallCodeUtils;
import com.google.template.soy.gosrc.internal.OptimizeBidiCodeGenVisitor;
import com.google.template.soy.shared.internal.ApiCallScope;
import com.google.template.soy.shared.internal.ModuleUtils;
import com.google.template.soy.shared.internal.GuiceSimpleScope;
import com.google.template.soy.shared.internal.SharedModule;
import com.google.template.soy.shared.restricted.SoyFunction;
import com.google.template.soy.shared.restricted.SoyPrintDirective;
import com.google.template.soy.sharedpasses.SharedPassesModule;

import java.util.Map;
import java.util.Set;


/**
 * Guice module for the Go Source backend.
 *
 * <p> Important: Do not use outside of Soy code (treat as superpackage-private).
 *
 */
public class GoSrcModule extends AbstractModule {


  @Override protected void configure() {

    // Install requisite modules.
    install(new SharedModule());
    install(new SharedPassesModule());

    // Bindings for when explicit dependencies are required.
    bind(GoSrcMain.class);
    bind(GenGoCodeVisitor.class);
    bind(OptimizeBidiCodeGenVisitor.class);
    bind(CanInitOutputVarVisitor.class);
    bind(GenCallCodeUtils.class);
    bind(IsComputableAsGoExprsVisitor.class);
    
    // Bind providers of factories (created via assisted inject).
    install(new FactoryModuleBuilder().build(GenGoExprsVisitorFactory.class));
    install(new FactoryModuleBuilder().build(TranslateToGoExprVisitorFactory.class));

    // Bind unscoped providers for parameters in ApiCallScope (these throw exceptions).
    bind(SoyGoSrcOptions.class)
        .toProvider(GuiceSimpleScope.<SoyGoSrcOptions>getUnscopedProvider())
        .in(ApiCallScope.class);
  }


  /**
   * Builds and provides the map of SoyGoSrcFunctions (name to function).
   * @param soyFunctionsSet The installed set of SoyFunctions (from Guice Multibinder). Each
   *     SoyFunction may or may not implement SoyGoSrcFunction.
   */
  @Provides
  @Singleton
  Map<String, SoyGoSrcFunction> provideSoyGoSrcFunctionsMap(Set<SoyFunction> soyFunctionsSet) {

    return ModuleUtils.buildSpecificSoyFunctionsMap(
        SoyGoSrcFunction.class, soyFunctionsSet);
  }


  /**
   * Builds and provides the map of SoyGoSrcDirectives (name to directive).
   * @param soyDirectivesSet The installed set of SoyDirectives (from Guice Multibinder). Each
   *     SoyDirective may or may not implement SoyGoSrcDirective.
   */
  @Provides
  @Singleton
  Map<String, SoyGoSrcPrintDirective> provideSoyGoSrcDirectivesMap(
      Set<SoyPrintDirective> soyDirectivesSet) {

    return ModuleUtils.buildSpecificSoyDirectivesMap(
        SoyGoSrcPrintDirective.class, soyDirectivesSet);
  }

}
