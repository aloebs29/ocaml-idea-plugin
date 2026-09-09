package com.reason.lang.ocaml;

import com.intellij.psi.*;
import com.intellij.psi.util.*;
import com.reason.lang.core.*;
import com.reason.lang.core.psi.*;
import com.reason.lang.core.psi.impl.*;
import org.junit.*;

@SuppressWarnings("ConstantConditions")
public class IncludeParsingTest extends OclParsingTestCase {
    @Test
    public void test_one() {
        RPsiInclude e = firstOfType(parseCode("include Belt"), RPsiInclude.class);

        assertNull(PsiTreeUtil.findChildOfType(e, RPsiFunctorCall.class));
        assertEquals("Belt", e.getIncludePath());
        assertEquals("Belt", ORUtil.findImmediateLastChildOfType(e, myTypes.A_MODULE_NAME).getText());
    }

    @Test
    public void test_path() {
        RPsiInclude e = firstOfType(parseCode("include Belt.Array"), RPsiInclude.class);

        assertEquals("Belt.Array", e.getIncludePath());
        assertEquals("Array", ORUtil.findImmediateLastChildOfType(e, myTypes.A_MODULE_NAME).getText());
    }

    @Test
    public void test_functor() {
        RPsiInclude e = firstOfType(parseCode("include Make(struct type t end)"), RPsiInclude.class);

        assertTrue(e.useFunctor());
        RPsiFunctorCall c = PsiTreeUtil.findChildOfType(e, RPsiFunctorCall.class);
        assertEquals("Make", c.getName());
        assertEquals(myTypes.A_MODULE_NAME, c.getReferenceIdentifier().getNode().getElementType());
        assertEquals("Make", e.getIncludePath());
    }

    @Test
    public void test_functor_path() {
        RPsiInclude e = firstOfType(parseCode("include A.Make(struct type t end)"), RPsiInclude.class);

        assertTrue(e.useFunctor());
        assertEquals("A.Make", e.getIncludePath());
    }

    @Test
    public void test_with_type() {
        RPsiInclude e = firstOfType(parseCode("include S with type t = Tok.t"), RPsiInclude.class);

        assertEquals("S", e.getIncludePath());
        assertEquals("include S with type t = Tok.t", e.getText());
    }

    @Test
    public void test_with_path_type() {
        RPsiInclude e = firstOfType(parseCode("include Grammar.S with type te = Tok.t and type 'c pattern = 'c Tok.p\ntype t"), RPsiInclude.class); // Coq: pcoq.ml

        assertEquals("Grammar.S", e.getIncludePath());
        assertEquals("include Grammar.S with type te = Tok.t and type 'c pattern = 'c Tok.p", e.getText());
    }

    @Test
    public void test_GH_497_include_inlined_module() {
        RPsiInclude e = firstOfType(parseCode("include module type of struct include Env.Path end"), RPsiInclude.class); // Coq: boot/path.mli

        assertEmpty(e.getIncludePath());
        assertNotNull(PsiTreeUtil.findChildOfType(e, RPsiModule.class));
    }

    @Test
    public void test_with_module_substitution() {
        PsiFile f = parseCode("include S with module M := N\nlet x = 1");
        RPsiInclude e = firstOfType(f, RPsiInclude.class);

        assertEquals("S", e.getIncludePath());
        assertEquals("include S with module M := N", e.getText());
        // A constraint is not a definition: marking it as one would index M as a module of the file
        assertNull(PsiTreeUtil.findChildOfType(e, RPsiInnerModule.class));
        assertSize(1, PsiTreeUtil.findChildrenOfType(f, RPsiLet.class));
    }

    @Test
    public void test_with_module_constraint() {
        RPsiInclude e = firstOfType(parseCode("include S with module M = N"), RPsiInclude.class);

        assertEquals("S", e.getIncludePath());
        assertEquals("include S with module M = N", e.getText());
        assertNull(PsiTreeUtil.findChildOfType(e, RPsiInnerModule.class));
    }

    @Test
    public void test_with_chained_module_substitutions() {
        // base.ml shadows every stdlib module this way
        RPsiInclude e = firstOfType(parseCode("""
                include (
                  Shadow_stdlib :
                    module type of struct
                      include Shadow_stdlib
                    end
                    with module Array := Shadow_stdlib.Array
                    with module In_channel := Shadow_stdlib.In_channel
                    with type 'a ref := 'a ref)
                """), RPsiInclude.class);

        RPsiConstraints constraints = PsiTreeUtil.findChildOfType(e, RPsiConstraints.class);
        // the three constraints are siblings, not nested into each other
        assertSize(3, PsiTreeUtil.getChildrenOfTypeAsList(constraints, RPsiTypeConstraint.class));
        // only the `module type of struct ... end`, none of the constraints
        assertSize(1, PsiTreeUtil.findChildrenOfType(e, RPsiInnerModule.class));
    }
}
