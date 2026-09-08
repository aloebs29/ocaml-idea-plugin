package com.reason.ide.library;

import com.intellij.navigation.*;
import com.intellij.openapi.application.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.*;
import com.reason.ide.*;
import com.reason.ide.settings.*;
import jpsplugin.com.reason.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Manage external library based on opam settings.
 * It is updated automatically when module entries are changed.
 */
public class OclLibraryRootProvider extends AdditionalLibraryRootsProvider {
    private static final Log LOG = Log.create("library.rootProvider");

    private final AtomicBoolean myVfsWarmUpStarted = new AtomicBoolean(false);

    @Override
    public @NotNull Collection<SyntheticLibrary> getAdditionalProjectLibraries(@NotNull Project project) {
        // checkReadAccessAllowed
        LOG.debug("Get additional project libraries");

        ORSettings settings = project.getService(ORSettings.class);
        String opamLocation = settings.getOpamLocation();
        String opamSwitch = settings.getSwitchName();
        if (opamLocation.isEmpty() && opamSwitch.isEmpty()) {
            return Collections.emptyList();
        }

        OpamLibrary library = new OpamLibrary(opamLocation, opamSwitch);
        if (library.getSourceRoots().isEmpty()) {
            warmUpVfs(project, opamLocation, opamSwitch);
        }

        return List.of(library);
    }

    /**
     * The switch lives outside of the project, so on a cold start the vfs usually knows nothing about it
     * and the library ends up with no source root at all - nothing gets indexed, and nothing from the
     * switch can be resolved. This runs under a read action, where a synchronous refresh is forbidden,
     * so the vfs is warmed up on a pooled thread and the library is rebuilt once the files are known.
     */
    private void warmUpVfs(@NotNull Project project, @NotNull String opamLocation, @NotNull String opamSwitch) {
        if (!myVfsWarmUpStarted.compareAndSet(false, true)) {
            return;
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                Path switchPath = Path.of(opamLocation, opamSwitch);
                VirtualFile switchDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(switchPath);
                if (switchDir == null || project.isDisposed()) {
                    LOG.debug("Opam switch not found on disk", switchPath);
                    return;
                }

                Collection<VirtualFile> roots = new OpamLibrary(opamLocation, opamSwitch).getSourceRoots();
                LOG.debug("Opam switch loaded in vfs, source roots", roots);
                if (!roots.isEmpty()) {
                    ApplicationManager.getApplication().invokeLater(
                            () -> WriteAction.run(() -> AdditionalLibraryRootsListener.fireAdditionalLibraryChanged(
                                    project, "Opam switch <" + opamSwitch + ">", Collections.emptyList(), roots, "opam")),
                            project.getDisposed());
                }
            } finally {
                myVfsWarmUpStarted.set(false);
            }
        });
    }

    @Override
    public @NotNull Collection<VirtualFile> getRootsToWatch(@NotNull Project project) {
        Collection<SyntheticLibrary> libraries = getAdditionalProjectLibraries(project);
        LOG.debug("Roots to watch", libraries);

        return libraries.stream()
                .map(lib -> Collections.singleton(((OpamLibrary) lib).getOpamSwitchLocation()))
                .collect(ArrayList::new, Collection::addAll, Collection::addAll);
    }

    static final class OpamLibrary extends SyntheticLibrary implements ItemPresentation {
        private final Collection<VirtualFile> mySourceRoots = new ArrayList<>();
        private final String myOpamRoot;
        private final String myOpamSwitch;

        private @Nullable VirtualFile getOpamSwitchLocation() {
            try {
                return VirtualFileManager.getInstance().findFileByNioPath(Path.of(myOpamRoot, myOpamSwitch));
            } catch (InvalidPathException e) {
                return null;
            }
        }

        public OpamLibrary(@NotNull String opamRoot, @NotNull String opamSwitch) {
            myOpamRoot = opamRoot;
            myOpamSwitch = opamSwitch;

            VirtualFile opamFile = getOpamSwitchLocation();
            if (opamFile != null) {
                VfsUtilCore.visitChildrenRecursively(opamFile, new VirtualFileVisitor<VirtualFile>() {
                    @Override
                    public @NotNull Result visitFileEx(@NotNull VirtualFile file) {
                        if (file.isDirectory()) {
                            String fileName = file.getName();
                            if (fileName.startsWith(".") || fileName.equals("doc")) {
                                return SKIP_CHILDREN;
                            }

                            List<VirtualFile> children = VfsUtil.getChildren(file, child -> {
                                String childName = child.getName();
                                return childName.endsWith(".ml") || childName.endsWith(".mli");
                            });

                            if (!children.isEmpty()) {
                                mySourceRoots.add(file);
                                return SKIP_CHILDREN;
                            }
                        }

                        return CONTINUE;
                    }
                });
            }
        }

        @Override
        public @NotNull Collection<VirtualFile> getSourceRoots() {
            return mySourceRoots;
        }

        @Override
        public @NotNull String getPresentableText() {
            return "Opam switch <" + myOpamSwitch + ">";
        }

        @Override
        public @NotNull String getLocationString() {
            try {
                return Path.of(myOpamRoot, myOpamSwitch).toString();
            } catch (InvalidPathException e) {
                return "";
            }
        }

        @Override
        public @NotNull Icon getIcon(boolean unused) {
            return ORIcons.OCL_SDK;
        }

        // Comparing the roots matters: the platform uses equality to detect that the library changed, and
        // it does change - the roots are empty until the switch has been loaded in the vfs.
        @Override
        public boolean equals(Object other) {
            return other instanceof OpamLibrary opamLibrary
                    && myOpamRoot.equals(opamLibrary.myOpamRoot)
                    && myOpamSwitch.equals(opamLibrary.myOpamSwitch)
                    && mySourceRoots.equals(opamLibrary.mySourceRoots);
        }

        @Override
        public int hashCode() {
            return Objects.hash(myOpamRoot, myOpamSwitch, mySourceRoots);
        }
    }
}
