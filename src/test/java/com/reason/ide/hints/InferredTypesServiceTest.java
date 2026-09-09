package com.reason.ide.hints;

import com.intellij.psi.*;
import com.reason.ide.*;
import com.reason.ide.files.*;
import org.jetbrains.annotations.*;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;

/**
 * The name of the cmt a source file compiles to. Getting it wrong means no inferred type is ever found,
 * which is silent: the quick doc just shows the name of the element with no type next to it.
 */
@RunWith(JUnit4.class)
public class InferredTypesServiceTest extends ORBasePlatformTestCase {
    @Test
    public void test_namespace_of_an_executable() {
        // `bin/main.ml` is compiled to `dune__exe__Main.cmt`, whatever the executable is called
        assertEquals("dune__exe__", namespaceOf("(executable\n (public_name hello-ocaml)\n (name main)\n (libraries hello_ocaml))"));
    }

    @Test
    public void test_namespace_of_several_executables() {
        assertEquals("dune__exe__", namespaceOf("(executables (names main other))"));
    }

    @Test
    public void test_namespace_of_a_library() {
        assertEquals("hello_ocaml__", namespaceOf("(library (name hello_ocaml))"));
    }

    @Test
    public void test_namespace_of_an_unwrapped_library() {
        assertEquals("", namespaceOf("(library (name hello_ocaml) (wrapped false))"));
    }

    @Test
    public void test_namespace_of_a_test() {
        assertEquals("", namespaceOf("(rule (alias runtest) (action (run ./test.exe)))"));
    }

    private @NotNull String namespaceOf(@NotNull String duneContent) {
        PsiFile dune = myFixture.configureByText("dune", duneContent);
        return InferredTypesService.findDuneNamespace((DuneFile) dune);
    }
}
