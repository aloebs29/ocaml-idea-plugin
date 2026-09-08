package com.reason.hints;

import com.intellij.openapi.application.*;
import com.intellij.openapi.components.*;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.vfs.*;
import com.reason.comp.*;
import com.reason.ide.hints.*;
import jpsplugin.com.reason.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static jpsplugin.com.reason.Platform.*;

@Service(Service.Level.PROJECT)
public final class InsightManager {
    private static final Log LOG = Log.create("hints");

    final @NotNull AtomicBoolean isDownloading = new AtomicBoolean(false);
    /**
     * Rincewind binaries that could not be downloaded. There is no published build for every ocaml
     * version (nothing above 4.14 at the moment), so retrying on every opened file would only produce
     * a stream of identical failures.
     */
    private final @NotNull Set<String> myUnavailable = ConcurrentHashMap.newKeySet();
    private final @NotNull Project myProject;

    InsightManager(@NotNull Project project) {
        myProject = project;
    }

    public void downloadRincewindIfNeeded(@NotNull VirtualFile sourceFile) {
        VirtualFile parentFile = sourceFile.getParent();
        if (parentFile == null) {
            LOG.debug("Can't get parent file", sourceFile);
            return;
        }

        String rincewindName = ReadAction.compute(() -> getRincewindFilename(parentFile, ""));
        if (rincewindName == null) {
            LOG.debug("No rincewind version found, abort downloading");
            return;
        }

        if (myUnavailable.contains(rincewindName)) {
            LOG.debug("No rincewind binary available, skip downloading", rincewindName);
            return;
        }

        File targetFile = getRincewindTarget(rincewindName);
        if (targetFile != null && !targetFile.exists()) {
            ProgressManager.getInstance().run(new RincewindDownloader(myProject, targetFile));
        }
    }

    /** Remembers that a binary can't be downloaded, so that it is not attempted again in this session. */
    void markUnavailable(@NotNull String rincewindName) {
        myUnavailable.add(rincewindName);
    }

    public void queryTypes(@Nullable VirtualFile sourceFile, @NotNull Path cmtPath, @NotNull ORProcessTerminated<InferredTypes> runAfter) {
        VirtualFile sourceParentFile = sourceFile != null ? sourceFile.getParent() : null;
        String rincewindName = getRincewindFilename(sourceParentFile, "");
        File rincewindFile = getRincewindTarget(rincewindName);
        if (sourceFile != null && rincewindFile != null) {
            myProject.getService(RincewindProcess.class).types(sourceFile, rincewindFile.getPath(), cmtPath.toString(), runAfter);
        }
    }

    public @NotNull List<String> dumpMeta(@NotNull VirtualFile cmtFile) {
        String rincewindName = getRincewindFilename(cmtFile.getParent(), "0.4");
        File rincewindFile = rincewindName == null ? null : getRincewindTarget(rincewindName);
        return rincewindFile == null
                ? Collections.emptyList()
                : myProject.getService(RincewindProcess.class).dumpMeta(rincewindFile.getPath(), cmtFile);
    }

    public @NotNull String dumpTree(@NotNull VirtualFile cmtFile) {
        String rincewindName = getRincewindFilename(cmtFile.getParent(), "");
        File rincewindFile = rincewindName == null ? null : getRincewindTarget(rincewindName);
        return rincewindFile == null
                ? "<unknown>\n  <reason>rincewindFile not found</reason>\n  <file>" + cmtFile.getPath() + "</file>\n</unknown/>"
                : myProject.getService(RincewindProcess.class).dumpTree(cmtFile, rincewindFile.getPath());
    }

    public @NotNull List<String> dumpInferredTypes(@NotNull VirtualFile cmtFile) {
        String rincewindName = getRincewindFilename(cmtFile.getParent(), "");
        File rincewindFile = rincewindName == null ? null : getRincewindTarget(rincewindName);
        return rincewindFile == null
                ? Collections.emptyList()
                : myProject.getService(RincewindProcess.class).dumpTypes(rincewindFile.getPath(), cmtFile);
    }

    /**
     * Binaries are kept outside of the plugin directory: that directory is wiped when the plugin is
     * updated or re-installed, which would silently discard a binary that was built by hand.
     * A binary sitting in the old location is still honoured, so existing installations keep working.
     */
    @Nullable File getRincewindTarget(@Nullable String filename) {
        if (filename == null) {
            return null;
        }

        File target = new File(getRincewindDirectory(), filename);
        if (!target.exists()) {
            Path pluginLocation = getPluginLocation();
            File legacyTarget = pluginLocation == null ? null : new File(pluginLocation.toFile(), filename);
            if (legacyTarget != null && legacyTarget.exists()) {
                LOG.debug("Rincewind found in the plugin directory", legacyTarget);
                return legacyTarget;
            }
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Rincewind filename: " + filename + " at " + target.getParent());
        }
        return target;
    }

    /** Where rincewind binaries are downloaded to, created if needed. */
    public static @NotNull File getRincewindDirectory() {
        File directory = new File(PathManager.getSystemPath(), "reasonml");
        if (!directory.exists() && !directory.mkdirs()) {
            LOG.warn("Can't create " + directory + ", falling back to the temp directory");
            return new File(System.getProperty("java.io.tmpdir"));
        }
        return directory;
    }

    @Nullable String getRincewindFilename(@Nullable VirtualFile sourceFile, @NotNull String excludedVersion) {
        if (sourceFile != null) {
            ORCompilerManager compilerManager = myProject.getService(ORCompilerManager.class);
            ORResolvedCompiler<?> compiler = compilerManager.getCompiler(sourceFile);
            String fullVersion = compiler != null ? compiler.getFullVersion() : null;
            String ocamlVersion = Rincewind.extractOcamlVersion(fullVersion);
            String rincewindVersion = Rincewind.getLatestVersion(ocamlVersion);

            // ocaml version default - opam -> use ocaml -version ??
            // opam switch different from default
            // opam settings set correctly (not default)

            if (ocamlVersion != null && !rincewindVersion.equals(excludedVersion)) {
                return "rincewind_" + getOsPrefix() + ocamlVersion + "-" + rincewindVersion + ".exe";
            }
        }

        return null;
    }
}
