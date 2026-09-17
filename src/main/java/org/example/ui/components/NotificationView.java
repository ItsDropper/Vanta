package org.example.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

public class NotificationView extends VBox {


    public enum Type {
        PROGRESS,
        SUCCESS,
        ERROR
    }

    private final Label titleLabel;
    private final Label messageLabel;
    private final ProgressIndicator progressIndicator;
    private final ProgressBar progressBar;
    private final Button closeButton;
    private final Rectangle accent;

    public NotificationView(
            String title,
            String message,
            Type type,
            Runnable onClose
    ) {

        setPrefWidth(380);
        setMaxWidth(380);

        setPadding(
                new Insets(16)
        );

        setSpacing(10);

        setMouseTransparent(false);

        setStyle(
                "-fx-background-color: #181a1f;" +
                        "-fx-background-radius: 12;" +
                        "-fx-border-color: #2a2d35;" +
                        "-fx-border-radius: 12;" +
                        "-fx-border-width: 1;"
        );

        accent = new Rectangle(
                4,
                44
        );

        accent.setArcWidth(4);
        accent.setArcHeight(4);

        titleLabel = new Label(
                title
        );

        titleLabel.setStyle(
                "-fx-font-size: 14px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: #ffffff;"
        );

        messageLabel = new Label(
                message
        );

        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(290);

        messageLabel.setStyle(
                "-fx-font-size: 12px;" +
                        "-fx-text-fill: #aeb3bd;"
        );

        progressIndicator =
                new ProgressIndicator();

        progressIndicator.setPrefSize(
                22,
                22
        );

        progressIndicator.setMaxSize(
                22,
                22
        );

        progressBar =
                new ProgressBar(0);

        progressBar.getStyleClass().add(
                "notification-progress"
        );

        progressBar.setMaxWidth(
                Double.MAX_VALUE
        );

        progressBar.setPrefHeight(
                5
        );

        progressBar.setMinHeight(
                5
        );

        progressBar.setMaxHeight(
                5
        );


        closeButton =
                new Button("×");

        closeButton.setPrefSize(
                28,
                28
        );

        closeButton.setMinSize(
                28,
                28
        );

        closeButton.setMaxSize(
                28,
                28
        );

        closeButton.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: #8f949e;" +
                        "-fx-font-size: 18px;" +
                        "-fx-padding: 0;"
        );

        closeButton.setOnAction(event -> {

            if (onClose != null) {
                onClose.run();
            }
        });

        VBox text =
                new VBox(3);

        text.getChildren().addAll(
                titleLabel,
                messageLabel
        );

        HBox.setHgrow(
                text,
                Priority.ALWAYS
        );

        HBox header =
                new HBox(10);

        header.setAlignment(
                Pos.CENTER_LEFT
        );

        header.getChildren().addAll(
                accent,
                text,
                progressIndicator,
                closeButton
        );

        getChildren().addAll(
                header,
                progressBar
        );

        setType(type);
    }

    public void setTitle(
            String title
    ) {

        titleLabel.setText(
                title
        );
    }

    public void setMessage(
            String message
    ) {

        messageLabel.setText(
                message
        );
    }

    public void setProgress(
            double progress
    ) {

        progressBar.setProgress(
                Math.max(
                        0,
                        Math.min(
                                1,
                                progress
                        )
                )
        );
    }

    public void setType(
            Type type
    ) {

        switch (type) {

            case PROGRESS -> {

                progressIndicator.setVisible(
                        true
                );

                progressIndicator.setManaged(
                        true
                );

                progressBar.setVisible(
                        true
                );

                progressBar.setManaged(
                        true
                );

                closeButton.setVisible(
                        false
                );

                closeButton.setManaged(
                        false
                );

                accent.setStyle(
                        "-fx-fill: #4d8dff;"
                );

                messageLabel.setStyle(
                        "-fx-font-size: 12px;" +
                                "-fx-text-fill: #aeb3bd;"
                );
            }

            case SUCCESS -> {

                progressIndicator.setVisible(
                        false
                );

                progressIndicator.setManaged(
                        false
                );

                progressBar.setVisible(
                        false
                );

                progressBar.setManaged(
                        false
                );

                closeButton.setVisible(
                        false
                );

                closeButton.setManaged(
                        false
                );

                accent.setStyle(
                        "-fx-fill: #55d187;"
                );

                messageLabel.setStyle(
                        "-fx-font-size: 12px;" +
                                "-fx-text-fill: #aeb3bd;"
                );
            }

            case ERROR -> {

                progressIndicator.setVisible(
                        false
                );

                progressIndicator.setManaged(
                        false
                );

                progressBar.setVisible(
                        false
                );

                progressBar.setManaged(
                        false
                );

                closeButton.setVisible(
                        true
                );

                closeButton.setManaged(
                        true
                );

                accent.setStyle(
                        "-fx-fill: #ff4d5a;"
                );

                messageLabel.setStyle(
                        "-fx-font-size: 12px;" +
                                "-fx-text-fill: #ff8f8f;"
                );
            }
        }
    }


}
