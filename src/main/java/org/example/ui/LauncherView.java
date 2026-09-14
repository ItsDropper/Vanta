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
import org.example.launcher.modrinth.ModrinthProject;
import org.example.launcher.service.AccountService;
import org.example.launcher.service.LaunchService;
import org.example.launcher.service.ModrinthService;
import org.example.ui.components.NotificationManager;
import org.example.ui.components.Sidebar;
import org.example.ui.views.*;

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

    private Instance selectedInstance;
    private BrowseModsView browseModsView;

    public LauncherView(Stage stage) {

        root = new StackPane();

        window = new BorderPane();
        window.getStyleClass().add("launcher");

        root.getChildren().add(window);

        // Notification overlay must be created after the window.
        notifications = new NotificationManager(root);


        window.prefWidthProperty().bind(root.widthProperty());
        window.prefHeightProperty().bind(root.heightProperty());
        window.maxWidthProperty().bind(root.widthProperty());
        window.maxHeightProperty().bind(root.heightProperty());

        Rectangle clip = new Rectangle();
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());

        window.setClip(clip);

        accountService = new AccountService();

        launchService = new LaunchService(
                accountService
        );

        TitleBar titleBar = new TitleBar(
                stage,
                accountService
        );

        window.setTop(titleBar);

        sidebar = new Sidebar();

        BorderPane.setMargin(
                sidebar,
                new Insets(16)
        );

        window.setLeft(sidebar);

        content = new StackPane();
        content.getStyleClass().add("content");

        content.setPadding(
                new Insets(16, 16, 16, 0)
        );

        window.setCenter(content);

        homeView = new HomeView(
                accountService,
                launchService
        );

        accountsView = new AccountsView(
                accountService
        );

        instancesView = new InstancesView(
                launchService,
                this::showCreateInstanceView,
                this::selectInstance,
                this::showInstanceMods
        );

        createInstanceTypeView = new CreateInstanceTypeView(
                this::showInstances,
                this::showCustomCreateInstanceView,
                this::showImportInstanceView,
                this::showPresetInstanceView,
                this::showModrinthModpackView
        );

        globalModsView = new GlobalModsView(
                this::showGlobalModDetails
        );

        settingsView = new SettingsView();

        sidebar.setOnPageSelected(
                this::showPage
        );

        showPage(
                Sidebar.Page.HOME
        );

        loadAccount();
    }

    private void showPage(Sidebar.Page page) {

        sidebar.setSelectedPage(page);

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

    private void showInstanceMods(Instance instance) {

        selectedInstance = instance;

        ModsView view = new ModsView(
                instance,
                () -> showBrowseMods(instance),
                () -> showInstanceSettings(instance)
        );

        content.getChildren().setAll(view);
    }

    private void showBrowseMods(Instance instance) {

        browseModsView = new BrowseModsView(
                instance,
                () -> showInstanceMods(instance),
                project -> showModDetails(
                        instance,
                        project
                )
        );

        content.getChildren().setAll(
                browseModsView
        );
    }

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
            String name
    ) {

        System.out.println("NOTIFICATION TEST: createPresetInstance reached");

        notifications.showProgress(
                "Installing " + preset.getName(),
                "Preparing Minecraft..."
        );

        notifications.showProgress(
                "Installing " + preset.getName(),
                "Preparing Minecraft..."
        );

        Thread thread = new Thread(() -> {

            try {

                notifications.updateProgress(
                        "Installing Minecraft "
                                + preset.getMinecraftVersion()
                                + "..."
                );

                Instance instance;

                if ("Fabric".equalsIgnoreCase(
                        preset.getLoader()
                )) {

                    instance =
                            InstanceInstaller.installFabric(
                                    name,
                                    preset.getMinecraftVersion()
                            );

                } else {

                    instance =
                            InstanceInstaller.installVanilla(
                                    name,
                                    preset.getMinecraftVersion()
                            );
                }

                ModrinthService modrinthService =
                        new ModrinthService();

                for (String modSlug : preset.getMods()) {

                    notifications.updateProgress(
                            "Installing "
                                    + modSlug
                                    + "..."
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

                notifications.success(
                        "Installation complete",
                        preset.getName()
                                + " is ready to play."
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

        thread.setDaemon(true);
        thread.start();
    }

    private String getErrorMessage(
            Throwable throwable
    ) {

        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();

        if (message == null || message.isBlank()) {
            return current
                    .getClass()
                    .getSimpleName();
        }

        return message;
    }

    private void showModrinthModpackView() {
        // Modrinth modpack browsing will be implemented here.
    }

    private void showInstances() {

        content.getChildren().setAll(
                instancesView
        );
    }

    private void selectInstance(
            Instance instance
    ) {

        selectedInstance = instance;

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

    private void showInstanceSettings(
            Instance instance
    ) {

        selectedInstance = instance;

        InstanceSettingsView instanceSettingsView =
                new InstanceSettingsView(
                        instance,
                        () -> showInstanceMods(instance),
                        () -> showInstanceMods(instance)
                );

        content.getChildren().setAll(
                instanceSettingsView
        );
    }

    private void loadAccount() {

        Thread thread = new Thread(() -> {

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
            }

        });

        thread.setDaemon(true);
        thread.start();
    }

    public Parent getRoot() {
        return root;
    }

    private void showModDetails(
            Instance instance,
            ModrinthProject project
    ) {

        content.getChildren().setAll(
                new ModDetailsView(
                        instance,
                        project.getProjectId(),
                        () -> {

                            if (browseModsView != null) {

                                content.getChildren().setAll(
                                        browseModsView
                                );

                            } else {

                                showBrowseMods(instance);
                            }
                        }
                )
        );
    }

    private void showGlobalModDetails(
            String projectId
    ) {

        content.getChildren().setAll(
                new ModDetailsView(
                        projectId,
                        () -> content.getChildren().setAll(
                                globalModsView
                        )
                )
        );
    }
}

