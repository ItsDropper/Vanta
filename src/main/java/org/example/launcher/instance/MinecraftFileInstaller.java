package org.example.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;

import org.example.launcher.MinecraftLocator;
import org.example.launcher.model.Instance;

import java.nio.file.Files;
import java.nio.file.Path;

public class MinecraftFileInstaller {

    // =============================================================
    // CLIENT
    // =============================================================

    public static void installClient(
            Instance instance,
            JsonNode metadata
    ) throws Exception {

        JsonNode downloads =
                metadata.get("downloads");

        if (downloads == null) {
            throw new IllegalStateException(
                    "Minecraft downloads metadata is missing."
            );
        }

        JsonNode client =
                downloads.get("client");

        if (client == null) {
            throw new IllegalStateException(
                    "Minecraft client download is missing."
            );
        }

        String url =
                client
                        .get("url")
                        .asText();

        Path minecraftDirectory =
                instance
                        .getDirectory()
                        .resolve("minecraft");

        Files.createDirectories(
                minecraftDirectory
        );

        Path target =
                minecraftDirectory.resolve(
                        "client.jar"
                );

        DownloadUtil.downloadFile(
                url,
                target
        );
    }

    // =============================================================
    // LIBRARIES
    // =============================================================

    public static void installLibraries(
            JsonNode metadata
    ) throws Exception {

        JsonNode libraries =
                metadata.get("libraries");

        if (libraries == null
                || !libraries.isArray()) {

            return;
        }

        Path librariesDirectory =
                MinecraftLocator
                        .getLibrariesDirectory();

        for (JsonNode library : libraries) {

            JsonNode downloads =
                    library.get("downloads");

            if (downloads == null) {
                continue;
            }

            // -----------------------------------------------------
            // NORMAL JAR
            // -----------------------------------------------------

            JsonNode artifact =
                    downloads.get("artifact");

            if (artifact != null) {

                String url =
                        artifact
                                .get("url")
                                .asText();

                String path =
                        artifact
                                .get("path")
                                .asText();

                Path target =
                        librariesDirectory.resolve(
                                path
                        );

                if (!Files.exists(target)) {

                    Files.createDirectories(
                            target.getParent()
                    );

                    DownloadUtil.downloadFile(
                            url,
                            target
                    );
                }
            }

            // -----------------------------------------------------
            // WINDOWS NATIVES
            // -----------------------------------------------------

            JsonNode classifiers =
                    downloads.get("classifiers");

            if (classifiers == null) {
                continue;
            }

            JsonNode windows =
                    classifiers.get(
                            "natives-windows"
                    );

            if (windows == null) {
                continue;
            }

            String url =
                    windows
                            .get("url")
                            .asText();

            String path =
                    windows
                            .get("path")
                            .asText();

            Path target =
                    librariesDirectory.resolve(
                            path
                    );

            if (!Files.exists(target)) {

                Files.createDirectories(
                        target.getParent()
                );

                DownloadUtil.downloadFile(
                        url,
                        target
                );
            }
        }
    }
}