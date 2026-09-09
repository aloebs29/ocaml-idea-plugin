package com.reason.ide.docs;

import com.intellij.lang.*;
import com.reason.ide.*;
import com.reason.ide.files.*;
import com.reason.lang.ocaml.*;
import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;

@RunWith(JUnit4.class)
public class ShowDocOCLTest extends ORBasePlatformTestCase {
    public static final Language LANG = OclLanguage.INSTANCE;

    @Test
    public void test_multiple_spaces_below() {
        configureCode("Doc.ml", "let x = 1;  \t\n  (** doc for x *)");
        FileBase a = configureCode("A.ml", "Doc.x<caret>");

        String doc = getDoc(a, LANG);
        assertEquals("<div class=\"definition\"><b>Doc</b><p><i>let x</i></p></div><div class=\"content\"><p>doc for x</p></div>", doc);
    }

    @Test
    public void test_type() {
        FileBase a = configureCode("A.ml", "(** my type *) type t<caret> = string");

        String doc = getDoc(a, LANG);
        assertEquals("<div class=\"definition\"><b>A</b><p><i>type t</i></p></div><div class=\"content\"><p>my type</p></div>", doc);
    }

    @Test
    public void test_GH_350() {
        configureCode("A.mli", "val compare : string -> string -> int\n(** compare doc *)");
        FileBase a = configureCode("A.ml", "let compare<caret> s1 s2 = 1");

        String doc = getDoc(a, LANG);
        assertEquals("<div class=\"definition\"><b>A</b><p><i>let compare</i></p></div><div class=\"content\"><p>compare doc</p></div>", doc);
    }

    @Test
    public void test_file_module() {
        configureCode("Doc.mli", "(** doc for the module *)\nval x : int");
        FileBase a = configureCode("A.ml", "let _ = Doc<caret>.x");

        String doc = getDoc(a, LANG);
        assertEquals("<div class=\"definition\"><b>module Doc</b></div><div class=\"content\"><p>doc for the module</p></div>", doc);
    }

    @Test
    public void test_module_alias_documented_by_its_target() {
        configureCode("Doc.mli", "(** doc for the module *)\nval x : int");
        configureCode("Wrapper.mli", "module Doc = Doc");
        FileBase a = configureCode("A.ml", "open Wrapper\nlet _ = Doc<caret>.x");

        String doc = getDoc(a, LANG);
        assertEquals("<div class=\"definition\"><b>module Doc</b></div><div class=\"content\"><p>doc for the module</p></div>", doc);
    }

    @Test
    public void test_module_alias_keeps_its_own_doc() {
        configureCode("Doc.mli", "(** doc for the module *)\nval x : int");
        configureCode("Wrapper.mli", "(** doc for the alias *)\nmodule Doc = Doc");
        FileBase a = configureCode("A.ml", "open Wrapper\nlet _ = Doc<caret>.x");

        String doc = getDoc(a, LANG);
        assertEquals("<div class=\"definition\"><b>Wrapper</b><p><i>innermodule Doc</i></p></div><div class=\"content\"><p>doc for the alias</p></div>", doc);
    }

    // A module aliased next to its definition wins over a same-named module from another directory,
    // the way Stdio.In_channel means stdio's in_channel.mli and not the stdlib one
    @Test
    public void test_module_alias_prefers_a_sibling_file() {
        myFixture.addFileToProject("stdlib/In_channel.mli", "(** stdlib in_channel *)\ntype t");
        myFixture.addFileToProject("stdio/In_channel.mli", "(** stdio in_channel *)\ntype t");
        myFixture.addFileToProject("stdio/Stdio.mli", "module In_channel = In_channel");
        FileBase a = configureCode("A.ml", "open Stdio\nlet _ = In_channel<caret>.t");

        String doc = getDoc(a, LANG);
        assertEquals("<div class=\"definition\"><b>module In_channel</b></div><div class=\"content\"><p>stdio in_channel</p></div>", doc);
    }
}
