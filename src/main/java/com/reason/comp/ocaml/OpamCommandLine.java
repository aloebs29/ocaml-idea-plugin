package com.reason.comp.ocaml;

import com.intellij.execution.configurations.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.*;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.*;
import com.intellij.util.containers.*;
import com.reason.ide.settings.*;
import jpsplugin.com.reason.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

import static com.intellij.openapi.application.ApplicationManager.*;

public abstract class OpamCommandLine {
    private static final Log LOG = Log.create("ocaml.opam");
    public static final VirtualFile[] EMPTY_VFILES = new VirtualFile[0];

    private final Project myProject;
    private final String myBinary;
    private final boolean myRedirectErrorStream;

    OpamCommandLine(@NotNull Project project, @NotNull String binary, boolean redirectErrorStream) {
        myProject = project;
        myBinary = binary;
        myRedirectErrorStream = redirectErrorStream;
    }

    protected OpamCommandLine(@NotNull Project project, @NotNull String binary) {
        this(project, binary, true);
    }

    protected abstract @NotNull List<String> getParameters();

    /** Runs in the content root of the module {@code source} belongs to. */
    public @Nullable GeneralCommandLine create(@NotNull VirtualFile source) {
        Module module = Platform.getModule(myProject, source);
        return build(module == null ? EMPTY_VFILES : ModuleRootManager.getInstance(module).getContentRoots());
    }

    /**
     * Runs in the project's first content root, for tools that are not tied to one file — the language server,
     * which is started once per project and finds its own per-module configuration through dune.
     */
    public @Nullable GeneralCommandLine createForProject() {
        return build(ProjectRootManager.getInstance(myProject).getContentRoots());
    }

    private @Nullable GeneralCommandLine build(@NotNull VirtualFile[] contentRoots) {
        ORSettings settings = myProject.getService(ORSettings.class);
        String opamLocation = settings.getOpamLocation();
        if (!opamLocation.isEmpty()) {
            String switchLocation = opamLocation + "/" + settings.getSwitchName();
            String binPath = switchLocation + "/bin";

            if (contentRoots.length > 0) {
                GeneralCommandLine cli = new GeneralCommandLine(ContainerUtil.prepend(getParameters(), myBinary));
                cli.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
                cli.setWorkDirectory(contentRoots[0].getPath());
                cli.setRedirectErrorStream(myRedirectErrorStream);

                Map<String, String> env = getApplication().getService(OpamEnv.class).getEnv(settings.getSwitchName());
                if (env != null) {
                    for (Map.Entry<String, String> entry : env.entrySet()) {
                        cli.withEnvironment(entry.getKey(), entry.getValue());
                    }
                }

                OCamlExecutable executable = OCamlExecutable.getExecutable(opamLocation, settings.getCygwinBash());
                return executable.patchCommandLine(cli, findBinaryDirectory(binPath, executable), false);
            } else {
                LOG.debug("Content roots", contentRoots);
                LOG.debug("Binary directory", binPath);
            }
        }

        return null;
    }

    /**
     * The switch's {@code bin} directory to run the binary out of, or null to leave it a bare name and let the
     * OS look it up.
     * <p>
     * A bare executable name is resolved against the <em>current</em> process's PATH, <em>not</em> against the
     * switch environment set on the command line above — that environment only applies once the process has
     * started. So a bare name only works when the IDE itself happens to have the switch on its PATH, which is
     * exactly what could not be relied on in fix 1: CLion had inherited a stale environment and could not find
     * {@code opam} either. Resolving against the configured switch makes these tools work regardless.
     * <p>
     * Falls back to null when the binary is not in the switch, so a tool installed elsewhere on the PATH keeps
     * working, and leaves WSL and Cygwin alone — they resolve paths and PATH in their own namespace.
     */
    private @Nullable String findBinaryDirectory(@NotNull String binPath, @NotNull OCamlExecutable executable) {
        if (!executable.isNativeLocal()) {
            return null;
        }

        for (String candidate : new String[]{myBinary, myBinary + Platform.WINDOWS_EXECUTABLE_SUFFIX}) {
            if (new File(binPath, candidate).isFile()) {
                return binPath;
            }
        }

        LOG.debug("Not in the switch, falling back to a PATH lookup", myBinary);
        return null;
    }
}
