package dev.itsdropper.vanta.ui.components;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import dev.itsdropper.vanta.ui.LauncherSettings;

import java.util.Timer;
import java.util.TimerTask;

public class NotificationManager {

    private static volatile NotificationManager activeManager;

    private final StackPane container;

    private NotificationView currentNotification;

    private Timer dismissTimer;

    public NotificationManager(StackPane root) {

        container = new StackPane();

        container.setPrefWidth(380);
        container.setMaxHeight(Double.MAX_VALUE);
        container.setPrefHeight(Region.USE_COMPUTED_SIZE);
        container.maxWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(0, Math.min(380, root.getWidth() - 32)),
                root.widthProperty()
        ));

        container.setPickOnBounds(false);

        StackPane.setAlignment(
                container,
                Pos.BOTTOM_RIGHT
        );

        StackPane.setMargin(
                container,
                new Insets(0, 24, 24, 24)
        );

        root.getChildren().add(container);
        activeManager = this;
    }

    public static NotificationManager getGlobal() {
        return activeManager;
    }

    public void showProgress(
            String title,
            String message
    ) {
        if (!LauncherSettings.isNotificationsEnabled()) { dismiss(); return; }

        runOnFxThread(() -> {

            cancelDismissTimer();
            removeCurrent();

            currentNotification =
                    new NotificationView(
                            title,
                            message,
                            NotificationView.Type.PROGRESS,
                            this::dismiss
                    );

            container.getChildren().setAll(
                    currentNotification
            );
        });
    }

    public void updateProgress(
            String message
    ) {

        runOnFxThread(() -> {

            if (currentNotification == null) {
                return;
            }

            currentNotification.setMessage(
                    message
            );
        });
    }

    public void setProgress(
            double progress
    ) {

        runOnFxThread(() -> {

            if (currentNotification == null) {
                return;
            }

            currentNotification.setProgress(
                    progress
            );
        });
    }

    public void success(
            String title,
            String message
    ) {
        if (!LauncherSettings.isNotificationsEnabled()) { dismiss(); return; }

        runOnFxThread(() -> {

            cancelDismissTimer();
            removeCurrent();

            currentNotification =
                    new NotificationView(
                            title,
                            message,
                            NotificationView.Type.SUCCESS,
                            this::dismiss
                    );

            container.getChildren().setAll(
                    currentNotification
            );

            scheduleDismiss(LauncherSettings.getNotificationDurationSeconds() * 1000L);
        });
    }

    public void error(
            String title,
            String message
    ) {
        if (!LauncherSettings.isNotificationsEnabled()) { dismiss(); return; }

        runOnFxThread(() -> {

            cancelDismissTimer();
            removeCurrent();

            currentNotification =
                    new NotificationView(
                            title,
                            message,
                            NotificationView.Type.ERROR,
                            this::dismiss
                    );

            container.getChildren().setAll(
                    currentNotification
            );
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

    private void scheduleDismiss(
            long milliseconds
    ) {

        cancelDismissTimer();

        dismissTimer =
                new Timer(
                        "Vanta-Notification",
                        true
                );

        dismissTimer.schedule(
                new TimerTask() {

                    @Override
                    public void run() {

                        Platform.runLater(
                                NotificationManager.this::dismiss
                        );
                    }

                },
                milliseconds
        );
    }

    private void cancelDismissTimer() {

        if (dismissTimer != null) {

            dismissTimer.cancel();
            dismissTimer = null;
        }
    }

    private void runOnFxThread(
            Runnable runnable
    ) {

        if (Platform.isFxApplicationThread()) {

            runnable.run();

        } else {

            Platform.runLater(
                    runnable
            );
        }
    }
}

