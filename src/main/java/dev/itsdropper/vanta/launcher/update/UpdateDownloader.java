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
import java.util.UUID;
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
        URI checksumUri = updateInfo.getSha256() == null
                ? validateInitialDownloadUrl(updateInfo.getChecksumUrl()) : null;

        Path updateDirectory = Path.of(System.getProperty("java.io.tmpdir"), "Vanta", "updates");
        Files.createDirectories(updateDirectory);

        String downloadId = UUID.randomUUID().toString();
        Path temporaryZip = updateDirectory.resolve("Vanta-" + version + "-" + downloadId + ".zip.download");
        Path zip = updateDirectory.resolve("Vanta-" + version + ".zip");
        Path checksumFile = updateDirectory.resolve("Vanta-" + version + "-" + downloadId + ".zip.sha256");

        try {
            report(progress, "Downloading Vanta " + version + "...");
            if (progressValue != null) progressValue.accept(0.0);

            if (checksumUri != null) {
                report(progress, "Downloading update checksum...");
                downloadFile(checksumUri, checksumFile, MAX_CHECKSUM_BYTES, null, null);
            }

            String expectedHash = updateInfo.getSha256() == null
                    ? readExpectedHash(checksumFile) : updateInfo.getSha256();
            boolean verified = false;
            for (int verificationAttempt = 1; verificationAttempt <= 3; verificationAttempt++) {
                downloadFile(archiveUri, temporaryZip, MAX_ARCHIVE_BYTES, progress, progressValue);

                report(progress, "Verifying update...");
                if (progressValue != null) progressValue.accept(-1.0);

                boolean sizeMatches = updateInfo.getSizeBytes() <= 0
                        || Files.size(temporaryZip) == updateInfo.getSizeBytes();
                String actualHash = calculateSha256(temporaryZip);
                boolean hashMatches = MessageDigest.isEqual(
                        expectedHash.toLowerCase(Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                        actualHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                if (sizeMatches && hashMatches) {
                    verified = true;
                    break;
                }

                deleteQuietly(temporaryZip);
                if (verificationAttempt == 3) {
                    throw new IOException(!sizeMatches
                            ? "The downloaded update size does not match the release manifest after 3 attempts."
                            : "The downloaded update failed SHA-256 verification after 3 attempts.");
                }
                report(progress, "Downloaded data did not verify. Retrying ("
                        + (verificationAttempt + 1) + "/3)...");
                java.util.concurrent.TimeUnit.MILLISECONDS.sleep(
                        UpdateHttpRetry.retryDelayMillis(verificationAttempt));
            }
            if (!verified) throw new IOException("The update could not be verified.");

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
        if (!version.matches("(?:0|[1-9][0-9]*)(?:\\.(?:0|[1-9][0-9]*)){1,2}")) {
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
        RetryableTransferException lastFailure = null;
        for (int attempt = 1; attempt <= 4; attempt++) {
            try {
                if (progressValue != null) progressValue.accept(0.0);
                downloadFileOnce(uri, destination, maximumBytes, progress, progressValue);
                return;
            } catch (RetryableTransferException interruptedTransfer) {
                lastFailure = interruptedTransfer;
                deleteQuietly(destination);
                if (attempt == 4) throw interruptedTransfer;
                report(progress, "Connection interrupted. Retrying update download ("
                        + (attempt + 1) + "/4)...");
                java.util.concurrent.TimeUnit.MILLISECONDS.sleep(
                        UpdateHttpRetry.retryDelayMillis(attempt));
            }
        }
        throw lastFailure == null
                ? new IOException("The update download could not be completed.")
                : lastFailure;
    }

    private void downloadFileOnce(
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

        HttpResponse<InputStream> response = UpdateHttpRetry.send(httpClient, request);

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
                while (true) {
                    try {
                        read = input.read(buffer);
                    } catch (IOException networkFailure) {
                        throw new RetryableTransferException(
                                "The update connection ended during transfer.", networkFailure);
                    }
                    if (read == -1) break;
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
                    throw new RetryableTransferException(
                            "The update download ended before the advertised file size.", null);
                }
                if (progressValue != null && contentLength > 0) progressValue.accept(1.0);
            }
        } catch (IOException | RuntimeException failure) {
            deleteQuietly(destination);
            throw failure;
        }
    }

    private static final class RetryableTransferException extends IOException {
        private RetryableTransferException(String message, Throwable cause) {
            super(message, cause);
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
