/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.tracer.impl.span.factory;

import org.mule.runtime.tracer.impl.span.ExecutionSpan;

import org.vibur.objectpool.PoolObjectFactory;

public class PoolExecutionSpanFactory implements PoolObjectFactory<ExecutionSpan> {

  @Override
  public ExecutionSpan create() {
    return new ExecutionSpan();
  }

  @Override
  public boolean readyToTake(ExecutionSpan executionSpan) {
    return true;
  }

  @Override
  public boolean readyToRestore(ExecutionSpan executionSpan) {
    return true;
  }

  @Override
  public void destroy(ExecutionSpan executionSpan) {

  }
}
