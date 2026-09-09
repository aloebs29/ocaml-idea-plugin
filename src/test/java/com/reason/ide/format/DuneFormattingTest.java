package com.reason.ide.format;

import com.intellij.openapi.command.*;
import com.intellij.psi.codeStyle.*;
import com.reason.ide.*;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;

@RunWith(JUnit4.class)
public class DuneFormattingTest extends ORBasePlatformTestCase {
    @Test
    public void test_stanza_from_the_dune_docs() {
        doReformat("""
                        (lang dune 3.17)
                        (name test)

                        (package
                        (name test)
                        (depends
                        (ocaml (>= 4.14))
                        fmt))
                        """,
                """
                        (lang dune 3.17)
                        (name test)

                        (package
                          (name test)
                          (depends
                            (ocaml (>= 4.14))
                            fmt))
                        """);
    }

    @Test
    public void test_over_indented_lines_are_brought_back() {
        doReformat("""
                        (executable
                                (name main)
                                    (libraries fmt))
                        """,
                """
                        (executable
                          (name main)
                          (libraries fmt))
                        """);
    }

    @Test
    public void test_closing_parens_line_up_with_the_scope_they_close() {
        doReformat("""
                        (library
                        (name lib)
                        (modules
                        a
                        b
                        )
                        )
                        """,
                """
                        (library
                          (name lib)
                          (modules
                            a
                            b
                          )
                        )
                        """);
    }

    @Test
    public void test_line_breaks_are_never_moved() {
        // an already correct file is left alone, including the single-line stanzas
        String code = """
                (rule
                  (targets a.ml)
                  (deps b.ml)
                  (action (run ./gen.exe %{deps})))
                """;
        doReformat(code, code);
    }

    @Test
    public void test_comments_follow_the_enclosing_scope() {
        doReformat("""
                        ; a top level comment
                        (package
                        ; a nested one
                        (name test))
                        """,
                """
                        ; a top level comment
                        (package
                          ; a nested one
                          (name test))
                        """);
    }

    @Test
    public void test_enter_indents_into_an_unclosed_stanza() {
        // the parens are unbalanced while typing, which is exactly when indentation matters
        doEnter("(package<caret>", "(package\n  ");
    }

    @Test
    public void test_enter_indents_into_a_nested_unclosed_scope() {
        doEnter("(package\n  (depends<caret>", "(package\n  (depends\n    ");
    }

    @Test
    public void test_enter_steps_off_the_line_of_the_unclosed_paren() {
        // the stanza is not at column zero, and the new line still has to follow it rather than the nesting depth
        doEnter("    (package<caret>", "    (package\n      ");
    }

    @Test
    public void test_enter_after_a_closed_stanza_goes_back_to_column_zero() {
        doEnter("(name test)<caret>", "(name test)\n");
    }

    private void doReformat(String before, String after) {
        myFixture.configureByText("dune", before);
        WriteCommandAction.runWriteCommandAction(getProject(),
                () -> {
                    CodeStyleManager.getInstance(getProject()).reformat(myFixture.getFile());
                });
        assertEquals(after, myFixture.getEditor().getDocument().getText());
    }

    private void doEnter(String before, String after) {
        myFixture.configureByText("dune", before);
        myFixture.type('\n');
        assertEquals(after, myFixture.getEditor().getDocument().getText());
    }
}
