package com.reason.comp.ocaml;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.*;
import com.intellij.openapi.components.*;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import jpsplugin.com.reason.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.regex.*;

@Service(Service.Level.APP)
public final class OpamProcess {
    private static final Log LOG = Log.create("ocaml.opam");
    private static final Pattern SEXP = Pattern.compile("\\(\"([^\"]+)\" \"([^\"]+)\"\\)");
    public static final ProcessHandler NULL_HANDLER = new ProcessHandler() {
        @Override protected void destroyProcessImpl() {
        }

        @Override protected void detachProcessImpl() {
        }

        @Override public boolean detachIsDefault() {
            return false;
        }

        @Nullable @Override public OutputStream getProcessInput() {
            return null;
        }
    };

    /**
     * Builds an opam command line.
     * <p>
     * The binary is resolved explicitly, because the PATH inherited by the IDE process is not reliable,
     * and the configured opam root is passed with {@code --root} so that the setting is actually
     * honoured instead of letting opam fall back to its own default root.
     */
    private @NotNull GeneralCommandLine createCli(@Nullable String opamExecutable, @NotNull String opamLocation,
                                                  @Nullable String cygwinBash, boolean redirectErrorStream,
                                                  String... parameters) {
        OCamlExecutable executable = OCamlExecutable.getExecutable(opamLocation, cygwinBash);
        String binary = OpamExecutableLocator.findOpamExecutable(opamExecutable, executable);

        List<String> params = new ArrayList<>(Arrays.asList(parameters));
        if (!opamLocation.isEmpty()) {
            params.add("--root=" + executable.toExecutablePath(opamLocation));
        }

        GeneralCommandLine cli = new GeneralCommandLine(ContainerUtil.prepend(params, binary));
        cli.setRedirectErrorStream(redirectErrorStream);

        return executable.patchCommandLine(cli, null, true);
    }

    private static @NotNull String withHint(@Nullable String message) {
        return (message == null ? "" : message)
                + "\nSet the opam executable explicitly in Settings | Languages & Frameworks | OCaml | Opam.";
    }

    public void list(@Nullable String opamExecutable, @NotNull String opamLocation, @NotNull String version, @Nullable String cygwinBash, @NotNull ORProcessTerminated<List<String[]>> onProcessTerminated) {
        ArrayList<String[]> installedLibs = new ArrayList<>();

        if (StringUtil.isEmpty(opamLocation) || StringUtil.isEmpty(version)) {
            onProcessTerminated.run(installedLibs);
            return;
        }

        GeneralCommandLine cli = createCli(opamExecutable, opamLocation, cygwinBash, true,
                "list", "--installed", "--safe", "--color=never", "--switch=" + version);

        KillableProcessHandler processHandler;
        try {
            processHandler = new KillableProcessHandler(cli);
            processHandler.addProcessListener(new ProcessListener() {
                @Override
                public void processTerminated(@NotNull ProcessEvent event) {
                    onProcessTerminated.run(installedLibs);
                }

                @Override
                public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                    if (ProcessOutputType.isStdout(outputType)) {
                        String text = event.getText().trim();
                        if (!text.isEmpty() && text.charAt(0) != '#') {
                            String[] split = text.split("\\s+", 3);
                            installedLibs.add(new String[]{split[0].trim(), split.length >= 2 ? split[1].trim() : "unknown", split.length >= 3 ? split[2].trim() : ""});
                        }
                    }
                }
            });
            processHandler.startNotify();
        } catch (ExecutionException e) {
            ORNotification.notifyError("Opam", "Can not list libraries", withHint(e.getMessage()));
            installedLibs.add(new String[]{"Error", e.getMessage()});
            onProcessTerminated.run(installedLibs);
        }
    }

    public void env(@Nullable String opamExecutable, @Nullable String opamLocation, @Nullable String version, @Nullable String cygwinBash, @NotNull ORProcessTerminated<Map<String, String>> onProcessTerminated) {
        Map<String, String> result = new HashMap<>();

        if (StringUtil.isEmpty(opamLocation) || StringUtil.isEmpty(version)) {
            result.put("Incorrect setting", "Setup SDK in project settings");
            onProcessTerminated.run(result);
            return;
        }

        GeneralCommandLine cli = createCli(opamExecutable, opamLocation, cygwinBash, true,
                "config", "env", "--sexp", "--switch=" + version);

        KillableProcessHandler processHandler;
        try {
            processHandler = new KillableProcessHandler(cli);
            processHandler.addProcessListener(new ProcessListener() {
                @Override
                public void processTerminated(@NotNull ProcessEvent event) {
                    onProcessTerminated.run(result);
                }

                @Override
                public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                    if (ProcessOutputType.isStdout(outputType)) {
                        String text = event.getText().trim();
                        Matcher matcher = SEXP.matcher(text);
                        if (matcher.matches()) {
                            String key = matcher.group(1);
                            String value = matcher.group(2);
                            result.put(key, value);
                        }
                    }
                }
            });
            processHandler.startNotify();
        } catch (ExecutionException e) {
            ORNotification.notifyError("Opam", "Can not read opam env", withHint(e.getMessage()));
            onProcessTerminated.run(result);
        }
    }

    public void listSwitch(@Nullable String opamExecutable, @NotNull String opamRootPath, @Nullable String cygwinBash, @NotNull ORProcessTerminated<List<OpamSwitch>> onProcessTerminated) {
        ProcessListener processListener = new ListProcessListener(onProcessTerminated);

        // stderr is deliberately not redirected: opam warnings would be interleaved with the table
        GeneralCommandLine cli = createCli(opamExecutable, opamRootPath, cygwinBash, false,
                "switch", "list", "--color=never");
        LOG.debug("List switches", cli.getCommandLineString());

        KillableProcessHandler processHandler;
        try {
            processHandler = new KillableProcessHandler(cli);
            processHandler.addProcessListener(processListener);
            processHandler.startNotify();
        } catch (ExecutionException e) {
            ORNotification.notifyError("Opam", "Can not run opam", withHint(e.getMessage()));
            processListener.processTerminated(new ProcessEvent(NULL_HANDLER));
        }
    }

    public record OpamSwitch(boolean isSelected, String name) {
        @Override public String toString() {
            return (isSelected ? ">" : "") + name;
        }
    }

    static class ListProcessListener implements ProcessListener {
        private final ORProcessTerminated<List<OpamSwitch>> myOnProcessTerminated;
        private final List<OpamSwitch> myResult = new ArrayList<>();

        private boolean myIsFooter = false;

        public ListProcessListener(@NotNull ORProcessTerminated<List<OpamSwitch>> onProcessTerminated) {
            myOnProcessTerminated = onProcessTerminated;
        }

        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
            myOnProcessTerminated.run(myResult);
        }

        @Override
        public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
            if (!ProcessOutputType.isStdout(outputType)) {
                return;
            }

            String line = event.getText();
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.charAt(0) == '#') { // blank line or header
                return;
            }
            if (trimmed.charAt(0) == '[') { // [WARNING] / [NOTE] and everything that follows it
                myIsFooter = true;
                return;
            }
            if (myIsFooter) {
                return;
            }

            // a switch is selected when the marker column is not empty
            boolean isSelected = !Character.isWhitespace(line.charAt(0));
            // when the marker is empty, the split yields a leading empty token, so the name is always at index 1
            String[] tokens = line.split("\\s+");
            if (tokens.length >= 2 && !tokens[1].isEmpty()) {
                myResult.add(new OpamSwitch(isSelected, tokens[1]));
            }
        }
    }
}
