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

        this.accountService = accountService;
        this.launchService = launchService;

        getStyleClass().add("page");
        getStyleClass().add("home-page");

        setPadding(new Insets(32, 36, 36, 36));
        setSpacing(22);

        // ---------------------------------------------------------
        // HEADER
        // ---------------------------------------------------------

        Label title =
                new Label("Home");

        title.getStyleClass().add("home-title");

        accountLabel =
                new Label("Checking account...");

        accountLabel.getStyleClass().add("home-account-label");

        VBox header =
                new VBox(
                        4,
                        title,
                        accountLabel
                );

        header.getStyleClass().add("home-header");

        // ---------------------------------------------------------
        // MAIN LAUNCH AREA
        // ---------------------------------------------------------

        Label eyebrow =
                new Label("PLAY");

        eyebrow.getStyleClass().add("home-eyebrow");

        minecraftLabel =
                new Label("Minecraft");

        minecraftLabel.getStyleClass().add("home-minecraft-title");

        versionInfo =
                new HBox(8);

        versionInfo.setAlignment(Pos.CENTER_LEFT);


        Label selectedLabel =
                new Label("SELECTED INSTANCE");

        selectedLabel.getStyleClass().add(
                "home-selected-label"
        );

        VBox launchInformation =
                new VBox(
                        7,
                        eyebrow,
                        minecraftLabel,
                        versionInfo
                );

        launchInformation.getStyleClass().add(
                "home-launch-information"
        );

        // ---------------------------------------------------------
        // PLAY CONTROL
        // ---------------------------------------------------------

        playButton =
                new Button("PLAY");

        playButton.getStyleClass().add(
                "home-play-button"
        );

        playButton.setPrefWidth(118);
        playButton.setMinWidth(118);
        playButton.setPrefHeight(42);
        playButton.setMinHeight(42);

        playButton.setFocusTraversable(false);

        playButton.setOnAction(
                event -> handlePlayButton()
        );

        statusLabel =
                new Label("Ready to launch");

        statusLabel.getStyleClass().add(
                "home-launch-status"
        );

        VBox launchAction =
                new VBox(
                        7,
                        playButton,
                        statusLabel
                );

        launchAction.setAlignment(
                Pos.CENTER_RIGHT
        );

        Region spacer =
                new Region();

        HBox.setHgrow(
                spacer,
                Priority.ALWAYS
        );

        HBox launchRow =
                new HBox(
                        20,
                        launchInformation,
                        spacer,
                        launchAction
                );

        launchRow.setAlignment(
                Pos.CENTER_LEFT
        );

        VBox hero =
                new VBox(
                        0,
                        launchRow
                );

        hero.setPadding(
                new Insets(26, 28, 26, 28)
        );

        hero.getStyleClass().add(
                "home-launch-card"
        );

        // ---------------------------------------------------------
        // LOWER INFORMATION
        // ---------------------------------------------------------

        VBox instanceCard =
                createInstanceCard();

        accountCard =
                new AccountCard();

        accountCard.getStyleClass().add(
                "home-account-card"
        );

        HBox cards =
                new HBox(
                        14,
                        instanceCard,
                        accountCard
                );

        cards.setAlignment(
                Pos.TOP_LEFT
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
        // LISTENERS
        // ---------------------------------------------------------

        accountService.addListener(
                this::onAccountChanged
        );

        launchService.addStateListener(
                this::onLaunchStateChanged
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
                new Label("INSTANCE");

        title.getStyleClass().add(
                "home-card-eyebrow"
        );

        instanceNameLabel =
                new Label("No instance selected");

        instanceNameLabel.getStyleClass().add(
                "home-card-title"
        );

        instanceDetailsLabel =
                new Label(
                        "Select an instance from the Instances page"
                );

        instanceDetailsLabel.getStyleClass().add(
                "home-card-description"
        );

        instanceStatusLabel =
                new Label("● No instance");

        instanceStatusLabel.getStyleClass().add(
                "home-card-status"
        );

        VBox card =
                new VBox(
                        8,
                        title,
                        instanceNameLabel,
                        instanceDetailsLabel,
                        instanceStatusLabel
                );

        card.setMinHeight(128);
        card.setPadding(
                new Insets(18, 20, 18, 20)
        );

        card.getStyleClass().add(
                "home-info-card"
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

        accountCard.setAccount(account);
    }

    private void onAccountChanged(
            Account account
    ) {

        Platform.runLater(
                () -> setAccount(account)
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

            if (instance == null) {

                minecraftLabel.setText("No instance selected");

                versionInfo.getChildren().clear();

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

        setVersionInfo(
                instance.getMinecraftVersion(),
                instance.getDisplayLoader()
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

    private void setVersionInfo(
            String version,
            String loader
    ) {

        versionInfo.getChildren().clear();

        Label versionLabel =
                new Label(version);

        versionLabel.getStyleClass().add(
                "home-version"
        );

        Label loaderLabel =
                new Label(loader);

        loaderLabel.getStyleClass().add(
                "home-loader"
        );

        versionInfo.getChildren().addAll(
                versionLabel,
                loaderLabel
        );
    }

    // =============================================================
    // PLAY / STOP
    // =============================================================

    private void handlePlayButton() {

        LaunchService.LaunchState state =
                launchService.getState();

        if (state ==
                LaunchService.LaunchState.RUNNING) {

            launchService.close();

            return;
        }

        if (state ==
                LaunchService.LaunchState.PREPARING
                || state ==
                LaunchService.LaunchState.STARTING
                || state ==
                LaunchService.LaunchState.CLOSING) {

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

        thread.setDaemon(true);

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

        Platform.runLater(
                () -> updateLaunchState(state)
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

                playButton.setDisable(true);

                playButton.setText("...");

                statusLabel.setText(
                        "Preparing Minecraft..."
                );

                setInstanceStatus(
                        "● Preparing"
                );
            }

            case STARTING -> {

                playButton.setDisable(true);

                playButton.setText("...");

                statusLabel.setText(
                        "Starting Minecraft..."
                );

                setInstanceStatus(
                        "● Starting"
                );
            }

            case RUNNING -> {

                playButton.setDisable(false);

                playButton.setText("CLOSE");

                if (!playButton.getStyleClass()
                        .contains("home-playing-button")) {

                    playButton.getStyleClass().add(
                            "home-playing-button"
                    );
                }

                statusLabel.setText(
                        "Minecraft is running."
                );

                setInstanceStatus(
                        "● Running"
                );
            }

            case CLOSING -> {

                playButton.setDisable(true);

                playButton.setText("...");

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

        playButton.setDisable(false);

        playButton.setText("PLAY");

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

