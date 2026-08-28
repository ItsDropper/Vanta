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

        Label minecraftLabel =
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

        HBox versionInfo =
                new HBox(
                        10,
                        versionLabel,
                        loaderLabel
                );

        versionInfo.setAlignment(
                Pos.CENTER
        );

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

        Label name =
                new Label("Minecraft 1.21.11");

        name.getStyleClass().add(
                "card-main"
        );

        Label details =
                new Label("Fabric");

        details.getStyleClass().add(
                "card-secondary"
        );

        Label status =
                new Label("● Ready");

        status.getStyleClass().add(
                "ready-label"
        );

        VBox card =
                new VBox(
                        8,
                        title,
                        name,
                        details,
                        status
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
// LAUNCH
// =============================================================

    private void launchMinecraft() {

        // ---------------------------------------------------------
        // CLOSE MINECRAFT
        // ---------------------------------------------------------

        if (launchService.isRunning()) {

            statusLabel.setText(
                    "Closing Minecraft..."
            );

            launchService.close();

            updateLaunchState();

            statusLabel.setText(
                    "Minecraft closed."
            );

            return;
        }

        // ---------------------------------------------------------
        // START MINECRAFT
        // ---------------------------------------------------------

        playButton.setDisable(true);

        playButton.setText(
                "PREPARING..."
        );

        statusLabel.setText(
                "Preparing Minecraft..."
        );

        Thread thread =
                new Thread(() -> {

                    try {

                        Platform.runLater(() -> {

                            playButton.setText(
                                    "STARTING..."
                            );

                            statusLabel.setText(
                                    "Starting Minecraft..."
                            );
                        });

                        var process =
                                launchService.launch();

                        Platform.runLater(() -> {

                            playButton.setDisable(false);

                            playButton.setText(
                                    "CLOSE"
                            );

                            playButton.getStyleClass().remove(
                                    "primary-button"
                            );

                            playButton.getStyleClass().add(
                                    "playing-button"
                            );

                            statusLabel.setText(
                                    "Minecraft is running."
                            );

                        });

                        // -------------------------------------------------
                        // WAIT FOR MINECRAFT TO CLOSE
                        // -------------------------------------------------

                        while (process.isAlive()) {

                            Thread.sleep(250);
                        }

                        Platform.runLater(() -> {

                            playButton.setDisable(false);

                            playButton.getStyleClass().remove(
                                    "playing-button"
                            );

                            playButton.getStyleClass().add(
                                    "primary-button"
                            );

                            playButton.setText(
                                    "PLAY"
                            );

                            statusLabel.setText(
                                    "Minecraft closed."
                            );
                        });

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        Platform.runLater(() -> {

                            playButton.setDisable(false);

                            playButton.getStyleClass().remove(
                                    "playing-button"
                            );

                            playButton.getStyleClass().add(
                                    "primary-button"
                            );

                            playButton.setText(
                                    "PLAY"
                            );

                            statusLabel.setText(
                                    ex.getMessage() != null
                                            ? ex.getMessage()
                                            : "Failed to launch Minecraft."
                            );
                        });
                    }

                });

        thread.setDaemon(true);
        thread.start();
    }

// =============================================================
// STATE
// =============================================================

    private void updateLaunchState() {

        if (launchService.isRunning()) {

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

            return;
        }

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
}
