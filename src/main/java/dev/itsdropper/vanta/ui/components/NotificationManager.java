package dev.itsdropper.vanta.ui.components;

import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import dev.itsdropper.vanta.ui.LauncherSettings;

public class NotificationManager {

    private static volatile NotificationManager activeManager;

    private static final double NOTIFICATION_WIDTH = 360;
    private static final double HORIZONTAL_WINDOW_GUTTER = 24;

    private final StackPane container;

    private NotificationView currentNotification;

    private PauseTransition dismissTransition;

    public NotificationManager(StackPane root) {
        container = new StackPane();
        container.setPrefWidth(NOTIFICATION_WIDTH);
        container.setMinWidth(0);
        container.setMaxHeight(180);
        container.setMinHeight(0);
        container.setPrefHeight(Region.USE_COMPUTED_SIZE);
        container.maxWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(0, Math.min(
                        NOTIFICATION_WIDTH,
                        root.getWidth() - HORIZONTAL_WINDOW_GUTTER
                )),
                root.widthProperty()
        ));
        container.setPickOnBounds(false);

        StackPane.setAlignment(container, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(
                container,
                new Insets(0, 12, 12, 12)
        );

        root.getChildren().add(container);
        activeManager = this;
    }

    public static NotificationManager getGlobal() {
        return activeManager;
    }

    public void showProgress(String title, String message) {
        if (!LauncherSettings.isNotificationsEnabled()) {
            dismiss();
            return;
        }

        runOnFxThread(() -> {
            cancelDismissTimer();
            removeCurrent();
            currentNotification = new NotificationView(
                    title,
                    message,
                    NotificationView.Type.PROGRESS,
                    this::dismiss
            );
            container.getChildren().setAll(currentNotification);
        });
    }

    public void updateProgress(String message) {
        runOnFxThread(() -> {
            if (currentNotification == null) {
                return;
            }
            currentNotification.setMessage(message);
        });
    }

    public void setProgress(double progress) {
        runOnFxThread(() -> {
            if (currentNotification == null) {
                return;
            }
            currentNotification.setProgress(progress);
        });
    }

    public void success(String title, String message) {
        if (!LauncherSettings.isNotificationsEnabled()) {
            dismiss();
            return;
        }

        runOnFxThread(() -> {
            cancelDismissTimer();
            removeCurrent();
            currentNotification = new NotificationView(
                    title,
                    message,
                    NotificationView.Type.SUCCESS,
                    this::dismiss
            );
            container.getChildren().setAll(currentNotification);
            scheduleDismiss(LauncherSettings.getNotificationDurationSeconds() * 1000L);
        });
    }

    public void error(String title, String message) {
        if (!LauncherSettings.isNotificationsEnabled()) {
            dismiss();
            return;
        }

        runOnFxThread(() -> {
            cancelDismissTimer();
            removeCurrent();
            currentNotification = new NotificationView(
                    title,
                    message,
                    NotificationView.Type.ERROR,
                    this::dismiss
            );
            container.getChildren().setAll(currentNotification);
        });
    }

    public void dismiss() {
        runOnFxThread(() -> {
            cancelDismissTimer();
            removeCurrent();
        });
    }

    private void removeCurrent() {
        currentNotification = null;
        container.getChildren().clear();
    }

    private void scheduleDismiss(long milliseconds) {
        cancelDismissTimer();
        dismissTransition = new PauseTransition(
                javafx.util.Duration.millis(milliseconds)
        );
        dismissTransition.setOnFinished(event -> dismiss());
        dismissTransition.play();
    }

    private void cancelDismissTimer() {
        if (dismissTransition != null) {
            dismissTransition.stop();
            dismissTransition = null;
        }
    }

    private void runOnFxThread(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }
}
