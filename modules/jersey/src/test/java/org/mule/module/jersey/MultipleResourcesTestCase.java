/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.module.jersey;

import static org.junit.Assert.assertEquals;
import org.mule.api.MuleMessage;
import org.mule.api.client.MuleClient;
import org.mule.tck.junit4.FunctionalTestCase;
import org.mule.transport.http.HttpConnector;
import org.mule.transport.http.HttpConstants;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/**
 * Tests that the jersey:resources component can handle multiple components
 * correctly.
 */
public class MultipleResourcesTestCase extends FunctionalTestCase
{

    @Override
    protected String getConfigFile()
    {
        return "multiple-resources-conf-flow.xml";
    }

    @Test
    public void testParams() throws Exception
    {
        MuleClient client = muleContext.getClient();

        Map<String, Object> props = new HashMap<String, Object>();
        props.put(HttpConnector.HTTP_METHOD_PROPERTY, HttpConstants.METHOD_GET);
        MuleMessage result = client.send("http://localhost:63081/helloworld/sayHelloWithUri/Dan", "", props);
        assertEquals((Integer)200, result.getInboundProperty(HttpConnector.HTTP_STATUS_PROPERTY, 0));
        assertEquals("Hello Dan", result.getPayloadAsString());

        result = client.send("http://localhost:63081/anotherworld/sayHelloWithUri/Dan", "", props);
        assertEquals((Integer)200, result.getInboundProperty(HttpConnector.HTTP_STATUS_PROPERTY, 0));
        assertEquals("Bonjour Dan", result.getPayloadAsString());
    }
}
