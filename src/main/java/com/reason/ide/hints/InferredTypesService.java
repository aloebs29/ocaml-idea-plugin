package com.reason.ide.hints;

import com.intellij.openapi.application.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import com.intellij.util.concurrency.*;
import com.reason.*;
import com.reason.comp.*;
import com.reason.hints.*;
import com.reason.ide.*;
import com.reason.ide.files.*;
import com.reason.lang.*;
import com.reason.lang.core.psi.impl.*;
import jpsplugin.com.reason.*;
import org.jetbrains.annotations.*;

import java.nio.file.*;
import java.util.concurrent.*;

import static com.reason.comp.ORCompiler.CompilerType.*;
import static com.reason.ide.hints.CodeLens.*;

public class InferredTypesService {
    private static final Log LOG = Log.create("hints.inferredTypes");

    private InferredTypesService() {
    }

    public static @Nullable PsiFile getPsiFile(@NotNull Project project) {
        Editor selectedTextEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (selectedTextEditor != null && !selectedTextEditor.isDisposed()) {
            Document document = selectedTextEditor.getDocument();
            PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
            if (psiFile instanceof FileBase && !FileHelper.isInterface(psiFile.getFileType())) {
                VirtualFile sourceFile = ORFileUtils.getVirtualFile(psiFile);
                FileType fileType = sourceFile == null ? null : sourceFile.getFileType();
                return FileHelper.isCompilable(fileType) ? psiFile : null;
            }
        }
        return null;
    }

    public static void queryTypes(@NotNull Project project, @NotNull PsiFile psiFile) {
        try {
            // Try to get the inferred types cached at the psi file user data
            VirtualFile sourceFile = ORFileUtils.getVirtualFile(psiFile);
            Application application = ApplicationManager.getApplication();

            SignatureProvider.InferredTypesWithLines sigContext = psiFile.getUserData(SignatureProvider.SIGNATURES_CONTEXT);
            InferredTypes signatures = sigContext == null ? null : sigContext.getTypes();
            ORLanguageProperties languageProperties = ORLanguageProperties.cast(psiFile.getLanguage());
            if (signatures == null) {
                InsightManager insightManager = project.getService(InsightManager.class);

                final String namespace = findNamespace(project, sourceFile);

                if (!DumbService.isDumb(project)) {
                    ReadAction.nonBlocking((Callable<Void>) () -> {
                                LOG.debug("Reading types from file", psiFile);
                                String nameWithoutExtension = sourceFile == null ? "" : sourceFile.getNameWithoutExtension();
                                VirtualFile cmtFile = ORFileUtils.findCmtFileFromSource(project, nameWithoutExtension, namespace);
                                if (cmtFile != null) {
                                    Path cmtPath = FileSystems.getDefault().getPath(cmtFile.getPath());
                                    insightManager.queryTypes(sourceFile, cmtPath,
                                            types -> application.runReadAction(() -> annotatePsiFile(project, languageProperties, sourceFile, types)));
                                }
                                return null;
                            })
                            .coalesceBy(insightManager)
                            .submit(AppExecutorUtil.getAppExecutorService());
                }
            } else {
                LOG.debug("Signatures found in user data cache");
                application.runReadAction(() -> annotatePsiFile(project, languageProperties, sourceFile, signatures));
            }
        } catch (Error e) {
            // might produce an AssertionError when project is being disposed, but the invokeLater still
            // process that code
        }
    }

    /** The prefix the compiler of {@code sourceFile} puts in front of the module it defines. */
    public static @NotNull String findNamespace(@NotNull Project project, @Nullable VirtualFile sourceFile) {
        if (sourceFile == null) {
            return "";
        }

        ORResolvedCompiler<? extends ORCompiler> compiler = project.getService(ORCompilerManager.class).getCompiler(sourceFile);
        if (compiler == null || compiler.getType() != DUNE) {
            return "";
        }

        VirtualFile duneSource = ORFileUtils.findAncestor(project, sourceFile, "dune");
        PsiFile dune = duneSource == null ? null : PsiManager.getInstance(project).findFile(duneSource);
        return dune instanceof DuneFile duneFile ? findDuneNamespace(duneFile) : "";
    }

    /**
     * The prefix dune puts in front of the modules a stanza defines. A library wraps its modules behind
     * its own name - `hello_ocaml__Foo` - while an executable is always wrapped behind `dune__exe__`,
     * whatever its `name` is. Returns an empty string when the stanza wraps nothing.
     */
    static @NotNull String findDuneNamespace(@NotNull DuneFile duneFile) {
        RPsiDuneStanza library = duneFile.getStanza("library");
        if (library != null) {
            RPsiDuneField wrapped = library.getField("wrapped");
            if (wrapped != null && "false".equals(wrapped.getValue())) {
                return "";
            }
            RPsiDuneField name = library.getField("name");
            if (name == null) {
                name = library.getField("public_name");
            }
            return name == null ? "" : StringUtil.toFirstLower(name.getValue()) + "__";
        }

        boolean isExecutable = duneFile.getStanza("executable") != null || duneFile.getStanza("executables") != null;
        return isExecutable ? "dune__exe__" : "";
    }

    public static void annotatePsiFile(@NotNull Project project, @Nullable ORLanguageProperties lang, @Nullable VirtualFile sourceFile, @Nullable InferredTypes types) {
        if (types == null || sourceFile == null) {
            return;
        }

        if (FileHelper.isInterface(sourceFile.getFileType())) {
            return;
        }

        LOG.debug("Updating signatures in user data cache for file", sourceFile);
        getSignatures(sourceFile).putAll(types.signaturesByLines(lang));

        PsiFile psiFile = PsiManager.getInstance(project).findFile(sourceFile);
        if (psiFile != null && !FileHelper.isInterface(psiFile.getFileType())) {
            String[] lines = psiFile.getText().split("\n");
            psiFile.putUserData(SignatureProvider.SIGNATURES_CONTEXT, new SignatureProvider.InferredTypesWithLines(types, lines));
        }
    }

    public static @NotNull CodeLens getSignatures(@NotNull VirtualFile file) {
        CodeLens userData = file.getUserData(CODE_LENS);
        if (userData == null) {
            userData = new CodeLens();
            file.putUserData(CODE_LENS, userData);
        }
        return userData;
    }
}
