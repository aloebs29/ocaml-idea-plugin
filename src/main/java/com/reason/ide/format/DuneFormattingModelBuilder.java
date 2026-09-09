package com.reason.ide.format;

import com.intellij.formatting.*;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings; // not the package: com.intellij.psi.codeStyle.Indent would clash with the formatter one
import org.jetbrains.annotations.*;

/**
 * Formats dune files (dune, dune-project, jbuild) by their s-expression nesting. See {@link DuneBlock} for the
 * style it produces.
 * <p>
 * Registering this also gives indentation on enter for free: with no formatter, the platform falls back on
 * repeating the previous line's indentation, but with one it derives the indent of a new line from the same
 * block tree. That is why there is no dune counterpart to {@link OclLineIndentProvider} — OCaml needs one
 * because its indentation can't be derived from a paren count, dune's can.
 */
public class DuneFormattingModelBuilder implements FormattingModelBuilder {
    @Override
    public @NotNull FormattingModel createModel(@NotNull FormattingContext context) {
        PsiFile file = context.getContainingFile();
        CodeStyleSettings settings = context.getCodeStyleSettings();
        int indentSize = settings.getIndentOptionsByFile(file).INDENT_SIZE;
        DuneBlock root = new DuneBlock(file.getNode(), Indent.getNoneIndent(), indentSize);
        return FormattingModelProvider.createFormattingModelForPsiFile(file, root, settings);
    }
}
