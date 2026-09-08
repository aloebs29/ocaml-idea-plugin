package com.reason.comp.ocaml;

import com.intellij.execution.configurations.*;
import jpsplugin.com.reason.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * Finds the `opam` binary of a local (non wsl, non cygwin) installation.
 * <p>
 * The IDE process often inherits a stale environment - typically when it is started by a long living
 * parent like the Toolbox or the desktop shell - so looking up PATH alone regularly fails even though
 * `opam` resolves fine in a terminal. When PATH doesn't help, well known install locations are probed
 * before giving up.
 */
public class OpamExecutableLocator {
    private static final Log LOG = Log.create("ocaml.opam");
    private static final String OPAM = "opam";

    private OpamExecutableLocator() {
    }

    /**
     * @param configuredExecutable explicit path coming from the settings, when the user configured one
     * @param executable           the environment opam is going to be run in
     * @return an absolute path when one could be found, the bare `opam` name otherwise
     */
    public static @NotNull String findOpamExecutable(@Nullable String configuredExecutable, @NotNull OCamlExecutable executable) {
        String configured = configuredExecutable == null ? "" : configuredExecutable.trim();
        if (!configured.isEmpty()) {
            return configured;
        }

        // wsl and cygwin resolve the binary in their own filesystem, using their own PATH
        if (!executable.isNativeLocal()) {
            return OPAM;
        }

        String binaryName = Platform.isWindows() ? OPAM + Platform.WINDOWS_EXECUTABLE_SUFFIX : OPAM;

        File inPath = PathEnvironmentVariableUtil.findInPath(binaryName);
        if (inPath != null) {
            return inPath.getPath();
        }

        for (String candidate : getCandidates(binaryName)) {
            File file = new File(candidate);
            if (file.exists() && !file.isDirectory()) {
                LOG.debug("Opam is not in PATH, found at", candidate);
                return candidate;
            }
        }

        LOG.warn("Opam not found in PATH nor in any well known location");
        return OPAM;
    }

    private static @NotNull List<String> getCandidates(@NotNull String binaryName) {
        List<String> candidates = new ArrayList<>();
        String home = System.getProperty("user.home");

        if (Platform.isWindows()) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData == null && home != null) {
                localAppData = home + "\\AppData\\Local";
            }
            if (localAppData != null) {
                candidates.add(localAppData + "\\Microsoft\\WinGet\\Links\\" + binaryName);
                candidates.add(localAppData + "\\Microsoft\\WindowsApps\\" + binaryName);
                candidates.add(localAppData + "\\Programs\\opam\\" + binaryName);
                candidates.add(localAppData + "\\opam\\" + binaryName);
            }
            String programFiles = System.getenv("ProgramFiles");
            if (programFiles != null) {
                candidates.add(programFiles + "\\opam\\" + binaryName);
            }
            if (home != null) {
                candidates.add(home + "\\bin\\" + binaryName);
            }
        } else {
            if (home != null) {
                candidates.add(home + "/.local/bin/" + binaryName);
                candidates.add(home + "/bin/" + binaryName);
            }
            candidates.add("/usr/local/bin/" + binaryName);
            candidates.add("/opt/homebrew/bin/" + binaryName);
            candidates.add("/opt/local/bin/" + binaryName);
            candidates.add("/usr/bin/" + binaryName);
        }

        return candidates;
    }
}
