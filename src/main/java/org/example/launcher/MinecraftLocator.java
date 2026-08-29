package org.example.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MinecraftLocator {

    public static Path getVantaDirectory() {

        String appData =
                System.getenv("APPDATA");

        if (appData == null) {
            throw new IllegalStateException(
                    "APPDATA is not available."
            );
        }

        Path directory =
                Paths.get(
                        appData,
                        "Vanta"
                );

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

    public static Path getLibrariesDirectory() {

        Path directory =
                getVantaDirectory()
                        .resolve("libraries");

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