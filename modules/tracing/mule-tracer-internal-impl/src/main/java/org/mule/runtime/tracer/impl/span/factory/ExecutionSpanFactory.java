/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.tracer.impl.span.factory;

import org.mule.runtime.tracer.api.context.SpanContext;
import org.mule.runtime.tracer.api.sniffer.SpanSnifferManager;
import org.mule.runtime.tracer.api.span.InternalSpan;
import org.mule.runtime.tracer.api.span.info.InitialSpanInfo;
import org.mule.runtime.tracer.exporter.api.SpanExporterFactory;
import org.mule.runtime.tracer.impl.clock.Clock;
import org.mule.runtime.tracer.impl.span.ExecutionSpan;

import javax.inject.Inject;

import org.vibur.objectpool.ConcurrentPool;
import org.vibur.objectpool.PoolService;
import org.vibur.objectpool.util.ConcurrentLinkedQueueCollection;

public class ExecutionSpanFactory implements EventSpanFactory {

  private static final int FOR_TNS_XSTL_TRANSFORMER_POOL_MAX_SIZE = 1000;
  private static final PoolService<ExecutionSpan> FOR_TNS_XSTL_TRANSFORMER_POOL =
      new ConcurrentPool<>(new ConcurrentLinkedQueueCollection<>(), new PoolExecutionSpanFactory(),
                           FOR_TNS_XSTL_TRANSFORMER_POOL_MAX_SIZE,
                           FOR_TNS_XSTL_TRANSFORMER_POOL_MAX_SIZE, false);

  @Inject
  private SpanExporterFactory spanExporterFactory;

  @Override
  public InternalSpan getSpan(SpanContext spanContext,
                              InitialSpanInfo initialSpanInfo) {
    ExecutionSpan executionSpan = FOR_TNS_XSTL_TRANSFORMER_POOL.take();
    if (executionSpan == null) {
      executionSpan = new ExecutionSpan();
    } else {
      executionSpan.setExecutionSpanFactory(this);
    }
    setSpanData(spanContext, initialSpanInfo, executionSpan);
    return executionSpan;
  }

  private void setSpanData(SpanContext spanContext, InitialSpanInfo initialSpanInfo, ExecutionSpan executionSpan) {
    executionSpan.setInitialSpanInfo(initialSpanInfo);
    executionSpan.setStartTime(Clock.getDefault().now());
    executionSpan.setParent(spanContext.getSpan().orElse(null));
    executionSpan.setSpanExporter(spanExporterFactory.getSpanExporter(executionSpan, initialSpanInfo));
  }

  public void returnSpanToPool(ExecutionSpan executionSpan) {
    FOR_TNS_XSTL_TRANSFORMER_POOL.restore(executionSpan);
  }

  @Override
  public SpanSnifferManager getSpanSnifferManager() {
    return spanExporterFactory.getSpanExporterManager();
  }
}
