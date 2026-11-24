package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.itsdropper.vanta.launcher.VersionReader;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Refreshes Mojang-owned LWJGL metadata immediately before launching modern
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
     * Refresh the official LWJGL dependency set and install it before the
     * launch classpath is built. Loader-specific metadata and libraries are
     * retained; only the Mojang-owned org.lwjgl group is rebased.
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
        replaceLwjglLibraries(merged, official.path("libraries"));

        ObjectNode base = previousBase == null ? null : readObject(baseFile);
        if (base != null) {
            replaceLwjglLibraries(base, official.path("libraries"));
        }

        try {
            writeJsonAtomically(versionFile, merged);
            if (base != null) {
                writeJsonAtomically(baseFile, base);
            }
            VersionReader.clearCache();

            // Downloads are verified against the SHA-1 values in Mojang's
            // metadata. NativeExtractor publishes a clean staged directory,
            // removing stale native binaries from the previous LWJGL version.
            MinecraftFileInstaller.installLibraries(merged);
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
            VersionReader.clearCache();
            throw failure;
        }
    }

    static boolean requiresModernFontEngine(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Minecraft version is required for security validation.");
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
        boolean hasLwjgl = false;
        for (JsonNode library : metadata.path("libraries")) {
            if (isLwjglLibrary(library)) {
                hasLwjgl = true;
                validateDownloadIntegrityMetadata(library);
            }
        }
        if (!hasLwjgl) {
            throw new IllegalStateException("Mojang metadata for " + expectedVersion
                    + " contains no official LWJGL dependencies; refusing to launch.");
        }

        JsonNode freetype = findLibrary(metadata.path("libraries"), "org.lwjgl:lwjgl-freetype");
        String coordinate = freetype == null ? "" : freetype.path("name").asText("");
        String[] parts = coordinate.split(":");
        if (parts.length < 3 || !isPatchedFreeTypeVersion(parts[2])) {
            throw new IllegalStateException("Mojang's current metadata for " + expectedVersion
                    + " does not contain the patched FreeType 2.15.0-or-newer dependency; "
                    + "refusing to launch with an unverified native font engine.");
        }
    }

    private static void validateDownloadIntegrityMetadata(JsonNode library) {
        JsonNode downloads = library.path("downloads");
        JsonNode artifact = downloads.path("artifact");
        if (artifact.isObject() && !artifact.isEmpty()) {
            validateArtifact(artifact, library.path("name").asText());
        }
        JsonNode classifiers = downloads.path("classifiers");
        if (classifiers.isObject()) {
            classifiers.fields().forEachRemaining(entry -> validateArtifact(
                    entry.getValue(), library.path("name").asText() + ":" + entry.getKey()));
        }
    }

    private static void validateArtifact(JsonNode artifact, String name) {
        if (!artifact.isObject()) {
            throw new IllegalStateException("Mojang LWJGL artifact metadata is invalid: " + name);
        }
        String path = artifact.path("path").asText();
        String url = artifact.path("url").asText();
        String sha1 = artifact.path("sha1").asText();
        if (path.isBlank() || url.isBlank() || !url.startsWith("https://")
                || !sha1.matches("(?i)[0-9a-f]{40}")) {
            throw new IllegalStateException("Mojang LWJGL artifact is missing a trusted URL, path, or SHA-1: " + name);
        }
    }

    private static ObjectNode readObject(Path file) throws IOException {
        JsonNode node = JSON.readTree(file.toFile());
        if (node == null || !node.isObject()) {
            throw new IOException("Invalid Minecraft version metadata: " + file);
        }
        return (ObjectNode) node;
    }

    static void replaceLwjglLibraries(ObjectNode metadata, JsonNode officialLibraries) {
        JsonNode existing = metadata.path("libraries");
        if (!existing.isArray()) {
            throw new IllegalStateException("Instance metadata has no library array.");
        }

        int insertionIndex = -1;
        List<JsonNode> retained = new ArrayList<>();
        for (JsonNode library : existing) {
            if (isLwjglLibrary(library)) {
                if (insertionIndex < 0) {
                    insertionIndex = retained.size();
                }
            } else {
                retained.add(library.deepCopy());
            }
        }

        List<JsonNode> officialLwjgl = new ArrayList<>();
        for (JsonNode library : officialLibraries) {
            if (isLwjglLibrary(library)) {
                officialLwjgl.add(library.deepCopy());
            }
        }
        if (officialLwjgl.isEmpty()) {
            throw new IllegalStateException("Mojang metadata contains no LWJGL libraries.");
        }

        if (insertionIndex < 0) {
            insertionIndex = retained.size();
        }
        retained.addAll(insertionIndex, officialLwjgl);

        ArrayNode result = JSON.createArrayNode();
        retained.forEach(result::add);
        metadata.set("libraries", result);
    }

    private static boolean isLwjglLibrary(JsonNode library) {
        String name = library == null ? "" : library.path("name").asText("");
        return name.startsWith("org.lwjgl:");
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
