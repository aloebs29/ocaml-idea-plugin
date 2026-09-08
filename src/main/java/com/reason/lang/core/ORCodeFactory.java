package com.reason.lang.core;

import com.intellij.lang.*;
import com.intellij.openapi.project.*;
import com.intellij.psi.*;
import com.reason.ide.files.*;
import com.reason.lang.core.psi.*;
import com.reason.lang.core.psi.impl.*;
import com.reason.lang.ocaml.*;
import org.jetbrains.annotations.*;

/**
 * Builds throwaway PSI to harvest a single identifier node from, used when renaming.
 * <p>
 * The snippets are parsed as OCaml. Only the symbol node is taken out of the resulting file, so the
 * language of the element being renamed does not have to match - but the language does have to be
 * registered, which is why this can no longer parse Reason.
 */
public class ORCodeFactory {
    private ORCodeFactory() {
    }

    @Nullable
    public static RPsiUpperSymbol createModuleName(@NotNull Project project, @NotNull String name) {
        FileBase file = createFileFromText(project, OclLanguage.INSTANCE, "module " + name + " = struct end");
        RPsiInnerModule module = ORUtil.findImmediateFirstChildOfClass(file, RPsiInnerModule.class);
        return ORUtil.findImmediateFirstChildOfClass(module, RPsiUpperSymbol.class);
    }

    @Nullable
    public static RPsiLowerSymbol createLetName(@NotNull Project project, @NotNull String name) {
        FileBase file = createFileFromText(project, OclLanguage.INSTANCE, "let " + name + " = 1");
        RPsiLet let = ORUtil.findImmediateFirstChildOfClass(file, RPsiLet.class);
        return ORUtil.findImmediateFirstChildOfClass(let, RPsiLowerSymbol.class);
    }

    @Nullable
    public static RPsiLowerSymbol createTypeName(@NotNull Project project, @NotNull String name) {
        FileBase file = createFileFromText(project, OclLanguage.INSTANCE, "type " + name);
        RPsiType type = ORUtil.findImmediateFirstChildOfClass(file, RPsiType.class);
        return ORUtil.findImmediateFirstChildOfClass(type, RPsiLowerSymbol.class);
    }

    @Nullable
    public static PsiElement createExpression(@NotNull Project project, @NotNull Language language, @NotNull String expression) {
        FileBase file = createFileFromText(project, language, expression);
        return file.getFirstChild();
    }

    @NotNull
    public static FileBase createFileFromText(@NotNull Project project, @NotNull Language language, @NotNull String text) {
        return (FileBase) PsiFileFactory.getInstance(project).createFileFromText("Dummy", language, text);
    }
}
