package org.example.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class ForgeInstaller {

    private static final String PROMOTIONS_URL =
            "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json";

    private static final String MAVEN_BASE =
            "https://maven.minecraftforge.net/net/minecraftforge/forge/";

    private ForgeInstaller() {
    }

    public static String findLatestLoaderVersion(
            String minecraftVersion
    ) throws Exception {

        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            throw new IllegalArgumentException("Minecraft version cannot be empty.");
        }

        JsonNode promotions =
                DownloadUtil.downloadJson(PROMOTIONS_URL);

        String latest =
                textValue(
                        promotions,
                        minecraftVersion + "-latest"
                );

        if (latest == null || latest.isBlank()) {
            latest =
                    textValue(
                            promotions,
                            minecraftVersion + "-recommended"
                    );
        }

        if (latest == null || latest.isBlank()) {
            throw new IllegalArgumentException(
                    "Forge is not available for Minecraft " + minecraftVersion + "."
            );
        }

        return latest;
    }

    public static void installForge(
            Instance instance,
            String forgeVersion
    ) throws Exception {

        if (instance == null) {
            throw new IllegalArgumentException("Instance cannot be null.");
        }

        if (forgeVersion == null || forgeVersion.isBlank()) {
            throw new IllegalArgumentException("Forge version cannot be empty.");
        }

        String coordinate =
                instance.getMinecraftVersion()
                        + "-"
                        + forgeVersion;

        String installerName =
                "forge-"
                        + coordinate
                        + "-installer.jar";

        String url =
                MAVEN_BASE
                        + coordinate
                        + "/"
                        + installerName;

        Path installer =
                Files.createTempFile(
                        "vanta-forge-",
                        "-installer.jar"
                );

        try {
            DownloadUtil.downloadFile(url, installer);

            Path target =
                    instance.getDirectory();

            Path java =
                    resolveJava();

            Process process =
                    new ProcessBuilder(
                            java.toString(),
                            "-jar",
                            installer.toString(),
                            "--installClient",
                            target.toString()
                    )
                            .directory(target.toFile())
                            .redirectErrorStream(true)
                            .start();

            String output =
                    new String(
                            process.getInputStream().readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8
                    );

            int exitCode =
                    process.waitFor();

            if (exitCode != 0) {
                throw new IOException(
                        "Forge installer failed (exit "
                                + exitCode
                                + "): "
                                + tail(output)
                );
            }

            Path metadata =
                    findForgeVersionMetadata(target);

            if (metadata == null) {
                throw new IOException(
                        "Forge installed, but no Forge version metadata was found."
                );
            }

            JsonNode forgeMetadata =
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(metadata.toFile());

            MinecraftVersionResolver.saveMetadata(
                    instance,
                    forgeMetadata
            );
        } finally {
            Files.deleteIfExists(installer);
        }
    }

    private static Path findForgeVersionMetadata(
            Path instanceDirectory
    ) throws IOException {

        Path versions =
                instanceDirectory.resolve("versions");

        if (!Files.isDirectory(versions)) {
            return null;
        }

        try (var stream = Files.walk(versions, 3)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> path.getParent() != null
                            && path.getParent().getFileName() != null
                            && path.getParent().getFileName().toString()
                            .toLowerCase()
                            .contains("forge"))
                    .max(Comparator.comparingLong(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }))
                    .orElse(null);
        }
    }

    private static Path resolveJava() {

        String configured =
                System.getProperty("java.home");

        String executable =
                System.getProperty("os.name", "")
                        .toLowerCase()
                        .contains("win")
                        ? "java.exe"
                        : "java";

        Path java =
                Path.of(
                        configured,
                        "bin",
                        executable
                );

        if (Files.isRegularFile(java)) {
            return java;
        }

        return Path.of(executable);
    }

    private static String textValue(
            JsonNode node,
            String key
    ) {

        JsonNode value =
                node == null ? null : node.get(key);

        return value == null || value.isNull()
                ? null
                : value.asText();
    }

    private static String tail(String output) {

        if (output == null || output.isBlank()) {
            return "No installer output was provided.";
        }

        output = output.trim();

        return output.length() > 1200
                ? output.substring(output.length() - 1200)
                : output;
    }
}
