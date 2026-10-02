package org.example.ui;

import org.example.launcher.MinecraftLocator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class OnboardingManager {

    private static final String KEY = "onboardingCompleted";
    private static final Path SETTINGS_FILE =
            MinecraftLocator.getVantaDirectory()
                    .resolve("settings.properties");

    private OnboardingManager() {
    }

    public static boolean shouldShow() {
        if (Boolean.getBoolean("vanta.debug.onboarding")) {
            return true;
        }

        Properties properties = load();
        return !Boolean.parseBoolean(
                properties.getProperty(KEY, "false")
        );
    }

    public static void markCompleted() {
        Properties properties = load();
        properties.setProperty(KEY, "true");

        try {
            Files.createDirectories(SETTINGS_FILE.getParent());

            try (OutputStream output =
                         Files.newOutputStream(SETTINGS_FILE)) {
                properties.store(
                        output,
                        "Vanta launcher settings"
                );
            }
        } catch (IOException ignored) {
        }
    }

    private static Properties load() {
        Properties properties = new Properties();

        if (!Files.exists(SETTINGS_FILE)) {
            return properties;
        }

        try (InputStream input =
                     Files.newInputStream(SETTINGS_FILE)) {
            properties.load(input);
        } catch (IOException ignored) {
        }

        return properties;
    }
}
