/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.http2.api.message;

import org.mule.runtime.http2.api.message.content.Http2Content;

public interface Http2Message {

  /**
   * @return the content.
   */
  Http2Content getContent();
}
