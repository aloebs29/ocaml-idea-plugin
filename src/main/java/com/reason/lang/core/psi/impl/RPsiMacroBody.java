package com.reason.lang.core.psi.impl;

import com.intellij.lang.*;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.*;
import com.reason.lang.core.type.*;
import org.jetbrains.annotations.*;

public class RPsiMacroBody extends ORCompositePsiElement<ORLangTypes> implements PsiLanguageInjectionHost {
    protected RPsiMacroBody(@NotNull ORLangTypes types, @NotNull IElementType elementType) {
        super(types, elementType);
    }

    @Override
    public boolean isValidHost() {
        return true;
    }

    @Override
    public @NotNull PsiLanguageInjectionHost updateText(@NotNull String text) {
        ASTNode valueNode = getNode().getFirstChildNode();
        if (valueNode instanceof LeafElement) {
            ((LeafElement) valueNode).replaceWithText(text);
        }
        return this;
    }

    /*
     This used to return com.intellij.json's JSStringLiteralEscaper, which decoded backslash escapes. That
     class lives in a separate plugin since 2025.1 and is not on this plugin's classpath, so the platform's
     plain escaper is used instead: the injected text is taken verbatim, which is right for the `{|...|}`
     literals macro bodies normally use, and only loses escape decoding for the quoted form.
     */
    @Override
    public @NotNull LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
        return LiteralTextEscaper.createSimple(this, false);
    }

    public @Nullable TextRange getMacroTextRange() {
        ASTNode firstChildNode = getNode().getFirstChildNode();
        IElementType elementType = firstChildNode == null ? null : firstChildNode.getElementType();
        if (elementType == myTypes.STRING_VALUE || elementType == myTypes.ML_STRING_VALUE || elementType == myTypes.C_INTERPOLATION_EXPR) {
            int max = getTextLength() - 1;
            if (1 <= max) {
                return new TextRange(1, max);
            }
        } else {
            int max = getTextLength() - 2;
            if (2 <= max) {
                return new TextRange(2, max);
            }
        }

        return null;
    }
}
