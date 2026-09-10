package com.reason.ide.lsp;

import com.intellij.execution.configurations.*;
import com.intellij.openapi.project.*;
import com.redhat.devtools.lsp4ij.*;
import com.redhat.devtools.lsp4ij.server.*;
import com.reason.comp.ocaml.*;
import jpsplugin.com.reason.*;
import org.jetbrains.annotations.*;

/**
 * Registers <code>ocamllsp</code> (ocaml-lsp-server) with LSP4IJ.
 * <p>
 * This is where the plugin's semantic features come from: hover, type information, documentation, completion,
 * go-to-definition, find-usages, rename, diagnostics and formatting are all answered by merlin behind the
 * language server rather than by this plugin's own PSI. The plugin still owns the lexer, parser, syntax
 * highlighting, folding, structure view and indentation — see {@code README.md}.
 * <p>
 * The server is <em>not</em> installed by this plugin: it comes from the configured opam switch
 * (<code>opam install ocaml-lsp-server</code>), which is also what keeps it in step with the switch's compiler
 * version.
 */
public class OCamlLspServerFactory implements LanguageServerFactory {
    /** Must match the {@code id} of the {@code <server>} registration in plugin.xml. */
    public static final String SERVER_ID = "ocamllsp";

    @Override
    public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
        return new OCamlLspConnectionProvider(project);
    }

    /**
     * Builds the command line lazily, on start, rather than when the provider is constructed.
     * <p>
     * The opam environment is computed asynchronously at startup, and the opam root and switch can be changed
     * in settings at any time. Resolving the command line eagerly would freeze whatever was configured when
     * the first OCaml file happened to be opened.
     */
    private static final class OCamlLspConnectionProvider extends OSProcessStreamConnectionProvider {
        private static final Log LOG = Log.create("ocaml.lsp");

        private final Project myProject;

        private OCamlLspConnectionProvider(@NotNull Project project) {
            myProject = project;
        }

        @Override
        public void start() throws CannotStartProcessException {
            GeneralCommandLine commandLine = new OCamlLspCommandLine(myProject).createForProject();
            if (commandLine == null) {
                // Either no opam root is configured, or the project has no content root. Both are settings
                // problems rather than bugs, so say which so the message is actionable in the LSP console.
                throw new CannotStartProcessException(
                        "Can't build an ocamllsp command line. Check that the opam root and switch are set in" +
                                " Settings | Languages & Frameworks | OCaml | Opam, and that ocaml-lsp-server is" +
                                " installed in that switch (`opam install ocaml-lsp-server`).");
            }

            LOG.debug("Starting", commandLine.getCommandLineString());
            setCommandLine(commandLine);
            super.start();
        }
    }
}
