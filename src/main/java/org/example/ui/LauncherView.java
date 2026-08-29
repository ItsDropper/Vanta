package org.example.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.example.launcher.model.Instance;
import org.example.launcher.service.AccountService;
import org.example.launcher.service.LaunchService;

import org.example.ui.components.Sidebar;

import org.example.ui.views.*;

public class LauncherView {

    private final BorderPane root;

    private final AccountService accountService;
    private final LaunchService launchService;

    private final Sidebar sidebar;
    private final StackPane content;

    private final HomeView homeView;
    private final AccountsView accountsView;
    private final InstancesView instancesView;
    private final ModsView modsView;
    private final SettingsView settingsView;
    private final CreateInstanceView createInstanceView;

    private Instance selectedInstance;

    private InstanceSettingsView instanceSettingsView;

    public LauncherView(Stage stage) {

        root = new BorderPane();

        root.getStyleClass().add(
                "launcher"
        );

        // ---------------------------------------------------------
        // SERVICES
        // ---------------------------------------------------------

        accountService =
                new AccountService();

        launchService =
                new LaunchService(
                        accountService
                );

        // ---------------------------------------------------------
        // SIDEBAR
        // ---------------------------------------------------------

        sidebar =
                new Sidebar();

        BorderPane.setMargin(
                sidebar,
                new Insets(16)
        );

        root.setLeft(
                sidebar
        );

        // ---------------------------------------------------------
        // VIEWS
        // ---------------------------------------------------------

        homeView =
                new HomeView(
                        accountService,
                        launchService
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
                        this::showInstanceSettings
                );

        createInstanceView =
                new CreateInstanceView(
                        this::showInstances,
                        this::instanceCreated
                );

        /*
         * Global MODS page is kept for now.
         *
         * It is only used when an instance is selected.
         * We will replace this with a proper instance-aware
         * content system later.
         */
        modsView =
                null;

        settingsView =
                new SettingsView();

        // ---------------------------------------------------------
        // CONTENT
        // ---------------------------------------------------------

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

        root.setCenter(
                content
        );

        // ---------------------------------------------------------
        // NAVIGATION
        // ---------------------------------------------------------

        sidebar.setOnPageSelected(
                this::showPage
        );

        showPage(
                Sidebar.Page.HOME
        );

        // ---------------------------------------------------------
        // ACCOUNT
        // ---------------------------------------------------------

        loadAccount();
    }

    // =============================================================
    // NAVIGATION
    // =============================================================

    private void showPage(
            Sidebar.Page page
    ) {

        switch (page) {

            case HOME ->
                    content.getChildren().setAll(
                            homeView
                    );

            case ACCOUNTS ->
                    content.getChildren().setAll(
                            accountsView
                    );

            case INSTANCES ->
                    content.getChildren().setAll(
                            instancesView
                    );

            case MODS -> {

                if (selectedInstance == null) {

                    content.getChildren().setAll(
                            instancesView
                    );

                    return;
                }

                showInstanceMods(
                        selectedInstance
                );
            }

            case SETTINGS ->
                    content.getChildren().setAll(
                            settingsView
                    );
        }
    }

    // =============================================================
    // INSTANCE MODS
    // =============================================================

    private void showInstanceMods(
            Instance instance
    ) {

        ModsView view =
                new ModsView(
                        instance
                );

        content.getChildren().setAll(
                view
        );
    }

    // =============================================================
    // CREATE INSTANCE
    // =============================================================

    private void showCreateInstanceView() {

        content.getChildren().setAll(
                createInstanceView
        );
    }

    private void showInstances() {

        content.getChildren().setAll(
                instancesView
        );
    }

    // =============================================================
    // INSTANCE SELECTION
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

    // =============================================================
    // INSTANCE CREATED
    // =============================================================

    private void instanceCreated() {

        instancesView.refresh();

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

        instanceSettingsView =
                new InstanceSettingsView(
                        instance,
                        this::showInstances,
                        () -> showInstanceMods(
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
                                    accountService
                                            .getCurrentAccount()
                            );

                            accountsView.refresh();
                        });

                    } catch (Throwable ex) {

                        ex.printStackTrace();
                    }
                });

        thread.setDaemon(
                true
        );

        thread.start();
    }

    // =============================================================
    // ROOT
    // =============================================================

    public Parent getRoot() {

        return root;
    }
}