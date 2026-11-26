package dev.itsdropper.vanta.launcher.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.Platform;
import dev.itsdropper.vanta.launcher.bundle.VantaBundleManifest;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** Installs the latest OS-specific Vanta Client artifact from its GitHub release. */
public final class GitHubReleaseModInstaller {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern CHECKSUM = Pattern.compile("^([0-9a-fA-F]{64})\\s+\\*?(.+)$");
    private static final long MAX_ARTIFACT_BYTES = 128L * 1024L * 1024L;
    private final HttpClient http;

    public GitHubReleaseModInstaller() {
        http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public InstalledArtifact installLatest(
            Instance instance,
            VantaBundleManifest.GitHubRelease source,
            String minecraftVersion
    ) throws Exception {
        if (instance == null || source == null || minecraftVersion == null || minecraftVersion.isBlank()) {
            throw new IllegalArgumentException("Instance, release source and Minecraft version are required.");
        }
        String platform = platformId();
        String api = "https://api.github.com/repos/" + source.repository() + "/releases/latest";
        JsonNode release = JSON.readTree(get(api, "application/vnd.github+json"));
        String tag = release.path("tag_name").asText("");
        if (tag.isBlank()) throw new IOException("The Vanta Client repository has no published release yet.");

        String suffix = "-" + minecraftVersion + "-" + platform + ".jar";
        String selectedName = null;
        String downloadUrl = null;
        String checksumUrl = null;
        for (JsonNode asset : release.path("assets")) {
            String name = asset.path("name").asText("");
            String url = asset.path("browser_download_url").asText("");
            if (name.startsWith(source.assetPrefix()) && name.endsWith(suffix) && url.startsWith("https://")) {
                selectedName = name;
                downloadUrl = url;
                break;
            }
        }
        if (selectedName == null) {
            throw new IOException("The latest Vanta Client release (" + tag
                    + ") has no build for Minecraft " + minecraftVersion + " on " + platform + ".");
        }
        for (JsonNode asset : release.path("assets")) {
            if (asset.path("name").asText("").equals(selectedName + ".sha256")) {
                checksumUrl = asset.path("browser_download_url").asText("");
                break;
            }
        }
        if (checksumUrl == null || !checksumUrl.startsWith("https://")) {
            throw new IOException("The latest Vanta Client release is missing its SHA-256 checksum.");
        }

        byte[] artifact = getBytes(downloadUrl, MAX_ARTIFACT_BYTES);
        String checksumText = new String(getBytes(checksumUrl, 4096), StandardCharsets.UTF_8).trim();
        Matcher matcher = CHECKSUM.matcher(checksumText);
        if (!matcher.matches() || !matcher.group(2).trim().equals(selectedName)) {
            throw new IOException("The Vanta Client release checksum file is malformed.");
        }
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(artifact));
        if (!actual.equalsIgnoreCase(matcher.group(1))) {
            throw new IOException("The downloaded Vanta Client artifact failed SHA-256 verification.");
        }

        Path mods = instance.getDirectory().resolve("mods");
        Files.createDirectories(mods);
        Path destination = mods.resolve(selectedName).normalize();
        if (!destination.getParent().equals(mods.normalize())) {
            throw new IOException("Invalid Vanta Client artifact filename.");
        }
        Path temporary = Files.createTempFile(mods, ".vanta-client-", ".part");
        try {
            Files.write(temporary, artifact);
            try (ZipFile jar = new ZipFile(temporary.toFile())) {
                if (jar.getEntry("fabric.mod.json") == null) {
                    throw new IOException("The downloaded Vanta Client file is not a Fabric mod.");
                }
                JsonNode metadata;
                try (var input = jar.getInputStream(jar.getEntry("fabric.mod.json"))) {
                    metadata = JSON.readTree(input);
                }
                if (!"dropperutils".equals(metadata.path("id").asText())
                        || !metadata.path("version").asText().equals(versionFromFilename(selectedName, source.assetPrefix(),
                                minecraftVersion, platform))) {
                    throw new IOException("The downloaded file is not the expected Vanta Client build.");
                }
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return new InstalledArtifact(tag, selectedName, destination);
    }

    private String get(String url, String accept) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30)).header("Accept", accept)
                .header("User-Agent", "VantaLauncher").GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub release lookup failed (HTTP " + response.statusCode() + ").");
        }
        return response.body();
    }

    private byte[] getBytes(String url, long maxBytes) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2)).header("User-Agent", "VantaLauncher").GET().build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub release download failed (HTTP " + response.statusCode() + ").");
        }
        if (response.body().length == 0 || response.body().length > maxBytes) {
            throw new IOException("GitHub release asset size is outside the allowed range.");
        }
        return response.body();
    }

    private static String versionFromFilename(String name, String prefix, String minecraftVersion, String platform) {
        String middle = name.substring(prefix.length(), name.length()
                - ("-" + minecraftVersion + "-" + platform + ".jar").length());
        return middle;
    }

    static String platformId() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String cpu = switch (arch) {
            case "amd64", "x86_64", "x86-64", "x64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            case "arm", "arm32", "armv7l" -> "aarch32";
            default -> throw new UnsupportedOperationException("Unsupported Vanta Client CPU architecture: " + arch);
        };
        String os = switch (Platform.operatingSystem()) {
            case WINDOWS -> "windows";
            case MACOS -> "macos";
            case LINUX -> "linux";
            case UNKNOWN -> throw new UnsupportedOperationException("Unsupported Vanta Client operating system.");
        };
        return os + "-" + cpu;
    }

    public record InstalledArtifact(String releaseTag, String fileName, Path path) {}
}
