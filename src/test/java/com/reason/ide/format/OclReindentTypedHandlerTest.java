package com.reason.ide.format;

import com.reason.ide.*;
import org.junit.*;

public class OclReindentTypedHandlerTest extends ORBasePlatformTestCase {
    @Test
    public void testDedentTokens() {
        assertTrue(OclReindentTypedHandler.isDedentToken("|"));
        assertTrue(OclReindentTypedHandler.isDedentToken("in"));
        assertTrue(OclReindentTypedHandler.isDedentToken("end"));
        assertTrue(OclReindentTypedHandler.isDedentToken(")"));
    }

    @Test
    public void testNotDedentTokens() {
        assertFalse(OclReindentTypedHandler.isDedentToken(""));
        assertFalse(OclReindentTypedHandler.isDedentToken("let"));
        assertFalse(OclReindentTypedHandler.isDedentToken("print_endline"));
    }

    /* `in` that turns out to be the start of an identifier has to put the line back where it was */
    @Test
    public void testTokenBeingTypedPast() {
        assertTrue(OclReindentTypedHandler.isDedentToken("int"));
        assertTrue(OclReindentTypedHandler.isDedentToken("ends"));
        assertFalse(OclReindentTypedHandler.isDedentToken("int_"));
    }
}
