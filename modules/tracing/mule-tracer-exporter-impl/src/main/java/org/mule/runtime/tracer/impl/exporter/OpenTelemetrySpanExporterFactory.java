/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.tracer.impl.exporter;

import static org.mule.runtime.tracer.impl.exporter.OpenTelemetrySpanExporter.builder;
import static org.mule.runtime.tracer.impl.exporter.optel.resources.OpenTelemetryResources.getNewExportedSpanCapturer;
import static org.mule.runtime.tracer.impl.exporter.optel.resources.OpenTelemetryResources.resolveExporterProcessor;

import org.mule.runtime.core.api.MuleContext;
import org.mule.runtime.tracer.api.sniffer.ExportedSpanSniffer;
import org.mule.runtime.tracer.api.sniffer.SpanSnifferManager;
import org.mule.runtime.tracer.api.span.InternalSpan;
import org.mule.runtime.tracer.api.span.exporter.SpanExporter;
import org.mule.runtime.tracer.api.span.info.InitialSpanInfo;
import org.mule.runtime.tracer.exporter.api.SpanExporterFactory;
import org.mule.runtime.tracer.exporter.api.config.SpanExporterConfiguration;

import javax.inject.Inject;

import io.opentelemetry.sdk.trace.SpanProcessor;

/**
 * An implementation of {@link SpanExporterFactory} that creates {@link SpanExporter} that exports the internal spans as
 * {@link OpenTelemetrySpanExporter}
 *
 * @since 4.5.0
 */
public class OpenTelemetrySpanExporterFactory implements SpanExporterFactory {

  @Inject
  SpanExporterConfiguration configuration;

  @Inject
  MuleContext muleContext;

  private SpanProcessor spanProcessor;

  @Override
  public SpanExporter getSpanExporter(InternalSpan internalSpan, InitialSpanInfo initialExportInfo) {
    return builder()
        .withStartSpanInfo(initialExportInfo)
        .withArtifactId(muleContext.getConfiguration().getId())
        .withArtifactType(muleContext.getArtifactType().getAsString())
        .withSpanProcessor(getSpanProcessor())
        .withInternalSpan(internalSpan)
        .build();
  }

  private SpanProcessor getSpanProcessor() {
    if (spanProcessor == null) {
      spanProcessor = resolveExporterProcessor(configuration);
    }
    return spanProcessor;
  }

  @Override
  public SpanSnifferManager getSpanExporterManager() {
    return new OpenTelemetrySpanExporterManager();
  }

  private static class OpenTelemetrySpanExporterManager implements SpanSnifferManager {

    @Override
    public ExportedSpanSniffer getExportedSpanSniffer() {
      return getNewExportedSpanCapturer();
    }
  }
}
