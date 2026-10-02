package org.example.ui;

import org.example.launcher.MinecraftLocator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class LauncherSettings {

    private static final Path SETTINGS_FILE =
            MinecraftLocator.getVantaDirectory().resolve("settings.properties");

    private LauncherSettings() {
    }

    public static boolean isAnimationsEnabled() {
        return getBoolean("animationsEnabled", true);
    }

    public static void setAnimationsEnabled(boolean value) {
        setBoolean("animationsEnabled", value);
    }

    public static boolean isUpdateChecksEnabled() {
        return getBoolean("updateChecksEnabled", true);
    }

    public static void setUpdateChecksEnabled(boolean value) {
        setBoolean("updateChecksEnabled", value);
    }

    public static int getDownloadThreads() {
        return getInt("downloadThreads", 8, 1, 16);
    }

    public static void setDownloadThreads(int value) {
        setInt("downloadThreads", clamp(value, 1, 16));
    }

    public static boolean isConfirmRemovalsEnabled() {
        return getBoolean("confirmRemovalsEnabled", true);
    }

    public static void setConfirmRemovalsEnabled(boolean value) {
        setBoolean("confirmRemovalsEnabled", value);
    }

    public static int getDefaultRamMb() {
        return getInt("defaultRamMb", 4096, 1024, 16384);
    }

    public static void setDefaultRamMb(int value) {
        setInt("defaultRamMb", clamp(value, 1024, 16384));
    }

    public static int getDefaultWidth() {
        return getInt("defaultWidth", 1280, 640, 7680);
    }

    public static void setDefaultWidth(int value) {
        setInt("defaultWidth", clamp(value, 640, 7680));
    }

    public static int getDefaultHeight() {
        return getInt("defaultHeight", 720, 480, 4320);
    }

    public static void setDefaultHeight(int value) {
        setInt("defaultHeight", clamp(value, 480, 4320));
    }

    public static boolean isDefaultFullscreen() {
        return getBoolean("defaultFullscreen", false);
    }

    public static void setDefaultFullscreen(boolean value) {
        setBoolean("defaultFullscreen", value);
    }

    public static void resetOnboarding() {
        setBoolean("onboardingCompleted", false);
    }

    private static boolean getBoolean(String key, boolean fallback) {
        Properties properties = load();
        return Boolean.parseBoolean(properties.getProperty(key, Boolean.toString(fallback)));
    }

    private static int getInt(String key, int fallback, int min, int max) {
        Properties properties = load();
        try {
            return clamp(Integer.parseInt(properties.getProperty(key, Integer.toString(fallback))), min, max);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void setBoolean(String key, boolean value) {
        Properties properties = load();
        properties.setProperty(key, Boolean.toString(value));
        save(properties);
    }

    private static void setInt(String key, int value) {
        Properties properties = load();
        properties.setProperty(key, Integer.toString(value));
        save(properties);
    }

    private static Properties load() {
        Properties properties = new Properties();

        if (!Files.exists(SETTINGS_FILE)) {
            return properties;
        }

        try (var reader = Files.newBufferedReader(SETTINGS_FILE)) {
            properties.load(reader);
        } catch (IOException ignored) {
        }

        return properties;
    }

    private static void save(Properties properties) {
        try {
            Files.createDirectories(SETTINGS_FILE.getParent());

            try (var writer = Files.newBufferedWriter(SETTINGS_FILE)) {
                properties.store(writer, "Vanta launcher settings");
            }
        } catch (IOException ignored) {
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
