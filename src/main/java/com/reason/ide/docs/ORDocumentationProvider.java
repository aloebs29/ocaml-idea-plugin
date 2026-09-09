package com.reason.ide.docs;

import com.intellij.lang.*;
import com.intellij.lang.documentation.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.psi.util.*;
import com.reason.*;
import com.reason.ide.files.*;
import com.reason.ide.hints.*;
import com.reason.ide.search.*;
import com.reason.ide.search.index.*;
import com.reason.ide.search.reference.*;
import com.reason.lang.*;
import com.reason.lang.core.*;
import com.reason.lang.core.psi.*;
import com.reason.lang.core.psi.impl.*;
import com.reason.lang.ocaml.*;
import com.reason.lang.reason.*;
import com.reason.lang.rescript.*;
import jpsplugin.com.reason.*;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.*;

public class ORDocumentationProvider extends AbstractDocumentationProvider {
    private static final Log LOG = Log.create("doc");

    private static final @NotNull Predicate<PsiElement> PSI_INTF_PREDICATE = psiElement -> ((FileBase) psiElement.getContainingFile()).isInterface();

    @Override
    public @Nullable String generateDoc(PsiElement resolvedElement, @Nullable PsiElement originalElement) {
        ORLanguageProperties languageProperties = ORLanguageProperties.cast(originalElement == null ? null : originalElement.getLanguage());

        PsiElement docElement = resolvedElement;
        if (resolvedElement instanceof RPsiModule module && module.isComponent()) {
            PsiElement make = module.getMakeFunction();
            if (make != null) {
                docElement = make;
            }
        } else if (resolvedElement instanceof FileBase resolvedFile) {
            String fileDoc = generateFileDoc(resolvedFile, languageProperties);
            if (fileDoc != null) {
                return fileDoc;
            }
        }

        // If it's an alias, resolve to the alias
        if (docElement instanceof RPsiLet let) {
            String alias = let.getAlias();
            if (alias != null) {
                PsiElement binding = let.getBinding();
                RPsiLowerSymbol lSymbol = binding == null ? null : ORUtil.findImmediateLastChildOfClass(binding, RPsiLowerSymbol.class);
                ORPsiLowerSymbolReference lReference = lSymbol == null ? null : lSymbol.getReference();
                PsiElement resolvedAlias = lReference == null ? null : lReference.resolveInterface();
                if (resolvedAlias != null) {
                    docElement = resolvedAlias;
                }
            }
        }

        PsiElement comment = findComment(docElement, docElement.getLanguage());

        // Nothing found, and it's a module alias: document whatever it is an alias of.
        // `Stdio.In_channel` is declared as `module In_channel = In_channel`, all of its documentation
        // lives in the in_channel.mli it points to.
        if (comment == null && docElement instanceof RPsiInnerModule aliasModule) {
            PsiElement aliased = resolveModuleAlias(aliasModule);
            if (aliased instanceof FileBase aliasedFile) {
                String fileDoc = generateFileDoc(aliasedFile, languageProperties);
                if (fileDoc != null) {
                    return fileDoc;
                }
            } else if (aliased != null) {
                PsiElement aliasedComment = findComment(aliased, aliased.getLanguage());
                if (aliasedComment != null) {
                    docElement = aliased;
                    comment = aliasedComment;
                }
            }
        }

        // Nothing found, try to find a comment in the interface if any
        if (comment == null && originalElement instanceof RPsiLowerSymbol && docElement instanceof RPsiQualifiedPathElement) {
            Project project = docElement.getProject();
            GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            String elementQName = ((RPsiQualifiedPathElement) docElement).getQualifiedName();
            if (elementQName != null) {
                Collection<RPsiVal> vals = ValFqnIndex.getElements(elementQName, project, scope);
                if (!vals.isEmpty()) {
                    RPsiVal next = vals.iterator().next();
                    comment = findComment(next, next.getLanguage());
                } else {
                    Collection<RPsiLet> lets = LetFqnIndex.getElements(elementQName, project, scope);
                    RPsiLet letIntf = lets.stream()
                            .filter(PSI_INTF_PREDICATE)
                            .findFirst().orElse(null);
                    if (letIntf != null) {
                        comment = findComment(letIntf, letIntf.getLanguage());
                    }
                }
            }
        }

        if (comment != null) {
            if (comment instanceof RPsiAnnotation) {
                PsiElement value = ((RPsiAnnotation) comment).getValue();
                String text = value == null ? null : value.getText();
                return text == null ? null : text.substring(1, text.length() - 1);
            }

            return isSpecialComment(comment)
                    ? DocFormatter.format(docElement.getContainingFile(), docElement, languageProperties, comment.getText())
                    : comment.getText();
        }
        //}

        return null;
    }

    @Override
    public @Nullable String getQuickNavigateInfo(@NotNull PsiElement resolvedElement, @NotNull PsiElement originalElement) {
        String quickDoc = null;
        ORLanguageProperties languageProperties = ORLanguageProperties.cast(originalElement.getLanguage());

        if (resolvedElement instanceof ORFakeResolvedElement) {
            // A fake element, used to query inferred types
            quickDoc = "Show usages of fake element '" + resolvedElement.getText() + "'";
        } else if (resolvedElement instanceof FileBase resolvedFile) {
            LOG.debug("Quickdoc of topModule", resolvedElement);

            String relative_path = Platform.getRelativePathToModule(resolvedFile);
            quickDoc =
                    "<div style='white-space:nowrap;font-style:italic'>"
                            + relative_path
                            + "</div>"
                            + "Module "
                            //+ DocFormatter.NAME_START
                            + resolvedFile.getModuleName();
            //+ DocFormatter.NAME_END;
        } else {
            LOG.trace("Resolved element", resolvedElement);

            if (resolvedElement instanceof RPsiType type) {
                String[] path = ORUtil.getQualifiedPath(type);
                String typeBinding = type.isAbstract() ? "This is an abstract type" : DocFormatter.escapeCodeForHtml(type.getBinding());
                return createQuickDocTemplate(path, "type", type.getName(), typeBinding);
            }

            if (resolvedElement instanceof RPsiSignatureElement) {
                RPsiSignature signature = ((RPsiSignatureElement) resolvedElement).getSignature();
                if (signature != null) {
                    String sig = DocFormatter.escapeCodeForHtml(signature.asText(languageProperties));
                    if (resolvedElement instanceof RPsiQualifiedPathElement qualifiedElement) {
                        String elementType = PsiTypeElementProvider.getType(resolvedElement);
                        return createQuickDocTemplate(qualifiedElement.getPath(), elementType, qualifiedElement.getName(), sig);
                    }
                    return sig;
                }
            }

            // No signature found, but resolved
            if (resolvedElement instanceof PsiQualifiedNamedElement) {
                LOG.debug("Quickdoc resolved to ", resolvedElement);

                String elementType = PsiTypeElementProvider.getType(resolvedElement);
                String desc = ((PsiQualifiedNamedElement) resolvedElement).getName();
                String[] path = ORUtil.getQualifiedPath(resolvedElement);

                PsiFile psiFile = originalElement.getContainingFile();
                String inferredType = getInferredSignature(originalElement, psiFile, languageProperties);

                if (inferredType == null) {
                    // Can't find type in the usage, try to get type from the definition
                    inferredType = getInferredSignature(resolvedElement, resolvedElement.getContainingFile(), languageProperties);
                }

                String sig = inferredType == null ? null : DocFormatter.escapeCodeForHtml(inferredType);
                if (resolvedElement instanceof RPsiVariantDeclaration) {
                    RPsiType type = PsiTreeUtil.getParentOfType(resolvedElement, RPsiType.class);
                    sig = "type " + (type == null ? "unknown" : type.getName());
                }

                return createQuickDocTemplate(path, elementType, desc, resolvedElement instanceof RPsiModule ? null : sig);
            }
        }

        return quickDoc;
    }

    @Override
    public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file, @Nullable PsiElement contextElement, int targetOffset) {
        PsiElement parent = contextElement == null ? null : contextElement.getParent();

        // When quick doc inside empty parenthesis, we want to display the function doc (GitHub #155)
        // functionName(<caret>) ==> functionName<caret>()
        if (contextElement != null && parent instanceof RPsiParameters) {
            Language contextLanguage = contextElement.getLanguage();
            if (contextLanguage == RmlLanguage.INSTANCE || contextLanguage == ResLanguage.INSTANCE) {
                PsiElement prevSibling = parent.getPrevSibling();
                if (prevSibling != null) {
                    PsiReference reference = prevSibling.getReference();
                    if (reference != null) {
                        return reference.resolve();
                    }
                }
            }
        }

        if (contextElement != null && parent instanceof RPsiLowerSymbol lowerParent) {
            ORPsiLowerSymbolReference reference = lowerParent.getReference();
            ResolveResult[] resolveResults = reference.multiResolve(false);
            if (0 < resolveResults.length) {
                Arrays.sort(resolveResults, (rr1, rr2) ->
                        ((ORPsiLowerSymbolReference.LowerResolveResult) rr1).inInterface()
                                ? -1
                                : (((ORPsiLowerSymbolReference.LowerResolveResult) rr2).inInterface() ? 1 : 0));
                return resolveResults[0].getElement();
            }
        }

        return null;
    }

    /** The leading doc comment of a file, which is the documentation of the module it defines. */
    private @Nullable String generateFileDoc(@NotNull FileBase file, @Nullable ORLanguageProperties languageProperties) {
        String text = "";

        PsiElement nextSibling = file.getFirstChild();
        while (nextSibling instanceof PsiComment) {
            if (isSpecialComment(nextSibling)) {
                text = nextSibling.getText();
                nextSibling = null;
            } else {
                // Not a special comment, try with next child until no more comments found
                nextSibling = PsiTreeUtil.nextVisibleLeaf(nextSibling);
            }
        }

        return text.isEmpty() ? null : DocFormatter.format(file, file, languageProperties, text);
    }

    /**
     * Follows a chain of module aliases - `module A = B` - down to whatever it ends on, a file or an
     * inner module. Returns null if the module is not an alias, or if the chain can't be resolved.
     */
    private @Nullable PsiElement resolveModuleAlias(@NotNull RPsiInnerModule module) {
        Set<PsiElement> visited = new HashSet<>();

        PsiElement current = module;
        while (current instanceof RPsiInnerModule inner && inner.getAlias() != null && visited.add(current)) {
            String alias = inner.getAlias();

            // `module X = X` re-exports the compilation unit X. Several libraries of a switch define the
            // same module name, and stdlib is implicitly opened everywhere, so the reference below can
            // resolve to a namesake from anywhere - or to the alias itself. A file sitting next to the
            // alias is unambiguously the one it re-exports.
            FileBase sibling = findAliasedFile(alias, module, true);
            if (sibling != null) {
                return sibling;
            }

            RPsiUpperSymbol aliasSymbol = inner.getAliasSymbol();
            PsiReference reference = aliasSymbol == null ? null : aliasSymbol.getReference();
            PsiElement resolved = reference == null ? null : reference.resolve();

            if (resolved == null || visited.contains(resolved)) {
                // The chain ends on itself, look the file up by name rather than giving up
                return findAliasedFile(alias, module, false);
            }

            current = resolved;
        }

        return current == module ? null : current;
    }

    /**
     * The file module an alias points to, the closest one to the alias when several files define it.
     * Only an unqualified alias can name a sibling: in `module X = A.B`, `B` is a module of `A`, not a
     * file that happens to be called B.
     */
    private @Nullable FileBase findAliasedFile(@NotNull String alias, @NotNull PsiElement context, boolean siblingOnly) {
        int lastDot = alias.lastIndexOf('.');
        if (alias.isEmpty() || (siblingOnly && lastDot != -1)) {
            return null;
        }

        Project project = context.getProject();
        PsiFile contextFile = context.getContainingFile();
        VirtualFile contextVFile = contextFile == null ? null : contextFile.getVirtualFile();
        VirtualFile contextDirectory = contextVFile == null ? null : contextVFile.getParent();
        if (siblingOnly && contextDirectory == null) {
            return null;
        }

        PsiManager psiManager = PsiManager.getInstance(project);
        FileBase found = null;
        for (VirtualFile candidate : FileModuleIndex.getContainingFiles(alias.substring(lastDot + 1), GlobalSearchScope.allScope(project))) {
            boolean isSibling = contextDirectory != null && contextDirectory.equals(candidate.getParent());
            if (siblingOnly && !isSibling) {
                continue;
            }
            if (!(psiManager.findFile(candidate) instanceof FileBase candidateFile) || candidateFile == contextFile) {
                continue;
            }
            if (found == null || isBetterAliasTarget(candidate, found.getVirtualFile(), contextDirectory)) {
                found = candidateFile;
            }
        }

        return found;
    }

    private static boolean isBetterAliasTarget(@NotNull VirtualFile candidate, @Nullable VirtualFile current, @Nullable VirtualFile contextDirectory) {
        boolean candidateIsSibling = contextDirectory != null && contextDirectory.equals(candidate.getParent());
        boolean currentIsSibling = current != null && contextDirectory != null && contextDirectory.equals(current.getParent());
        if (candidateIsSibling != currentIsSibling) {
            return candidateIsSibling;
        }
        // Same locality, prefer the interface: that is where the documentation is written
        return FileHelper.isInterface(candidate.getFileType())
                && (current == null || !FileHelper.isInterface(current.getFileType()));
    }

    private @Nullable PsiElement findComment(@Nullable PsiElement resolvedElement, @NotNull Language lang) {
        // Try to find a comment just below (OCaml only)
        if (lang == OclLanguage.INSTANCE) {
            PsiElement belowComment = findBelowComment(resolvedElement);
            if (belowComment != null) {
                return belowComment;
            }
        }

        // Else try to find a comment just above
        return findAboveComment(resolvedElement);
    }

    private @Nullable PsiElement findAboveComment(@Nullable PsiElement element) {
        if (element == null) {
            return null;
        }

        PsiElement commentElement = null;

        // search for a comment above
        boolean search = true;
        PsiElement prevSibling = element.getPrevSibling();
        while (search) {
            if (prevSibling instanceof PsiComment) {
                search = false;
                commentElement = prevSibling;
            } else if (prevSibling instanceof PsiWhiteSpace) {
                prevSibling = prevSibling.getPrevSibling();
            } else if (prevSibling instanceof RPsiAnnotation annotation) {
                if ("@ocaml.doc".equals(annotation.getName())) {
                    search = false;
                    commentElement = annotation;
                } else {
                    prevSibling = prevSibling.getPrevSibling();
                }
            } else {
                search = false;
            }
        }

        return commentElement;
    }

    private @Nullable PsiElement findBelowComment(@Nullable PsiElement element) {
        if (element != null) {
            PsiElement nextSibling = element.getNextSibling();
            PsiElement nextNextSibling = nextSibling == null ? null : nextSibling.getNextSibling();
            if (nextNextSibling instanceof PsiComment
                    && nextSibling instanceof PsiWhiteSpace
                    && nextSibling.getText().replaceAll("[ \t]", "").length() == 1) {
                return nextNextSibling;
            }
        }

        return null;
    }

    private @Nullable String getInferredSignature(@NotNull PsiElement element, @NotNull PsiFile psiFile, @Nullable ORLanguageProperties language) {
        SignatureProvider.InferredTypesWithLines signaturesContext = psiFile.getUserData(SignatureProvider.SIGNATURES_CONTEXT);
        if (signaturesContext != null) {
            RPsiSignature elementSignature = signaturesContext.getSignatureByOffset(element.getTextOffset());
            if (elementSignature != null) {
                return elementSignature.asText(language);
            }
        }
        return null;
    }

    private @NotNull String createQuickDocTemplate(@Nullable String[] path, @Nullable String type, @Nullable String name, @Nullable String signature) {
        return Joiner.join(".", path)
                + "<br/>"
                + (type == null ? "" : type)
                + (" <b>" + name + "</b>")
                + (signature == null ? "" : "<hr/>" + signature);
    }

    public static boolean isSpecialComment(@Nullable PsiElement element) {
        if (element == null) {
            return false;
        }

        String nextText = element.getText();
        return (nextText.startsWith("(**") || nextText.startsWith("/**")) && nextText.charAt(3) != '*';
    }
}
