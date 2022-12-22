/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.tracer.impl.span;

import org.mule.runtime.api.profiling.tracing.Span;
import org.mule.runtime.api.profiling.tracing.SpanDuration;
import org.mule.runtime.api.profiling.tracing.SpanError;
import org.mule.runtime.api.profiling.tracing.SpanIdentifier;
import org.mule.runtime.tracer.api.span.InternalSpan;
import org.mule.runtime.tracer.api.span.SpanAttribute;
import org.mule.runtime.tracer.api.span.error.InternalSpanError;
import org.mule.runtime.tracer.api.span.exporter.SpanExporter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

public class RootInternalSpan implements InternalSpan {

  public static final String ROOT_SPAN = "root";

  private String name = ROOT_SPAN;
  private final List<SpanAttribute> attributes = new ArrayList<>();

  @Override
  public Span getParent() {
    return null;
  }

  @Override
  public SpanIdentifier getIdentifier() {
    return null;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public SpanDuration getDuration() {
    return null;
  }

  @Override
  public List<SpanError> getErrors() {
    return emptyList();
  }

  @Override
  public boolean hasErrors() {
    return false;
  }

  @Override
  public void end() {
    // Nothing to do.
  }

  @Override
  public void addError(InternalSpanError error) {
    // Nothing to do.
  }

  @Override
  public void updateName(String name) {
    this.name = name;
  }

  @Override
  public SpanExporter getSpanExporter() {
    return null;
  }

  @Override
  public List<SpanAttribute> getAttributes() {
    return emptyList();
  }

  @Override
  public Map<String, String> serializeAsMap() {
    return emptyMap();
  }


  @Override
  public void updateChildSpanExporter(InternalSpan internalSpan) {
    if (!ROOT_SPAN.equals(name)) {
      internalSpan.updateRootName(name);
      attributes.forEach(internalSpan::setRootAttribute);
    }
  }

  @Override
  public void addAttribute(String key, String value) {
    attributes.add(new SpanAttribute(key, value));
  }
}
