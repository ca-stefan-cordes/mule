/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.artifact.activation.internal.extension;

import static java.lang.Thread.currentThread;
import static java.util.stream.Collectors.toSet;

import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.core.api.extension.MuleExtensionModelProvider;
import org.mule.runtime.core.api.extension.RuntimeExtensionModelProvider;
import org.mule.runtime.core.api.registry.SpiServiceRegistry;
import org.mule.runtime.module.artifact.activation.api.extension.ExtensionDiscoveryRequest;
import org.mule.runtime.module.artifact.activation.api.extension.ExtensionModelDiscoverer;
import org.mule.runtime.module.artifact.activation.api.extension.ExtensionModelGenerator;
import org.mule.runtime.module.artifact.activation.internal.PluginsDependenciesProcessor;

import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

public class DefaultExtensionModelDiscoverer implements ExtensionModelDiscoverer {

  private final ExtensionModelGenerator extensionModelLoader;

  public DefaultExtensionModelDiscoverer(ExtensionModelGenerator extensionModelLoader) {
    this.extensionModelLoader = extensionModelLoader;
  }

  @Override
  public Set<ExtensionModel> discoverRuntimeExtensionModels() {
    return new SpiServiceRegistry()
        .lookupProviders(RuntimeExtensionModelProvider.class, currentThread().getContextClassLoader())
        .stream()
        .map(RuntimeExtensionModelProvider::createExtensionModel)
        .collect(toSet());
  }

  @Override
  public Set<ExtensionModel> discoverPluginsExtensionModels(ExtensionDiscoveryRequest discoveryRequest) {
    return new HashSet<>(PluginsDependenciesProcessor
        .process(discoveryRequest.getArtifactPlugins(), discoveryRequest.isParallelDiscovery(), (extensions, artifactPlugin) -> {
          Set<ExtensionModel> dependencies = new HashSet<>();

          dependencies.addAll(extensions);
          dependencies.addAll(discoveryRequest.getParentArtifactExtensions());
          if (!dependencies.contains(MuleExtensionModelProvider.getExtensionModel())) {
            dependencies = ImmutableSet.<ExtensionModel>builder()
                .addAll(extensions)
                .addAll(discoverRuntimeExtensionModels())
                .build();
          }

          ExtensionModel extension = extensionModelLoader.obtainExtensionModel(discoveryRequest, artifactPlugin, dependencies);
          if (extension != null) {
            extensions.add(extension);
          }
        }));
  }
}
