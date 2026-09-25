package org.example.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import org.example.launcher.instance.InstanceInstaller;
import org.example.launcher.model.Instance;
import org.example.launcher.model.InstancePreset;
import org.example.launcher.modrinth.ModrinthContentType;
import org.example.launcher.modrinth.ModrinthProject;
import org.example.launcher.service.*;
import org.example.launcher.update.*;
import org.example.ui.components.NotificationManager;
import org.example.ui.components.Sidebar;
import org.example.ui.views.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class LauncherView {

    private final StackPane root;
    private final BorderPane window;

    private final AccountService accountService;
    private final LaunchService launchService;

    private final Sidebar sidebar;
    private final StackPane content;

    private final HomeView homeView;
    private final AccountsView accountsView;
    private final InstancesView instancesView;
    private final GlobalModsView globalModsView;
    private final SettingsView settingsView;
    private final CreateInstanceTypeView createInstanceTypeView;

    private final NotificationManager notifications;

    private final TitleBar titleBar;

    private Instance selectedInstance;

    public LauncherView(Stage stage) {

        root =
                new StackPane();

        window =
                new BorderPane();

        window.getStyleClass().add(
                "launcher"
        );

        root.getChildren().add(
                window
        );

        notifications =
                new NotificationManager(
                        root
                );

        window.prefWidthProperty().bind(
                root.widthProperty()
        );

        window.prefHeightProperty().bind(
                root.heightProperty()
        );

        window.maxWidthProperty().bind(
                root.widthProperty()
        );

        window.maxHeightProperty().bind(
                root.heightProperty()
        );

        Rectangle clip =
                new Rectangle();

        clip.setArcWidth(
                24
        );

        clip.setArcHeight(
                24
        );

        clip.widthProperty().bind(
                root.widthProperty()
        );

        clip.heightProperty().bind(
                root.heightProperty()
        );

        window.setClip(
                clip
        );

        accountService =
                new AccountService();

        launchService =
                new LaunchService(
                        accountService
                );

        titleBar =
                new TitleBar(
                        stage,
                        accountService
                );

        window.setTop(
                titleBar
        );

        sidebar =
                new Sidebar();

        BorderPane.setMargin(
                sidebar,
                new Insets(
                        16
                )
        );

        window.setLeft(
                sidebar
        );

        content =
                new StackPane();

        content.getStyleClass().add(
                "content"
        );

        content.setPadding(
                new Insets(
                        16,
                        16,
                        16,
                        0
                )
        );

        window.setCenter(
                content
        );

        homeView =
                new HomeView(
                        accountService,
                        launchService,
                        this::showRepairView
                );

        accountsView =
                new AccountsView(
                        accountService
                );

        instancesView =
                new InstancesView(
                        launchService,
                        this::showCreateInstanceView,
                        this::selectInstance,
                        this::showInstanceMods
                );

        createInstanceTypeView =
                new CreateInstanceTypeView(
                        this::showCreateInstanceView,
                        this::showCustomCreateInstanceView,
                        this::showImportInstanceView,
                        this::showPresetInstanceView,
                        this::showModrinthModpackView
                );

        globalModsView =
                new GlobalModsView(
                        this::showGlobalModDetails
                );

        settingsView =
                new SettingsView();

        sidebar.setOnPageSelected(
                this::showPage
        );

        showPage(
                Sidebar.Page.HOME
        );

        loadAccount();
        checkForUpdates();
    }

    private void showPage(
            Sidebar.Page page
    ) {

        sidebar.setSelectedPage(
                page
        );

        switch (page) {

            case HOME -> content.getChildren().setAll(
                    homeView
            );

            case ACCOUNTS -> content.getChildren().setAll(
                    accountsView
            );

            case INSTANCES -> content.getChildren().setAll(
                    instancesView
            );

            case MODS -> content.getChildren().setAll(
                    globalModsView
            );

            case SETTINGS -> content.getChildren().setAll(
                    settingsView
            );
        }
    }

    // =============================================================
    // INSTALLED CONTENT
    // =============================================================

    private void showInstanceMods(
            Instance instance
    ) {

        selectedInstance =
                instance;

        ModsView view =
                new ModsView(
                        instance,
                        () ->
                                showBrowseContent(
                                        instance,
                                        ModrinthContentType.MOD
                                ),
                        () ->
                                showInstanceSettings(
                                        instance
                                ),
                        () ->
                                showScreenshots(
                                        instance
                                )
                );

        content.getChildren().setAll(
                view
        );
    }

    // =============================================================
    // BROWSE CONTENT
    // =============================================================

    private void showBrowseContent(
            Instance instance,
            ModrinthContentType contentType
    ) {

        final ContentBrowserView[] viewHolder =
                new ContentBrowserView[1];

        viewHolder[0] =
                new ContentBrowserView(
                        instance,
                        contentType,
                        () ->
                                showInstanceMods(
                                        instance
                                ),
                        project ->
                                showContentDetails(
                                        instance,
                                        project,
                                        viewHolder[0].getContentType()
                                )
                );

        content.getChildren().setAll(
                viewHolder[0]
        );
    }

    // =============================================================
    // CONTENT DETAILS
    // =============================================================

    private void showContentDetails(
            Instance instance,
            ModrinthProject project,
            ModrinthContentType contentType
    ) {

        content.getChildren().setAll(
                new ModDetailsView(
                        instance,
                        project.getProjectId(),
                        contentType,
                        () ->
                                showBrowseContent(
                                        instance,
                                        contentType
                                )
                )
        );
    }

    // =============================================================
    // CREATE INSTANCE
    // =============================================================

    private void showCreateInstanceView() {

        content.getChildren().setAll(
                createInstanceTypeView
        );
    }

    private void showCustomCreateInstanceView() {

        CreateInstanceView createInstanceView =
                new CreateInstanceView(
                        this::showCreateInstanceView,
                        this::instanceCreated
                );

        content.getChildren().setAll(
                createInstanceView
        );
    }

    private void showImportInstanceView() {

        ImportInstanceView importInstanceView =
                new ImportInstanceView(
                        this::showCreateInstanceView,
                        this::instanceCreated
                );

        content.getChildren().setAll(
                importInstanceView
        );
    }

    private void showPresetInstanceView() {

        InstancePresetView presetView =
                new InstancePresetView(
                        this::showPresetConfigurationView,
                        this::showCreateInstanceView
                );

        content.getChildren().setAll(
                presetView
        );
    }

    private void showPresetConfigurationView(
            InstancePreset preset
    ) {

        PresetConfigurationView configurationView =
                new PresetConfigurationView(
                        preset,
                        this::createPresetInstance,
                        this::showPresetInstanceView
                );

        content.getChildren().setAll(
                configurationView
        );
    }


    private void createPresetInstance(
            InstancePreset preset,
            String name,
            String minecraftVersion
    ) {

        notifications.showProgress(
                "Installing " + preset.getName(),
                "Preparing Minecraft "
                        + minecraftVersion
                        + "..."
        );

        Thread thread =
                new Thread(() -> {

                    try {

                        notifications.updateProgress(
                                "Installing Minecraft "
                                        + minecraftVersion
                                        + "..."
                        );

                        Instance instance;

                        if ("Fabric".equalsIgnoreCase(
                                preset.getLoader()
                        )) {

                            instance =
                                    InstanceInstaller.installFabric(
                                            name,
                                            minecraftVersion
                                    );

                        } else {

                            instance =
                                    InstanceInstaller.installVanilla(
                                            name,
                                            minecraftVersion
                                    );
                        }

                        ModrinthService modrinthService =
                                new ModrinthService();

                        List<String> skippedMods =
                                new ArrayList<>();

                        List<String> skippedShaders =
                                new ArrayList<>();

                        // =====================================================
                        // INSTALL MODS
                        // =====================================================

                        if (preset.getMods() != null) {

                            for (String modSlug :
                                    preset.getMods()) {

                                notifications.updateProgress(
                                        "Installing "
                                                + modSlug
                                                + "..."
                                );

                                System.out.println(
                                        "Resolving preset mod: "
                                                + modSlug
                                );

                                try {

                                    ModrinthProject project =
                                            modrinthService.getProjectBySlug(
                                                    modSlug
                                            );

                                    if (project == null) {

                                        skippedMods.add(
                                                modSlug
                                        );

                                        System.out.println(
                                                "[Vanta] Skipping preset mod "
                                                        + modSlug
                                                        + ": project not found."
                                        );

                                        continue;
                                    }

                                    modrinthService.installMod(
                                            instance,
                                            project
                                    );

                                } catch (IOException
                                         | InterruptedException e) {

                                    String message =
                                            e.getMessage();

                                    if (message != null
                                            && message.contains(
                                            "No compatible version found"
                                    )) {

                                        skippedMods.add(
                                                modSlug
                                        );

                                        System.out.println(
                                                "[Vanta] Skipping preset mod "
                                                        + modSlug
                                                        + ": no compatible version found."
                                        );

                                        continue;
                                    }

                                    throw e;
                                }
                            }
                        }

                        // =====================================================
                        // INSTALL SHADERS
                        // =====================================================

                        if (preset.getShaders() != null) {

                            for (String shaderSlug :
                                    preset.getShaders()) {

                                notifications.updateProgress(
                                        "Installing shader "
                                                + shaderSlug
                                                + "..."
                                );

                                System.out.println(
                                        "Resolving preset shader: "
                                                + shaderSlug
                                );

                                try {

                                    ModrinthProject project =
                                            modrinthService.getProjectBySlug(
                                                    shaderSlug
                                            );

                                    if (project == null) {

                                        skippedShaders.add(
                                                shaderSlug
                                        );

                                        System.out.println(
                                                "[Vanta] Skipping preset shader "
                                                        + shaderSlug
                                                        + ": project not found."
                                        );

                                        continue;
                                    }

                                    modrinthService.installShader(
                                            instance,
                                            project
                                    );

                                } catch (IOException
                                         | InterruptedException e) {

                                    String message =
                                            e.getMessage();

                                    if (message != null
                                            && message.contains(
                                            "No compatible shader version found"
                                    )) {

                                        skippedShaders.add(
                                                shaderSlug
                                        );

                                        System.out.println(
                                                "[Vanta] Skipping preset shader "
                                                        + shaderSlug
                                                        + ": no compatible version found."
                                        );

                                        continue;
                                    }

                                    throw e;
                                }
                            }
                        }

                        // =====================================================
                        // RESULT
                        // =====================================================

                        List<String> skipped =
                                new ArrayList<>();

                        skipped.addAll(
                                skippedMods
                        );

                        skipped.addAll(
                                skippedShaders
                        );

                        if (skipped.isEmpty()) {

                            notifications.success(
                                    "Installation complete",
                                    preset.getName()
                                            + " ("
                                            + minecraftVersion
                                            + ") is ready to play."
                            );

                        } else {

                            String skippedText =
                                    String.join(
                                            ", ",
                                            skipped
                                    );

                            notifications.success(
                                    "Installation complete",
                                    preset.getName()
                                            + " ("
                                            + minecraftVersion
                                            + ") is ready. "
                                            + "Could not install: "
                                            + skippedText
                            );
                        }

                        Platform.runLater(
                                this::instanceCreated
                        );

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        notifications.error(
                                "Installation failed",
                                getErrorMessage(ex)
                        );

                        Platform.runLater(() ->
                                showPresetConfigurationView(
                                        preset
                                )
                        );
                    }

                });

        thread.setName(
                "Vanta-Preset-Installer"
        );

        thread.setDaemon(
                true
        );

        thread.start();
    }



    private String getErrorMessage(
            Throwable throwable
    ) {

        Throwable current =
                throwable;

        while (current.getCause() != null) {

            current = current.getCause();
        }

        String message =
                current.getMessage();

        if (message == null
                || message.isBlank()) {

            return current
                    .getClass()
                    .getSimpleName();
        }

        return message;
    }

    // =============================================================
    // MODRINTH MODPACKS
    // =============================================================

    private void showModrinthModpackView() {

        ModpackBrowserView modpackBrowserView =
                new ModpackBrowserView(
                        "1.21.11",
                        this::showCreateInstanceView,
                        this::instanceCreated
                );

        content.getChildren().setAll(
                modpackBrowserView
        );
    }

    // =============================================================
    // INSTANCE
    // =============================================================

    private void selectInstance(
            Instance instance
    ) {

        selectedInstance =
                instance;

        homeView.setSelectedInstance(
                instance
        );

        showPage(
                Sidebar.Page.HOME
        );
    }

    private void instanceCreated() {

        instancesView.refresh();

        content.getChildren().setAll(
                instancesView
        );
    }

    private void showInstances() {

        content.getChildren().setAll(
                instancesView
        );
    }

    // =============================================================
    // INSTANCE SETTINGS
    // =============================================================

    private void showInstanceSettings(
            Instance instance
    ) {

        selectedInstance =
                instance;

        InstanceSettingsView instanceSettingsView =
                new InstanceSettingsView(
                        instance,
                        () ->
                                showInstanceMods(
                                        instance
                                ),
                        () ->
                                showInstanceMods(
                                        instance
                                )
                );

        content.getChildren().setAll(
                instanceSettingsView
        );
    }

    private void showScreenshots(
            Instance instance
    ) {

        selectedInstance =
                instance;

        ScreenshotsView screenshotsView =
                new ScreenshotsView(
                        instance,
                        () ->
                                showInstanceSettings(
                                        instance
                                )
                );

        content.getChildren().setAll(
                screenshotsView
        );
    }

    // =============================================================
    // ACCOUNT
    // =============================================================

    private void loadAccount() {

        Thread thread =
                new Thread(() -> {

                    try {

                        accountService.loadAccount();

                        Platform.runLater(() -> {

                            homeView.setAccount(
                                    accountService.getCurrentAccount()
                            );

                            accountsView.updateAccountDisplay();
                        });

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        notifications.error(
                                "Account error",
                                getErrorMessage(ex)
                        );
                    }

                });

        thread.setDaemon(
                true
        );

        thread.start();
    }

    private void checkForUpdates() {

        UpdateChecker updateChecker =
                new UpdateChecker();

        updateChecker
                .check()
                .thenAccept(updateInfo -> {

                    if (!updateInfo.isUpdateAvailable()) {
                        return;
                    }

                    Platform.runLater(() ->
                            titleBar.showUpdate(
                                    updateInfo
                            )
                    );

                    titleBar.setUpdateAction(
                            this::performUpdate
                    );


                })
                .exceptionally(error -> {

                    error.printStackTrace();

                    notifications.error(
                            "Update check failed",
                            "Vanta could not check for updates."
                    );

                    return null;
                });
    }

    // =============================================================
    // UPDATE
    // =============================================================


    private void performUpdate(
            UpdateInfo updateInfo
    ) {

        notifications.showProgress(
                "Updating Vanta",
                "Preparing update..."
        );

        notifications.setProgress(0.0);

        Thread thread =
                new Thread(() -> {

                    try {

                        String version =
                                updateInfo
                                        .getLatestVersion()
                                        .replaceFirst(
                                                "^[vV]",
                                                ""
                                        );

                        UpdateDownloader downloader =
                                new UpdateDownloader();

                        notifications.updateProgress(
                                "Downloading Vanta "
                                        + version
                                        + "..."
                        );

                        Path updateZip =
                                downloader.downloadAndVerify(
                                        updateInfo,
                                        notifications::updateProgress,
                                        notifications::setProgress
                                );

                        notifications.updateProgress(
                                "Preparing Vanta "
                                        + version
                                        + "..."
                        );

                        notifications.setProgress(
                                -1.0
                        );

                        Path applicationDirectory =
                                ApplicationLocator
                                        .getApplicationDirectory();

                        notifications.updateProgress(
                                "Restarting Vanta..."
                        );

                        notifications.setProgress(
                                -1.0
                        );

                        UpdaterLauncher.start(
                                ProcessHandle
                                        .current()
                                        .pid(),
                                updateZip,
                                applicationDirectory
                        );

                        Platform.exit();

                    } catch (InterruptedException e) {

                        Thread.currentThread()
                                .interrupt();

                        logUpdateError(e);

                        notifications.error(
                                "Update failed",
                                "The update was interrupted."
                        );

                    } catch (Exception e) {

                        logUpdateError(e);

                        notifications.error(
                                "Update failed",
                                getUpdateErrorMessage(e)
                        );
                    }

                });

        thread.setName(
                "Vanta-Update"
        );

        thread.setDaemon(
                true
        );

        thread.start();
    }



    private String getUpdateErrorMessage(
            Throwable throwable
    ) {

        Throwable current =
                throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message =
                current.getMessage();

        if (message == null
                || message.isBlank()) {

            return "Vanta could not complete the update.";
        }

        return message;
    }

    private void logUpdateError(
            Throwable throwable
    ) {

        try {

            Path log =
                    Path.of(
                            System.getProperty(
                                    "java.io.tmpdir"
                            ),
                            "Vanta-update-error.log"
                    );

            Files.writeString(
                    log,
                    throwable.toString()
                            + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

            try (var writer =
                         Files.newBufferedWriter(
                                 log,
                                 StandardOpenOption.APPEND
                         )) {

                throwable.printStackTrace(
                        new java.io.PrintWriter(
                                writer
                        )
                );
            }

        } catch (Exception ignored) {
        }
    }

    // =============================================================
    // ROOT
    // =============================================================

    public Parent getRoot() {

        return root;
    }

    // =============================================================
    // GLOBAL MOD DETAILS
    // =============================================================

    private void showGlobalModDetails(
            String projectId
    ) {

        content.getChildren().setAll(
                new ModDetailsView(
                        projectId,
                        () ->
                                content.getChildren().setAll(
                                        globalModsView
                                )
                )
        );
    }

    private void showRepairView(
            LaunchFailure failure
    ) {

        Instance instance = launchService.getFailedInstance();

        if (instance == null) {
            instance = selectedInstance;
        }

        if (instance == null) {

            notifications.error(
                    "Minecraft failed to launch",
                    "Vanta could not determine which instance failed."
            );

            return;
        }

        List<RepairView.RepairIssue> issues =
                LaunchFailureParser.parse(
                        failure.details()
                );

        boolean hasAutomaticRepair =
                issues.stream()
                        .anyMatch(
                                RepairView.RepairIssue::canRepair
                        );

        if (hasAutomaticRepair) {

            attemptAutomaticRepair(
                    instance,
                    issues
            );

            return;
        }

// Show the actual launch error as a notification.
        notifications.error(
                failure.title(),
                failure.description()
        );

        final Instance repairInstance = instance;

        RepairView repairView =
                new RepairView(
                        repairInstance,
                        failure.title(),
                        failure.description(),
                        issues,
                        () -> showPage(
                                Sidebar.Page.HOME
                        ),
                        () -> repairIssues(
                                repairInstance,
                                issues
                        )
                );

        content.getChildren().setAll(
                repairView
        );
    }

    private void attemptAutomaticRepair(
            Instance instance,
            List<RepairView.RepairIssue> issues
    ) {

        RepairView.RepairIssue repairIssue = null;

        for (RepairView.RepairIssue issue : issues) {

            if (issue == null) {
                continue;
            }

            if (issue.canRepair()) {
                repairIssue = issue;
                break;
            }
        }

        if (repairIssue == null) {

            System.out.println(
                    "[Vanta Repair] No deterministic automatic repair found."
            );

            Platform.runLater(() ->
                    showPage(
                            Sidebar.Page.HOME
                    )
            );

            return;
        }

        RepairView.RepairIssue selectedIssue =
                repairIssue;

        // =========================================================
        // MINECRAFT VERSION REPAIR
        // =========================================================

        if (selectedIssue.canRepairMinecraftVersion()) {

            String targetVersion =
                    selectedIssue.repairMinecraftVersion();

            notifications.showProgress(
                    "Repairing Minecraft",
                    "Installing Minecraft "
                            + targetVersion
                            + "..."
            );

            Thread thread =
                    new Thread(() -> {

                        try {

                            System.out.println(
                                    "[Vanta Repair] Minecraft version repair started."
                            );

                            System.out.println(
                                    "[Vanta Repair] Current version: "
                                            + instance.getMinecraftVersion()
                            );

                            System.out.println(
                                    "[Vanta Repair] Target version: "
                                            + targetVersion
                            );

                            Instance updatedInstance =
                                    InstanceInstaller.updateMinecraftVersion(
                                            instance,
                                            targetVersion
                                    );

                            System.out.println(
                                    "[Vanta Repair] Minecraft version repair completed."
                            );

                            notifications.success(
                                    "Repair complete",
                                    "Minecraft was updated to "
                                            + targetVersion
                            );

                            Platform.runLater(() -> {

                                try {

                                    launchService.launch(
                                            updatedInstance
                                    );

                                } catch (Exception ex) {

                                    ex.printStackTrace();

                                    notifications.error(
                                            "Minecraft still failed",
                                            getErrorMessage(ex)
                                    );
                                }
                            });

                        } catch (Throwable ex) {

                            ex.printStackTrace();

                            System.out.println(
                                    "[Vanta Repair] Minecraft version repair failed."
                            );

                            notifications.error(
                                    "Automatic repair failed",
                                    getErrorMessage(ex)
                            );

                            Platform.runLater(() ->
                                    showPage(
                                            Sidebar.Page.HOME
                                    )
                            );
                        }

                    });

            thread.setName(
                    "Vanta-Minecraft-Version-Repair"
            );

            thread.setDaemon(
                    true
            );

            thread.start();

            return;
        }

        // =========================================================
        // MOD DEPENDENCY REPAIR
        // =========================================================

        if (selectedIssue.repairProjectId() == null
                || selectedIssue.repairProjectId().isBlank()) {

            System.out.println(
                    "[Vanta Repair] Repair issue had no valid Modrinth project."
            );

            Platform.runLater(() ->
                    showPage(
                            Sidebar.Page.HOME
                    )
            );

            return;
        }

        notifications.showProgress(
                "Repairing Minecraft",
                "Resolving "
                        + selectedIssue.repairProjectId()
                        + "..."
        );

        Thread thread =
                new Thread(() -> {

                    try {

                        ModrinthService modrinthService =
                                new ModrinthService();

                        System.out.println(
                                "[Vanta Repair] Automatic dependency repair started."
                        );

                        System.out.println(
                                "[Vanta Repair] Project: "
                                        + selectedIssue.repairProjectId()
                        );

                        System.out.println(
                                "[Vanta Repair] Required versions: "
                                        + selectedIssue.repairVersions()
                        );

                        ModrinthProject project =
                                modrinthService.getProjectBySlug(
                                        selectedIssue.repairProjectId()
                                );

                        if (project == null) {

                            throw new IOException(
                                    "Could not find Modrinth project: "
                                            + selectedIssue.repairProjectId()
                            );
                        }

                        modrinthService.repairModDependency(
                                instance,
                                project.getProjectId(),
                                selectedIssue.repairVersions()
                        );

                        System.out.println(
                                "[Vanta Repair] Automatic dependency repair completed."
                        );

                        notifications.success(
                                "Repair complete",
                                "Vanta repaired the Minecraft dependency."
                        );

                        Platform.runLater(() -> {

                            try {

                                launchService.launch(
                                        instance
                                );

                            } catch (Exception ex) {

                                ex.printStackTrace();

                                notifications.error(
                                        "Minecraft still failed",
                                        getErrorMessage(ex)
                                );
                            }
                        });

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        System.out.println(
                                "[Vanta Repair] Automatic dependency repair failed."
                        );

                        notifications.error(
                                "Automatic repair failed",
                                getErrorMessage(ex)
                        );

                        Platform.runLater(() ->
                                showPage(
                                        Sidebar.Page.HOME
                                )
                        );
                    }

                });

        thread.setName(
                "Vanta-Automatic-Repair"
        );

        thread.setDaemon(
                true
        );

        thread.start();
    }



    private void repairIssues(
            Instance instance,
            List<RepairView.RepairIssue> issues
    ) {

        notifications.showProgress(
                "Repairing instance",
                "Resolving dependencies..."
        );

        Thread thread =
                new Thread(() -> {

                    try {

                        ModrinthService modrinthService =
                                new ModrinthService();

                        boolean repaired =
                                false;

                        for (RepairView.RepairIssue issue :
                                issues) {

                            if (!issue.canRepair()) {
                                continue;
                            }

                            notifications.updateProgress(
                                    "Installing "
                                            + issue.repairProjectId()
                                            + " "
                                            + String.join(
                                            " or ",
                                            issue.repairVersions()
                                    )
                                            + "..."
                            );

                            ModrinthProject project =
                                    modrinthService.getProjectBySlug(
                                            issue.repairProjectId()
                                    );

                            modrinthService.repairModDependency(
                                    instance,
                                    project.getProjectId(),
                                    issue.repairVersions()
                            );

                            repaired = true;
                        }

                        if (!repaired) {

                            notifications.error(
                                    "Repair failed",
                                    "No automatically repairable dependencies were found."
                            );

                            return;
                        }

                        notifications.success(
                                "Repair complete",
                                "The required dependencies were installed."
                        );

                        Platform.runLater(() ->
                                showPage(
                                        Sidebar.Page.HOME
                                )
                        );

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        notifications.error(
                                "Repair failed",
                                getErrorMessage(ex)
                        );
                    }

                });

        thread.setName(
                "Vanta-Dependency-Repair"
        );

        thread.setDaemon(
                true
        );

        thread.start();
    }
}

