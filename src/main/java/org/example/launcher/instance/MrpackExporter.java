package org.example.launcher.instance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.example.launcher.model.Instance;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class MrpackExporter {

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    private MrpackExporter() {
    }

    /**
     * Exports a Vanta instance as a Modrinth .mrpack file.
     *
     * The exported pack contains:
     *
     * - Minecraft version
     * - Loader information
     * - mods
     * - config
     * - resourcepacks
     * - shaderpacks
     *
     * Minecraft itself, libraries, assets, saves, logs,
     * screenshots, Vanta metadata and settings are not exported.
     */
    public static void exportMrpack(
            Instance instance,
            Path output
    ) throws IOException {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        if (output == null) {
            throw new IllegalArgumentException(
                    "Output path cannot be null."
            );
        }

        Path instanceDirectory =
                instance.getDirectory();

        if (instanceDirectory == null
                || !Files.isDirectory(instanceDirectory)) {

            throw new IOException(
                    "Instance directory does not exist."
            );
        }

        output = output.toAbsolutePath().normalize();

        if (output.getParent() != null) {
            Files.createDirectories(
                    output.getParent()
            );
        }

        Path temporaryOutput =
                output.resolveSibling(
                        output.getFileName()
                                + ".exporting"
                );

        Files.deleteIfExists(
                temporaryOutput
        );

        try {

            ObjectNode index =
                    createIndex(instance);

            try (
                    OutputStream fileOutput =
                            Files.newOutputStream(
                                    temporaryOutput
                            );

                    ZipOutputStream zip =
                            new ZipOutputStream(
                                    fileOutput
                            )
            ) {

                // -------------------------------------------------
                // MODRINTH INDEX
                // -------------------------------------------------

                byte[] indexBytes =
                        MAPPER
                                .writerWithDefaultPrettyPrinter()
                                .writeValueAsBytes(index);

                ZipEntry indexEntry =
                        new ZipEntry(
                                "modrinth.index.json"
                        );

                zip.putNextEntry(
                        indexEntry
                );

                zip.write(indexBytes);

                zip.closeEntry();

                // -------------------------------------------------
                // INSTANCE CONTENT
                // -------------------------------------------------

                List<String> directories =
                        List.of(
                                "mods",
                                "config",
                                "resourcepacks",
                                "shaderpacks"
                        );

                for (String directoryName : directories) {

                    Path directory =
                            instanceDirectory.resolve(
                                    directoryName
                            );

                    if (!Files.isDirectory(directory)) {
                        continue;
                    }

                    addDirectory(
                            zip,
                            instanceDirectory,
                            directory
                    );
                }
            }

            // Atomic-ish replacement:
            // the incomplete .exporting file never becomes
            // the final pack name.
            Files.move(
                    temporaryOutput,
                    output,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
            );

        } catch (Exception e) {

            Files.deleteIfExists(
                    temporaryOutput
            );

            if (e instanceof IOException ioException) {
                throw ioException;
            }

            throw new IOException(
                    "Failed to export instance.",
                    e
            );
        }
    }

    // =============================================================
    // MODRINTH INDEX
    // =============================================================

    private static ObjectNode createIndex(
            Instance instance
    ) {

        ObjectNode index =
                MAPPER.createObjectNode();

        index.put(
                "formatVersion",
                1
        );

        index.put(
                "game",
                "minecraft"
        );

        index.put(
                "versionId",
                instance.getMinecraftVersion()
        );

        index.put(
                "name",
                instance.getName()
        );

        index.put(
                "summary",
                "Exported from Vanta"
        );

        ObjectNode dependencies =
                index.putObject(
                        "dependencies"
                );

        dependencies.put(
                "minecraft",
                instance.getMinecraftVersion()
        );

        String loader =
                instance.getLoader();

        String loaderVersion =
                instance.getLoaderVersion();

        if (loader != null
                && !loader.isBlank()
                && loaderVersion != null
                && !loaderVersion.isBlank()) {

            switch (loader.toLowerCase()) {

                case "fabric" ->
                        dependencies.put(
                                "fabric-loader",
                                loaderVersion
                        );

                case "forge" ->
                        dependencies.put(
                                "forge",
                                loaderVersion
                        );

                case "neoforge" ->
                        dependencies.put(
                                "neoforge",
                                loaderVersion
                        );

                case "quilt" ->
                        dependencies.put(
                                "quilt-loader",
                                loaderVersion
                        );

                default -> {
                    // Unknown loaders are deliberately not
                    // written into the Modrinth manifest.
                }
            }
        }

        /*
         * All local instance files are placed inside
         * overrides/, so the files array can remain empty.
         *
         * This is valid for packs containing locally supplied
         * files.
         */
        ArrayNode files =
                index.putArray(
                        "files"
                );

        return index;
    }

    // =============================================================
    // DIRECTORY
    // =============================================================

    private static void addDirectory(
            ZipOutputStream zip,
            Path instanceDirectory,
            Path directory
    ) throws IOException {

        List<Path> paths =
                new ArrayList<>();

        try (var stream = Files.walk(directory)) {

            stream
                    .filter(Files::isRegularFile)
                    .forEach(paths::add);
        }

        paths.sort(
                Comparator.comparing(
                        Path::toString
                )
        );

        for (Path file : paths) {

            Path relative =
                    instanceDirectory.relativize(
                            file
                    );

            String entryName =
                    "overrides/"
                            + relative
                            .toString()
                            .replace(
                                    '\\',
                                    '/'
                            );

            addFile(
                    zip,
                    file,
                    entryName
            );
        }
    }

    // =============================================================
    // FILE
    // =============================================================

    private static void addFile(
            ZipOutputStream zip,
            Path file,
            String entryName
    ) throws IOException {

        ZipEntry entry =
                new ZipEntry(
                        entryName
                );

        zip.putNextEntry(
                entry
        );

        Files.copy(
                file,
                zip
        );

        zip.closeEntry();
    }

    // =============================================================
    // HASH
    // =============================================================

    private static String sha1(
            Path file
    ) throws IOException {

        return hash(
                file,
                "SHA-1"
        );
    }

    private static String sha512(
            Path file
    ) throws IOException {

        return hash(
                file,
                "SHA-512"
        );
    }

    private static String hash(
            Path file,
            String algorithm
    ) throws IOException {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            algorithm
                    );

            try (
                    var input =
                            Files.newInputStream(
                                    file
                            )
            ) {

                byte[] buffer =
                        new byte[8192];

                int read;

                while (
                        (read =
                                input.read(buffer))
                                != -1
                ) {

                    digest.update(
                            buffer,
                            0,
                            read
                    );
                }
            }

            StringBuilder result =
                    new StringBuilder();

            for (byte value : digest.digest()) {

                result.append(
                        String.format(
                                "%02x",
                                value
                        )
                );
            }

            return result.toString();

        } catch (java.security.NoSuchAlgorithmException e) {

            throw new IOException(
                    "Hash algorithm is unavailable: "
                            + algorithm,
                    e
            );
        }
    }
}

