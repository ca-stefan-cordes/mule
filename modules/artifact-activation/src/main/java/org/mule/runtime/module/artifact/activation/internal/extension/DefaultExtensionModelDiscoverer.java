/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.artifact.activation.internal.extension;

import static org.mule.runtime.module.artifact.api.descriptor.BundleDescriptor.MULE_PLUGIN_CLASSIFIER;

import static java.lang.Thread.currentThread;
import static java.util.Collections.synchronizedSet;
import static java.util.stream.Collectors.toSet;

import static org.slf4j.LoggerFactory.getLogger;

import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.core.api.extension.MuleExtensionModelProvider;
import org.mule.runtime.core.api.extension.RuntimeExtensionModelProvider;
import org.mule.runtime.core.api.registry.SpiServiceRegistry;
import org.mule.runtime.module.artifact.activation.api.extension.ExtensionDiscoveryRequest;
import org.mule.runtime.module.artifact.activation.api.extension.ExtensionModelDiscoverer;
import org.mule.runtime.module.artifact.activation.api.extension.ExtensionModelGenerator;
import org.mule.runtime.module.artifact.api.descriptor.ArtifactPluginDescriptor;
import org.mule.runtime.module.artifact.api.descriptor.BundleDescriptor;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableSet;

import org.jgrapht.alg.TransitiveReduction;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.slf4j.Logger;

public class DefaultExtensionModelDiscoverer implements ExtensionModelDiscoverer {

  private static final Logger LOGGER = getLogger(DefaultExtensionModelDiscoverer.class);

  // private final Function<ArtifactPluginDescriptor, ExtensionModel> extensionModelLoader;
  private final ExtensionModelGenerator extensionModelLoader;

  // public DefaultExtensionModelDiscoverer() {
  // this.extensionModelLoader = new ClassIntrospectionLalala();
  // }

  // public DefaultExtensionModelDiscoverer(Function<ArtifactPluginDescriptor, ExtensionModel> extensionModelLoader) {
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
    final Set<ExtensionModel> discoveredExtensions = synchronizedSet(new HashSet<>());

    SimpleDirectedGraph<BundleDescriptor, DefaultEdge> depsGraph = new SimpleDirectedGraph<>(DefaultEdge.class);

    discoveryRequest.getArtifactPlugins()
        .stream()
        .forEach(apd -> depsGraph.addVertex(apd.getBundleDescriptor()));
    discoveryRequest.getArtifactPlugins()
        .stream()
        .forEach(apd -> apd.getClassLoaderModel().getDependencies().stream()
            .filter(dep -> dep.getDescriptor().getClassifier().map(MULE_PLUGIN_CLASSIFIER::equals).orElse(false)
                // account for dependencies from parent artifact
                && depsGraph.containsVertex(dep.getDescriptor()))
            .forEach(dep -> depsGraph.addEdge(apd.getBundleDescriptor(), dep.getDescriptor(), new DefaultEdge())));
    TransitiveReduction.INSTANCE.reduce(depsGraph);

    LOGGER.debug("Dependencies graph: {}", depsGraph);

    while (!depsGraph.vertexSet().isEmpty()) {
      Set<BundleDescriptor> processedDependencies = synchronizedSet(new HashSet<>());

      artifactPluginsStream(discoveryRequest)
          .filter(artifactPlugin -> depsGraph.vertexSet().contains(artifactPlugin.getBundleDescriptor())
              && depsGraph.outDegreeOf(artifactPlugin.getBundleDescriptor()) == 0)
          .forEach(artifactPlugin -> {
            LOGGER.debug("discoverPluginExtensionModel(parallel): {}", artifactPlugin.toString());

            // need this auxiliary structure because the graph does not support concurrent modifications
            processedDependencies.add(artifactPlugin.getBundleDescriptor());
            discoverPluginExtensionModel(discoveryRequest, discoveredExtensions, artifactPlugin);
          });

      processedDependencies.forEach(depsGraph::removeVertex);
      LOGGER.debug("discoverPluginsExtensionModels(parallel): next iteration on the depsGraph...");
    }

    return discoveredExtensions;
  }

  private Stream<ArtifactPluginDescriptor> artifactPluginsStream(ExtensionDiscoveryRequest discoveryRequest) {
    if (discoveryRequest.isParallelDiscovery()) {
      return discoveryRequest.getArtifactPlugins().parallelStream();
    } else {
      return discoveryRequest.getArtifactPlugins().stream();
    }
  }

  private void discoverPluginExtensionModel(ExtensionDiscoveryRequest discoveryRequest,
                                            final Set<ExtensionModel> extensions,
                                            ArtifactPluginDescriptor artifactPlugin) {
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
  }
}
