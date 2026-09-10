package com.reason.comp.ocaml;

import com.intellij.openapi.project.*;
import org.jetbrains.annotations.*;

import java.util.*;

/**
 * Command line for <code>ocamllsp</code> (ocaml-lsp-server), taken from the configured opam switch.
 * <p>
 * The server is started once per project and reads its own per-module configuration by shelling out to
 * <code>dune ocaml-merlin</code>, so it only needs the switch environment and a working directory — which is
 * why this uses {@link #createForProject()} rather than resolving a module from a source file.
 * <p>
 * Inheriting the switch environment from {@link OpamEnv} is what puts <code>dune</code> on the server's
 * <code>PATH</code>. Without it ocamllsp starts but answers nothing useful, since it cannot ask dune where a
 * module's build artifacts are.
 */
public class OCamlLspCommandLine extends OpamCommandLine {
    public OCamlLspCommandLine(@NotNull Project project) {
        // stderr must stay separate: it carries the server's log output, while stdout is the LSP wire protocol
        // and anything interleaved into it corrupts the stream.
        super(project, "ocamllsp", false);
    }

    @Override
    protected @NotNull List<String> getParameters() {
        return Collections.emptyList();
    }
}
