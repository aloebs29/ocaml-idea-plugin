package com.reason.ide.format;

import com.reason.ide.*;
import org.junit.*;

public class OclLineIndentProviderTest extends ORBasePlatformTestCase {
    /* pressing enter leaves an empty line, which ocp-indent would read as a continuation of the phrase above */
    @Test
    public void testAnchorsAnEmptyLine() {
        assertEquals("open Base\nopen Stdio\n" + OclLineIndentProvider.INDENT_ANCHOR,
                OclLineIndentProvider.anchorLastLine("open Base\nopen Stdio\n"));
    }

    /* the platform re-indents an existing line too, and there the first token is the whole point */
    @Test
    public void testLeavesALineWithATokenAlone() {
        assertEquals("let f x =\n  | None", OclLineIndentProvider.anchorLastLine("let f x =\n  | None"));
    }

    @Test
    public void testAnchorsALineOfWhitespaceOnly() {
        assertEquals("let f x =\n    " + OclLineIndentProvider.INDENT_ANCHOR,
                OclLineIndentProvider.anchorLastLine("let f x =\n    "));
    }

    /* enter on the very first line, so there is nothing before the caret at all */
    @Test
    public void testAnchorsAnEmptyFile() {
        assertEquals(OclLineIndentProvider.INDENT_ANCHOR, OclLineIndentProvider.anchorLastLine(""));
    }
}
