package dev.itsdropper.vanta.ui;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** Restores and saves the main window's size and position when the user has enabled it. */
public final class WindowBounds {
    private WindowBounds() {
    }

    /** Applies the saved bounds before the stage is shown. Ignores positions that are no longer on any screen. */
    public static void restore(Stage stage) {
        if (!LauncherSettings.isRememberWindowBoundsEnabled() || !LauncherSettings.hasWindowBounds()) {
            return;
        }
        double width = Math.max(stage.getMinWidth(), LauncherSettings.getWindowWidth());
        double height = Math.max(stage.getMinHeight(), LauncherSettings.getWindowHeight());
        Rectangle2D saved = new Rectangle2D(
                LauncherSettings.getWindowX(), LauncherSettings.getWindowY(), width, height);
        boolean visible = !Screen.getScreensForRectangle(saved).isEmpty()
                && Screen.getScreensForRectangle(saved).stream()
                .anyMatch(screen -> screen.getVisualBounds().intersects(saved));
        stage.setWidth(width);
        stage.setHeight(height);
        if (visible) {
            stage.setX(saved.getMinX());
            stage.setY(saved.getMinY());
        } else {
            stage.centerOnScreen();
        }
        if (LauncherSettings.isWindowMaximized()) {
            stage.setMaximized(true);
        }
    }

    /** Saves the current bounds. A maximized window keeps its previous restored size. */
    public static void save(Stage stage) {
        if (!LauncherSettings.isRememberWindowBoundsEnabled() || stage.isIconified()) {
            return;
        }
        LauncherSettings.setWindowMaximized(stage.isMaximized());
        if (!stage.isMaximized()) {
            LauncherSettings.setWindowBounds(
                    (int) Math.round(stage.getX()), (int) Math.round(stage.getY()),
                    (int) Math.round(stage.getWidth()), (int) Math.round(stage.getHeight()));
        }
    }
}
