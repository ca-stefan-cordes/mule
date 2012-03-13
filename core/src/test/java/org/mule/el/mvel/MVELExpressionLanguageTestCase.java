/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.el.mvel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.mule.api.MuleEvent;
import org.mule.api.MuleMessage;
import org.mule.api.construct.FlowConstruct;
import org.mule.api.expression.InvalidExpressionException;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.config.MuleManifest;
import org.mule.tck.junit4.AbstractMuleContextTestCase;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mockito;

@RunWith(Parameterized.class)
public class MVELExpressionLanguageTestCase extends AbstractMuleContextTestCase
{

    protected Variant variant;
    protected MVELExpressionLanguage mvel;

    public MVELExpressionLanguageTestCase(Variant variant)
    {
        this.variant = variant;
    }

    @Before
    public void setupMVEL() throws InitialisationException
    {
        mvel = new MVELExpressionLanguage(muleContext);
        mvel.initialise();
    }

    @Test
    public void testEvaluateString()
    {
        // Literals
        assertEquals("hi", evaluate("'hi'"));
        assertEquals(4, evaluate("2*2"));

        // Static context
        assertEquals(Calendar.getInstance().getTimeZone(), evaluate("server.timeZone"));
        assertEquals(MuleManifest.getProductVersion(), evaluate("mule.version"));
        assertEquals(muleContext.getConfiguration().getId(), evaluate("app.name"));
    }

    @Test
    public void testEvaluateStringMapOfStringObject()
    {
        // Literals
        assertEquals("hi", evaluate("'hi'", Collections.<String, Object> emptyMap()));
        assertEquals(4, evaluate("2*2", Collections.<String, Object> emptyMap()));

        // Static context
        assertEquals(Calendar.getInstance().getTimeZone(),
            evaluate("server.timeZone", Collections.<String, Object> emptyMap()));
        assertEquals(MuleManifest.getProductVersion(),
            evaluate("mule.version", Collections.<String, Object> emptyMap()));
        assertEquals(muleContext.getConfiguration().getId(),
            evaluate("app.name", Collections.<String, Object> emptyMap()));

        // Custom variables (via method param)
        assertEquals(1, evaluate("foo", Collections.<String, Object> singletonMap("foo", 1)));
        assertEquals("bar", evaluate("foo", Collections.<String, Object> singletonMap("foo", "bar")));
    }

    @Test
    public void testEvaluateStringMuleEvent()
    {
        MuleEvent event = createMockEvent();

        // // Literals
        // assertEquals("hi", evaluate("'hi'", event));
        // assertEquals(4, evaluate("2*2", event));
        //
        // // Static context
        // assertEquals(Calendar.getInstance().getTimeZone(), evaluate("server.timeZone", event));
        // assertEquals(MuleManifest.getProductVersion(), evaluate("mule.version", event));
        // assertEquals(muleContext.getConfiguration().getId(), evaluate("app.name", event));

        // Event context
        assertEquals("myFlow", evaluate("flow.name", event));
        assertEquals("foo", evaluate("message.payload", event));

    }

    @Test
    public void testEvaluateStringMuleEventMapOfStringObject()
    {
        MuleEvent event = createMockEvent();

        // Literals
        assertEquals("hi", evaluate("'hi'", event));
        assertEquals(4, evaluate("2*2", event));

        // Static context
        assertEquals(Calendar.getInstance().getTimeZone(), evaluate("server.timeZone", event));
        assertEquals(MuleManifest.getProductVersion(), evaluate("mule.version", event));
        assertEquals(muleContext.getConfiguration().getId(), evaluate("app.name", event));

        // Event context
        assertEquals("myFlow", evaluate("flow.name", event));
        assertEquals("foo", evaluate("message.payload", event));

        // Custom variables (via method param)
        assertEquals(1, evaluate("foo", Collections.<String, Object> singletonMap("foo", 1)));
        assertEquals("bar", evaluate("foo", Collections.<String, Object> singletonMap("foo", "bar")));
    }

    @Test
    public void testEvaluateStringMuleMessage()
    {
        MuleMessage message = createMockMessage();

        // Literals
        assertEquals("hi", evaluate("'hi'", message));
        assertEquals(4, evaluate("2*2", message));

        // Static context
        assertEquals(Calendar.getInstance().getTimeZone(), evaluate("server.timeZone", message));
        assertEquals(MuleManifest.getProductVersion(), evaluate("mule.version", message));
        assertEquals(muleContext.getConfiguration().getId(), evaluate("app.name", message));

        // Event context
        assertEquals("foo", evaluate("message.payload", message));
    }

    @Test
    public void testEvaluateStringMuleMessageMapOfStringObject()
    {
        MuleMessage message = createMockMessage();

        // Literals
        assertEquals("hi", evaluate("'hi'", message));
        assertEquals(4, evaluate("2*2", message));

        // Static context
        assertEquals(Calendar.getInstance().getTimeZone(), evaluate("server.timeZone", message));
        assertEquals(MuleManifest.getProductVersion(), evaluate("mule.version", message));
        assertEquals(muleContext.getConfiguration().getId(), evaluate("app.name", message));

        // Event context
        assertEquals("foo", evaluate("message.payload", message));

        // Custom variables (via method param)
        assertEquals(1, evaluate("foo", Collections.<String, Object> singletonMap("foo", 1)));
        assertEquals("bar", evaluate("foo", Collections.<String, Object> singletonMap("foo", "bar")));
    }

    @Test
    public void testIsValid()
    {
        assertTrue(mvel.isValid("2*2"));
    }

    @Test
    public void testIsValidInvalid()
    {
        assertFalse(mvel.isValid("2*'2"));
    }

    @Test
    public void testValidate()
    {
        validate("2*2");
    }

    @Test(expected = InvalidExpressionException.class)
    public void testValidateInvalid()
    {
        validate("2*'2");
    }

    protected Object evaluate(String expression)
    {
        if (variant.equals(Variant.EXPRESSION_WITH_DELIMITER))
        {
            return mvel.evaluate("#[" + expression + "]");
        }
        else
        {
            return mvel.evaluate(expression);
        }
    }

    protected Object evaluate(String expression, Map<String, Object> vars)
    {
        if (variant.equals(Variant.EXPRESSION_WITH_DELIMITER))
        {
            return mvel.evaluate("#[" + expression + "]", vars);
        }
        else
        {
            return mvel.evaluate(expression, vars);
        }
    }

    protected Object evaluate(String expression, MuleMessage message)
    {
        if (variant.equals(Variant.EXPRESSION_WITH_DELIMITER))
        {
            return mvel.evaluate("#[" + expression + "]", message);
        }
        else
        {
            return mvel.evaluate(expression, message);
        }
    }

    protected Object evaluate(String expression, MuleEvent event)
    {
        if (variant.equals(Variant.EXPRESSION_WITH_DELIMITER))
        {
            return mvel.evaluate("#[" + expression + "]", event);
        }
        else
        {
            return mvel.evaluate(expression, event);
        }
    }

    protected void validate(String expression)
    {
        if (variant.equals(Variant.EXPRESSION_WITH_DELIMITER))
        {
            mvel.validate("#[" + expression + "]");
        }
        else
        {
            mvel.validate(expression);
        }
    }

    protected MuleEvent createMockEvent()
    {
        MuleEvent event = mock(MuleEvent.class);
        FlowConstruct flowConstruct = mock(FlowConstruct.class);
        when(flowConstruct.getName()).thenReturn("myFlow");
        MuleMessage message = createMockMessage();
        Mockito.when(event.getFlowConstruct()).thenReturn(flowConstruct);
        Mockito.when(event.getMessage()).thenReturn(message);
        return event;
    }

    protected MuleMessage createMockMessage()
    {
        MuleMessage message = mock(MuleMessage.class);
        Mockito.when(message.getPayload()).thenReturn("foo");
        return message;
    }

    public static enum Variant
    {
        EXPRESSION_WITH_DELIMITER, EXPRESSION_STRAIGHT_UP
    }

    @Parameters
    public static List<Object[]> parameters()
    {
        return Arrays.asList(new Object[][]{{Variant.EXPRESSION_WITH_DELIMITER},
            {Variant.EXPRESSION_STRAIGHT_UP}});
    }

}
