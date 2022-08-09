/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.runtime.core.internal.profiling.tracing.event.span;

import org.mule.runtime.core.privileged.profiling.tracing.ChildSpanInfo;

/**
 * A {@link ChildSpanInfo} that allows to customize the default {@link DefaultChildSpanInfo#getChildSpanSuggestedName()}.
 *
 * @since 4.5.0
 */
public class DefaultChildSpanInfo implements ChildSpanInfo {

  private String childSpanSuggestedName;

  public DefaultChildSpanInfo(String childSpanSuggestedName) {
    this.childSpanSuggestedName = childSpanSuggestedName;
  }

  @Override
  public String getChildSpanSuggestedName() {
    return childSpanSuggestedName;
  }
}
