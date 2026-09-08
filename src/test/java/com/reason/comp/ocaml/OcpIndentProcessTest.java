package com.reason.comp.ocaml;

import com.reason.ide.*;
import org.junit.*;

public class OcpIndentProcessTest extends ORBasePlatformTestCase {
    @Test
    public void testIndentedLine() {
        assertEquals(Integer.valueOf(2), OcpIndentProcess.parseNumericOutput("2\n"));
    }

    @Test
    public void testTopLevel() {
        assertEquals(Integer.valueOf(0), OcpIndentProcess.parseNumericOutput("0\n"));
    }

    /* windows keeps the separator */
    @Test
    public void testCarriageReturn() {
        assertEquals(Integer.valueOf(4), OcpIndentProcess.parseNumericOutput("4\r\n"));
    }

    @Test
    public void testNoTrailingSeparator() {
        assertEquals(Integer.valueOf(6), OcpIndentProcess.parseNumericOutput("6"));
    }

    /* asking for a line past the end of the text prints nothing */
    @Test
    public void testEmptyOutput() {
        assertNull(OcpIndentProcess.parseNumericOutput(""));
        assertNull(OcpIndentProcess.parseNumericOutput("\n"));
    }

    @Test
    public void testNotANumber() {
        assertNull(OcpIndentProcess.parseNumericOutput("ocp-indent: unknown option\n"));
    }
}
