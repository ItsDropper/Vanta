package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.itsdropper.vanta.launcher.loader.LoaderStackConfiguration;
import dev.itsdropper.vanta.launcher.loader.LoaderStackManager;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * Installs the official LiteLoader release metadata as a LaunchWrapper
 * profile overlay. Stable releases only: the upstream snapshot repositories
 * are no longer a reliable distribution source.
 */
public final class LiteLoaderInstaller {
    public static final String MANIFEST_URL = "https://dl.liteloader.com/versions/versions.json";
    public static final String TWEAKER = "com.mumfrey.liteloader.launch.LiteLoaderTweaker";
    public static final String LAUNCHWRAPPER_MAIN = "net.minecraft.launchwrapper.Launch";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private LiteLoaderInstaller() {
    }

    public static String findLatestLoaderVersion(String minecraftVersion) throws Exception {
        return latestRelease(versionMetadata(minecraftVersion)).path("version").asText();
    }

    public static JsonNode buildOverlay(String minecraftVersion, JsonNode versionMetadata) {
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            throw new IllegalArgumentException("Minecraft version is required.");
        }
        JsonNode artifact = latestRelease(versionMetadata);
        String version = requiredText(artifact, "version");
        String repository = secureRepository(versionMetadata.path("repo").path("url").asText());
        String file = requiredText(artifact, "file");
        validatePathSegment(version, "LiteLoader version");
        validatePathSegment(file, "LiteLoader artifact filename");

        ObjectNode overlay = MAPPER.createObjectNode();
        overlay.put("id", "liteloader-" + minecraftVersion + "-" + version);
        overlay.put("mainClass", LAUNCHWRAPPER_MAIN);

        ArrayNode libraries = overlay.putArray("libraries");
        ObjectNode loaderLibrary = libraries.addObject();
        loaderLibrary.put("name", "com.mumfrey:liteloader:" + version);
        loaderLibrary.put("url", repository);

        JsonNode dependencies = artifact.path("libraries");
        if (!dependencies.isArray()) {
            throw new IllegalStateException("LiteLoader release metadata has no dependency list.");
        }
        for (JsonNode dependency : dependencies) {
            if (!dependency.isObject() || dependency.path("name").asText().isBlank()) {
                throw new IllegalStateException("LiteLoader release contains an invalid library entry.");
            }
            libraries.add(dependency.deepCopy());
        }

        overlay.put("minecraftArguments", "");
        overlay.put("type", "release");
        overlay.put("liteLoaderArtifact", file);
        overlay.put("liteLoaderRepository", repository);
        overlay.put("liteLoaderMd5", artifact.path("md5").asText(""));
        return overlay;
    }

    public static void install(Instance instance) throws Exception {
        if (instance == null) {
            throw new IllegalArgumentException("A valid LiteLoader instance is required.");
        }
        install(instance, instance.getMinecraftVersion());
    }

    public static void install(Instance instance, String minecraftVersion) throws Exception {
        if (instance == null || instance.getDirectory() == null) {
            throw new IllegalArgumentException("A valid LiteLoader instance is required.");
        }
        JsonNode metadata = versionMetadata(minecraftVersion);
        JsonNode overlay = buildOverlay(minecraftVersion, metadata);

        String version = overlay.path("libraries").get(0).path("name").asText().substring(
                "com.mumfrey:liteloader:".length());
        String file = overlay.path("liteLoaderArtifact").asText();
        String repository = overlay.path("liteLoaderRepository").asText();
        String md5 = overlay.path("liteLoaderMd5").asText();

        Path libraryDirectory = MinecraftLocator.getLibrariesDirectoryPath()
                .resolve("com").resolve("mumfrey").resolve("liteloader").resolve(version);
        Path target = libraryDirectory.resolve("liteloader-" + version + ".jar")
                .toAbsolutePath().normalize();
        Path root = MinecraftLocator.getLibrariesDirectoryPath().toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            throw new IllegalStateException("LiteLoader artifact path escapes the libraries directory.");
        }
        Files.createDirectories(target.getParent());

        if (!Files.isRegularFile(target) || !matchesMd5(target, md5)) {
            Files.deleteIfExists(target);
            String url = repository + "com/mumfrey/liteloader/" + version + "/" + file;
            DownloadUtil.downloadFile(url, target);
            if (!Files.isRegularFile(target) || Files.size(target) == 0
                    || !matchesMd5(target, md5)) {
                Files.deleteIfExists(target);
                throw new IllegalStateException("Downloaded LiteLoader artifact failed integrity validation.");
            }
        }

        // Existing libraries are retained; this installs the profile's
        // LaunchWrapper/ASM dependencies using the launcher's normal resolver.
        FabricInstaller.installLibraries(overlay);

        Path profileDirectory = instance.getDirectory().resolve("loader-profiles");
        Files.createDirectories(profileDirectory);
        Path profileFile = profileDirectory.resolve("liteloader.json");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(profileFile.toFile(), overlay);

        LoaderStackConfiguration configuration = new LoaderStackConfiguration(List.of(
                new LoaderStackConfiguration.Layer(
                        "liteloader",
                        version,
                        LoaderStackConfiguration.LayerType.PROFILE_OVERLAY,
                        100,
                        List.of(TWEAKER),
                        "loader-profiles/liteloader.json"
                )
        ));
        LoaderStackManager.apply(instance, configuration);
    }

    private static JsonNode versionMetadata(String minecraftVersion) throws Exception {
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            throw new IllegalArgumentException("Minecraft version is required.");
        }
        JsonNode manifest = DownloadUtil.downloadJson(MANIFEST_URL);
        JsonNode version = manifest.path("versions").path(minecraftVersion);
        if (!version.isObject()) {
            throw new IllegalArgumentException(
                    "LiteLoader does not publish a stable release for Minecraft " + minecraftVersion + ".");
        }
        latestRelease(version);
        return version;
    }

    private static JsonNode latestRelease(JsonNode versionMetadata) {
        JsonNode artifact = versionMetadata.path("artefacts")
                .path("com.mumfrey:liteloader").path("latest");
        if (!artifact.isObject()) {
            throw new IllegalArgumentException("No stable LiteLoader release is published for this Minecraft version.");
        }
        requiredText(artifact, "version");
        requiredText(artifact, "file");
        if (!artifact.path("libraries").isArray()) {
            throw new IllegalStateException("LiteLoader release dependency metadata is invalid.");
        }
        return artifact;
    }

    private static String secureRepository(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("LiteLoader repository URL is missing.");
        }
        String normalized = value.trim().replaceFirst("^http://", "https://");
        java.net.URI uri = java.net.URI.create(normalized);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"dl.liteloader.com".equalsIgnoreCase(uri.getHost())
                || uri.getQuery() != null || uri.getFragment() != null
                || !uri.getPath().endsWith("/")) {
            throw new IllegalStateException("LiteLoader metadata contains an untrusted repository URL.");
        }
        return normalized;
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalStateException("LiteLoader metadata is missing " + field + ".");
        }
        return value.asText().trim();
    }

    private static void validatePathSegment(String value, String label) {
        if (value == null || value.isBlank() || value.equals(".") || value.equals("..")
                || value.contains("/") || value.contains("\\") || value.contains("?")) {
            throw new IllegalStateException(label + " is unsafe.");
        }
    }

    private static boolean matchesMd5(Path file, String expected) throws Exception {
        if (expected == null || !expected.matches("(?i)[0-9a-f]{32}")) {
            return false;
        }
        MessageDigest digest = MessageDigest.getInstance("MD5");
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expected);
    }
}
