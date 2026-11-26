package dev.itsdropper.vanta.launcher.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.json.VantaJson;
import dev.itsdropper.vanta.launcher.Platform;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class UpdateService {
    private static final String API_BASE =
            "https://api.github.com/repos/ItsDropper/Vanta/releases";
    private static final String RELEASE_DOWNLOAD_BASE =
            "https://github.com/ItsDropper/Vanta/releases/download/";
    private static final String STABLE_MANIFEST_URL =
            "https://github.com/ItsDropper/Vanta/releases/latest/download/manifest.json";
    private static final String STABLE_MANIFEST_SIGNATURE_URL =
            "https://github.com/ItsDropper/Vanta/releases/latest/download/manifest.sig";
    private static final int MAX_METADATA_BYTES = 1024 * 1024;
    private static final int MAX_SIGNATURE_BYTES = 16 * 1024;
    private static final int MAX_ROLLBACK_CANDIDATES = 10;
    private static final Pattern RELEASE_VERSION =
            Pattern.compile("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)");
    private static final Set<String> METADATA_HOSTS = Set.of(
            "api.github.com", "github.com",
            "release-assets.githubusercontent.com", "objects.githubusercontent.com");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public UpdateService() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        objectMapper = createMetadataMapper();
    }

    static ObjectMapper createMetadataMapper() {
        return VantaJson.strictMapper();
    }

    public UpdateInfo checkForUpdate(String currentVersion) throws IOException, InterruptedException {
        return fetchSignedManifest(currentVersion, STABLE_MANIFEST_URL,
                STABLE_MANIFEST_SIGNATURE_URL, null);
    }

    /** Rollback packages use the same signed-manifest trust path as stable updates. */
    public UpdateInfo getVersion(String currentVersion, String targetVersion)
            throws IOException, InterruptedException {
        String normalized = normalizeTargetVersion(targetVersion);
        return fetchSignedManifest(currentVersion, releaseManifestUrl(normalized),
                releaseSignatureUrl(normalized), normalized);
    }

    static String releaseManifestUrl(String version) {
        return RELEASE_DOWNLOAD_BASE + "v" + normalizeTargetVersion(version) + "/manifest.json";
    }

    static String releaseSignatureUrl(String version) {
        return RELEASE_DOWNLOAD_BASE + "v" + normalizeTargetVersion(version) + "/manifest.sig";
    }

    public List<UpdateInfo> getAvailableVersions(String currentVersion)
            throws IOException, InterruptedException {
        JsonNode releases = requestJson(API_BASE + "?per_page=" + MAX_ROLLBACK_CANDIDATES);
        if (!releases.isArray()) {
            throw new IOException("GitHub did not return a release list.");
        }
        List<UpdateInfo> versions = new ArrayList<>();
        for (JsonNode release : releases) {
            if (release.path("draft").asBoolean(false) || release.path("prerelease").asBoolean(false)) {
                continue;
            }
            final String version;
            try {
                version = normalizeTargetVersion(release.path("tag_name").asText(""));
            } catch (IllegalArgumentException invalidTag) {
                continue;
            }
            if (UpdateInfo.compareVersions(version, currentVersion) >= 0) {
                continue;
            }
            try {
                UpdateInfo info = getVersion(currentVersion, version);
                if (info.isDowngrade()) {
                    versions.add(info);
                }
            } catch (HttpStatusException missingHistoricalManifest) {
                if (missingHistoricalManifest.statusCode() != 404) {
                    throw missingHistoricalManifest;
                }
                // Historical releases without signed manifests are not trusted rollback targets.
            }
        }
        versions.sort((a, b) -> UpdateInfo.compareVersions(b.getLatestVersion(), a.getLatestVersion()));
        return List.copyOf(versions);
    }

    private UpdateInfo fetchSignedManifest(String currentVersion, String manifestUrl,
                                           String signatureUrl, String expectedVersion)
            throws IOException, InterruptedException {
        byte[] manifestBytes = requestBytes(manifestUrl, MAX_METADATA_BYTES);
        byte[] signatureBytes = requestBytes(signatureUrl, MAX_SIGNATURE_BYTES);

        // Authenticate the original response bytes before parsing any manifest field.
        ReleaseManifestSignatures.verifyPinned(manifestBytes, signatureBytes);
        JsonNode manifest = objectMapper.readTree(manifestBytes);
        UpdateInfo update = ReleaseManifest.parse(manifest, currentVersion, Platform.distributionId());
        if (expectedVersion != null && !expectedVersion.equals(update.getLatestVersion())) {
            throw new IOException("The signed release manifest does not match the requested rollback version.");
        }
        return update;
    }

    private JsonNode requestJson(String url) throws IOException, InterruptedException {
        return objectMapper.readTree(requestBytes(url, MAX_METADATA_BYTES));
    }

    private byte[] requestBytes(String url, int maximumBytes) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json, application/octet-stream")
                .header("User-Agent", "Vanta-Launcher")
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            validateMetadataResponseUri(response.uri());
            if (response.statusCode() != 200) {
                throw new HttpStatusException(response.statusCode());
            }
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (contentLength > maximumBytes) {
                throw new IOException("Vanta release metadata exceeds the allowed size.");
            }
            byte[] bytes = body.readNBytes(maximumBytes + 1);
            if (bytes.length == 0) {
                throw new IOException("Vanta release metadata response was empty.");
            }
            if (bytes.length > maximumBytes) {
                throw new IOException("Vanta release metadata exceeds the allowed size.");
            }
            return bytes;
        }
    }

    private static void validateMetadataResponseUri(URI uri) throws IOException {
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || !METADATA_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                || uri.getUserInfo() != null || uri.getFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IOException("Vanta release metadata redirected to an untrusted host.");
        }
    }

    static String normalizeTargetVersion(String targetVersion) {
        if (targetVersion == null) {
            throw new IllegalArgumentException("Vanta version cannot be null.");
        }
        String normalized = targetVersion.trim().replaceFirst("^[vV]", "");
        if (!RELEASE_VERSION.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid Vanta version: " + targetVersion);
        }
        return normalized;
    }

    static final class HttpStatusException extends IOException {
        private final int statusCode;

        HttpStatusException(int statusCode) {
            super("GitHub returned HTTP " + statusCode + " while retrieving Vanta release metadata.");
            this.statusCode = statusCode;
        }

        int statusCode() { return statusCode; }
    }
}
