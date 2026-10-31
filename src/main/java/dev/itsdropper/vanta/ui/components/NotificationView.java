package dev.itsdropper.vanta.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

public class NotificationView extends VBox {

    private static final int MAX_TITLE_LENGTH = 72;
    private static final int MAX_MESSAGE_LENGTH = 96;

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
    private String fullMessage = "";

    public NotificationView(
            String title,
            String message,
            Type type,
            Runnable onClose
    ) {
        setPrefWidth(360);
        setMinWidth(0);
        setMaxWidth(Double.MAX_VALUE);
        setPadding(new Insets(9, 12, 9, 12));
        setSpacing(6);
        setFillWidth(true);
        setMouseTransparent(false);
        getStyleClass().add("notification");

        accent = new Rectangle(3, 30);
        accent.setArcWidth(3);
        accent.setArcHeight(3);
        accent.getStyleClass().addAll("notification-accent", "notification-accent-progress");

        titleLabel = new Label();
        titleLabel.setWrapText(false);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.getStyleClass().add("notification-title");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);
        setDisplayText(titleLabel, title, MAX_TITLE_LENGTH);

        messageLabel = new Label();
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(Double.MAX_VALUE);
        messageLabel.setMinHeight(0);
        messageLabel.setMaxHeight(32);
        messageLabel.getStyleClass().add("notification-message");
        messageLabel.widthProperty().addListener((observable, previousWidth, width) -> updateMessagePresentation());
        setMessage(message);

        progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(16, 16);
        progressIndicator.setMinSize(16, 16);
        progressIndicator.setMaxSize(16, 16);

        progressBar = new ProgressBar(0);
        progressBar.getStyleClass().add("notification-progress");
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setMinHeight(4);
        progressBar.setPrefHeight(4);
        progressBar.setMaxHeight(4);

        closeButton = new Button("×");
        closeButton.setPrefSize(22, 22);
        closeButton.setMinSize(22, 22);
        closeButton.setMaxSize(22, 22);
        closeButton.getStyleClass().add("notification-close");
        closeButton.setAccessibleText("Dismiss notification");
        closeButton.setTooltip(new Tooltip("Dismiss"));
        closeButton.setOnAction(event -> {
            if (onClose != null) {
                onClose.run();
            }
        });

        VBox text = new VBox(2, titleLabel, messageLabel);
        text.setMinWidth(0);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setMinWidth(0);
        header.getChildren().addAll(accent, text, progressIndicator, closeButton);

        getChildren().addAll(header, progressBar);
        setType(type);
    }

    private static void setDisplayText(Label label, String value, int maxLength) {
        String fullText = value == null ? "" : value.strip();
        boolean truncated = fullText.length() > maxLength;
        String visibleText = truncated
                ? fullText.substring(0, maxLength - 1).stripTrailing() + "…"
                : fullText;

        label.setText(visibleText);
        label.setTooltip(truncated ? new Tooltip(fullText) : null);
    }

    private void setAccentStyle(String styleClass) {
        accent.getStyleClass().removeAll(
                "notification-accent-progress",
                "notification-accent-success",
                "notification-accent-error"
        );
        accent.getStyleClass().add(styleClass);
    }

    public void setTitle(String title) {
        setDisplayText(titleLabel, title, MAX_TITLE_LENGTH);
    }

    public void setMessage(String message) {
        fullMessage = message == null ? "" : message.strip();
        updateMessagePresentation();
    }

    private void updateMessagePresentation() {
        double availableWidth = messageLabel.getWidth();
        int widthBasedLimit = availableWidth > 0
                ? Math.max(24, (int) Math.floor(availableWidth / 5.8) * 2)
                : MAX_MESSAGE_LENGTH;
        setDisplayText(
                messageLabel,
                fullMessage,
                Math.min(MAX_MESSAGE_LENGTH, widthBasedLimit)
        );
    }

    public void setProgress(double progress) {
        progressBar.setProgress(Math.max(0, Math.min(1, progress)));
    }

    public void setType(Type type) {
        switch (type) {
            case PROGRESS -> {
                progressIndicator.setVisible(true);
                progressIndicator.setManaged(true);
                progressBar.setVisible(true);
                progressBar.setManaged(true);
                closeButton.setVisible(false);
                closeButton.setManaged(false);
                setAccentStyle("notification-accent-progress");
                messageLabel.getStyleClass().remove("notification-message-error");
            }
            case SUCCESS -> {
                progressIndicator.setVisible(false);
                progressIndicator.setManaged(false);
                progressBar.setVisible(false);
                progressBar.setManaged(false);
                closeButton.setVisible(false);
                closeButton.setManaged(false);
                setAccentStyle("notification-accent-success");
                messageLabel.getStyleClass().remove("notification-message-error");
            }
            case ERROR -> {
                progressIndicator.setVisible(false);
                progressIndicator.setManaged(false);
                progressBar.setVisible(false);
                progressBar.setManaged(false);
                closeButton.setVisible(true);
                closeButton.setManaged(true);
                setAccentStyle("notification-accent-error");
                if (!messageLabel.getStyleClass().contains("notification-message-error")) {
                    messageLabel.getStyleClass().add("notification-message-error");
                }
            }
        }
    }
}
