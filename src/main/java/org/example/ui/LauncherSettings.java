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

    public static boolean isAutoUpdateOnLaunchEnabled() {
        return getBoolean("autoUpdateOnLaunch", false);
    }

    public static void setAutoUpdateOnLaunchEnabled(boolean value) {
        setBoolean("autoUpdateOnLaunch", value);
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

    public static boolean isAutoOpenBrowserEnabled() {
        return getBoolean("autoOpenBrowserEnabled", false);
    }

    public static void setAutoOpenBrowserEnabled(boolean value) {
        setBoolean("autoOpenBrowserEnabled", value);
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

    public static String getDefaultMinecraftVersion() {
        return getString("defaultMinecraftVersion", "");
    }

    public static void setDefaultMinecraftVersion(String value) {
        setString("defaultMinecraftVersion", value == null ? "" : value.trim());
    }

    public static String getDefaultLoader() {
        return getString("defaultLoader", "Fabric");
    }

    public static void setDefaultLoader(String value) {
        String loader = value == null ? "Fabric" : value.trim();
        if (!"Vanilla".equals(loader) && !"Fabric".equals(loader) && !"Forge".equals(loader)) {
            loader = "Fabric";
        }
        setString("defaultLoader", loader);
    }

    public static boolean isHideLauncherOnLaunchEnabled() {
        return getBoolean("hideLauncherOnLaunch", false);
    }

    public static void setHideLauncherOnLaunchEnabled(boolean value) {
        setBoolean("hideLauncherOnLaunch", value);
    }

    public static String getDefaultJavaPath() {
        return getString("defaultJavaPath", "");
    }

    public static void setDefaultJavaPath(String value) {
        setString("defaultJavaPath", value == null ? "" : value.trim());
    }

    public static String getDefaultJavaArguments() {
        return getString("defaultJavaArguments", "");
    }

    public static void setDefaultJavaArguments(String value) {
        setString("defaultJavaArguments", value == null ? "" : value.trim());
    }

    public static boolean isDiscordPresenceEnabled() {
        return getBoolean("discordPresenceEnabled", false);
    }

    public static void setDiscordPresenceEnabled(boolean value) {
        setBoolean("discordPresenceEnabled", value);
    }

    public static boolean isDiscordShowPlaytimeEnabled() {
        return getBoolean("discordShowPlaytime", true);
    }

    public static void setDiscordShowPlaytimeEnabled(boolean value) {
        setBoolean("discordShowPlaytime", value);
    }

    public static boolean isDiscordShowInstanceEnabled() {
        return getBoolean("discordShowInstance", true);
    }

    public static void setDiscordShowInstanceEnabled(boolean value) {
        setBoolean("discordShowInstance", value);
    }

    public static boolean isDiscordShowVersionEnabled() {
        return getBoolean("discordShowVersion", true);
    }

    public static void setDiscordShowVersionEnabled(boolean value) {
        setBoolean("discordShowVersion", value);
    }

    public static boolean isDiscordShowLoaderEnabled() {
        return getBoolean("discordShowLoader", true);
    }

    public static void setDiscordShowLoaderEnabled(boolean value) {
        setBoolean("discordShowLoader", value);
    }

    public static boolean isShowPlaytimeEnabled() { return getBoolean("showPlaytime", true); }
    public static void setShowPlaytimeEnabled(boolean value) { setBoolean("showPlaytime", value); }
    public static boolean isCompactInstancesEnabled() { return getBoolean("compactInstances", false); }
    public static void setCompactInstancesEnabled(boolean value) { setBoolean("compactInstances", value); }
    public static boolean isStateScanOnOpenEnabled() { return getBoolean("stateScanOnOpen", true); }
    public static void setStateScanOnOpenEnabled(boolean value) { setBoolean("stateScanOnOpen", value); }
    public static boolean isNotificationsEnabled() { return getBoolean("notificationsEnabled", true); }
    public static void setNotificationsEnabled(boolean value) { setBoolean("notificationsEnabled", value); }
    public static boolean isRememberLastSettingsPageEnabled() { return getBoolean("rememberLastSettingsPage", true); }
    public static void setRememberLastSettingsPageEnabled(boolean value) { setBoolean("rememberLastSettingsPage", value); }
    public static int getDownloadRetries() { return getInt("downloadRetries", 3, 1, 8); }
    public static void setDownloadRetries(int value) { setInt("downloadRetries", clamp(value, 1, 8)); }
    public static boolean isVerifyDownloadsEnabled() { return getBoolean("verifyDownloads", true); }
    public static void setVerifyDownloadsEnabled(boolean value) { setBoolean("verifyDownloads", value); }
    public static boolean isAutoRepairOnLaunchEnabled() { return getBoolean("autoRepairOnLaunch", true); }
    public static void setAutoRepairOnLaunchEnabled(boolean value) { setBoolean("autoRepairOnLaunch", value); }
    public static boolean isShowModrinthDownloadsEnabled() { return getBoolean("showModrinthDownloads", true); }
    public static void setShowModrinthDownloadsEnabled(boolean value) { setBoolean("showModrinthDownloads", value); }
    public static boolean isConfirmInstanceDeletionEnabled() { return getBoolean("confirmInstanceDeletion", true); }
    public static void setConfirmInstanceDeletionEnabled(boolean value) { setBoolean("confirmInstanceDeletion", value); }
    public static int getStateScanWorkers() {
        int fallback = Math.max(2, Math.min(Runtime.getRuntime().availableProcessors(), 8));
        return getInt("stateScanWorkers", fallback, 1, 32);
    }

    public static void setStateScanWorkers(int value) {
        setInt("stateScanWorkers", clamp(value, 1, 32));
    }

    public static boolean isStateAutoRepairEnabled() {
        return getBoolean("stateAutoRepair", false);
    }

    public static void setStateAutoRepairEnabled(boolean value) {
        setBoolean("stateAutoRepair", value);
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

    private static String getString(String key, String fallback) {
        Properties properties = load();
        return properties.getProperty(key, fallback);
    }

    private static void setString(String key, String value) {
        Properties properties = load();
        properties.setProperty(key, value);
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
