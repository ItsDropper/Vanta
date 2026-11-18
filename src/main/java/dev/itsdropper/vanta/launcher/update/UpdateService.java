package dev.itsdropper.vanta.launcher.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.Platform;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class UpdateService {

    private static final String API_BASE =
            "https://api.github.com/repos/ItsDropper/Vanta/releases";
    private static final String STABLE_MANIFEST_URL =
            "https://github.com/ItsDropper/Vanta/releases/latest/download/manifest.json";
    private static final int MAX_METADATA_BYTES = 1024 * 1024;
    private static final Set<String> METADATA_HOSTS = Set.of(
            "api.github.com",
            "github.com",
            "release-assets.githubusercontent.com",
            "objects.githubusercontent.com"
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public UpdateService() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        objectMapper = new ObjectMapper();
    }

    public UpdateInfo checkForUpdate(String currentVersion) throws IOException, InterruptedException {
        try {
            JsonNode manifest = requestJson(STABLE_MANIFEST_URL);
            return ReleaseManifest.parse(manifest, currentVersion, Platform.distributionId());
        } catch (IOException missingManifest) {
            // Existing published releases predate manifests. Keep those installations
            // updateable until the first manifest-backed release is published.
            if (!missingManifest.getMessage().contains("HTTP 404")) {
                throw missingManifest;
            }
            return parseLatestRelease(requestJson(API_BASE + "/latest"), currentVersion);
        }
    }

    UpdateInfo parseLatestRelease(JsonNode release, String currentVersion) throws IOException {
        try {
            return parseRelease(release, currentVersion);
        } catch (IOException unavailablePackage) {
            if (release.path("tag_name").asText("").isBlank()) throw unavailablePackage;
            return new UpdateInfo(currentVersion, currentVersion, null, null,
                    "No compatible Vanta package is published for this operating system and architecture yet.");
        }
    }

    public UpdateInfo getVersion(String currentVersion, String targetVersion)
            throws IOException, InterruptedException {
        String normalized = targetVersion.trim().replaceFirst("^[vV]", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Vanta version cannot be blank.");
        }
        JsonNode release = requestJson(API_BASE + "/tags/v" + normalized);
        return parseRelease(release, currentVersion);
    }

    public List<UpdateInfo> getAvailableVersions(String currentVersion)
            throws IOException, InterruptedException {
        JsonNode releases = requestJson(API_BASE + "?per_page=100");
        if (!releases.isArray()) {
            throw new IOException("GitHub did not return a release list.");
        }
        List<UpdateInfo> versions = new ArrayList<>();
        for (JsonNode release : releases) {
            if (release.path("draft").asBoolean(false) || release.path("prerelease").asBoolean(false)) {
                continue;
            }
            try {
                UpdateInfo info = parseRelease(release, currentVersion);
                if (info.getLatestVersion() != null && !info.getLatestVersion().isBlank()
                        && info.isDowngrade()) {
                    versions.add(info);
                }
            } catch (IOException ignored) {
                // A release without a complete Vanta package is not rollback-capable.
            }
        }
        versions.sort((a, b) -> UpdateInfo.compareVersions(b.getLatestVersion(), a.getLatestVersion()));
        return versions;
    }

    private JsonNode requestJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Vanta-Launcher")
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("GitHub returned HTTP " + response.statusCode()
                        + " while retrieving Vanta release metadata.");
            }
            validateMetadataResponseUri(response.uri());
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (contentLength > MAX_METADATA_BYTES) {
                throw new IOException("The Vanta release metadata exceeds the allowed size.");
            }
            byte[] bytes = body.readNBytes(MAX_METADATA_BYTES + 1);
            if (bytes.length > MAX_METADATA_BYTES) {
                throw new IOException("The Vanta release metadata exceeds the allowed size.");
            }
            return objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private static void validateMetadataResponseUri(URI uri) throws IOException {
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || !METADATA_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                || uri.getUserInfo() != null
                || uri.getFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IOException("Vanta release metadata redirected to an untrusted host.");
        }
    }

    private UpdateInfo parseRelease(JsonNode release, String currentVersion) throws IOException {
        String latestVersion = release.path("tag_name").asText();
        String releaseNotes = release.path("body").asText("");
        if (latestVersion.isBlank()) {
            throw new IOException("GitHub release did not contain a version.");
        }
        String[] assets = findUpdateAssets(release);
        return new UpdateInfo(currentVersion, latestVersion, assets[0], assets[1], releaseNotes);
    }

    String[] findUpdateAssets(JsonNode release) throws IOException {
        JsonNode assets = release.path("assets");
        if (!assets.isArray()) {
            throw new IOException("GitHub release did not contain any assets.");
        }
        String downloadUrl = null;
        String checksumUrl = null;
        String version = release.path("tag_name").asText("").replaceFirst("^[vV]", "");
        String expectedArchive = "Vanta-" + version + "-" + Platform.distributionId() + ".zip";
        String expectedChecksum = expectedArchive + ".sha256";
        String legacyWindowsArchive = "Vanta-" + version + ".zip";
        boolean allowLegacyWindowsAsset = Platform.operatingSystem() == Platform.OperatingSystem.WINDOWS;

        for (JsonNode asset : assets) {
            String name = asset.path("name").asText();
            String assetUrl = asset.path("browser_download_url").asText();
            if (name.equalsIgnoreCase(expectedArchive)
                    || (allowLegacyWindowsAsset && name.equalsIgnoreCase(legacyWindowsArchive))) {
                downloadUrl = assetUrl;
            }
            if (name.equalsIgnoreCase(expectedChecksum)
                    || (allowLegacyWindowsAsset && name.equalsIgnoreCase(legacyWindowsArchive + ".sha256"))) {
                checksumUrl = assetUrl;
            }
        }
        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new IOException("No Vanta application ZIP was found in the selected GitHub release.");
        }
        if (checksumUrl == null || checksumUrl.isBlank()) {
            throw new IOException("No SHA-256 checksum was found for the selected Vanta release.");
        }
        return new String[]{downloadUrl, checksumUrl};
    }
}
