/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.extension.internal.ast;

import static java.util.Collections.newSetFromMap;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;
import static org.mule.runtime.extension.internal.ast.MacroExpansionModuleModel.DEFAULT_GLOBAL_ELEMENTS;

import org.mule.runtime.api.config.FeatureFlaggingService;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.api.meta.model.config.ConfigurationModel;
import org.mule.runtime.ast.api.ArtifactAst;
import org.mule.runtime.ast.api.ComponentAst;
import org.mule.runtime.config.api.properties.ConfigurationPropertiesResolver;
import org.mule.runtime.config.internal.model.ApplicationModelAstPostProcessor;
import org.mule.runtime.extension.api.property.XmlExtensionModelProperty;

import javax.inject.Inject;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.stream.Stream;


public class MacroExpansionAstPostProcessor implements ApplicationModelAstPostProcessor {

  @Inject
  private ConfigurationPropertiesResolver configurationPropertiesResolver;

  @Override
  public ArtifactAst postProcessAst(ArtifactAst ast, Set<ExtensionModel> extensionModels,
                                    ConfigurationPropertiesResolver configurationPropertiesResolver) {
    return new MacroExpansionModulesModel(ast, extensionModels, configurationPropertiesResolver).expand();
  }

  @Override
  public Set<ComponentAst> resolveRootComponents(Collection<ComponentAst> rootComponents, Set<ExtensionModel> extensionModels) {
    final Set<ConfigurationModel> xmlSdk1ConfigModels = newSetFromMap(new IdentityHashMap<>());

    extensionModels
        .stream()
        .flatMap(extension -> extension.getModelProperty(XmlExtensionModelProperty.class)
            .map(mp -> extension.getConfigurationModels().stream())
            .orElse(Stream.empty()))
        .forEach(xmlSdk1ConfigModels::add);

    // Handle specific case for nested configs/topLevelElements generated by XmlSdk1 macroexpansion
    return concat(rootComponents.stream(),
                  rootComponents.stream()
                      .flatMap(root -> root.recursiveStream()
                          .filter(comp -> comp.getModel(ConfigurationModel.class)
                              .map(xmlSdk1ConfigModels::contains)
                              .orElse(comp.getIdentifier().getName().equals(DEFAULT_GLOBAL_ELEMENTS)))
                          .flatMap(ComponentAst::directChildrenStream)))
                              .filter(comp -> !comp.getIdentifier().getName().equals(DEFAULT_GLOBAL_ELEMENTS))
                              .collect(toSet());
  }
}
