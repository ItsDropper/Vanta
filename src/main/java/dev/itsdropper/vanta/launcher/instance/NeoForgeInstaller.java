package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;

public final class NeoForgeInstaller {

    private static final String MAVEN_BASE =
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/";

    private NeoForgeInstaller() {
    }

    public static String findLatestLoaderVersion(
            String minecraftVersion
    ) throws Exception {

        if (minecraftVersion == null
                || !minecraftVersion.matches("[0-9]+(?:\\.[0-9]+){1,3}")) {
            throw new IllegalArgumentException("Minecraft version is invalid.");
        }

        // NeoForge Maven versions omit the leading "1." for the 1.x era:
        // Minecraft 1.21.1 maps to NeoForge 21.1.x.
        String prefix = mavenMinecraftPrefix(minecraftVersion);

        Path metadataFile = Files.createTempFile(
                "vanta-neoforge-metadata-", ".xml");

        try {
            DownloadUtil.downloadFile(MAVEN_BASE + "maven-metadata.xml", metadataFile);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            var document = factory.newDocumentBuilder().parse(metadataFile.toFile());
            var versionNodes = document.getElementsByTagName("version");
            List<String> candidates = new ArrayList<>();

            for (int i = 0; i < versionNodes.getLength(); i++) {
                String version = versionNodes.item(i).getTextContent().trim();
                if (version.startsWith(prefix) && version.length() > prefix.length()) {
                    candidates.add(version);
                }
            }

            if (candidates.isEmpty()) {
                throw new IllegalArgumentException(
                        "NeoForge is not available for Minecraft " + minecraftVersion + ".");
            }

            // Prefer stable builds; use beta builds only if no stable build exists.
            List<String> stable = candidates.stream()
                    .filter(version -> !version.contains("-"))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            List<String> selectable = stable.isEmpty() ? candidates : stable;
            selectable.sort(NeoForgeInstaller::compareVersions);
            return selectable.get(selectable.size() - 1);
        } finally {
            Files.deleteIfExists(metadataFile);
        }
    }

    private static String mavenMinecraftPrefix(String minecraftVersion) {
        if (minecraftVersion == null
                || !minecraftVersion.matches("[0-9]+(?:\\.[0-9]+){1,3}")) {
            throw new IllegalArgumentException("Minecraft version is invalid.");
        }

        String normalized = minecraftVersion.startsWith("1.")
                ? minecraftVersion.substring(2)
                : minecraftVersion;
        if (!normalized.contains(".")) {
            normalized += ".0";
        }
        return normalized + ".";
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

    public static void installNeoForge(
            Instance instance,
            String neoforgeVersion
    ) throws Exception {

        if (instance == null) {
            throw new IllegalArgumentException("Instance cannot be null.");
        }

        if (neoforgeVersion == null
                || !neoforgeVersion.matches("[A-Za-z0-9][A-Za-z0-9._+-]*")) {
            throw new IllegalArgumentException("NeoForge version is invalid.");
        }

        String expectedPrefix = mavenMinecraftPrefix(instance.getMinecraftVersion());
        if (!neoforgeVersion.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException(
                    "NeoForge " + neoforgeVersion + " does not match Minecraft "
                            + instance.getMinecraftVersion() + ".");
        }

        String coordinate = neoforgeVersion;

        String installerName =
                "neoforge-"
                        + coordinate
                        + "-installer.jar";

        String url =
                MAVEN_BASE
                        + coordinate
                        + "/"
                        + installerName;

        Path installer =
                Files.createTempFile(
                        "vanta-neoforge-",
                        "-installer.jar"
                );

        boolean createdProfile = false;

        try {
            DownloadUtil.downloadFile(url, installer);

            Path target =
                    instance.getDirectory();

            /*
             * NeoForge's official client installer expects a Minecraft
             * launcher profile in the target directory. Vanta uses
             * isolated instance directories instead of the vanilla
             * launcher's profile system, so provide a minimal temporary
             * profile for the installer and remove it afterwards.
             */
            Path launcherProfile =
                    target.resolve("launcher_profiles.json");

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
                            "--install-client",
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
                        "NeoForge installer failed (exit "
                                + exitCode
                                + "): "
                                + tail(output)
                );
            }

            Path metadata =
                    findNeoForgeVersionMetadata(target, neoforgeVersion);

            if (metadata == null) {
                throw new IOException(
                        "NeoForge installed, but no NeoForge version metadata was found."
                );
            }

            JsonNode neoforgeMetadata =
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(metadata.toFile());

            JsonNode mainClass = neoforgeMetadata.path("mainClass");
            JsonNode profileLibraries = neoforgeMetadata.path("libraries");
            if (!neoforgeMetadata.isObject()
                    || !mainClass.isTextual()
                    || mainClass.asText().isBlank()
                    || !profileLibraries.isArray()) {
                throw new IOException(
                        "NeoForge installer produced invalid version metadata.");
            }

            /*
             * NeoForge's installer uses the target directory as a
             * temporary Minecraft launcher root. Vanta, however,
             * keeps shared libraries in %APPDATA%/Vanta/libraries.
             *
             * Copy every library produced by NeoForge into Vanta's
             * shared library store while preserving its Maven path.
             * This is critical for NeoForge's universal JAR and its
             * transitive dependencies.
             */
            syncNeoForgeLibraries(
                    target
            );

            /*
             * NeoForge's generated version JSON is an overlay: it can
             * inherit the vanilla version and therefore omit fields
             * such as assetIndex. Vanta stores one self-contained
             * version.json, so merge NeoForge's fields onto the vanilla
             * metadata instead of replacing the vanilla metadata.
             */
            JsonNode vanillaMetadata =
                    MinecraftVersionResolver.downloadMetadata(
                            instance.getMinecraftVersion()
                    );

            ObjectNode mergedMetadata =
                    vanillaMetadata.deepCopy();

            neoforgeMetadata.fields().forEachRemaining(entry -> {
                if (!"inherits".equals(entry.getKey())
                        && !"inheritsFrom".equals(entry.getKey())
                        && !"libraries".equals(entry.getKey())) {
                    mergedMetadata.set(
                            entry.getKey(),
                            entry.getValue().deepCopy()
                    );
                }
            });

            JsonNode neoforgeLibraries =
                    neoforgeMetadata.get("libraries");

            if (neoforgeLibraries != null && neoforgeLibraries.isArray()) {
                ArrayNode libraries =
                        mergedMetadata.withArray("libraries");
                neoforgeLibraries.forEach(library ->
                        libraries.add(library.deepCopy())
                );
            }

            MinecraftVersionResolver.saveMetadata(
                    instance,
                    mergedMetadata
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

    private static void syncNeoForgeLibraries(
            Path instanceDirectory
    ) throws IOException {

        Path sourceLibraries =
                instanceDirectory.resolve("libraries");

        if (!Files.isDirectory(sourceLibraries)) {
            throw new IOException(
                    "NeoForge installer completed without creating a libraries directory."
            );
        }

        Path sharedLibraries =
                dev.itsdropper.vanta.launcher.MinecraftLocator
                        .getLibrariesDirectory();

        try (var stream = Files.walk(sourceLibraries)) {

            stream
                    .filter(Files::isRegularFile)
                    .forEach(source -> {

                        try {
                            Path relative =
                                    sourceLibraries.relativize(source);

                            Path target =
                                    sharedLibraries
                                            .resolve(relative)
                                            .normalize();

                            if (!target.startsWith(
                                    sharedLibraries.normalize()
                            )) {
                                throw new IOException(
                                        "Unsafe NeoForge library path: "
                                                + relative
                                );
                            }

                            Files.createDirectories(
                                    target.getParent()
                            );

                            /*
                             * NeoForge generated libraries are authoritative
                             * for this installation. Replace stale files,
                             * but never leave a zero-byte library behind.
                             */
                            Files.copy(
                                    source,
                                    target,
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                            );

                            if (!Files.isRegularFile(target)
                                    || Files.size(target) == 0) {
                                throw new IOException(
                                        "NeoForge library copy produced an invalid file: "
                                                + target
                                );
                            }

                        } catch (IOException e) {
                            throw new NeoForgeLibrarySyncException(e);
                        }
                    });
        } catch (NeoForgeLibrarySyncException e) {
            throw e.getCause();
        }
    }

    private static final class NeoForgeLibrarySyncException
            extends RuntimeException {

        private final IOException cause;

        private NeoForgeLibrarySyncException(
                IOException cause
        ) {
            this.cause = cause;
        }

        @Override
        public synchronized IOException getCause() {
            return cause;
        }
    }

    private static Path findNeoForgeVersionMetadata(
            Path instanceDirectory,
            String loaderVersion
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
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                        String parentName = path.getParent() == null || path.getParent().getFileName() == null
                                ? "" : path.getParent().getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                        try {
                            JsonNode metadata = new com.fasterxml.jackson.databind.ObjectMapper()
                                    .readTree(path.toFile());
                            String id = textValue(metadata, "id");
                            String profileIdentity = (fileName + " " + parentName + " "
                                    + (id == null ? "" : id)).toLowerCase(java.util.Locale.ROOT);
                            return profileIdentity.contains("neoforge")
                                    && profileIdentity.contains(loaderVersion.toLowerCase(java.util.Locale.ROOT));
                        } catch (IOException ignored) {
                            return false;
                        }
                    })
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
