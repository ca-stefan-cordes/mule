/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.module.deployment.impl.internal.policy;

import static org.mule.runtime.module.artifact.activation.api.extension.ExtensionModelDiscoverer.defaultExtensionModelDiscoverer;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toMap;

import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.core.api.extension.ExtensionManager;
import org.mule.runtime.deployment.model.api.artifact.extension.ExtensionModelDiscoverer;
import org.mule.runtime.deployment.model.api.artifact.extension.ExtensionModelLoaderRepository;
import org.mule.runtime.deployment.model.api.plugin.ArtifactPlugin;
import org.mule.runtime.module.artifact.activation.api.extension.ExtensionDiscoveryRequest;
import org.mule.runtime.module.artifact.api.classloader.ArtifactClassLoader;
import org.mule.runtime.module.artifact.api.descriptor.ArtifactPluginDescriptor;
import org.mule.runtime.module.extension.api.manager.ExtensionManagerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates {@link ExtensionManager} for mule artifacts that own a {@link MuleContext}
 */
public class ArtifactExtensionManagerFactory implements ExtensionManagerFactory {

  private final Map<ArtifactPluginDescriptor, ArtifactClassLoader> artifactPluginsClassLoaders;
  private final ExtensionManagerFactory extensionManagerFactory;
  private final ExtensionModelDiscoverer extensionModelDiscoverer;

  /**
   * Creates a extensionManager factory
   *
   * @param artifactPlugins                artifact plugins deployed inside the artifact. Non null.
   * @param extensionModelLoaderRepository {@link ExtensionModelLoaderRepository} with the available extension loaders. Non null.
   * @param extensionManagerFactory        creates the {@link ExtensionManager} for the artifact. Non null
   */
  public ArtifactExtensionManagerFactory(List<ArtifactPlugin> artifactPlugins,
                                         ExtensionManagerFactory extensionManagerFactory) {
    this.artifactPluginsClassLoaders = artifactPlugins
        .stream()
        .collect(toMap(ArtifactPlugin::getDescriptor, ArtifactPlugin::getArtifactClassLoader,
                       (x, y) -> y, LinkedHashMap::new));

    this.extensionManagerFactory = extensionManagerFactory;
    this.extensionModelDiscoverer = new ExtensionModelDiscoverer();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ExtensionManager create(MuleContext muleContext) {
    return create(muleContext, emptySet());
  }

  protected ExtensionManager create(MuleContext muleContext, Set<ExtensionModel> parentArtifactExtensions) {
    final ExtensionManager extensionManager = extensionManagerFactory.create(muleContext);
    final Set<ExtensionModel> extensions = new HashSet<>();
    extensionModelDiscoverer.discoverRuntimeExtensionModels()
        .forEach(extensionManager::registerExtension);

    extensions.addAll(defaultExtensionModelDiscoverer(artifactPluginsClassLoaders::get)
        .discoverPluginsExtensionModels(ExtensionDiscoveryRequest
            .builder()
            .setArtifactPlugins(new ArrayList<>(artifactPluginsClassLoaders.keySet()))
            .setParentArtifactExtensions(parentArtifactExtensions)
            .build()));

    extensions.forEach(extensionManager::registerExtension);
    return extensionManager;
  }

}
