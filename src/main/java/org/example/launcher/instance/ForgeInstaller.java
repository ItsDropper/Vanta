package org.example.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.launcher.model.Instance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;

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

        String metadataUrl =
                MAVEN_BASE
                        + "maven-metadata.xml";

        Path metadataFile =
                Files.createTempFile(
                        "vanta-forge-metadata-",
                        ".xml"
                );

        try {
            DownloadUtil.downloadFile(metadataUrl, metadataFile);

            var document =
                    DocumentBuilderFactory
                            .newInstance()
                            .newDocumentBuilder()
                            .parse(metadataFile.toFile());

            var versionNodes =
                    document.getElementsByTagName("version");

            List<String> candidates =
                    new ArrayList<>();

            String prefix =
                    minecraftVersion + "-";

            for (int i = 0; i < versionNodes.getLength(); i++) {
                String version =
                        versionNodes.item(i)
                                .getTextContent()
                                .trim();

                if (version.startsWith(prefix)
                        && version.length() > prefix.length()) {
                    candidates.add(
                            version.substring(prefix.length())
                    );
                }
            }

            if (candidates.isEmpty()) {
                throw new IllegalArgumentException(
                        "Forge is not available for Minecraft "
                                + minecraftVersion
                                + "."
                );
            }

            candidates.sort(
                    ForgeInstaller::compareVersions
            );

            return candidates.get(candidates.size() - 1);

        } finally {
            Files.deleteIfExists(metadataFile);
        }
    }

    private static int compareVersions(
            String left,
            String right
    ) {
        String[] a = left.split("[.-]");
        String[] b = right.split("[.-]");

        int length = Math.max(a.length, b.length);

        for (int i = 0; i < length; i++) {
            String av = i < a.length ? a[i] : "0";
            String bv = i < b.length ? b[i] : "0";

            try {
                int ai = Integer.parseInt(av);
                int bi = Integer.parseInt(bv);

                if (ai != bi) {
                    return Integer.compare(ai, bi);
                }
            } catch (NumberFormatException e) {
                int comparison =
                        av.compareToIgnoreCase(bv);

                if (comparison != 0) {
                    return comparison;
                }
            }
        }

        return 0;
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

            /*
             * Forge's official client installer expects a Minecraft
             * launcher profile in the target directory. Vanta uses
             * isolated instance directories instead of the vanilla
             * launcher's profile system, so provide a minimal temporary
             * profile for the installer and remove it afterwards.
             */
            Path launcherProfile =
                    target.resolve("launcher_profiles.json");

            boolean createdProfile =
                    false;

            if (!Files.exists(launcherProfile)) {
                Files.writeString(
                        launcherProfile,
                        "{}",
                        StandardCharsets.UTF_8
                );
                createdProfile = true;
            }

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
                            StandardCharsets.UTF_8
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

            if (createdProfile) {
                Files.deleteIfExists(
                        instance.getDirectory()
                                .resolve("launcher_profiles.json")
                );
            }
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
