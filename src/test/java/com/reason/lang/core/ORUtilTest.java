package com.reason.lang.core;

import com.google.common.collect.*;
import com.intellij.psi.util.*;
import com.reason.ide.*;
import com.reason.ide.files.*;
import com.reason.lang.core.psi.*;
import jpsplugin.com.reason.*;
import org.junit.*;

@SuppressWarnings("ConstantConditions")
public class ORUtilTest extends ORBasePlatformTestCase {
    @Test
    public void testModuleNameToFileNameWhenEmpty() {
        assertEquals("", ORUtil.moduleNameToFileName(""));
    }

    @Test
    public void testModuleNameToFileName() {
        assertEquals("testLower", ORUtil.moduleNameToFileName("TestLower"));
    }

    @Test
    public void testFileNameToModuleNameWhenEmpty() {
        assertEquals("", ORUtil.fileNameToModuleName(""));
        assertEquals("", ORUtil.fileNameToModuleName(".ml"));
    }

    @Test
    public void testFileNameToModuleName() {
        assertEquals("Lower", ORUtil.fileNameToModuleName("lower.ml"));
        assertEquals("Upper", ORUtil.fileNameToModuleName("Upper.ml"));
    }

    @Test
    public void test_letQualifiedPath() {
        FileBase f = configureCode("A.ml", "let make () = let x = 1 in ()");
        RPsiLet e = ImmutableList.copyOf(PsiTreeUtil.findChildrenOfType(f, RPsiLet.class)).get(1);

        String qPath = Joiner.join(".", ORUtil.getQualifiedPath(e));

        assertEquals("A.make", qPath);
    }

    @Test
    public void test_letDestructuredQualifiedPath() {
        FileBase f = configureCode("A.ml", "module M = struct let make () = let (x, y) = other in () end");
        RPsiLet letExpression = ImmutableList.copyOf(PsiTreeUtil.findChildrenOfType(f, RPsiLet.class)).get(1);

        String qualifiedPath = Joiner.join(".", ORUtil.getQualifiedPath(letExpression));

        assertEquals("A.M.make", qualifiedPath);
    }

    @Test
    public void test_in_interface_file() {
        FileBase intf = configureCode("A.mli", "module M : sig val x : int end");
        FileBase impl = configureCode("A.ml", "module M = struct let x = 1 end");

        assertTrue(ORUtil.inInterface(PsiTreeUtil.findChildOfType(intf, RPsiVal.class)));
        assertFalse(ORUtil.inInterface(PsiTreeUtil.findChildOfType(impl, RPsiLet.class)));
    }

    @Test
    public void test_in_interface_module_type() {
        FileBase f = configureCode("A.ml", "module type M = sig val x : int end");

        assertTrue(ORUtil.inInterface(PsiTreeUtil.findChildOfType(f, RPsiVal.class)));
    }

    @Test
    public void test_in_interface_anonymous_module_type() {
        FileBase f = configureCode("A.ml", "module M : sig val x : int end = struct let x = 1 end");

        assertTrue(ORUtil.inInterface(PsiTreeUtil.findChildOfType(f, RPsiVal.class)));
        assertFalse(ORUtil.inInterface(PsiTreeUtil.findChildOfType(f, RPsiLet.class)));
    }
}
