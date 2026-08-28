package org.example.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.example.launcher.service.AccountService;
import org.example.launcher.service.LaunchService;

import org.example.ui.components.Sidebar;

import org.example.ui.views.AccountsView;
import org.example.ui.views.HomeView;
import org.example.ui.views.InstancesView;
import org.example.ui.views.ModsView;
import org.example.ui.views.SettingsView;

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

    public LauncherView(Stage stage) {

        root = new BorderPane();
        root.getStyleClass().add("launcher");

        // ---------------------------------------------------------
        // SERVICES
        // ---------------------------------------------------------

        accountService = new AccountService();

        launchService = new LaunchService(
                accountService
        );

        // ---------------------------------------------------------
        // SIDEBAR
        // ---------------------------------------------------------

        sidebar = new Sidebar();

        BorderPane.setMargin(
                sidebar,
                new Insets(16)
        );

        root.setLeft(sidebar);

        // ---------------------------------------------------------
        // VIEWS
        // ---------------------------------------------------------

        homeView = new HomeView(
                accountService,
                launchService
        );

        accountsView = new AccountsView(
                accountService
        );

        instancesView = new InstancesView();

        modsView = new ModsView();

        settingsView = new SettingsView();

        // ---------------------------------------------------------
        // CONTENT
        // ---------------------------------------------------------

        content = new StackPane();
        content.getStyleClass().add("content");

        content.setPadding(
                new Insets(
                        16,
                        16,
                        16,
                        0
                )
        );

        root.setCenter(content);

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
        // LOAD ACCOUNT
        // ---------------------------------------------------------

        loadAccount();
    }

    private void showPage(Sidebar.Page page) {


        switch (page) {

            case HOME ->
                    content.getChildren().setAll(homeView);

            case ACCOUNTS ->
                    content.getChildren().setAll(accountsView);

            case INSTANCES ->
                    content.getChildren().setAll(instancesView);

            case MODS ->
                    content.getChildren().setAll(modsView);

            case SETTINGS ->
                    content.getChildren().setAll(settingsView);
        }


    }


    private void loadAccount() {

        Thread thread = new Thread(() -> {

            try {

                accountService.loadAccount();

                Platform.runLater(() -> {

                    homeView.setAccount(
                            accountService.getCurrentAccount()
                    );

                    accountsView.refresh();
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


}
