package org.example.ui;

import javafx.scene.Node;
import javafx.scene.paint.Color;

import org.example.launcher.MinecraftLocator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Centralized launcher theme state.
 *
 * Keeps the accent preference persistent and exposes the looked-up
 * colors used by the JavaFX design system.
 */
public final class ThemeManager {

    public static final String DEFAULT_ACCENT = "#5688ED";

    private static final String ACCENT_KEY = "accentColor";
    private static final Path SETTINGS_FILE =
            MinecraftLocator.getVantaDirectory()
                    .resolve("settings.properties");

    private ThemeManager() {
    }

    public static String loadAccent() {
        if (!Files.exists(SETTINGS_FILE)) {
            return DEFAULT_ACCENT;
        }

        Properties properties = new Properties();

        try (var reader = Files.newBufferedReader(SETTINGS_FILE)) {
            properties.load(reader);

            String accent =
                    properties.getProperty(ACCENT_KEY);

            if (isValidAccent(accent)) {
                return accent.toUpperCase();
            }
        } catch (IOException ignored) {
        }

        return DEFAULT_ACCENT;
    }

    public static void saveAccent(String accent) {
        if (!isValidAccent(accent)) {
            return;
        }

        Properties properties = new Properties();

        if (Files.exists(SETTINGS_FILE)) {
            try (var reader = Files.newBufferedReader(SETTINGS_FILE)) {
                properties.load(reader);
            } catch (IOException ignored) {
            }
        }

        properties.setProperty(
                ACCENT_KEY,
                accent.toUpperCase()
        );

        try {
            Files.createDirectories(
                    SETTINGS_FILE.getParent()
            );

            try (var writer =
                         Files.newBufferedWriter(SETTINGS_FILE)) {

                properties.store(
                        writer,
                        "Vanta launcher settings"
                );
            }
        } catch (IOException ignored) {
        }
    }

    public static void apply(Node node, String accent) {
        if (node == null || !isValidAccent(accent)) {
            return;
        }

        Color color =
                Color.web(accent);

        String normalized =
                accent.toUpperCase();

        String soft =
                toHex(color.darker().darker());

        String hover =
                toHex(color.brighter());

        String rgb =
                Math.round(color.getRed() * 255)
                        + ","
                        + Math.round(color.getGreen() * 255)
                        + ","
                        + Math.round(color.getBlue() * 255);

        node.setStyle(
                "-vanta-accent: " + normalized + ";"
                        + "-vanta-accent-soft: " + soft + ";"
                        + "-vanta-accent-hover: " + hover + ";"
                        + "-vanta-accent-rgb: " + rgb + ";"
        );
    }

    public static boolean isValidAccent(String accent) {
        return accent != null
                && accent.matches("#[0-9a-fA-F]{6}");
    }

    private static String toHex(Color color) {
        return String.format(
                "#%02X%02X%02X",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255)
        );
    }
}
