/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.profiling.consumer.operations;

import static org.mule.runtime.api.profiling.type.RuntimeProfilingEventTypes.END_SPAN;

import static java.lang.System.currentTimeMillis;

import org.mule.runtime.api.profiling.ProfilingDataProducer;
import org.mule.runtime.api.profiling.ProfilingService;
import org.mule.runtime.api.profiling.type.context.ComponentProcessingStrategyProfilingEventContext;
import org.mule.runtime.api.profiling.type.context.SpanProfilingEventContext;;

/**
 *
 */
public class EndOperationSpanProfilingExecutionCommand implements
    ProfilingExecutionOperation<ComponentProcessingStrategyProfilingEventContext> {

  private final ProfilingDataProducer<SpanProfilingEventContext, ComponentProcessingStrategyProfilingEventContext> profilingDataProducer;

  public EndOperationSpanProfilingExecutionCommand(ProfilingService profilingService) {
    profilingDataProducer = profilingService.getProfilingDataProducer(END_SPAN);
  }

  @Override
  public void execute(ComponentProcessingStrategyProfilingEventContext eventContext) {
    profilingDataProducer.triggerProfilingEvent(eventContext, context -> new OperationExecutionEndEventContext(context));
  }

  private class OperationExecutionEndEventContext implements SpanProfilingEventContext {

    private final ComponentProcessingStrategyProfilingEventContext eventContext;
    private long triggerTimeStamp;

    public OperationExecutionEndEventContext(ComponentProcessingStrategyProfilingEventContext eventContext) {
      this.eventContext = eventContext;
      triggerTimeStamp = currentTimeMillis();
    }

    @Override
    public long getTriggerTimestamp() {
      return triggerTimeStamp;
    }
  }
}
