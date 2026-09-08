package com.reason.ide.settings;

import com.intellij.openapi.application.*;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.*;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.*;
import com.intellij.openapi.roots.libraries.ui.*;
import com.intellij.openapi.roots.libraries.ui.impl.*;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.vfs.*;
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.*;
import com.reason.comp.dune.*;
import com.reason.comp.ocaml.*;
import com.reason.ide.console.*;
import com.reason.ide.library.*;
import jpsplugin.com.reason.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.nio.file.*;
import java.util.*;

import static com.intellij.openapi.application.ApplicationManager.getApplication;

public class ORSettingsConfigurable implements SearchableConfigurable, Configurable.NoScroll {
    private final @NotNull Project myProject;
    private ORSettings mySettings;

    private JPanel myRootPanel;
    private JTabbedPane myTabs;

    private boolean myIsWsl = false;
    private String myCygwinBash;

    // General
    private JTextField f_generalFormatWidthColumns;
    private JCheckBox f_generalIsFormatOnSave;
    private JCheckBox myUseSuperErrorsCheckBox;

    private OpamConfigurationTab myOpamConfigurationTab;

    public ORSettingsConfigurable(@NotNull Project project) {
        myProject = project;
    }

    @NotNull
    @Override
    public String getId() {
        return getHelpTopic();
    }

    @Nls
    @Override
    public @NotNull String getDisplayName() {
        return "OCaml";
    }

    @NotNull
    @Override
    public String getHelpTopic() {
        return "settings.ocaml";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        mySettings = myProject.getService(ORSettings.class);

        createGeneralTab();
        myOpamConfigurationTab.createComponent(mySettings.getProject(), mySettings.getSwitchName());

        return myRootPanel;
    }

    @Override
    public void apply() {
        // General
        mySettings.setFormatOnSaveEnabled(f_generalIsFormatOnSave.isSelected());
        mySettings.setFormatColumnWidth(sanitizeInput(f_generalFormatWidthColumns.getText()));
        mySettings.setUseSuperErrors(myUseSuperErrorsCheckBox.isSelected());
        // Opam
        mySettings.setOpamExecutable(sanitizeInput(myOpamConfigurationTab.getOpamExecutable()));
        mySettings.setOpamLocation(sanitizeInput(myOpamConfigurationTab.getOpamLocation()));
        mySettings.setCygwinBash(myCygwinBash);
        mySettings.setIsWsl(myIsWsl);
        mySettings.setSwitchName(myOpamConfigurationTab.getSelectedSwitch());

        // Create external library based on the selected opam switch
        createExternalLibraryDependency(mySettings.getProject(), mySettings.getSwitchName(), mySettings.getOpamLocation());
        // Compute env
        OpamEnv opamEnv = getApplication().getService(OpamEnv.class);
        opamEnv.computeEnv(mySettings.getOpamExecutable(), mySettings.getOpamLocation(), mySettings.getSwitchName(), mySettings.getCygwinBash(), null);
        // Display compiler info in console (if any)
        myProject.getService(ORToolWindowManager.class).shouldShowToolWindows();
    }

    private void createExternalLibraryDependency(@NotNull Project project, @NotNull String switchName, String opamLocation) {
        if (switchName.isEmpty()) {
            return;
        }

        LibraryTable projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
        LibraryTable.ModifiableModel projectLibraryTableModel = projectLibraryTable.getModifiableModel();

        String libraryName = "switch:" + switchName;

        // Remove existing lib
        Library oldLibrary = projectLibraryTableModel.getLibraryByName(libraryName);
        VirtualFile opamRootCandidate = VirtualFileManager.getInstance().findFileByNioPath(Path.of(opamLocation, switchName));
        if (opamRootCandidate != null && opamRootCandidate.exists() && opamRootCandidate.isValid()) {
            Library library = oldLibrary == null ? projectLibraryTableModel.createLibrary(libraryName, OclLibraryKind.INSTANCE) : null;
            Library.ModifiableModel libraryModel = library == null ? null : library.getModifiableModel();

            if (libraryModel != null) {
                OclLibraryType libraryType = (OclLibraryType) LibraryType.findByKind(OclLibraryKind.INSTANCE);
                LibraryRootsComponentDescriptor rootsComponentDescriptor = libraryType.createLibraryRootsComponentDescriptor();
                List<OrderRoot> orderRoots = RootDetectionUtil.detectRoots(Collections.singleton(opamRootCandidate), myRootPanel, project, rootsComponentDescriptor);
                for (OrderRoot orderRoot : orderRoots) {
                    libraryModel.addRoot(orderRoot.getFile(), orderRoot.getType());
                }
            }

            ApplicationManager.getApplication().invokeAndWait(() -> WriteAction.run(() -> {
                if (libraryModel != null) {
                    libraryModel.commit();
                    projectLibraryTableModel.commit();
                }

                // Find module that contains dune config root file
                Map<Module, VirtualFile> duneContentRoots = Platform.findModulesFor(project, DunePlatform.DUNE_PROJECT_FILENAME);
                for (Module module : duneContentRoots.keySet()) {
                    ModuleRootModificationUtil.updateModel(module, moduleModel -> {
                        // Remove all libraries entries that are of type Ocaml
                        moduleModel.orderEntries().forEach(entry -> {
                            Library entryLibrary = (entry instanceof LibraryOrderEntry) ? ((LibraryOrderEntry) entry).getLibrary() : null;
                            PersistentLibraryKind<?> entryLibraryKind = (entryLibrary instanceof LibraryBridge) ? ((LibraryBridge) entryLibrary).getKind() : null;
                            if (entryLibraryKind instanceof OclLibraryKind) {
                                moduleModel.removeOrderEntry(entry);
                            }

                            return true;
                        });
                        // Add the new lib as order entry
                        moduleModel.addLibraryEntry(library == null ? oldLibrary : library);
                    });
                }
            }));
        }
    }

    @Override
    public boolean isModified() {
        // General
        boolean isFormatOnSaveModified =
                f_generalIsFormatOnSave.isSelected() != mySettings.isFormatOnSaveEnabled();
        boolean isFormatWidthColumnsModified =
                !f_generalFormatWidthColumns.getText().equals(mySettings.getFormatColumnWidth());
        boolean isUseSuperErrorModified = myUseSuperErrorsCheckBox.isSelected() != mySettings.isUseSuperErrors();
        // Opam
        boolean isOpamExecutableModified = myOpamConfigurationTab.isOpamExecutableModified(mySettings.getOpamExecutable());
        boolean isOpamLocationModified = myOpamConfigurationTab.isOpamLocationModified(mySettings.getOpamLocation());
        boolean isOpamSwitchModified = myOpamConfigurationTab.isOpamSwitchModified(mySettings.getSwitchName());

        return isFormatOnSaveModified || isFormatWidthColumnsModified || isUseSuperErrorModified
                || isOpamExecutableModified || isOpamLocationModified || isOpamSwitchModified;
    }

    @Override
    public void reset() {
        // General
        f_generalIsFormatOnSave.setSelected(mySettings.isFormatOnSaveEnabled());
        f_generalFormatWidthColumns.setText(mySettings.getFormatColumnWidth());
        myUseSuperErrorsCheckBox.setSelected(mySettings.isUseSuperErrors());
        // Opam
        myCygwinBash = mySettings.getCygwinBash();
        myIsWsl = mySettings.isWsl();
        myOpamConfigurationTab.setOpamExecutable(mySettings.getOpamExecutable());
        myOpamConfigurationTab.setOpamLocation(mySettings.getOpamLocation());
        myOpamConfigurationTab.setDetectionText();
        myOpamConfigurationTab.createSwitch(mySettings.getOpamLocation(), mySettings.getSwitchName());
    }

    private void createGeneralTab() {
    }

    private static @NotNull String sanitizeInput(@NotNull TextFieldWithBrowseButton textFieldWithBrowseButton) {
        return sanitizeInput(textFieldWithBrowseButton.getText());
    }

    private static @NotNull String sanitizeInput(@NotNull String input) {
        return input.trim();
    }
}
