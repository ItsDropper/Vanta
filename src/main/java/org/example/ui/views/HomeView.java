package org.example.ui.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.example.launcher.account.Account;
import org.example.launcher.model.Instance;
import org.example.launcher.service.AccountService;
import org.example.launcher.service.LaunchService;
import org.example.ui.components.AccountCard;

public class HomeView extends VBox {

    private final AccountService accountService;
    private final LaunchService launchService;

    private final AccountCard accountCard;

    private final Label accountLabel;
    private final Label statusLabel;
    private final Button playButton;

    private Instance selectedInstance;

    private final Label minecraftLabel;
    private final HBox versionInfo;

    private Label instanceNameLabel;
    private Label instanceDetailsLabel;
    private Label instanceStatusLabel;

    public HomeView(
            AccountService accountService,
            LaunchService launchService
    ) {

        this.accountService =
                accountService;

        this.launchService =
                launchService;

        getStyleClass().add(
                "page"
        );

        setPadding(
                new Insets(36)
        );

        setSpacing(
                24
        );

        // ---------------------------------------------------------
        // HEADER
        // ---------------------------------------------------------

        Label title =
                new Label(
                        "Welcome back"
                );

        title.getStyleClass().add(
                "page-title"
        );

        accountLabel =
                new Label(
                        "Checking account..."
                );

        accountLabel.getStyleClass().add(
                "page-subtitle"
        );

        VBox header =
                new VBox(
                        6,
                        title,
                        accountLabel
                );

        // ---------------------------------------------------------
        // HERO
        // ---------------------------------------------------------

        minecraftLabel =
                new Label(
                        "Minecraft"
                );

        minecraftLabel.getStyleClass().add(
                "home-minecraft-title"
        );

        Label versionLabel =
                new Label(
                        "1.21.11"
                );

        versionLabel.getStyleClass().add(
                "home-version"
        );

        Label loaderLabel =
                new Label(
                        "Fabric"
                );

        loaderLabel.getStyleClass().add(
                "home-loader"
        );

        versionInfo =
                new HBox(
                        10,
                        versionLabel,
                        loaderLabel
                );

        versionInfo.setAlignment(
                Pos.CENTER
        );

        // ---------------------------------------------------------
        // PLAY BUTTON
        // ---------------------------------------------------------

        playButton =
                new Button(
                        "▶"
                );

        playButton.getStyleClass().add(
                "primary-button"
        );

        playButton.getStyleClass().add(
                "play-control"
        );

        playButton.setPrefWidth(
                64
        );

        playButton.setPrefHeight(
                56
        );

        playButton.setMinWidth(
                64
        );

        playButton.setMinHeight(
                56
        );

        playButton.setFocusTraversable(
                false
        );

        playButton.setOnAction(
                event -> handlePlayButton()
        );

        statusLabel =
                new Label(
                        "Ready to launch"
                );

        statusLabel.getStyleClass().add(
                "status-label"
        );

        Label quickLaunchLabel =
                new Label(
                        "QUICK LAUNCH"
                );

        quickLaunchLabel.getStyleClass().add(
                "card-title"
        );

        VBox launchInformation =
                new VBox(
                        8,
                        quickLaunchLabel,
                        minecraftLabel,
                        versionInfo
                );

        VBox launchAction =
                new VBox(
                        8,
                        playButton,
                        statusLabel
                );

        launchAction.setAlignment(
                Pos.CENTER_RIGHT
        );

        Region heroSpacer =
                new Region();

        HBox.setHgrow(
                heroSpacer,
                Priority.ALWAYS
        );

        HBox hero =
                new HBox(
                        28,
                        launchInformation,
                        heroSpacer,
                        launchAction
                );

        hero.setAlignment(
                Pos.CENTER_LEFT
        );

        hero.setPadding(
                new Insets(34)
        );

        hero.getStyleClass().add(
                "home-hero"
        );

        // ---------------------------------------------------------
        // ACCOUNT LISTENER
        // ---------------------------------------------------------

        accountService.addListener(
                this::onAccountChanged
        );

        // ---------------------------------------------------------
        // LAUNCH LISTENER
        // ---------------------------------------------------------

        launchService.addStateListener(
                this::onLaunchStateChanged
        );

        // ---------------------------------------------------------
        // INSTANCE CARD
        // ---------------------------------------------------------

        VBox instanceCard =
                createInstanceCard();

        // ---------------------------------------------------------
        // ACCOUNT CARD
        // ---------------------------------------------------------

        accountCard =
                new AccountCard();

        // ---------------------------------------------------------
        // CARDS
        // ---------------------------------------------------------

        HBox cards =
                new HBox(
                        16,
                        instanceCard,
                        accountCard
                );

        HBox.setHgrow(
                instanceCard,
                Priority.ALWAYS
        );

        HBox.setHgrow(
                accountCard,
                Priority.ALWAYS
        );

        // ---------------------------------------------------------
        // BUILD
        // ---------------------------------------------------------

        getChildren().addAll(
                header,
                hero,
                cards
        );

        refreshAccount();

        updateLaunchState(
                launchService.getState()
        );
    }

    // =============================================================
    // INSTANCE CARD
    // =============================================================

    private VBox createInstanceCard() {

        Label title =
                new Label(
                        "INSTANCE"
                );

        title.getStyleClass().add(
                "card-title"
        );

        instanceNameLabel =
                new Label(
                        "No instance selected"
                );

        instanceNameLabel.getStyleClass().add(
                "card-main"
        );

        instanceDetailsLabel =
                new Label(
                        "Select an instance from the Instances page"
                );

        instanceDetailsLabel.getStyleClass().add(
                "card-secondary"
        );

        instanceStatusLabel =
                new Label(
                        "● No instance"
                );

        instanceStatusLabel.getStyleClass().add(
                "card-secondary"
        );

        VBox card =
                new VBox(
                        8,
                        title,
                        instanceNameLabel,
                        instanceDetailsLabel,
                        instanceStatusLabel
                );

        card.setPadding(
                new Insets(20)
        );

        card.getStyleClass().add(
                "info-card"
        );

        return card;
    }

    // =============================================================
    // ACCOUNT
    // =============================================================

    public void refreshAccount() {

        Account account =
                accountService.getCurrentAccount();

        setAccount(
                account
        );
    }

    public void setAccount(
            Account account
    ) {

        if (account == null) {

            accountLabel.setText(
                    "No Microsoft account connected"
            );

            accountCard.setDisconnected();

            return;
        }

        String username =
                account.getUsername();

        accountLabel.setText(
                "Signed in as " + username
        );

        accountCard.setAccount(
                account
        );
    }

    private void onAccountChanged(
            Account account
    ) {

        Platform.runLater(() ->
                setAccount(account)
        );
    }

    // =============================================================
    // SELECTED INSTANCE
    // =============================================================

    public void setSelectedInstance(
            Instance instance
    ) {

        this.selectedInstance =
                instance;

        if (instance == null) {

            minecraftLabel.setText(
                    "Minecraft"
            );

            versionInfo.getChildren().clear();

            Label version =
                    new Label(
                            "1.21.11"
                    );

            version.getStyleClass().add(
                    "home-version"
            );

            Label loader =
                    new Label(
                            "Fabric"
                    );

            loader.getStyleClass().add(
                    "home-loader"
            );

            versionInfo.getChildren().addAll(
                    version,
                    loader
            );

            instanceNameLabel.setText(
                    "No instance selected"
            );

            instanceDetailsLabel.setText(
                    "Select an instance from the Instances page"
            );

            instanceStatusLabel.setText(
                    "● No instance"
            );

            return;
        }

        minecraftLabel.setText(
                instance.getName()
        );

        versionInfo.getChildren().clear();

        Label version =
                new Label(
                        instance.getMinecraftVersion()
                );

        version.getStyleClass().add(
                "home-version"
        );

        Label loader =
                new Label(
                        instance.getDisplayLoader()
                );

        loader.getStyleClass().add(
                "home-loader"
        );

        versionInfo.getChildren().addAll(
                version,
                loader
        );

        instanceNameLabel.setText(
                instance.getName()
        );

        instanceDetailsLabel.setText(
                instance.getMinecraftVersion()
                        + " • "
                        + instance.getDisplayLoader()
        );

        instanceStatusLabel.setText(
                "● Ready"
        );
    }

    // =============================================================
    // PLAY / STOP
    // =============================================================

    private void handlePlayButton() {

        LaunchService.LaunchState state =
                launchService.getState();

        if (state == LaunchService.LaunchState.RUNNING) {

            launchService.close();

            return;
        }

        if (state == LaunchService.LaunchState.PREPARING
                || state == LaunchService.LaunchState.STARTING
                || state == LaunchService.LaunchState.CLOSING) {

            return;
        }

        if (selectedInstance == null) {

            statusLabel.setText(
                    "No instance selected."
            );

            return;
        }

        Instance instance =
                selectedInstance;

        Thread thread =
                new Thread(() -> {

                    try {

                        launchService.launch(
                                instance
                        );

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        Platform.runLater(() -> {

                            String message =
                                    ex.getMessage();

                            statusLabel.setText(
                                    message != null
                                            && !message.isBlank()
                                            ? message
                                            : "Failed to launch Minecraft."
                            );
                        });
                    }
                });

        thread.setDaemon(
                true
        );

        thread.setName(
                "Vanta-Home-Launch"
        );

        thread.start();
    }

    // =============================================================
    // LAUNCH STATE
    // =============================================================

    private void onLaunchStateChanged(
            LaunchService.LaunchState state
    ) {

        Platform.runLater(() ->
                updateLaunchState(state)
        );
    }

    private void updateLaunchState(
            LaunchService.LaunchState state
    ) {

        if (state == null) {

            state =
                    LaunchService.LaunchState.IDLE;
        }

        switch (state) {

            case PREPARING -> {

                playButton.setDisable(
                        true
                );

                playButton.setText(
                        "…"
                );

                statusLabel.setText(
                        "Preparing Minecraft..."
                );

                setInstanceStatus(
                        "● Preparing"
                );
            }

            case STARTING -> {

                playButton.setDisable(
                        true
                );

                playButton.setText(
                        "…"
                );

                statusLabel.setText(
                        "Starting Minecraft..."
                );

                setInstanceStatus(
                        "● Starting"
                );
            }

            case RUNNING -> {

                playButton.setDisable(
                        false
                );

                playButton.setText(
                        "CLOSE"
                );

                playButton.getStyleClass().remove(
                        "home-playing-button"
                );

                playButton.getStyleClass().add(
                        "home-playing-button"
                );

                statusLabel.setText(
                        "Minecraft is running."
                );

                setInstanceStatus(
                        "● Running"
                );
            }

            case CLOSING -> {

                playButton.setDisable(
                        true
                );

                playButton.setText(
                        "…"
                );

                statusLabel.setText(
                        "Closing Minecraft..."
                );

                setInstanceStatus(
                        "● Closing"
                );
            }

            case ERROR -> {

                resetPlayButton();

                statusLabel.setText(
                        "Minecraft failed to launch."
                );

                setInstanceStatus(
                        "● Error"
                );
            }

            case IDLE -> {

                resetPlayButton();

                statusLabel.setText(
                        "Ready to launch"
                );

                setInstanceStatus(
                        "● Ready"
                );
            }
        }
    }

    private void resetPlayButton() {

        playButton.setDisable(
                false
        );

        playButton.setText(
                "▶"
        );

        playButton.getStyleClass().remove(
                "home-playing-button"
        );
    }

    private void setInstanceStatus(
            String status
    ) {

        if (selectedInstance != null) {

            instanceStatusLabel.setText(
                    status
            );
        }
    }
}