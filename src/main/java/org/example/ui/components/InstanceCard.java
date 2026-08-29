package org.example.ui.components;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.example.launcher.model.Instance;
import org.example.launcher.service.LaunchService;

public class InstanceCard extends HBox {

    private final Label nameLabel;
    private final Label versionLabel;
    private final Label loaderLabel;
    private final Label statusLabel;

    private final Button playButton;
    private final Button settingsButton;

    private Instance instance;

    public InstanceCard(
            Instance instance,
            Runnable onPlay,
            Runnable onSelect,
            Runnable onSettings
    ) {

        this.instance =
                instance;

        // ---------------------------------------------------------
        // CARD
        // ---------------------------------------------------------

        setSpacing(18);

        setPadding(
                new Insets(
                        18,
                        20,
                        18,
                        20
                )
        );

        setAlignment(
                Pos.CENTER_LEFT
        );

        setMaxWidth(
                Double.MAX_VALUE
        );

        getStyleClass().add(
                "instance-card"
        );

        // ---------------------------------------------------------
        // ICON
        // ---------------------------------------------------------

        Label icon =
                new Label("◇");

        icon.getStyleClass().add(
                "instance-icon"
        );

        VBox iconBox =
                new VBox(icon);

        iconBox.setAlignment(
                Pos.CENTER
        );

        iconBox.getStyleClass().add(
                "instance-icon-box"
        );

        // ---------------------------------------------------------
        // INFORMATION
        // ---------------------------------------------------------

        nameLabel =
                new Label();

        nameLabel.getStyleClass().add(
                "instance-name"
        );

        versionLabel =
                new Label();

        versionLabel.getStyleClass().add(
                "instance-version"
        );

        loaderLabel =
                new Label();

        loaderLabel.getStyleClass().add(
                "instance-loader"
        );

        HBox metadata =
                new HBox(
                        8,
                        versionLabel,
                        loaderLabel
                );

        metadata.setAlignment(
                Pos.CENTER_LEFT
        );

        VBox information =
                new VBox(
                        6,
                        nameLabel,
                        metadata
                );

        information.setAlignment(
                Pos.CENTER_LEFT
        );

        HBox.setHgrow(
                information,
                Priority.ALWAYS
        );

        // ---------------------------------------------------------
        // STATUS
        // ---------------------------------------------------------

        statusLabel =
                new Label(
                        "● READY"
                );

        statusLabel.getStyleClass().add(
                "instance-ready"
        );

        // ---------------------------------------------------------
        // PLAY BUTTON
        // ---------------------------------------------------------

        playButton =
                new Button(
                        "PLAY"
                );

        playButton.getStyleClass().add(
                "instance-play-button"
        );

        playButton.setPrefWidth(
                110
        );

        playButton.setPrefHeight(
                38
        );

        playButton.setOnAction(
                event -> {

                    event.consume();

                    onPlay.run();
                }
        );

        // ---------------------------------------------------------
        // SETTINGS
        // ---------------------------------------------------------

        settingsButton =
                new Button(
                        "SETTINGS"
                );

        settingsButton.getStyleClass().add(
                "instance-settings-button"
        );

        settingsButton.setPrefWidth(
                92
        );

        settingsButton.setPrefHeight(
                38
        );

        settingsButton.setOnAction(
                event -> {

                    event.consume();

                    onSettings.run();
                }
        );

        // Prevent buttons from selecting the card.
        playButton.setOnMouseClicked(
                event -> event.consume()
        );

        settingsButton.setOnMouseClicked(
                event -> event.consume()
        );

        // ---------------------------------------------------------
        // RIGHT SIDE
        // ---------------------------------------------------------

        HBox right =
                new HBox(
                        10,
                        statusLabel,
                        settingsButton,
                        playButton
                );

        right.setAlignment(
                Pos.CENTER_RIGHT
        );

        // ---------------------------------------------------------
        // BUILD
        // ---------------------------------------------------------

        getChildren().addAll(
                iconBox,
                information,
                right
        );

        // ---------------------------------------------------------
        // SELECT
        // ---------------------------------------------------------

        setOnMouseClicked(
                event -> onSelect.run()
        );

        update();
    }

    // =============================================================
    // UPDATE
    // =============================================================

    private void update() {

        nameLabel.setText(
                instance.getName()
        );

        versionLabel.setText(
                "Minecraft "
                        + instance.getMinecraftVersion()
        );

        loaderLabel.setText(
                instance.getDisplayLoader()
        );
    }

    // =============================================================
    // LAUNCH STATE
    // =============================================================

    public void setLaunchState(
            LaunchService.LaunchState state
    ) {

        Platform.runLater(() -> {

            switch (state) {

                case IDLE -> {

                    playButton.setDisable(false);
                    playButton.setText("PLAY");

                    setPlayingStyle(false);

                    statusLabel.setText(
                            "● READY"
                    );
                }

                case PREPARING -> {

                    playButton.setDisable(true);
                    playButton.setText(
                            "PREPARING..."
                    );

                    setPlayingStyle(false);

                    statusLabel.setText(
                            "● PREPARING"
                    );
                }

                case STARTING -> {

                    playButton.setDisable(true);
                    playButton.setText(
                            "STARTING..."
                    );

                    setPlayingStyle(false);

                    statusLabel.setText(
                            "● STARTING"
                    );
                }

                case RUNNING -> {

                    playButton.setDisable(false);
                    playButton.setText(
                            "CLOSE"
                    );

                    setPlayingStyle(true);

                    statusLabel.setText(
                            "● RUNNING"
                    );
                }

                case CLOSING -> {

                    playButton.setDisable(true);
                    playButton.setText(
                            "CLOSING..."
                    );

                    setPlayingStyle(true);

                    statusLabel.setText(
                            "● CLOSING"
                    );
                }

                case ERROR -> {

                    playButton.setDisable(false);
                    playButton.setText(
                            "PLAY"
                    );

                    setPlayingStyle(false);

                    statusLabel.setText(
                            "● ERROR"
                    );
                }
            }
        });
    }

    private void setPlayingStyle(
            boolean playing
    ) {

        playButton.getStyleClass().remove(
                "playing-button"
        );

        playButton.getStyleClass().remove(
                "instance-play-button"
        );

        if (playing) {

            playButton.getStyleClass().add(
                    "playing-button"
            );

        } else {

            playButton.getStyleClass().add(
                    "instance-play-button"
            );
        }
    }

    // =============================================================
    // GET INSTANCE
    // =============================================================

    public Instance getInstance() {

        return instance;
    }

    // =============================================================
    // SET INSTANCE
    // =============================================================

    public void setInstance(
            Instance instance
    ) {

        if (instance == null) {
            return;
        }

        this.instance =
                instance;

        update();
    }

    // =============================================================
    // SELECTED STATE
    // =============================================================

    public void setSelected(
            boolean selected
    ) {

        getStyleClass().remove(
                "selected-instance"
        );

        if (selected) {

            getStyleClass().add(
                    "selected-instance"
            );
        }
    }
}