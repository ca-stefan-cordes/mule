/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.tracer.impl.context.extractor.w3c;

import static org.mule.runtime.tracer.impl.context.extractor.w3c.TraceStateContextFieldExtractor.TRACESTATE;
import static org.mule.test.allure.AllureConstants.EventContextFeature.EVENT_CONTEXT;
import static org.mule.test.allure.AllureConstants.EventContextFeature.EventContextStory.DISTRIBUTED_TRACE_CONTEXT;

import org.mule.runtime.tracer.impl.context.extractor.TraceContextFieldExtractor;

import io.qameta.allure.Feature;
import io.qameta.allure.Story;

@Feature(EVENT_CONTEXT)
@Story(DISTRIBUTED_TRACE_CONTEXT)
public class TraceStateContextFieldExtractorTestCase extends AbstractW3CTraceContextExtractorTestCase {

  @Override
  public String getTraceField() {
    return TRACESTATE;
  }

  @Override
  public TraceContextFieldExtractor getTraceContextFieldExtractor() {
    return new TraceStateContextFieldExtractor();
  }
}
