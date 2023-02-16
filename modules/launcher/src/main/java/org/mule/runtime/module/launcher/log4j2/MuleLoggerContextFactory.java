/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.launcher.log4j2;

import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.module.launcher.log4j2.ArtifactAwareContextSelector.LOGGER;
import static org.mule.runtime.module.launcher.log4j2.ArtifactAwareContextSelector.resolveLoggerContextClassLoader;
import static org.mule.runtime.module.reboot.api.MuleContainerBootstrapUtils.getMuleBase;
import static org.mule.runtime.module.reboot.api.MuleContainerBootstrapUtils.getMuleConfDir;

import static java.lang.System.getProperty;

import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.module.artifact.api.classloader.ArtifactClassLoader;
import org.mule.runtime.module.artifact.api.classloader.DirectoryResourceLocator;
import org.mule.runtime.module.artifact.api.classloader.LocalResourceLocator;
import org.mule.runtime.module.artifact.api.descriptor.DeployableArtifactDescriptor;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;

import io.opentelemetry.api.logs.GlobalLoggerProvider;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.selector.ContextSelector;

/**
 * Encapsulates the logic to get the proper log configuration.
 *
 * @since 3.8.0
 */
public class MuleLoggerContextFactory {

  static final String LOG4J_CONFIGURATION_FILE_PROPERTY = "log4j.configurationFile";

  static {
    SdkLoggerProvider sdkLoggerProvider =
        SdkLoggerProvider.builder()
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(OtlpGrpcLogRecordExporter.builder().build()))
            .build();
    GlobalLoggerProvider.set(sdkLoggerProvider);
  }

  /**
   * Builds a new {@link LoggerContext} for the given {@code classLoader} and {@code selector}
   *
   * @param classLoader the classloader of the artifact this logger context is for.
   * @param selector    the selector to bew used when building the loggers for the new context.
   * @return
   */
  public LoggerContext build(final ClassLoader classLoader, final ContextSelector selector, boolean logSeparationEnabled) {
    NewContextParameters parameters = resolveContextParameters(classLoader);
    if (parameters == null) {
      return getDefaultContext(selector, logSeparationEnabled);
    }

    MuleLoggerContext loggerContext =
        new MuleLoggerContext(parameters.contextName,
                              parameters.loggerConfigFile,
                              classLoader,
                              selector,
                              isStandalone(),
                              logSeparationEnabled);

    if ((classLoader instanceof ArtifactClassLoader) &&
        selector instanceof ArtifactAwareContextSelector) {
      final ArtifactClassLoader artifactClassLoader = (ArtifactClassLoader) classLoader;

      artifactClassLoader.addShutdownListener(
                                              () -> ((ArtifactAwareContextSelector) selector)
                                                  .destroyLoggersFor(resolveLoggerContextClassLoader(classLoader)));
    }

    // try {
    // SdkLoggerProvider sdkLoggerProvider =
    // SdkLoggerProvider.builder()
    // .addLogRecordProcessor(SimpleLogRecordProcessor.create(new LogRecordExporter() {
    //
    // @Override
    // public CompletableResultCode export(Collection<LogRecordData> collection) {
    // return CompletableResultCode.ofSuccess();
    // }
    //
    // @Override
    // public CompletableResultCode flush() {
    // return CompletableResultCode.ofSuccess();
    // }
    //
    // @Override
    // public CompletableResultCode shutdown() {
    // return CompletableResultCode.ofSuccess();
    // }
    // }))
    // .build();
    // GlobalLoggerProvider.set(sdkLoggerProvider);
    // } catch (IllegalStateException e) {
    // // Nothing to do
    // }

    return loggerContext;
  }

  private NewContextParameters resolveContextParameters(ClassLoader classLoader) {
    if (classLoader instanceof ArtifactClassLoader) {
      ArtifactClassLoader artifactClassLoader = (ArtifactClassLoader) classLoader;
      return new NewContextParameters(getArtifactLoggingConfig(artifactClassLoader), artifactClassLoader.getArtifactId());
    } else {
      // this is not an app init, use the top-level defaults
      if (getMuleConfDir() != null) {
        return new NewContextParameters(getLogConfig(new DirectoryResourceLocator(getMuleConfDir().getAbsolutePath())),
                                        classLoader.toString());
      }
    }

    return null;
  }

  private URI getArtifactLoggingConfig(ArtifactClassLoader muleCL) {
    URI appLogConfig;
    try {
      DeployableArtifactDescriptor appDescriptor = muleCL.getArtifactDescriptor();

      if (appDescriptor.getLogConfigFile() == null) {
        appLogConfig = getLogConfig(muleCL);
      } else if (!appDescriptor.getLogConfigFile().exists()) {
        LOGGER
            .warn("Configured 'log.configFile' in app descriptor points to a non-existant file. Using default configuration.");
        appLogConfig = getLogConfig(muleCL);
      } else {
        appLogConfig = appDescriptor.getLogConfigFile().toURI();
      }
    } catch (Exception e) {
      LOGGER.warn("{} while looking for 'log.configFile' entry in app descriptor: {}. Using default configuration.",
                  e.getClass().getName(), e.getMessage());
      appLogConfig = getLogConfig(muleCL);
    }

    if (appLogConfig != null && LOGGER.isInfoEnabled()) {
      LOGGER.info("Found logging config for application '{}' at '{}'", muleCL.getArtifactId(), appLogConfig);
    }

    return appLogConfig;
  }

  private LoggerContext getDefaultContext(ContextSelector selector, boolean logSeparationEnabled) {
    return new MuleLoggerContext("Default", selector, isStandalone(), logSeparationEnabled);
  }

  private boolean isStandalone() {
    return getMuleConfDir() != null;
  }

  /**
   * Checks if there's an app-specific logging configuration available, scope the lookup to this classloader only, as
   * getResource() will delegate to parents locate xml config first, fallback to properties format if not found
   *
   * @param localResourceLocator
   * @return
   */
  private URI getLogConfig(LocalResourceLocator localResourceLocator) {
    URI appLogConfig = null;

    if (appLogConfig == null) {
      URL localResource = localResourceLocator.findLocalResource("log4j2-test.xml");
      if (localResource != null) {
        try {
          appLogConfig = localResource.toURI();
        } catch (URISyntaxException e) {
          throw new MuleRuntimeException(createStaticMessage("Could not read log file " + appLogConfig), e);
        }
      }
    }

    URI fallbackLogConfig = new File(getMuleBase(), "conf/log4j2.xml").toURI();

    if (appLogConfig == null) {
      URL localResource = localResourceLocator.findLocalResource("log4j2.xml");
      try {
        if (localResource != null && !fallbackLogConfig.equals(localResource.toURI())) {
          appLogConfig = localResource.toURI();
        }
      } catch (URISyntaxException e) {
        throw new MuleRuntimeException(createStaticMessage("Could not read log file " + appLogConfig), e);
      }
    }

    if (appLogConfig == null) {
      if (getProperty(LOG4J_CONFIGURATION_FILE_PROPERTY) != null) {
        appLogConfig = new File(getProperty(LOG4J_CONFIGURATION_FILE_PROPERTY)).toURI();
      } else {
        appLogConfig = fallbackLogConfig;
      }
    }

    return appLogConfig;
  }

  private class NewContextParameters {

    private final URI loggerConfigFile;
    private final String contextName;

    private NewContextParameters(URI loggerConfigFile, String contextName) {
      this.loggerConfigFile = loggerConfigFile;
      this.contextName = contextName;
    }
  }
}
