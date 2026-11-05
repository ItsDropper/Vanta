package dev.itsdropper.vanta.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MinecraftLocator {

    /**
     * Resolves Vanta's per-user data directory using the host operating system's
     * standard application-data location. The path is returned without creation.
     */
    public static Path getVantaDirectoryPath() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.isBlank()) {
                throw new IllegalStateException("APPDATA is not available on Windows.");
            }
            return Paths.get(appData, "Vanta").toAbsolutePath().normalize();
        }

        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) {
            throw new IllegalStateException("The user home directory is not available.");
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return Paths.get(home, "Library", "Application Support", "Vanta")
                    .toAbsolutePath().normalize();
        }

        String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
        Path configHome = xdgConfigHome != null && !xdgConfigHome.isBlank()
                ? Paths.get(xdgConfigHome)
                : Paths.get(home, ".config");
        return configHome.resolve("Vanta").toAbsolutePath().normalize();
    }

    public static Path getVantaDirectory() {
        Path directory = getVantaDirectoryPath();
        try {
            Files.createDirectories(directory);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Vanta directory.", e);
        }
        return directory;
    }

    public static Path getInstancesDirectory() {
        Path directory = getVantaDirectory().resolve("instances");
        try {
            Files.createDirectories(directory);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create instances directory.", e);
        }
        return directory;
    }

    /** Resolves the shared libraries directory without creating it. */
    public static Path getLibrariesDirectoryPath() {
        return getVantaDirectoryPath().resolve("libraries");
    }

    public static Path getLibrariesDirectory() {
        Path directory = getLibrariesDirectoryPath();
        try {
            Files.createDirectories(directory);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create libraries directory.", e);
        }
        return directory;
    }
}
