package org.example.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.example.launcher.model.Instance;
import org.example.launcher.modrinth.ModrinthProject;
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

    // Global Modrinth browser
    private final GlobalModsView globalModsView;

    private final SettingsView settingsView;
    private final CreateInstanceView createInstanceView;

    private Instance selectedInstance;
    private BrowseModsView browseModsView;



    public LauncherView(Stage stage) {

        root =
                new BorderPane();

        root.getStyleClass().add(
                "launcher"
        );

        // =========================================================
        // SERVICES
        // =========================================================

        accountService =
                new AccountService();

        launchService =
                new LaunchService(
                        accountService
                );

        // =========================================================
        // SIDEBAR
        // =========================================================

        sidebar =
                new Sidebar();

        BorderPane.setMargin(
                sidebar,
                new Insets(16)
        );

        root.setLeft(
                sidebar
        );

        // =========================================================
        // VIEWS
        // =========================================================

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
                        this::showInstanceMods
                );

        createInstanceView =
                new CreateInstanceView(
                        this::showInstances,
                        this::instanceCreated
                );

        /*
         * GLOBAL MODS
         *
         * This page does not require an instance.
         *
         * It is completely separate from the
         * instance-specific Installed Mods page.
         */
        globalModsView =
                new GlobalModsView(
                        this::showGlobalModDetails
                );

        settingsView =
                new SettingsView();

        // =========================================================
        // CONTENT
        // =========================================================

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

        // =========================================================
        // NAVIGATION
        // =========================================================

        sidebar.setOnPageSelected(
                this::showPage
        );

        showPage(
                Sidebar.Page.HOME
        );

        // =========================================================
        // ACCOUNT
        // =========================================================

        loadAccount();
    }

    // =============================================================
    // GLOBAL NAVIGATION
    // =============================================================

    private void showPage(
            Sidebar.Page page
    ) {

        switch (page) {

            case HOME -> {

                content.getChildren().setAll(
                        homeView
                );
            }

            case ACCOUNTS -> {

                content.getChildren().setAll(
                        accountsView
                );
            }

            case INSTANCES -> {

                content.getChildren().setAll(
                        instancesView
                );
            }

            case MODS -> {

                /*
                 * GLOBAL MODS
                 *
                 * No instance selection required.
                 */
                content.getChildren().setAll(
                        globalModsView
                );
            }

            case SETTINGS -> {

                content.getChildren().setAll(
                        settingsView
                );
            }
        }
    }

    // =============================================================
    // INSTANCE INSTALLED MODS
    // =============================================================

    private void showInstanceMods(
            Instance instance
    ) {

        selectedInstance =
                instance;

        ModsView view =
                new ModsView(
                        instance,
                        () -> showBrowseMods(
                                instance
                        ),
                        () -> showInstanceSettings(
                                instance
                        )
                );

        content.getChildren().setAll(
                view
        );
    }

    // =============================================================
    // INSTANCE BROWSE MODS
    // =============================================================


    private void showBrowseMods(
            Instance instance
    ) {

        browseModsView =
                new BrowseModsView(
                        instance,
                        () -> showInstanceMods(
                                instance
                        ),
                        project ->
                                showModDetails(
                                        instance,
                                        project
                                )
                );

        content.getChildren().setAll(
                browseModsView
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

        InstanceSettingsView instanceSettingsView =
                new InstanceSettingsView(
                        instance,

                        /*
                         * BACK
                         *
                         * Always returns to Installed Mods.
                         */
                        () -> showInstanceMods(
                                instance
                        ),

                        /*
                         * MODS
                         *
                         * Also returns to Installed Mods.
                         */
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

                                showBrowseMods(
                                        instance
                                );
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
                        () ->
                                content.getChildren().setAll(
                                        globalModsView
                                )
                )
        );
    }


}

