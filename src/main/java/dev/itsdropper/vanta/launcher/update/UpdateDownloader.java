package dev.itsdropper.vanta.launcher.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

public class UpdateDownloader {
    private static final long MAX_ARCHIVE_BYTES = 1024L * 1024 * 1024;
    private static final long MAX_CHECKSUM_BYTES = 4 * 1024;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10);
    private static final Set<String> DOWNLOAD_HOSTS = Set.of(
            "github.com",
            "release-assets.githubusercontent.com",
            "objects.githubusercontent.com"
    );

    private final HttpClient httpClient;

    public UpdateDownloader() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Path downloadAndVerify(
            UpdateInfo updateInfo,
            Consumer<String> progress,
            Consumer<Double> progressValue
    ) throws IOException, InterruptedException {
        if (updateInfo == null) throw new IllegalArgumentException("Update information is required.");

        String version = normalizeVersion(updateInfo.getLatestVersion());
        URI archiveUri = validateInitialDownloadUrl(updateInfo.getDownloadUrl());
        URI checksumUri = validateInitialDownloadUrl(updateInfo.getChecksumUrl());

        Path updateDirectory = Path.of(System.getProperty("java.io.tmpdir"), "Vanta", "updates");
        Files.createDirectories(updateDirectory);

        Path temporaryZip = updateDirectory.resolve("Vanta-" + version + ".zip.download");
        Path zip = updateDirectory.resolve("Vanta-" + version + ".zip");
        Path checksumFile = updateDirectory.resolve("Vanta-" + version + ".zip.sha256");

        try {
            report(progress, "Downloading Vanta " + version + "...");
            if (progressValue != null) progressValue.accept(0.0);

            downloadFile(archiveUri, temporaryZip, MAX_ARCHIVE_BYTES, progress, progressValue);

            report(progress, "Downloading update checksum...");
            downloadFile(checksumUri, checksumFile, MAX_CHECKSUM_BYTES, null, null);

            report(progress, "Verifying update...");
            if (progressValue != null) progressValue.accept(-1.0);

            String expectedHash = readExpectedHash(checksumFile);
            String actualHash = calculateSha256(temporaryZip);
            if (!MessageDigest.isEqual(
                    expectedHash.toLowerCase(Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    actualHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                throw new IOException("The downloaded update failed integrity verification.");
            }

            report(progress, "Update verified successfully.");
            try {
                Files.move(temporaryZip, zip, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryZip, zip, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.deleteIfExists(checksumFile);

            report(progress, "Preparing Vanta " + version + "...");
            if (progressValue != null) progressValue.accept(-1.0);
            return zip;
        } catch (IOException | InterruptedException | RuntimeException failure) {
            deleteQuietly(temporaryZip);
            deleteQuietly(checksumFile);
            throw failure;
        }
    }

    static String normalizeVersion(String rawVersion) throws IOException {
        if (rawVersion == null) throw new IOException("The update version is missing.");
        String version = rawVersion.trim().replaceFirst("^[vV]", "");
        if (!version.matches("[0-9]+(?:\\.[0-9]+){1,2}")) {
            throw new IOException("The update version is invalid.");
        }
        return version;
    }

    static URI validateInitialDownloadUrl(String value) throws IOException {
        URI uri = parseHttpsUri(value);
        if (!"github.com".equalsIgnoreCase(uri.getHost())) {
            throw new IOException("Update downloads must originate from GitHub releases.");
        }
        return uri;
    }

    private static URI validateResponseUri(URI uri) throws IOException {
        URI checked = parseHttpsUri(uri == null ? null : uri.toString());
        if (!DOWNLOAD_HOSTS.contains(checked.getHost().toLowerCase(Locale.ROOT))) {
            throw new IOException("The update download redirected to an untrusted host.");
        }
        return checked;
    }

    private static URI parseHttpsUri(String value) throws IOException {
        final URI uri;
        try {
            uri = URI.create(value == null ? "" : value);
        } catch (IllegalArgumentException failure) {
            throw new IOException("The update download URL is invalid.", failure);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getFragment() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IOException("Update downloads require a valid HTTPS URL.");
        }
        return uri;
    }

    private void downloadFile(
            URI uri,
            Path destination,
            long maximumBytes,
            Consumer<String> progress,
            Consumer<Double> progressValue
    ) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "Vanta-Launcher")
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );

        try (InputStream input = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("The update server returned HTTP " + response.statusCode() + ".");
            }
            validateResponseUri(response.uri());

            long contentLength = response.headers()
                    .firstValueAsLong("Content-Length")
                    .orElse(-1);
            if (contentLength > maximumBytes) {
                throw new IOException("The update server returned a file larger than the allowed size.");
            }

            Files.deleteIfExists(destination);
            try (var output = Files.newOutputStream(destination)) {
                byte[] buffer = new byte[8192];
                long downloaded = 0;
                long lastUpdate = 0;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (downloaded > maximumBytes - read) {
                        throw new IOException("The update download exceeded the allowed file size.");
                    }
                    output.write(buffer, 0, read);
                    downloaded += read;

                    if (progress != null && (downloaded - lastUpdate >= 262_144
                            || (contentLength >= 0 && downloaded == contentLength))) {
                        lastUpdate = downloaded;
                        if (contentLength > 0) {
                            int percentage = (int) (downloaded * 100 / contentLength);
                            progress.accept("Downloading update... " + percentage + "%");
                            if (progressValue != null) progressValue.accept(percentage / 100.0);
                        } else {
                            progress.accept("Downloading update... " + formatBytes(downloaded));
                        }
                    }
                }
                if (contentLength >= 0 && downloaded != contentLength) {
                    throw new IOException("The update download ended before the advertised file size.");
                }
                if (progressValue != null && contentLength > 0) progressValue.accept(1.0);
            }
        } catch (IOException | RuntimeException failure) {
            deleteQuietly(destination);
            throw failure;
        }
    }

    private String readExpectedHash(Path checksumFile) throws IOException {
        if (Files.size(checksumFile) > MAX_CHECKSUM_BYTES) {
            throw new IOException("The update checksum file is too large.");
        }
        String contents = Files.readString(checksumFile).trim();
        if (contents.isBlank()) throw new IOException("The SHA-256 checksum file was empty.");
        String hash = contents.split("\\s+")[0].trim();
        if (!hash.matches("(?i)[0-9a-f]{64}")) {
            throw new IOException("The update checksum was invalid.");
        }
        return hash;
    }

    private String calculateSha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception failure) {
            if (failure instanceof IOException io) throw io;
            throw new IOException("Failed to verify the update.", failure);
        }
    }

    private static void report(Consumer<String> progress, String message) {
        if (progress != null) progress.accept(message);
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Preserve the original download or verification failure.
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
