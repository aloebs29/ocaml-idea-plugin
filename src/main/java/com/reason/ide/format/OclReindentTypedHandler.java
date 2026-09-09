package com.reason.ide.format;

import com.intellij.codeInsight.editorActions.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.util.*;
import com.intellij.psi.*;
import com.intellij.psi.tree.*;
import com.reason.*;
import com.reason.lang.ocaml.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Dedents the current line once the user types the token that decides its indentation.
 * <p>
 * {@link OclLineIndentProvider} runs on enter, when the line is still empty and its first token therefore
 * unknown; it can only answer with the enclosing block's indent. But a line opening with {@code |} or
 * {@code end} belongs one level further out, and ocp-indent says so as soon as that token is there — so we ask
 * it again, and the line snaps left under the caret. Anything else is left alone: re-indenting a line the user
 * is in the middle of writing would fight with them.
 */
public class OclReindentTypedHandler extends TypedHandlerDelegate {
    /**
     * The tokens whose presence at the start of a line changes ocp-indent's answer: the closing delimiters,
     * and the keywords that end or continue a construct opened on an earlier line.
     */
    private static final Set<String> DEDENT_TOKENS =
            Set.of("|", ")", "]", "}", "in", "end", "done", "else", "with", "and");

    @Override
    public @NotNull Result charTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
        if (Character.isWhitespace(c) || !FileHelper.isOCaml(file.getFileType())) {
            return Result.CONTINUE;
        }

        Document document = editor.getDocument();
        int offset = editor.getCaretModel().getOffset();
        int lineStart = document.getLineStartOffset(document.getLineNumber(offset));
        String typedSoFar = document.getText(new TextRange(lineStart, offset)).stripLeading();
        if (!isDedentToken(typedSoFar)) {
            return Result.CONTINUE;
        }

        PsiDocumentManager.getInstance(project).commitDocument(document);
        if (isInsideCommentOrString(file, offset - 1)) {
            return Result.CONTINUE;
        }

        String indent = OclLineIndentProvider.computeIndent(project, document, offset);
        if (indent != null) {
            // Only the leading whitespace is rewritten, so the caret rides along with the token it follows.
            document.replaceString(lineStart, offset - typedSoFar.length(), indent);
        }

        return Result.CONTINUE;
    }

    /**
     * Whether the line so far is one of the tokens that dedent — or was one before the last character, so that
     * an {@code in} turning out to be the start of {@code int_of_string} puts the line back where it belongs.
     */
    static boolean isDedentToken(@NotNull String typedSoFar) {
        return DEDENT_TOKENS.contains(typedSoFar)
                || (!typedSoFar.isEmpty() && DEDENT_TOKENS.contains(typedSoFar.substring(0, typedSoFar.length() - 1)));
    }

    /** A lone {@code |} on its own line is ordinary text inside a comment or a multi-line string. */
    private static boolean isInsideCommentOrString(@NotNull PsiFile file, int offset) {
        PsiElement leaf = file.findElementAt(offset);
        if (leaf == null) {
            return false;
        }
        if (leaf instanceof PsiComment) {
            return true;
        }
        IElementType type = leaf.getNode().getElementType();
        OclTypes types = OclTypes.INSTANCE;
        return type == types.STRING_VALUE || type == types.CHAR_VALUE || type == types.ML_STRING_VALUE;
    }
}
