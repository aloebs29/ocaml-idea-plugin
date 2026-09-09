package com.reason.ide.docs;

import com.intellij.lang.documentation.*;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.util.text.*;
import com.intellij.psi.*;
import com.reason.*;
import com.reason.ide.files.*;
import com.reason.lang.*;
import com.reason.lang.core.psi.*;
import com.reason.lang.doc.*;
import com.reason.lang.doc.ocaml.*;
import com.reason.lang.doc.reason.*;
import jpsplugin.com.reason.*;
import org.jetbrains.annotations.*;

/**
 * See {@link com.intellij.codeInsight.documentation.DocumentationManagerProtocol} for link protocol.
 */
class DocFormatter {
    private static final Log LOG = Log.create("doc.formatter");

    private DocFormatter() {
    }

    static @NotNull String format(@NotNull PsiFile file, @NotNull PsiElement element, @Nullable ORLanguageProperties lang, @NotNull String text) {
        if (file instanceof FileBase source) {
            // Definition

            HtmlBuilder definitionBuilder = new HtmlBuilder();

            if (element == file) {
                // Documenting the file itself, ie the module it defines. Its path is empty and its name is
                // a filename, so neither the qualified path nor the PsiNamedElement rendering below apply.
                definitionBuilder.append(HtmlChunk.text("module " + source.getModuleName()).bold());

                HtmlBuilder fileBuilder = new HtmlBuilder();
                fileBuilder.append(definitionBuilder.wrapWith(DocumentationMarkup.DEFINITION_ELEMENT));
                fileBuilder.append(new HtmlBuilder()
                        .append(newConverter(source).convert(element, text))
                        .wrapWith(DocumentationMarkup.CONTENT_ELEMENT));
                return fileBuilder.toString();
            }

            String path = source.getModuleName();
            if (element instanceof RPsiQualifiedPathElement) {
                path = Joiner.join(".", ((RPsiQualifiedPathElement) element).getPath());
            }
            definitionBuilder.append(HtmlChunk.text(path).bold());

            if (element instanceof PsiNamedElement) {
                String className = element.getClass().getSimpleName().substring(4).replace("Impl", "").toLowerCase();
                String name = ((PsiNamedElement) element).getName();
                if (name != null) {
                    definitionBuilder.append(HtmlChunk.raw("<p><i>"));
                    definitionBuilder.append(HtmlChunk.text(className + " " + name));

                    if (element instanceof RPsiSignatureElement) {
                        RPsiSignature signature = ((RPsiSignatureElement) element).getSignature();
                        if (signature != null) {
                            definitionBuilder.append(HtmlChunk.text(" : ")).append(HtmlChunk.text(signature.asText(lang)).wrapWith("code"));
                        }
                    }
                }
                definitionBuilder.append(HtmlChunk.raw("</i></p>"));
            }

            // Content

            HtmlBuilder contentBuilder = new HtmlBuilder();
            contentBuilder.append(newConverter(source).convert(element, text));

            // final render

            HtmlBuilder builder = new HtmlBuilder();
            builder.append(definitionBuilder.wrapWith(DocumentationMarkup.DEFINITION_ELEMENT));
            builder.append(contentBuilder.wrapWith(DocumentationMarkup.CONTENT_ELEMENT));

            if (LOG.isDebugEnabled()) {
                LOG.debug(builder.toString());
            }

            return builder.toString();
        }
        return text;
    }


    private static @NotNull ORDocConverter newConverter(@NotNull FileBase source) {
        FileType fileType = source.getFileType();
        boolean isReasonLikeComment = FileHelper.isReason(fileType) || FileHelper.isRescript(fileType);
        return isReasonLikeComment ? new RmlDocConverter() : new OclDocConverter();
    }

    static @NotNull String escapeCodeForHtml(@Nullable PsiElement code) {
        if (code == null) {
            return "";
        }

        return escapeCodeForHtml(code.getText());
    }

    @Nullable
    public static String escapeCodeForHtml(@Nullable String code) {
        return code == null ? null : code.
                replaceAll("<", "&lt;").
                replaceAll(">", "&gt;");
    }
}
