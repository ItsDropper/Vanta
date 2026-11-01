package dev.itsdropper.vanta.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MinecraftLocator {

    /**
     * Resolves Vanta's data directory without creating it.
     */
    public static Path getVantaDirectoryPath() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            throw new IllegalStateException("APPDATA is not available.");
        }
        return Paths.get(appData, "Vanta").toAbsolutePath().normalize();
    }

    public static Path getVantaDirectory() {

        Path directory = getVantaDirectoryPath();

        try {

            Files.createDirectories(
                    directory
            );

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to create Vanta directory.",
                    e
            );
        }

        return directory;
    }

    public static Path getInstancesDirectory() {

        Path directory =
                getVantaDirectory()
                        .resolve("instances");

        try {

            Files.createDirectories(
                    directory
            );

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to create instances directory.",
                    e
            );
        }

        return directory;
    }

    /**
     * Resolves the shared libraries directory without creating it.
     */
    public static Path getLibrariesDirectoryPath() {
        return getVantaDirectoryPath().resolve("libraries");
    }

    public static Path getLibrariesDirectory() {

        Path directory = getLibrariesDirectoryPath();

        try {

            Files.createDirectories(
                    directory
            );

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to create libraries directory.",
                    e
            );
        }

        return directory;
    }
}