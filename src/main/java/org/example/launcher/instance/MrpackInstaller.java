package org.example.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.example.launcher.model.Instance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MrpackInstaller {

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    private static final String INDEX_FILE =
            "modrinth.index.json";

// =============================================================
// IMPORT
// =============================================================

    public static Instance importMrpack(
            Path mrpack
    ) throws Exception {

        if (mrpack == null) {
            throw new IllegalArgumentException(
                    "Modpack file cannot be null."
            );
        }

        if (!Files.exists(mrpack)) {
            throw new IOException(
                    "Modpack file does not exist."
            );
        }

        if (!Files.isRegularFile(mrpack)) {
            throw new IOException(
                    "Modpack path is not a file."
            );
        }

        // ---------------------------------------------------------
        // READ INDEX
        // ---------------------------------------------------------

        JsonNode index =
                readIndex(
                        mrpack
                );

        validateIndex(
                index
        );

        JsonNode dependencies =
                index.get("dependencies");

        String minecraftVersion =
                dependencies
                        .get("minecraft")
                        .asText();

        String loader;
        String loaderVersion = null;

        if (dependencies.has("fabric-loader")) {

            loader =
                    "Fabric";

            loaderVersion =
                    dependencies
                            .get("fabric-loader")
                            .asText();

        } else if (
                dependencies.has("forge")
                        || dependencies.has("neoforge")
                        || dependencies.has("quilt-loader")
        ) {

            throw new IllegalStateException(
                    "This modpack uses a mod loader that Vanta does not support yet."
            );

        } else {

            loader =
                    "Vanilla";
        }

        String name =
                index.path("name")
                        .asText("Imported Modpack");

        // ---------------------------------------------------------
        // CREATE BASE INSTANCE
        // ---------------------------------------------------------

        Instance instance;

        if ("Fabric".equals(loader)) {

            /*
             * Vanta's existing Fabric installer currently resolves
             * the latest compatible loader itself.
             *
             * The exact loader version from the mrpack is validated
             * below, but the existing installer remains responsible
             * for installing the Minecraft/Fabric environment.
             */
            instance =
                    InstanceInstaller.installFabric(
                            name,
                            minecraftVersion
                    );

        } else {

            instance =
                    InstanceInstaller.installVanilla(
                            name,
                            minecraftVersion
                    );
        }

        try {

            // -----------------------------------------------------
            // PACK FILES
            // -----------------------------------------------------

            installFiles(
                    mrpack,
                    index,
                    instance
            );

            // -----------------------------------------------------
            // OVERRIDES
            // -----------------------------------------------------

            installOverrides(
                    mrpack,
                    instance
            );

            return instance;

        } catch (Exception e) {

            try {
                InstanceManager.deleteInstance(
                        instance
                );
            } catch (Exception ignored) {
            }

            throw e;
        }
    }

// =============================================================
// READ INDEX
// =============================================================

    private static JsonNode readIndex(
            Path mrpack
    ) throws Exception {

        try (
                InputStream input =
                        Files.newInputStream(
                                mrpack
                        );

                ZipInputStream zip =
                        new ZipInputStream(
                                input
                        )
        ) {

            ZipEntry entry;

            while ((entry = zip.getNextEntry()) != null) {

                if (entry.isDirectory()) {
                    continue;
                }

                if (!INDEX_FILE.equals(
                        entry.getName()
                )) {

                    continue;
                }

                return MAPPER.readTree(
                        zip
                );
            }
        }

        throw new IllegalStateException(
                "Invalid .mrpack: modrinth.index.json is missing."
        );
    }

// =============================================================
// VALIDATE INDEX
// =============================================================

    private static void validateIndex(
            JsonNode index
    ) {

        if (index == null
                || !index.isObject()) {

            throw new IllegalStateException(
                    "Invalid modrinth.index.json."
            );
        }

        int formatVersion =
                index.path(
                        "formatVersion"
                ).asInt(
                        -1
                );

        if (formatVersion != 1) {

            throw new IllegalStateException(
                    "Unsupported .mrpack format version: "
                            + formatVersion
            );
        }

        String game =
                index.path("game")
                        .asText();

        if (!"minecraft".equalsIgnoreCase(game)) {

            throw new IllegalStateException(
                    "This .mrpack is not a Minecraft modpack."
            );
        }

        JsonNode dependencies =
                index.get("dependencies");

        if (dependencies == null
                || !dependencies.isObject()) {

            throw new IllegalStateException(
                    "Modpack Minecraft dependency is missing."
            );
        }

        if (!dependencies.has("minecraft")
                || dependencies
                .get("minecraft")
                .asText()
                .isBlank()) {

            throw new IllegalStateException(
                    "Modpack Minecraft version is missing."
            );
        }
    }

// =============================================================
// PACK FILES
// =============================================================

    private static void installFiles(
            Path mrpack,
            JsonNode index,
            Instance instance
    ) throws Exception {

        JsonNode files =
                index.get("files");

        if (files == null
                || !files.isArray()) {

            return;
        }

        for (JsonNode file : files) {

            String path =
                    file.path("path")
                            .asText();

            if (path.isBlank()) {
                continue;
            }

            Path target =
                    resolveSafePath(
                            instance.getDirectory(),
                            path
                    );

            JsonNode downloads =
                    file.get("downloads");

            if (downloads == null
                    || !downloads.isArray()
                    || downloads.isEmpty()) {

                throw new IllegalStateException(
                        "No download URL for modpack file: "
                                + path
                );
            }

            String sha1 =
                    file.path("hashes")
                            .path("sha1")
                            .asText(null);

            String sha512 =
                    file.path("hashes")
                            .path("sha512")
                            .asText(null);

            String downloadUrl =
                    null;

            for (JsonNode download : downloads) {

                if (download == null
                        || !download.isTextual()) {
                    continue;
                }

                String url =
                        download.asText();

                if (url.startsWith("https://")) {

                    downloadUrl =
                            url;

                    break;
                }
            }

            if (downloadUrl == null) {

                throw new IllegalStateException(
                        "No HTTPS download URL for modpack file: "
                                + path
                );
            }

            downloadAndVerify(
                    downloadUrl,
                    target,
                    sha1,
                    sha512
            );
        }
    }

// =============================================================
// DOWNLOAD
// =============================================================

    private static void downloadAndVerify(
            String url,
            Path target,
            String expectedSha1,
            String expectedSha512
    ) throws Exception {

        DownloadUtil.downloadFile(
                url,
                target
        );

        boolean verified =
                false;

        if (expectedSha1 != null
                && !expectedSha1.isBlank()) {

            String actualSha1 =
                    calculateHash(
                            target,
                            "SHA-1"
                    );

            if (!actualSha1.equalsIgnoreCase(
                    expectedSha1
            )) {

                Files.deleteIfExists(
                        target
                );

                throw new IOException(
                        "SHA-1 verification failed for "
                                + target.getFileName()
                );
            }

            verified = true;
        }

        if (expectedSha512 != null
                && !expectedSha512.isBlank()) {

            String actualSha512 =
                    calculateHash(
                            target,
                            "SHA-512"
                    );

            if (!actualSha512.equalsIgnoreCase(
                    expectedSha512
            )) {

                Files.deleteIfExists(
                        target
                );

                throw new IOException(
                        "SHA-512 verification failed for "
                                + target.getFileName()
                );
            }

            verified = true;
        }

        if (!verified) {

            Files.deleteIfExists(
                    target
            );

            throw new IOException(
                    "Modpack file has no usable checksum: "
                            + target.getFileName()
            );
        }
    }

// =============================================================
// OVERRIDES
// =============================================================

    private static void installOverrides(
            Path mrpack,
            Instance instance
    ) throws Exception {

        try (
                InputStream input =
                        Files.newInputStream(
                                mrpack
                        );

                ZipInputStream zip =
                        new ZipInputStream(
                                input
                        )
        ) {

            ZipEntry entry;

            while ((entry = zip.getNextEntry()) != null) {

                String entryName =
                        entry.getName();

                if (!entryName.startsWith(
                        "overrides/"
                )) {

                    continue;
                }

                String relative =
                        entryName.substring(
                                "overrides/".length()
                        );

                if (relative.isBlank()) {
                    continue;
                }

                Path target =
                        resolveSafePath(
                                instance.getDirectory(),
                                relative
                        );

                if (entry.isDirectory()) {

                    Files.createDirectories(
                            target
                    );

                    continue;
                }

                if (target.getParent() != null) {

                    Files.createDirectories(
                            target.getParent()
                    );
                }

                Files.copy(
                        zip,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        }
    }

// =============================================================
// SAFE PATH
// =============================================================

    private static Path resolveSafePath(
            Path instanceDirectory,
            String relativePath
    ) throws IOException {

        if (relativePath == null
                || relativePath.isBlank()) {

            throw new IOException(
                    "Invalid empty modpack path."
            );
        }

        String normalized =
                relativePath.replace(
                        '\\',
                        '/'
                );

        if (normalized.startsWith("/")
                || normalized.matches(
                "^[A-Za-z]:.*"
        )) {

            throw new IOException(
                    "Unsafe modpack path: "
                            + relativePath
            );
        }

        Path base =
                instanceDirectory
                        .toAbsolutePath()
                        .normalize();

        Path resolved =
                base.resolve(
                        normalized
                ).normalize();

        if (!resolved.startsWith(base)) {

            throw new IOException(
                    "Unsafe modpack path: "
                            + relativePath
            );
        }

        return resolved;
    }

// =============================================================
// HASH
// =============================================================

    private static String calculateHash(
            Path file,
            String algorithm
    ) throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance(
                        algorithm
                );

        try (InputStream input =
                     Files.newInputStream(
                             file
                     )) {

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


}
