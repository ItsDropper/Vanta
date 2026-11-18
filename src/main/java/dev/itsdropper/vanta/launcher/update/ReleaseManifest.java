package dev.itsdropper.vanta.launcher.update;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Strict parser for the versioned Vanta release manifest.
 *
 * The manifest is delivered over GitHub HTTPS. Artifact SHA-256 values are
 * checked against the downloaded bytes before the updater stages an update.
 */
final class ReleaseManifest {
    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final Pattern VERSION = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+");
    private static final Pattern SHA256 = Pattern.compile("(?i)[0-9a-f]{64}");
    private static final long MAX_ARTIFACT_BYTES = 1024L * 1024 * 1024;

    private ReleaseManifest() {}

    static UpdateInfo parse(JsonNode manifest, String currentVersion, String platformId)
            throws IOException {
        if (manifest == null || !manifest.isObject()) {
            throw new IOException("The Vanta release manifest must be a JSON object.");
        }
        if (manifest.path("schema_version").asInt(-1) != SUPPORTED_SCHEMA_VERSION) {
            throw new IOException("The Vanta release manifest uses an unsupported schema version.");
        }
        if (!"vanta".equals(manifest.path("product").asText())) {
            throw new IOException("The release manifest belongs to a different product.");
        }
        if (!"stable".equals(manifest.path("channel").asText())) {
            throw new IOException("The stable release endpoint returned a non-stable manifest.");
        }

        String version = requiredText(manifest, "version");
        if (!VERSION.matcher(version).matches()) {
            throw new IOException("The release manifest contains an invalid version.");
        }
        String minimumVersion = requiredText(manifest, "min_launcher_version");
        if (!VERSION.matcher(minimumVersion).matches()) {
            throw new IOException("The release manifest contains an invalid minimum launcher version.");
        }
        try {
            if (UpdateInfo.compareVersions(currentVersion, minimumVersion) < 0) {
                throw new IOException("This update requires Vanta " + minimumVersion
                        + " or newer. Install the latest launcher manually first.");
            }
        } catch (IllegalArgumentException invalidCurrentVersion) {
            throw new IOException("The installed Vanta version cannot be compared with the release manifest.",
                    invalidCurrentVersion);
        }

        String publishedAt = requiredText(manifest, "published_at");
        try {
            Instant.parse(publishedAt);
        } catch (RuntimeException invalidTimestamp) {
            throw new IOException("The release manifest contains an invalid publication timestamp.",
                    invalidTimestamp);
        }

        String releaseNotesUrl = requiredText(manifest, "release_notes_url");
        validateGitHubUrl(releaseNotesUrl, "release notes");

        JsonNode artifact = manifest.path("artifacts").path(platformId);
        if (!artifact.isObject()) {
            return new UpdateInfo(currentVersion, currentVersion, null, null,
                    "No compatible Vanta package is published for " + platformId + ".");
        }

        String fileName = requiredText(artifact, "file_name");
        String expectedFileName = "Vanta-" + version + "-" + platformId + ".zip";
        if (!expectedFileName.equals(fileName)) {
            throw new IOException("The release manifest contains an unexpected artifact filename.");
        }
        String downloadUrl = requiredText(artifact, "download_url");
        URI downloadUri = validateGitHubUrl(downloadUrl, "artifact");
        String expectedPath = "/ItsDropper/Vanta/releases/download/v" + version + "/" + fileName;
        if (!expectedPath.equals(downloadUri.getPath())) {
            throw new IOException("The release artifact URL does not match its declared version and filename.");
        }

        String sha256 = requiredText(artifact, "sha256").toLowerCase(Locale.ROOT);
        if (!SHA256.matcher(sha256).matches()) {
            throw new IOException("The release manifest contains an invalid SHA-256 digest.");
        }
        long size = artifact.path("size_bytes").asLong(-1);
        if (size <= 0 || size > MAX_ARTIFACT_BYTES) {
            throw new IOException("The release manifest contains an invalid artifact size.");
        }

        return new UpdateInfo(currentVersion, version, downloadUrl, null, releaseNotesUrl,
                sha256, size, minimumVersion);
    }

    private static String requiredText(JsonNode node, String field) throws IOException {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IOException("The release manifest is missing '" + field + "'.");
        }
        return value.asText().trim();
    }

    private static URI validateGitHubUrl(String value, String description) throws IOException {
        final URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException invalidUri) {
            throw new IOException("The release manifest contains an invalid " + description + " URL.",
                    invalidUri);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"github.com".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null
                || uri.getFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IOException("The release manifest " + description + " URL is not a trusted GitHub HTTPS URL.");
        }
        return uri;
    }
}
