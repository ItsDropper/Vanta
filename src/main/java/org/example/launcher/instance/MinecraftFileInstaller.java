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

        String sha1 =
                getSha1(client);

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

        downloadIfNeeded(
                url,
                target,
                sha1
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

                String sha1 =
                        getSha1(artifact);

                Path target =
                        librariesDirectory.resolve(
                                path
                        );

                downloadIfNeeded(
                        url,
                        target,
                        sha1
                );
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

            String sha1 =
                    getSha1(windows);

            Path target =
                    librariesDirectory.resolve(
                            path
                    );

            downloadIfNeeded(
                    url,
                    target,
                    sha1
            );
        }
    }

    // =============================================================
    // DOWNLOAD / VERIFY
    // =============================================================

    private static void downloadIfNeeded(
            String url,
            Path target,
            String expectedSha1
    ) throws Exception {

        if (Files.exists(target)) {

            if (expectedSha1 == null
                    || expectedSha1.isBlank()) {

                return;
            }

            String actualSha1 =
                    calculateSha1(target);

            if (actualSha1.equalsIgnoreCase(
                    expectedSha1
            )) {

                return;
            }

            System.out.println(
                    "Checksum mismatch. Re-downloading: "
                            + target
            );

            Files.deleteIfExists(
                    target
            );
        }

        DownloadUtil.downloadFile(
                url,
                target,
                expectedSha1
        );
    }

    // =============================================================
    // SHA-1
    // =============================================================

    private static String calculateSha1(
            Path file
    ) throws Exception {

        java.security.MessageDigest digest =
                java.security.MessageDigest.getInstance(
                        "SHA-1"
                );

        try (java.io.InputStream input =
                     Files.newInputStream(file)) {

            byte[] buffer =
                    new byte[8192];

            int read;

            while ((read = input.read(buffer)) != -1) {

                digest.update(
                        buffer,
                        0,
                        read
                );
            }
        }

        byte[] hash =
                digest.digest();

        StringBuilder result =
                new StringBuilder(
                        hash.length * 2
                );

        for (byte value : hash) {

            result.append(
                    String.format(
                            "%02x",
                            value & 0xff
                    )
            );
        }

        return result.toString();
    }

    // =============================================================
    // METADATA
    // =============================================================

    private static String getSha1(
            JsonNode node
    ) {

        JsonNode sha1 =
                node.get("sha1");

        if (sha1 == null
                || sha1.isNull()) {

            return null;
        }

        String value =
                sha1.asText();

        if (value == null
                || value.isBlank()) {

            return null;
        }

        return value;
    }
}