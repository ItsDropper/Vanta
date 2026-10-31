package dev.itsdropper.vanta.ui.components;

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
        setMinWidth(0);
        setMaxWidth(Double.MAX_VALUE);

        setPadding(
                new Insets(16)
        );

        setSpacing(10);

        setMouseTransparent(false);

        getStyleClass().add("notification");

        accent = new Rectangle(4, 44);
        accent.setArcWidth(4);
        accent.setArcHeight(4);
        accent.getStyleClass().addAll("notification-accent", "notification-accent-progress");

        titleLabel = new Label(
                title
        );

        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.getStyleClass().add("notification-title");

        messageLabel = new Label(
                message
        );

        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(Double.MAX_VALUE);

        messageLabel.getStyleClass().add("notification-message");

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

        closeButton.getStyleClass().add("notification-close");
        closeButton.setAccessibleText("Dismiss notification");
        closeButton.setTooltip(new javafx.scene.control.Tooltip("Dismiss"));

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

    private void setAccentStyle(String styleClass) {
        accent.getStyleClass().removeAll(
                "notification-accent-progress",
                "notification-accent-success",
                "notification-accent-error"
        );
        accent.getStyleClass().add(styleClass);
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

                setAccentStyle("notification-accent-progress");
                messageLabel.getStyleClass().remove("notification-message-error");
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

                setAccentStyle("notification-accent-success");
                messageLabel.getStyleClass().remove("notification-message-error");
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

                setAccentStyle("notification-accent-error");
                if (!messageLabel.getStyleClass().contains("notification-message-error")) {
                    messageLabel.getStyleClass().add("notification-message-error");
                }
            }
        }
    }


}
