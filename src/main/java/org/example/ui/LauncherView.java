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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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

                                ModrinthProject project =
                                        modrinthService.getProjectBySlug(
                                                modSlug
                                        );

                                modrinthService.installMod(
                                        instance,
                                        project
                                );
                            }
                        }

                        notifications.success(
                                "Installation complete",
                                preset.getName()
                                        + " ("
                                        + minecraftVersion
                                        + ") is ready to play."
                        );

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

                    titleBar.setTestUpdateAction( this::simulateUpdate );

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



    private void simulateUpdate() {

        notifications.showProgress(
                "Updating Vanta",
                "Downloading Vanta 1.0.6..."
        );

        notifications.setProgress(0);

        Thread thread =
                new Thread(() -> {

                    try {

                        for (int i = 0; i <= 100; i += 2) {

                            notifications.setProgress(
                                    i / 100.0
                            );

                            notifications.updateProgress(
                                    "Downloading Vanta 1.0.6... "
                                            + i
                                            + "%"
                            );

                            Thread.sleep(40);
                        }

                        notifications.updateProgress(
                                "Verifying update..."
                        );

                        Thread.sleep(1200);

                        notifications.updateProgress(
                                "Preparing Vanta 1.0.6..."
                        );

                        Thread.sleep(1200);

                        notifications.updateProgress(
                                "Restarting Vanta..."
                        );

                        Thread.sleep(1200);

                        notifications.success(
                                "Update simulation complete",
                                "Vanta would restart now."
                        );

                    } catch (InterruptedException e) {

                        Thread.currentThread().interrupt();

                        notifications.error(
                                "Update simulation failed",
                                "The simulation was interrupted."
                        );
                    }

                });

        thread.setName(
                "Vanta-Fake-Update"
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

        Instance instance =
                selectedInstance;

        if (instance == null) {

            return;
        }

        List<RepairView.RepairIssue> issues =
                LaunchFailureParser.parse(
                        failure.details()
                );

        RepairView repairView =
                new RepairView(
                        instance,
                        failure.title(),
                        failure.description(),
                        issues,
                        () -> showPage(
                                Sidebar.Page.HOME
                        ),
                        () -> {
                            notifications.success(
                                    "Repair",
                                    "Automatic repair is not implemented yet."
                            );
                        }
                );

        content.getChildren().setAll(
                repairView
        );
    }
}

