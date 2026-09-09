package com.reason.ide.format;

import com.intellij.lang.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.codeStyle.lineIndent.*;
import com.reason.*;
import com.reason.comp.ocaml.*;
import com.reason.lang.ocaml.*;
import org.jetbrains.annotations.*;

/**
 * Indents the current line with ocp-indent when the user presses enter (or asks for a smart indent).
 * <p>
 * The platform calls this before committing the document, on the EDT, with the line break already inserted:
 * {@code offset} is on the new line. Returning null leaves the platform to fall back on its formatter-based
 * indent, which for OCaml amounts to keeping the previous line's indentation — that is also what happens when
 * ocp-indent isn't installed, so the feature degrades to the old behaviour instead of breaking editing.
 */
public class OclLineIndentProvider implements LineIndentProvider {
    /** Above that, the cost of piping the file to ocp-indent on every enter isn't worth it. */
    private static final int MAX_TEXT_LENGTH = 1_000_000;

    /**
     * Stands in for the code the user is about to type on the otherwise empty line.
     * <p>
     * ocp-indent answers for a line from what precedes it <em>and</em> from the line's own first token. Asked
     * about a line with no token at all it assumes the previous expression is being continued, which is almost
     * never what pressing enter means: after {@code open Stdio} it answers 4 rather than 0, and after
     * {@code | Some x -> f (a +. g x)} it answers 16 — the column just inside the application. An empty comment
     * is the only filler that is lexically valid in every position while starting nothing, so the answer stays
     * the enclosing block's indent: still 2 inside a {@code let} body or after {@code match x with}, but 0 once
     * the phrase before it is complete.
     */
    static final String INDENT_ANCHOR = "(**)";

    @Override
    public boolean isSuitableFor(@Nullable Language language) {
        return language == OclLanguage.INSTANCE;
    }

    @Override
    public @Nullable String getLineIndent(@NotNull Project project, @NotNull Editor editor, @Nullable Language language, int offset) {
        if (offset < 0) {
            return null;
        }
        return computeIndent(project, editor.getDocument(), offset);
    }

    /**
     * The indentation the line containing {@code offset} should start with, or null if ocp-indent could not
     * answer. Shared with {@link OclReindentTypedHandler}, which asks again once the line has a first token.
     */
    static @Nullable String computeIndent(@NotNull Project project, @NotNull Document document, int offset) {
        if (MAX_TEXT_LENGTH < document.getTextLength()) {
            return null;
        }

        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        if (file == null || !FileHelper.isOCaml(file.getFileType())) {
            return null;
        }

        int lineIndex = document.getLineNumber(offset);
        // ocp-indent is a line indenter: the indentation of a line only depends on what comes before it, plus
        // the line itself (a line starting with `end` or `|` dedents). Everything after can be dropped, which
        // keeps editing near the top of a big file cheap.
        String text = anchorLastLine(document.getText(new TextRange(0, document.getLineEndOffset(lineIndex))));

        Integer indent = project.getService(OcpIndentProcess.class).getIndent(file, text, lineIndex + 1);
        return indent == null ? null : " ".repeat(indent);
    }

    /** Appends {@link #INDENT_ANCHOR} when the line being asked about carries no token of its own. */
    static @NotNull String anchorLastLine(@NotNull String text) {
        String lastLine = text.substring(text.lastIndexOf('\n') + 1);
        return lastLine.isBlank() ? text + INDENT_ANCHOR : text;
    }
}
