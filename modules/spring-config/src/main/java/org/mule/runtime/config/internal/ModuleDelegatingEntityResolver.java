/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.config.internal;

import static java.lang.String.format;
import static java.lang.Thread.currentThread;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;

import org.mule.runtime.api.dsl.DslResolvingContext;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.core.api.registry.ServiceRegistry;
import org.mule.runtime.core.api.registry.SpiServiceRegistry;
import org.mule.runtime.extension.api.dsl.syntax.resources.spi.ExtensionSchemaGenerator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Custom implementation of resolver for schemas where it will delegate to our custom resolver, then if not found will try to
 * generate the XSDs from the extensions (through {@link }).
 *
 * @since 4.0
 */
public class ModuleDelegatingEntityResolver implements EntityResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(ModuleDelegatingEntityResolver.class);

  private static final String CORE_XSD = "http://www.mulesoft.org/schema/mule/core/current/mule.xsd";
  private static final String CORE_CURRENT_XSD = "http://www.mulesoft.org/schema/mule/core/current/mule-core.xsd";
  private static final String CORE_DEPRECATED_XSD = "http://www.mulesoft.org/schema/mule/core/current/mule-core-deprecated.xsd";
  private static final String COMPATIBILITY_XSD =
      "http://www.mulesoft.org/schema/mule/compatibility/current/mule-compatibility.xsd";
  private static final String TEST_XSD = "http://www.mulesoft.org/schema/mule/test/current/mule-test.xsd";
  private static final int MAX_RESOLUTION_FAILURE_THRESHOLD = 10;

  private static Boolean internalIsRunningTests;

  private final Set<ExtensionModel> extensions;
  private final EntityResolver muleEntityResolver;
  // TODO(fernandezlautaro): MULE-11024 once implemented, extensionSchemaFactory must not be Optional
  private Optional<ExtensionSchemaGenerator> extensionSchemaFactory;
  /**
   * Saves already checked entities so that if the resolution fails more than {@link #MAX_RESOLUTION_FAILURE_THRESHOLD}
   * it will raise and exception instead of looping in failure over and over again.
   */
  private Map<String, AtomicInteger> checkedEntities;

  /**
   * Returns an instance of {@link ModuleDelegatingEntityResolver}
   *
   * @param extensions fallback set to dynamically generate schemas from {@link ExtensionModel} if the current
   *                   {@link #muleEntityResolver} delegates return null when resolving the entity.
   */
  public ModuleDelegatingEntityResolver(Set<ExtensionModel> extensions) {
    ClassLoader classLoader = currentThread().getContextClassLoader();
    this.muleEntityResolver = new MuleCustomEntityResolver(classLoader);
    this.extensions = extensions;
    this.checkedEntities = new HashMap<>();

    ServiceRegistry spiServiceRegistry = new SpiServiceRegistry();
    // TODO(fernandezlautaro): MULE-11024 until the implementation is moved up to extensions-api, we need to work with Optional to
    // avoid breaking the mule testing framework (cannot add the dependency, as it will imply a circular dependency)
    final Collection<ExtensionSchemaGenerator> schemaResourceFactories =
        spiServiceRegistry.lookupProviders(ExtensionSchemaGenerator.class, getClass().getClassLoader());
    if (schemaResourceFactories.isEmpty()) {
      extensionSchemaFactory = empty();
    } else if (schemaResourceFactories.size() == 1) {
      extensionSchemaFactory = of(schemaResourceFactories.iterator().next());
    } else {
      // TODO(fernandezlautaro): MULE-11024 remove this code once implemented using just
      // spiServiceRegistry.lookupProvider(SchemaResourceFactory.class, getClass().getClassLoader()) (notice the method name
      // chance from #lookupProviders to #lookupProvider)
      throw new IllegalArgumentException(format("There are '%s' providers for '%s' when there must be 1 or zero.",
                                                schemaResourceFactories.size(), ExtensionSchemaGenerator.class.getName()));
    }
  }

  @Override
  public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(format("Looking schema for public identifier(publicId): '%s', system identifier(systemId): '%s'",
                          publicId == null ? "" : publicId,
                          systemId));
    }

    systemId = overrideSystemIdForCompatibility(publicId, systemId);

    InputSource inputSource;
    inputSource = muleEntityResolver.resolveEntity(publicId, systemId);
    if (inputSource == null) {
      inputSource = generateFromExtensions(publicId, systemId);
    }
    if (inputSource == null) {
      AtomicInteger failures = checkedEntities.get(systemId);
      if (failures != null) {
        if (failures.incrementAndGet() > MAX_RESOLUTION_FAILURE_THRESHOLD) {
          String namespaceNotFound =
              publicId == null ? format("Can't resolve %s", systemId) : format("Can't resolve %s (%s)", publicId, systemId);
          String message = format("%s, A dependency or plugin might be missing", namespaceNotFound);
          throw new MuleRuntimeException(createStaticMessage(message));
        }
      } else {
        checkedEntities.put(systemId, new AtomicInteger(1));
      }
    }
    return inputSource;
  }

  private String overrideSystemIdForCompatibility(String publicId, String systemId) throws SAXException, IOException {
    if (systemId.equals(CORE_XSD)) {
      Boolean useDeprecated = canResolveEntity(publicId, CORE_DEPRECATED_XSD);
      Boolean usingCompatibility = canResolveEntity(publicId, COMPATIBILITY_XSD);
      boolean runningTests = isRunningTests();

      if (useDeprecated && (usingCompatibility || runningTests)) {
        return CORE_DEPRECATED_XSD;
      } else {
        return CORE_CURRENT_XSD;
      }
    } else if (systemId.equals(TEST_XSD)) {
      boolean runningTests = isRunningTests();
      if (!runningTests && generateFromExtensions(publicId, systemId) == null) {
        String message = "Internal runtime mule-test.xsd can't be used in real applications";
        throw new MuleRuntimeException(createStaticMessage(message));
      }
    } else if (systemId.contains("spring")) {
      systemId = systemId.replace("-current.xsd", ".xsd");
    }

    return systemId;
  }

  protected boolean canResolveEntity(String publicId, String systemId) throws SAXException, IOException {
    final InputSource resolvedEntity = muleEntityResolver.resolveEntity(publicId, systemId);
    try {
      return resolvedEntity != null;
    } finally {
      if (resolvedEntity != null) {
        if (resolvedEntity.getByteStream() != null) {
          resolvedEntity.getByteStream().close();
        }
        if (resolvedEntity.getCharacterStream() != null) {
          resolvedEntity.getCharacterStream().close();
        }
      }
    }
  }

  private boolean isRunningTests() {
    if (internalIsRunningTests != null) {
      return internalIsRunningTests;
    }
    for (StackTraceElement element : new Throwable().getStackTrace()) {
      if (element.getClassName().startsWith("org.junit.runners.")) {
        internalIsRunningTests = true;
        return true;
      }
    }
    internalIsRunningTests = false;
    return false;
  }

  private InputSource generateFromExtensions(String publicId, String systemId) {
    InputSource inputSource = null;
    // TODO(fernandezlautaro): MULE-11024 once implemented, remove the extensionSchemaFactory.isPresent() from the `if` statement
    if (extensionSchemaFactory.isPresent()) {
      Optional<ExtensionModel> extensionModel = extensions.stream()
          .filter(em -> systemId.equals(em.getXmlDslModel().getSchemaLocation()))
          .findAny();
      if (extensionModel.isPresent()) {
        InputStream schema = getSchemaFromExtension(extensionModel.get());
        inputSource = new InputSource(schema);
        inputSource.setPublicId(publicId);
        inputSource.setSystemId(systemId);
      }
    }
    return inputSource;
  }

  /**
   * Given an {@link ExtensionModel} it will generate the XSD for it.
   *
   * @param extensionModel extension to generate the schema for
   * @return the bytes that represent the schema for the {@code extensionModel}
   */
  private InputStream getSchemaFromExtension(ExtensionModel extensionModel) {
    String generatedResource = extensionSchemaFactory
        .map(f -> f.generate(extensionModel, DslResolvingContext.getDefault(extensions)))
        .orElseThrow(
                     () -> new IllegalStateException("There were no schema generators available when trying to work with the extension '"
                         + extensionModel.getName() + "'"));
    return new ByteArrayInputStream(generatedResource.getBytes(UTF_8));
  }
}
