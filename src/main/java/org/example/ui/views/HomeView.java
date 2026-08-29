package org.example.ui.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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

        this.accountService = accountService;
        this.launchService = launchService;

        getStyleClass().add("page");

        setPadding(new Insets(36));
        setSpacing(24);

        // ---------------------------------------------------------
        // HEADER
        // ---------------------------------------------------------

        Label title =
                new Label("Welcome back");

        title.getStyleClass().add(
                "page-title"
        );

        accountLabel =
                new Label("Checking account...");

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
                new Label("Minecraft");

        minecraftLabel.getStyleClass().add(
                "home-minecraft-title"
        );

        Label versionLabel =
                new Label("1.21.11");

        versionLabel.getStyleClass().add(
                "home-version"
        );

        Label loaderLabel =
                new Label("Fabric");

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
                new Button("PLAY");

        playButton.getStyleClass().add(
                "primary-button"
        );

        playButton.setPrefWidth(220);
        playButton.setPrefHeight(56);

        playButton.setOnAction(
                event -> launchMinecraft()
        );

        statusLabel =
                new Label("Ready to launch");

        statusLabel.getStyleClass().add(
                "status-label"
        );

        VBox hero =
                new VBox(
                        12,
                        minecraftLabel,
                        versionInfo,
                        playButton,
                        statusLabel
                );

        hero.setAlignment(
                Pos.CENTER
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

        updateLaunchState();
    }

    // =============================================================
    // INSTANCE CARD
    // =============================================================

    private VBox createInstanceCard() {

        Label title =
                new Label("INSTANCE");

        title.getStyleClass().add(
                "card-title"
        );

        instanceNameLabel =
                new Label("No instance selected");

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
                new Label("● No instance");

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

        setAccount(account);
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

        this.selectedInstance = instance;

        if (instance == null) {

            minecraftLabel.setText(
                    "Minecraft"
            );

            versionInfo.getChildren().clear();

            Label version =
                    new Label("1.21.11");

            version.getStyleClass().add(
                    "home-version"
            );

            Label loader =
                    new Label("Fabric");

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
    // LAUNCH
    // =============================================================

    private void launchMinecraft() {

        // ---------------------------------------------------------
        // CLOSE
        // ---------------------------------------------------------

        if (launchService.isRunning()) {

            playButton.setDisable(true);

            playButton.setText(
                    "CLOSING..."
            );

            statusLabel.setText(
                    "Closing Minecraft..."
            );

            launchService.close();

            waitForClose();

            return;
        }

        // ---------------------------------------------------------
        // IGNORE CLICKS WHILE LAUNCHING
        // ---------------------------------------------------------

        if (
                launchService.getState()
                        == LaunchService.LaunchState.PREPARING
                        ||
                        launchService.getState()
                                == LaunchService.LaunchState.STARTING
                        ||
                        launchService.getState()
                                == LaunchService.LaunchState.CLOSING
        ) {

            return;
        }

        // ---------------------------------------------------------
        // VALIDATE INSTANCE
        // ---------------------------------------------------------

        if (selectedInstance == null) {

            statusLabel.setText(
                    "No instance selected."
            );

            return;
        }

        // ---------------------------------------------------------
        // PREPARING
        // ---------------------------------------------------------

        playButton.setDisable(true);

        playButton.setText(
                "PREPARING..."
        );

        statusLabel.setText(
                "Preparing Minecraft..."
        );

        Instance instance =
                selectedInstance;

        Thread thread =
                new Thread(() -> {

                    try {

                        // -------------------------------------------------
                        // LAUNCH
                        // -------------------------------------------------

                        Platform.runLater(() -> {

                            playButton.setText(
                                    "STARTING..."
                            );

                            statusLabel.setText(
                                    "Starting Minecraft..."
                            );
                        });

                        var process =
                                launchService.launch(
                                        instance
                                );

                        // -------------------------------------------------
                        // RUNNING
                        // -------------------------------------------------

                        Platform.runLater(() -> {

                            playButton.setDisable(false);

                            playButton.setText(
                                    "CLOSE"
                            );

                            playButton.getStyleClass().remove(
                                    "primary-button"
                            );

                            if (!playButton.getStyleClass().contains(
                                    "playing-button"
                            )) {

                                playButton.getStyleClass().add(
                                        "playing-button"
                                );
                            }

                            statusLabel.setText(
                                    "Minecraft is running."
                            );

                            instanceStatusLabel.setText(
                                    "● Running"
                            );
                        });

                        // -------------------------------------------------
                        // WAIT
                        // -------------------------------------------------

                        while (process.isAlive()) {

                            Thread.sleep(250);
                        }

                        // -------------------------------------------------
                        // CLOSED
                        // -------------------------------------------------

                        Platform.runLater(() -> {

                            resetPlayButton();

                            statusLabel.setText(
                                    "Minecraft closed."
                            );

                            if (selectedInstance != null) {

                                instanceStatusLabel.setText(
                                        "● Ready"
                                );
                            }
                        });

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        Platform.runLater(() -> {

                            resetPlayButton();

                            statusLabel.setText(
                                    ex.getMessage() != null
                                            ? ex.getMessage()
                                            : "Failed to launch Minecraft."
                            );

                            if (selectedInstance != null) {

                                instanceStatusLabel.setText(
                                        "● Error"
                                );
                            }
                        });
                    }

                });

        thread.setDaemon(true);

        thread.setName(
                "Vanta-Home-Launch"
        );

        thread.start();
    }

    // =============================================================
    // WAIT FOR CLOSE
    // =============================================================

    private void waitForClose() {

        Thread thread =
                new Thread(() -> {

                    while (
                            launchService.isRunning()
                    ) {

                        try {

                            Thread.sleep(100);

                        } catch (InterruptedException e) {

                            Thread.currentThread()
                                    .interrupt();

                            return;
                        }
                    }

                    Platform.runLater(() -> {

                        resetPlayButton();

                        statusLabel.setText(
                                "Minecraft closed."
                        );

                        if (selectedInstance != null) {

                            instanceStatusLabel.setText(
                                    "● Ready"
                            );
                        }
                    });

                });

        thread.setDaemon(true);

        thread.setName(
                "Vanta-Home-Close-Monitor"
        );

        thread.start();
    }

    // =============================================================
    // RESET BUTTON
    // =============================================================

    private void resetPlayButton() {

        playButton.setDisable(false);

        playButton.setText(
                "PLAY"
        );

        playButton.getStyleClass().remove(
                "playing-button"
        );

        if (!playButton.getStyleClass().contains(
                "primary-button"
        )) {

            playButton.getStyleClass().add(
                    "primary-button"
            );
        }
    }

    // =============================================================
    // STATE
    // =============================================================

    private void updateLaunchState() {

        LaunchService.LaunchState state =
                launchService.getState();

        switch (state) {

            case PREPARING -> {

                playButton.setDisable(true);

                playButton.setText(
                        "PREPARING..."
                );

                statusLabel.setText(
                        "Preparing Minecraft..."
                );
            }

            case STARTING -> {

                playButton.setDisable(true);

                playButton.setText(
                        "STARTING..."
                );

                statusLabel.setText(
                        "Starting Minecraft..."
                );
            }

            case RUNNING -> {

                playButton.setDisable(false);

                playButton.setText(
                        "CLOSE"
                );

                playButton.getStyleClass().remove(
                        "primary-button"
                );

                if (!playButton.getStyleClass().contains(
                        "playing-button"
                )) {

                    playButton.getStyleClass().add(
                            "playing-button"
                    );
                }

                statusLabel.setText(
                        "Minecraft is running."
                );
            }

            case CLOSING -> {

                playButton.setDisable(true);

                playButton.setText(
                        "CLOSING..."
                );

                statusLabel.setText(
                        "Closing Minecraft..."
                );
            }

            case ERROR -> {

                resetPlayButton();

                statusLabel.setText(
                        "Minecraft failed to launch."
                );
            }

            case IDLE -> {

                resetPlayButton();

                statusLabel.setText(
                        "Ready to launch"
                );
            }
        }
    }
}

