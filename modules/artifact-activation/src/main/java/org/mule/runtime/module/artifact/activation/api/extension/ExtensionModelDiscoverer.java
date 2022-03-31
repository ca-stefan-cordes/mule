/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.artifact.activation.api.extension;

import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.module.artifact.activation.internal.extension.ClassIntrospectionExtensionModelGenerator;
import org.mule.runtime.module.artifact.activation.internal.extension.DefaultExtensionModelDiscoverer;
import org.mule.runtime.module.artifact.api.classloader.ArtifactClassLoader;
import org.mule.runtime.module.artifact.api.descriptor.ArtifactPluginDescriptor;

import java.util.Set;
import java.util.function.Function;

/**
 * 
 * @since 4.5
 */
public interface ExtensionModelDiscoverer {

  public static ExtensionModelDiscoverer defaultExtensionModelDiscoverer(Function<ArtifactPluginDescriptor, ArtifactClassLoader> classLoaderFactory) {
    return new DefaultExtensionModelDiscoverer(new ClassIntrospectionExtensionModelGenerator(classLoaderFactory));
  }

  /**
   * Discover the extension models provided by the runtime.
   *
   * @return {@link Set} of the runtime provided {@link ExtensionModel}s.
   */
  public Set<ExtensionModel> discoverRuntimeExtensionModels();

  /**
   * For each artifactPlugin discovers the {@link ExtensionModel}.
   *
   * @param discoveryRequest an object containing the parameterization of the discovery process.
   * @return The discovered {@link ExtensionModel}s.
   */
  public Set<ExtensionModel> discoverPluginsExtensionModels(ExtensionDiscoveryRequest discoveryRequest);

}
