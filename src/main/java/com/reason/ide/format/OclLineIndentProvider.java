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

    @Override
    public boolean isSuitableFor(@Nullable Language language) {
        return language == OclLanguage.INSTANCE;
    }

    @Override
    public @Nullable String getLineIndent(@NotNull Project project, @NotNull Editor editor, @Nullable Language language, int offset) {
        if (offset < 0) {
            return null;
        }

        Document document = editor.getDocument();
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
        String text = document.getText(new TextRange(0, document.getLineEndOffset(lineIndex)));

        Integer indent = project.getService(OcpIndentProcess.class).getIndent(file, text, lineIndex + 1);
        return indent == null ? null : " ".repeat(indent);
    }
}
