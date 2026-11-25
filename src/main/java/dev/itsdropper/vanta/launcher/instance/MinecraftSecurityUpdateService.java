package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.itsdropper.vanta.launcher.ClasspathBuilder;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Refreshes Mojang-owned FreeType metadata immediately before launching modern
 * Minecraft instances. Mojang can publish security fixes by changing a
 * version JSON without changing the Minecraft version number; relying on
 * installation-time metadata would leave existing instances stale.
 */
public final class MinecraftSecurityUpdateService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String BASE_METADATA_FILE = "version.base.json";

    private MinecraftSecurityUpdateService() {
    }

    /**
     * Refresh the official FreeType dependency and install it before the
     * launch classpath is built. Loader-specific metadata and libraries are
     * retained; only Mojang's org.lwjgl:lwjgl-freetype artifacts are rebased.
     *
     * This is deliberately synchronous. A background refresh that completes
     * after process creation leaves the first launch exposed and only protects
     * a later launch.
     */
    public static void refreshBeforeLaunch(Instance instance) throws Exception {
        if (instance == null || instance.getDirectory() == null) {
            throw new IllegalArgumentException("A valid Minecraft instance is required.");
        }
        if (!requiresModernFontEngine(instance.getMinecraftVersion())) {
            return;
        }

        Path minecraftDirectory = instance.getDirectory().resolve("minecraft");
        Path versionFile = minecraftDirectory.resolve("version.json");
        if (!Files.isRegularFile(versionFile)) {
            throw new IOException("Minecraft version metadata is missing; repair this instance before launching.");
        }

        JsonNode official = MinecraftVersionResolver.downloadMetadata(instance.getMinecraftVersion());
        validateOfficialMetadata(official, instance.getMinecraftVersion());

        byte[] previousVersion = Files.readAllBytes(versionFile);
        Path baseFile = minecraftDirectory.resolve(BASE_METADATA_FILE);
        byte[] previousBase = Files.isRegularFile(baseFile) ? Files.readAllBytes(baseFile) : null;

        ObjectNode merged = readObject(versionFile);
        replaceFreetypeLibraries(merged, official.path("libraries"));

        ObjectNode base = previousBase == null ? null : readObject(baseFile);
        if (base != null) {
            replaceFreetypeLibraries(base, official.path("libraries"));
        }

        try {
            writeJsonAtomically(versionFile, merged);
            if (base != null) {
                writeJsonAtomically(baseFile, base);
            }
            ClasspathBuilder.clearCache();

            // Downloads are verified against the SHA-1 values in Mojang's
            // metadata. NativeExtractor publishes a clean staged directory,
            // removing stale native binaries from the previous LWJGL version.
            MinecraftFileInstaller.installLibraries(freetypeOnlyMetadata(merged));
            NativeInstaller.extract(instance, merged);

            JsonNode freetype = findLibrary(official.path("libraries"), "org.lwjgl:lwjgl-freetype");
            if (freetype != null) {
                System.out.println("[Vanta Security] Refreshed Mojang FreeType dependency for "
                        + instance.getMinecraftVersion() + ": "
                        + freetype.path("name").asText()
                        + " (SHA-1 " + freetype.path("downloads").path("artifact").path("sha1").asText("native-only") + ").");
            } else {
                System.out.println("[Vanta Security] Official metadata no longer declares lwjgl-freetype for "
                        + instance.getMinecraftVersion() + ".");
            }
        } catch (Exception failure) {
            restore(versionFile, previousVersion, failure);
            if (previousBase != null) {
                restore(baseFile, previousBase, failure);
            }
            ClasspathBuilder.clearCache();
            throw failure;
        }
    }

    static boolean requiresModernFontEngine(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Minecraft version is required for security validation.");
        }
        String normalized = version.toLowerCase(java.util.Locale.ROOT);
        // Pre-1.0 Alpha/Beta/Classic/Indev/Infdev identifiers are not
        // affected; they predate Minecraft's FreeType font pipeline.
        if (normalized.startsWith("a") || normalized.startsWith("b")
                || normalized.startsWith("c") || normalized.startsWith("rd-")
                || normalized.startsWith("inf-")) {
            return false;
        }
        // The FreeType pipeline first appeared in snapshot 24w03a.
        if (normalized.matches("\\d{2}w\\d{2}[a-z]")) {
            int year = Integer.parseInt(normalized.substring(0, 2));
            int week = Integer.parseInt(normalized.substring(3, 5));
            return year > 24 || (year == 24 && week >= 3);
        }
        String[] parts = version.split("[.-]");
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 && parts[2].matches("\\d+")
                    ? Integer.parseInt(parts[2]) : 0;
            return major > 1 || (major == 1 && (minor > 20 || (minor == 20 && patch >= 5)));
        } catch (NumberFormatException ignored) {
            // Unknown future version formats must not silently bypass the
            // refresh. Treat them as modern and require official metadata.
            return true;
        }
    }

    private static void validateOfficialMetadata(JsonNode metadata, String expectedVersion) {
        if (metadata == null || !metadata.isObject()
                || !expectedVersion.equals(metadata.path("id").asText())
                || !metadata.path("libraries").isArray()) {
            throw new IllegalStateException("Mojang returned incomplete or mismatched metadata for "
                    + expectedVersion + "; refusing to launch.");
        }
        boolean hasFreetype = false;
        for (JsonNode library : metadata.path("libraries")) {
            if (isFreetypeLibrary(library)) {
                hasFreetype = true;
                validateDownloadIntegrityMetadata(library);
                String[] coordinate = library.path("name").asText("").split(":");
                if (coordinate.length < 3 || !isPatchedFreeTypeVersion(coordinate[2])) {
                    throw new IllegalStateException("Mojang's current metadata for " + expectedVersion
                            + " contains an unpatched FreeType artifact (" + library.path("name").asText()
                            + "); refusing to launch with an unverified native font engine.");
                }
            }
        }
        if (!hasFreetype) {
            throw new IllegalStateException("Mojang metadata for " + expectedVersion
                    + " contains no official FreeType dependency; refusing to launch.");
        }
    }

    static void validateDownloadIntegrityMetadata(JsonNode library) {
        JsonNode downloads = library.path("downloads");
        boolean hasVerifiedArtifact = false;
        JsonNode artifact = downloads.path("artifact");
        if (artifact.isObject() && !artifact.isEmpty()) {
            validateArtifact(artifact, library.path("name").asText());
            hasVerifiedArtifact = true;
        }
        JsonNode classifiers = downloads.path("classifiers");
        if (classifiers.isObject() && !classifiers.isEmpty()) {
            var entries = classifiers.fields();
            while (entries.hasNext()) {
                var entry = entries.next();
                validateArtifact(entry.getValue(),
                        library.path("name").asText() + ":" + entry.getKey());
                hasVerifiedArtifact = true;
            }
        }
        if (!hasVerifiedArtifact) {
            throw new IllegalStateException("Mojang FreeType metadata contains no verifiable artifacts: "
                    + library.path("name").asText("<unnamed library>"));
        }
    }

    private static void validateArtifact(JsonNode artifact, String name) {
        if (!artifact.isObject()) {
            throw new IllegalStateException("Mojang FreeType artifact metadata is invalid: " + name);
        }
        String path = artifact.path("path").asText();
        String url = artifact.path("url").asText();
        String sha1 = artifact.path("sha1").asText();
        java.net.URI artifactUri;
        try {
            artifactUri = java.net.URI.create(url);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("Mojang FreeType artifact URL is invalid: " + name, invalid);
        }
        String normalizedPath = path.replace((char) 92, '/');
        boolean unsafePath = normalizedPath.startsWith("/")
                || normalizedPath.contains(":")
                || java.util.Arrays.stream(normalizedPath.split("/", -1))
                .anyMatch(segment -> segment.isBlank() || segment.equals(".") || segment.equals(".."));
        if (unsafePath || !"https".equalsIgnoreCase(artifactUri.getScheme())
                || artifactUri.getHost() == null || artifactUri.getUserInfo() != null
                || (artifactUri.getPort() != -1 && artifactUri.getPort() != 443)
                || !sha1.matches("(?i)[0-9a-f]{40}")) {
            throw new IllegalStateException("Mojang FreeType artifact is missing a safe relative path, trusted HTTPS URL, or SHA-1: " + name);
        }
    }

    private static ObjectNode freetypeOnlyMetadata(JsonNode metadata) {
        ObjectNode result = JSON.createObjectNode();
        ArrayNode libraries = JSON.createArrayNode();
        for (JsonNode library : metadata.path("libraries")) {
            if (isFreetypeLibrary(library)) {
                libraries.add(library.deepCopy());
            }
        }
        if (libraries.isEmpty()) {
            throw new IllegalStateException("Updated instance metadata lost its patched FreeType libraries.");
        }
        result.set("libraries", libraries);
        return result;
    }

    private static ObjectNode readObject(Path file) throws IOException {
        JsonNode node = JSON.readTree(file.toFile());
        if (node == null || !node.isObject()) {
            throw new IOException("Invalid Minecraft version metadata: " + file);
        }
        return (ObjectNode) node;
    }

    static void replaceFreetypeLibraries(ObjectNode metadata, JsonNode officialLibraries) {
        JsonNode existing = metadata.path("libraries");
        if (!existing.isArray()) {
            throw new IllegalStateException("Instance metadata has no library array.");
        }

        int insertionIndex = -1;
        List<JsonNode> retained = new ArrayList<>();
        for (JsonNode library : existing) {
            if (isFreetypeLibrary(library)) {
                if (insertionIndex < 0) {
                    insertionIndex = retained.size();
                }
            } else {
                retained.add(library.deepCopy());
            }
        }

        List<JsonNode> officialFreetype = new ArrayList<>();
        for (JsonNode library : officialLibraries) {
            if (isFreetypeLibrary(library)) {
                officialFreetype.add(library.deepCopy());
            }
        }
        if (officialFreetype.isEmpty()) {
            throw new IllegalStateException("Mojang metadata contains no FreeType libraries.");
        }

        if (insertionIndex < 0) {
            insertionIndex = retained.size();
        }
        retained.addAll(insertionIndex, officialFreetype);

        ArrayNode result = JSON.createArrayNode();
        retained.forEach(result::add);
        metadata.set("libraries", result);
    }

    private static boolean isFreetypeLibrary(JsonNode library) {
        String name = library == null ? "" : library.path("name").asText("");
        return name.startsWith("org.lwjgl:lwjgl-freetype:");
    }

    static boolean isPatchedFreeTypeVersion(String lwjglVersion) {
        if (lwjglVersion == null) {
            return false;
        }
        int marker = lwjglVersion.lastIndexOf("-mojang-");
        if (marker < 0) {
            return false;
        }
        String[] version = lwjglVersion.substring(marker + "-mojang-".length()).split("\\.");
        if (version.length != 3 || !version[0].matches("\\d+")
                || !version[1].matches("\\d+") || !version[2].matches("\\d+")) {
            return false;
        }
        try {
            int major = Integer.parseInt(version[0]);
            int minor = Integer.parseInt(version[1]);
            int patch = Integer.parseInt(version[2]);
            return major > 2 || (major == 2 && (minor > 15 || (minor == 15 && patch >= 0)));
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static JsonNode findLibrary(JsonNode libraries, String coordinatePrefix) {
        if (libraries == null || !libraries.isArray()) {
            return null;
        }
        for (JsonNode library : libraries) {
            if (library.path("name").asText("").startsWith(coordinatePrefix + ":")) {
                return library;
            }
        }
        return null;
    }

    private static void writeJsonAtomically(Path target, JsonNode metadata) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".security-update.tmp");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), metadata);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void restore(Path target, byte[] previous, Exception failure) {
        try {
            Path temporary = target.resolveSibling(target.getFileName() + ".security-rollback.tmp");
            Files.write(temporary, previous);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }
}
