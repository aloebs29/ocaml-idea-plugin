package com.reason.comp.ocaml;

import com.intellij.execution.process.*;
import com.reason.ide.*;
import org.junit.*;

import java.util.*;

public class OpamProcessTest extends ORBasePlatformTestCase {
    private final List<OpamProcess.OpamSwitch> myResult = new ArrayList<>();
    private final OpamProcess.ListProcessListener myListener = new OpamProcess.ListProcessListener(myResult::addAll);

    /*
    Windows:
    #  switch   compiler                                                                        description
    →  4.08.0                                                                                   ocaml-base-compiler = 4.08.0 | ocaml-system = 4.08.0
       default  arch-x86_64.1,ocaml-base-compiler.5.2.0,ocaml-options-vanilla.1,system-mingw.1  ocaml >= 4.05.0

    [WARNING] The environment is not in sync with the current switch.
    */
    @Test
    public void testListSwitches() {
        send("#  switch   compiler                                                                        description");
        send("→  4.08.0                                                                                   ocaml-base-compiler = 4.08.0 | ocaml-system = 4.08.0");
        send("   default  arch-x86_64.1,ocaml-base-compiler.5.2.0,ocaml-options-vanilla.1,system-mingw.1  ocaml >= 4.05.0");
        send("");
        send("[WARNING] The environment is not in sync with the current switch.");
        myListener.processTerminated(new ProcessEvent(OpamProcess.NULL_HANDLER));

        assertSize(2, myResult);
        assertEquals(myResult.get(0).name(), "4.08.0");
        assertTrue(myResult.get(0).isSelected());
        assertEquals(myResult.get(1).name(), "default");
        assertFalse(myResult.get(1).isSelected());
    }

    /* opam 2.5 on native windows uses an ascii `->` marker, and lines keep their separator */
    @Test
    public void testListSwitchesAsciiMarker() {
        send("#   switch   compiler                                                                        description\n");
        send("->  5.5.1    arch-x86_64.1,ocaml-base-compiler.5.5.1,ocaml-options-vanilla.1,system-mingw.1  ocaml = 5.5.1\n");
        send("    default  arch-x86_64.1,ocaml-base-compiler.5.2.1,ocaml-options-vanilla.1,system-mingw.1  ocaml-base-compiler\n");
        myListener.processTerminated(new ProcessEvent(OpamProcess.NULL_HANDLER));

        assertSize(2, myResult);
        assertEquals("5.5.1", myResult.get(0).name());
        assertTrue(myResult.get(0).isSelected());
        assertEquals("default", myResult.get(1).name());
        assertFalse(myResult.get(1).isSelected());
    }

    /* a switch without any description must not be dropped */
    @Test
    public void testListSwitchesWithoutDescription() {
        send("#  switch  compiler                  description");
        send("   5.1.0   ocaml-base-compiler.5.1.0");
        myListener.processTerminated(new ProcessEvent(OpamProcess.NULL_HANDLER));

        assertSize(1, myResult);
        assertEquals("5.1.0", myResult.get(0).name());
        assertFalse(myResult.get(0).isSelected());
    }

    /* the continuation lines of a trailing warning are not switches */
    @Test
    public void testListSwitchesIgnoresFooter() {
        send("#   switch  compiler                  description");
        send("->  5.5.1   ocaml-base-compiler.5.5.1  ocaml = 5.5.1");
        send("");
        send("[WARNING] The environment is not in sync with the current switch.");
        send("          You should run: eval $(opam env)");
        myListener.processTerminated(new ProcessEvent(OpamProcess.NULL_HANDLER));

        assertSize(1, myResult);
        assertEquals("5.5.1", myResult.get(0).name());
    }

    private void send(String text) {
        myListener.onTextAvailable(new ProcessEvent(OpamProcess.NULL_HANDLER, text), ProcessOutputTypes.STDOUT);
    }
}
