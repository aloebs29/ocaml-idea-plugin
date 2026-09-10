package com.reason.lang.core.psi.impl;

import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.*;
import com.reason.ide.search.reference.*;
import com.reason.lang.core.type.*;
import org.jetbrains.annotations.*;

public class RPsiUpperSymbol extends LeafPsiElement {
    protected final ORLangTypes myTypes;

    // region Constructors
    public RPsiUpperSymbol(@NotNull ORLangTypes types, @NotNull IElementType tokenType, CharSequence text) {
        super(tokenType, text);
        myTypes = types;
    }
    // endregion

    /**
     * Always null: resolution is the language server's job now.
     * <p>
     * References are part of the PSI rather than an extension point, so unregistering the providers in
     * plugin.xml did not stop them from running — the platform still called {@code multiResolve} for
     * ctrl-click and for highlighting, which went through {@code ORReferenceAnalyzer} and the module indexes.
     * Handing back a reference here would also win over the language server: a resolvable PSI reference is
     * what the platform navigates with, so ctrl-click would use this plugin's own resolution and never reach
     * merlin. Null is what {@link LeafPsiElement} returns by default.
     * <p>
     * The return type is kept concrete so the unregistered providers that call this still compile; they are
     * not invoked. See {@code README.md}, "Backing out to PSI-based semantics".
     */
    @Override
    public @Nullable ORPsiUpperSymbolReference getReference() {
        return null;
    }

    @Override
    public String toString() {
        return "RPsiUpperSymbol:" + getElementType() + " (" + getText() + ")";
    }
}
