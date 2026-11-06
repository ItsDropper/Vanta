package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.jar.JarFile;

/**
 * Installs the self-contained VantaLoader Java agent published by the
 * VantaLoader repository's immutable commit-pinned nightly release.
 */
public final class VantaLoaderInstaller {
    private static final String RELEASE_API =
            "https://api.github.com/repos/ItsDropper/VantaLoader/releases/tags/";
    private static final String RELEASES_API =
            "https://api.github.com/repos/ItsDropper/VantaLoader/releases?per_page=100";
    private static final java.util.regex.Pattern NIGHTLY_TAG = java.util.regex.Pattern.compile(
            "^nightly-\\d+\\.\\d+\\.\\d+-\\d{8}-[0-9a-f]{7}$");
    private static final java.util.regex.Pattern LEGACY_COMMIT_TAG = java.util.regex.Pattern.compile(
            "^nightly-[0-9a-f]{40}$");
    private static final String AGENT_ASSET = "VantaLoader-agent.jar";
    private static final String CHECKSUM_ASSET = AGENT_ASSET + ".sha256";
    private static final long MAX_AGENT_BYTES = 64L * 1024 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private VantaLoaderInstaller() { }

    public static String findLatestLoaderVersion() throws Exception {
        String tag = latestReleaseTag();
        return readRelease(tag).tag();
    }

    public static boolean supportsMinecraftVersion(String minecraftVersion) {
        if (minecraftVersion == null) return false;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^1\\.(\\d+)(?:\\.(\\d+))?(?:[-+].*)?$")
                .matcher(minecraftVersion.trim());
        if (!matcher.matches()) return false;
        try {
            int minor = Integer.parseInt(matcher.group(1));
            int patch = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
            return minor > 20 || (minor == 20 && patch >= 5);
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public static Path agentPath(Instance instance) {
        if (instance == null) throw new IllegalArgumentException("Instance cannot be null.");
        return agentPath(instance, instance.getLoaderVersion());
    }

    public static Path agentPath(Instance instance, String version) {
        if (instance == null) throw new IllegalArgumentException("Instance cannot be null.");
        if (version == null || !(version.equals("nightly")
                || LEGACY_COMMIT_TAG.matcher(version).matches()
                || NIGHTLY_TAG.matcher(version).matches())) {
            throw new IllegalStateException("VantaLoader instance has an invalid release identifier.");
        }
        return instance.getDirectory().resolve("libraries")
                .resolve("dev").resolve("itsdropper").resolve("vantaloader")
                .resolve(version).resolve(AGENT_ASSET);
    }

    public static Path install(Instance instance) throws Exception {
        if (instance == null) throw new IllegalArgumentException("Instance cannot be null.");
        if (!"VantaLoader".equalsIgnoreCase(instance.getLoader())) {
            throw new IllegalArgumentException("Instance is not configured for VantaLoader.");
        }

        String selectedVersion = instance.getLoaderVersion();
        if (selectedVersion == null || selectedVersion.isBlank()) {
            throw new IOException("VantaLoader instance is missing its pinned release version.");
        }
        // "nightly" is retained only as a migration path for instances created
        // by older launcher builds. New instances always pin an immutable tag.
        // Older launcher builds stored either the floating "nightly" marker
        // or a commit-SHA tag that never matched the actual published release
        // naming scheme. Migrate both to a real immutable nightly release tag.
        String resolvedVersion = ("nightly".equals(selectedVersion)
                || LEGACY_COMMIT_TAG.matcher(selectedVersion).matches())
                ? latestReleaseTag() : selectedVersion;
        Release release = readRelease(resolvedVersion);

        byte[] jarBytes = download(release.agentUri(), MAX_AGENT_BYTES);
        byte[] checksumBytes = download(release.checksumUri(), 4096);
        String expected = new String(checksumBytes, java.nio.charset.StandardCharsets.UTF_8)
                .trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!expected.matches("[0-9a-f]{64}")) {
            throw new IOException("VantaLoader release has an invalid SHA-256 checksum file.");
        }
        String actual = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(jarBytes));
        if (!MessageDigest.isEqual(
                expected.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
            throw new IOException("VantaLoader agent SHA-256 verification failed.");
        }

        Path destination = agentPath(instance, release.tag()).toAbsolutePath().normalize();
        Path instanceRoot = instance.getDirectory().toAbsolutePath().normalize();
        if (!destination.startsWith(instanceRoot)) {
            throw new IOException("Resolved VantaLoader agent path escapes the instance directory.");
        }
        Files.createDirectories(destination.getParent());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".download");
        try {
            Files.write(temporary, jarBytes);
            validateAgentJar(temporary);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return destination;
    }

    public static boolean isValidAgentJar(Path path) {
        if (path == null || !Files.isRegularFile(path)) return false;
        try {
            validateAgentJar(path);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void validateAgentJar(Path path) throws IOException {
        try (JarFile jar = new JarFile(path.toFile(), true)) {
            if (jar.getManifest() == null
                    || !"dev.itsdropper.vantaloader.bootstrap.VantaLoaderAgent".equals(
                            jar.getManifest().getMainAttributes().getValue("Premain-Class"))) {
                throw new IOException("VantaLoader agent JAR has no valid Premain-Class manifest entry.");
            }
            if (jar.getEntry("dev/itsdropper/vantaloader/bootstrap/VantaLoaderAgent.class") == null
                    || jar.getEntry("com/google/gson/JsonParser.class") == null
                    || jar.getEntry("org/objectweb/asm/ClassReader.class") == null
                    || jar.getEntry("org/objectweb/asm/tree/ClassNode.class") == null
                    || jar.getEntry("org/objectweb/asm/commons/AdviceAdapter.class") == null
                    || jar.getEntry("org/objectweb/asm/util/CheckClassAdapter.class") == null
                    || jar.getEntry("org/objectweb/asm/tree/analysis/Analyzer.class") == null) {
                throw new IOException("VantaLoader agent JAR is missing required runtime or ASM classes.");
            }
        }
    }

    private static String latestReleaseTag() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES_API))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "VantaLauncher")
                .GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("VantaLoader release lookup returned HTTP " + response.statusCode() + ".");
        }

        return selectLatestNightlyReleaseTag(MAPPER.readTree(response.body()));
    }

    static String selectLatestNightlyReleaseTag(JsonNode releases) throws IOException {
        String latestTag = null;
        String latestPublishedAt = "";
        if (releases == null || !releases.isArray()) {
            throw new IOException("VantaLoader release lookup returned an invalid response.");
        }
        for (JsonNode release : releases) {
            String tag = release.path("tag_name").asText("");
            String publishedAt = release.path("published_at").asText("");
            if (release.path("draft").asBoolean(true)
                    || !NIGHTLY_TAG.matcher(tag).matches()
                    || publishedAt.isBlank()
                    || !hasRequiredAssets(release.path("assets"))) {
                continue;
            }
            if (publishedAt.compareTo(latestPublishedAt) > 0) {
                latestPublishedAt = publishedAt;
                latestTag = tag;
            }
        }
        if (latestTag == null) {
            throw new IOException("No published VantaLoader nightly agent release is available.");
        }
        return latestTag;
    }

    private static boolean hasRequiredAssets(JsonNode assets) {
        if (!assets.isArray()) return false;
        boolean agent = false;
        boolean checksum = false;
        for (JsonNode asset : assets) {
            String name = asset.path("name").asText("");
            agent |= AGENT_ASSET.equals(name);
            checksum |= CHECKSUM_ASSET.equals(name);
        }
        return agent && checksum;
    }

    private static Release readRelease(String tag) throws Exception {
        if (tag == null || !NIGHTLY_TAG.matcher(tag).matches()) {
            throw new IOException("Invalid pinned VantaLoader release identifier.");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASE_API + tag))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "VantaLauncher")
                .GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404) {
            throw new IOException("VantaLoader nightly agent has not been published yet.");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("VantaLoader release service returned HTTP " + response.statusCode() + ".");
        }

        JsonNode root = MAPPER.readTree(response.body());
        if (root.path("draft").asBoolean(true)) {
            throw new IOException("The VantaLoader nightly release is not published.");
        }
        String publishedTag = root.path("tag_name").asText("");
        if (!tag.equals(publishedTag)) {
            throw new IOException("Unexpected VantaLoader release tag: " + publishedTag);
        }

        URI agent = null;
        URI checksum = null;
        JsonNode assets = root.path("assets");
        if (!assets.isArray()) throw new IOException("VantaLoader release has no asset list.");
        for (JsonNode asset : assets) {
            String name = asset.path("name").asText("");
            String url = asset.path("browser_download_url").asText("");
            long size = asset.path("size").asLong(-1);
            if (AGENT_ASSET.equals(name)) {
                if (size <= 0 || size > MAX_AGENT_BYTES) {
                    throw new IOException("VantaLoader agent asset has an invalid size.");
                }
                agent = validateAssetUri(url, tag);
            } else if (CHECKSUM_ASSET.equals(name)) {
                checksum = validateAssetUri(url, tag);
            }
        }
        if (agent == null || checksum == null) {
            throw new IOException("VantaLoader nightly release is missing its agent or checksum asset.");
        }
        return new Release(publishedTag, agent, checksum);
    }

    private static URI validateAssetUri(String value, String releaseTag) throws IOException {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"github.com".equalsIgnoreCase(uri.getHost())
                    || !uri.getPath().startsWith("/ItsDropper/VantaLoader/releases/download/"
                            + releaseTag + "/")) {
                throw new IOException("VantaLoader release contains an unexpected asset URL.");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IOException("VantaLoader release contains an invalid asset URL.", exception);
        }
    }

    private static byte[] download(URI uri, long maximumBytes) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "VantaLauncher")
                .GET().build();
        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("VantaLoader asset download returned HTTP " + response.statusCode() + ".");
        }
        byte[] bytes = response.body();
        if (bytes.length == 0 || bytes.length > maximumBytes) {
            throw new IOException("VantaLoader asset has an invalid downloaded size.");
        }
        return bytes;
    }

    private record Release(String tag, URI agentUri, URI checksumUri) { }
}
