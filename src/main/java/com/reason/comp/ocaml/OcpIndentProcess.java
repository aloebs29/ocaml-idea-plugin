package com.reason.comp.ocaml;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.vfs.*;
import com.reason.ide.settings.*;
import jpsplugin.com.reason.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static jpsplugin.com.reason.Platform.*;

/**
 * Computes the indentation of a single line by delegating to <code>ocp-indent</code>, the reference OCaml
 * line indenter, taken from the configured opam switch.
 * <p>
 * Only the numeric mode is used (<code>--numeric --lines=n-n</code>): ocp-indent then prints the column the
 * line should start at instead of rewriting the code, which is exactly what a
 * {@link com.intellij.psi.codeStyle.lineIndent.LineIndentProvider} needs and keeps the answer independent of
 * whatever the line currently contains.
 * <p>
 * This runs on the EDT while the user types, so every failure mode is bounded: a binary that can't be started
 * is not retried for a while, and a process that does not answer in time is killed.
 */
@Service(Service.Level.PROJECT)
public final class OcpIndentProcess {
    private static final Log LOG = Log.create("format.ocaml.indent");

    /** Nothing typed can legitimately take that long; past it we would rather not indent than freeze the editor. */
    private static final long TIMEOUT_MS = 1_000;
    /**
     * How long a failure to start ocp-indent silences further attempts. Long enough that a missing binary
     * doesn't mean a process per keystroke, short enough that a failure caused by the opam environment not
     * being computed yet heals on its own.
     */
    private static final long RETRY_AFTER_MS = 30_000;

    private final Project myProject;
    /** Settings signature for which starting ocp-indent last failed, and until when to stop trying. */
    private volatile @Nullable String myUnavailableFor;
    private volatile long myUnavailableUntil;

    public OcpIndentProcess(Project project) {
        myProject = project;
    }

    /**
     * @param file       the edited file, used to locate the module and its opam switch
     * @param text       the source to indent, up to and including the line of interest
     * @param lineNumber the 1-based line to compute the indentation of
     * @return the number of columns the line should be indented by, or null if ocp-indent could not answer
     */
    public @Nullable Integer getIndent(@NotNull VirtualFile file, @NotNull String text, int lineNumber) {
        String settingsKey = getSettingsKey();
        if (settingsKey.equals(myUnavailableFor) && System.currentTimeMillis() < myUnavailableUntil) {
            return null;
        }

        GeneralCommandLine commandLine = new OcpIndentCommandLine(myProject, lineNumber).create(file);
        if (commandLine == null) {
            // No opam root configured, or the file belongs to no module. Worth saying out loud: otherwise
            // indentation just silently does nothing and there is no trace of why.
            silenceFor(settingsKey, "Can't build an ocp-indent command line for " + file.getName() +
                    ", so the line was left as it is. Check that the opam root and switch are set in" +
                    " Settings | Languages & Frameworks | OCaml | Opam.");
            return null;
        }

        Process indent = null;
        try {
            indent = commandLine.createProcess();

            // ocp-indent reads all of stdin before writing anything, and numeric mode answers with a handful of
            // bytes, so writing then reading can't deadlock on a full pipe.
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(indent.getOutputStream(), UTF8))) {
                writer.write(text);
            }

            if (!indent.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                LOG.warn("ocp-indent did not answer in " + TIMEOUT_MS + "ms, giving up");
                return null;
            }

            String out = new String(indent.getInputStream().readAllBytes(), UTF8);
            if (indent.exitValue() != 0) {
                LOG.warn("ocp-indent failed: " + StringUtil.trimLastCR(new String(indent.getErrorStream().readAllBytes(), UTF8)));
                return null;
            }

            return parseNumericOutput(out);
        } catch (ExecutionException e) {
            // Most likely not installed in the switch, or the opam environment is not computed yet.
            silenceFor(settingsKey, "Can't run ocp-indent (install it with `opam install ocp-indent`): " + e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | RuntimeException e) {
            LOG.warn(e);
            return null;
        } finally {
            if (indent != null) {
                indent.destroyForcibly();
            }
        }
    }

    /** Reports why indentation is off, then stops both the retries and the repeated logging for a while. */
    private void silenceFor(@NotNull String settingsKey, @NotNull String reason) {
        myUnavailableFor = settingsKey;
        myUnavailableUntil = System.currentTimeMillis() + RETRY_AFTER_MS;
        LOG.warn(reason + " Indentation on enter is off for the next " + (RETRY_AFTER_MS / 1000) + "s.");
    }

    /** Numeric mode prints one column number per requested line; we always ask for exactly one. */
    static @Nullable Integer parseNumericOutput(@NotNull String output) {
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                try {
                    int column = Integer.parseInt(trimmed);
                    return 0 <= column ? column : null;
                } catch (NumberFormatException e) {
                    LOG.warn("Unexpected ocp-indent output: " + trimmed);
                    return null;
                }
            }
        }
        return null; // asked for a line past the end of the text
    }

    private @NotNull String getSettingsKey() {
        ORSettings settings = myProject.getService(ORSettings.class);
        return settings.getOpamLocation() + "|" + settings.getSwitchName();
    }

    static class OcpIndentCommandLine extends OpamCommandLine {
        private final int myLineNumber;

        OcpIndentCommandLine(@NotNull Project project, int lineNumber) {
            super(project, "ocp-indent", false);
            myLineNumber = lineNumber;
        }

        @Override
        protected @NotNull List<String> getParameters() {
            return List.of("--numeric", "--lines=" + myLineNumber + "-" + myLineNumber);
        }
    }
}
