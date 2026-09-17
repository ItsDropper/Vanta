package org.example.launcher.update;

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
import java.util.HexFormat;
import java.util.function.Consumer;

public class UpdateDownloader {

    private final HttpClient httpClient;

    public UpdateDownloader() {

        httpClient =
                HttpClient.newBuilder()
                        .followRedirects(
                                HttpClient.Redirect.NORMAL
                        )
                        .build();
    }

    public Path downloadAndVerify(
            UpdateInfo updateInfo,
            Consumer<String> progress,
            Consumer<Double> progressValue
    ) throws IOException, InterruptedException {

        Path updateDirectory =
                Path.of(
                        System.getProperty("java.io.tmpdir"),
                        "Vanta",
                        "updates"
                );

        Files.createDirectories(
                updateDirectory
        );

        String version =
                updateInfo
                        .getLatestVersion()
                        .replaceFirst(
                                "^[vV]",
                                ""
                        );

        Path temporaryZip =
                updateDirectory.resolve(
                        "Vanta-" +
                                version +
                                ".zip.download"
                );

        Path zip =
                updateDirectory.resolve(
                        "Vanta-" +
                                version +
                                ".zip"
                );

        Path checksumFile =
                updateDirectory.resolve(
                        "Vanta-" +
                                version +
                                ".zip.sha256"
                );

        try {

            progress.accept(
                    "Downloading Vanta "
                            + version
                            + "..."
            );

            if (progressValue != null) {
                progressValue.accept(0.0);
            }

            downloadFile(
                    updateInfo.getDownloadUrl(),
                    temporaryZip,
                    progress,
                    progressValue
            );

            progress.accept(
                    "Downloading update checksum..."
            );

            downloadFile(
                    updateInfo.getChecksumUrl(),
                    checksumFile,
                    null,
                    null
            );

            progress.accept(
                    "Verifying update..."
            );

            if (progressValue != null) {
                progressValue.accept(-1.0);
            }

            String expectedHash =
                    readExpectedHash(
                            checksumFile
                    );

            String actualHash =
                    calculateSha256(
                            temporaryZip
                    );

            if (!expectedHash.equalsIgnoreCase(
                    actualHash
            )) {

                Files.deleteIfExists(
                        temporaryZip
                );

                Files.deleteIfExists(
                        checksumFile
                );

                throw new IOException(
                        "The downloaded update failed integrity verification."
                );
            }

            progress.accept(
                    "Update verified successfully."
            );

            Files.move(
                    temporaryZip,
                    zip,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );

            Files.deleteIfExists(
                    checksumFile
            );

            progress.accept(
                    "Preparing Vanta "
                            + version
                            + "..."
            );

            if (progressValue != null) {
                progressValue.accept(-1.0);
            }

            return zip;

        } catch (IOException | InterruptedException e) {

            Files.deleteIfExists(
                    temporaryZip
            );

            Files.deleteIfExists(
                    checksumFile
            );

            throw e;
        }
    }

    private void downloadFile(
            String url,
            Path destination,
            Consumer<String> progress,
            Consumer<Double> progressValue
    ) throws IOException, InterruptedException {

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header(
                                "User-Agent",
                                "Vanta-Launcher"
                        )
                        .GET()
                        .build();

        HttpResponse<InputStream> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofInputStream()
                );

        if (response.statusCode() != 200) {

            response.body().close();

            throw new IOException(
                    "The update server returned HTTP "
                            + response.statusCode()
                            + "."
            );
        }

        long contentLength =
                response.headers()
                        .firstValueAsLong(
                                "Content-Length"
                        )
                        .orElse(-1);

        try (InputStream input =
                     response.body()) {

            Files.deleteIfExists(
                    destination
            );

            try (var output =
                         Files.newOutputStream(
                                 destination
                         )) {

                byte[] buffer =
                        new byte[8192];

                long downloaded = 0;
                int read;

                long lastUpdate = 0;

                while ((read =
                        input.read(buffer)) != -1) {

                    output.write(
                            buffer,
                            0,
                            read
                    );

                    downloaded += read;

                    if (progress != null
                            && (downloaded - lastUpdate >= 262_144
                            || downloaded == contentLength)) {

                        lastUpdate = downloaded;

                        if (contentLength > 0) {

                            int percentage =
                                    (int) (
                                            downloaded * 100
                                                    / contentLength
                                    );

                            progress.accept(
                                    "Downloading update... "
                                            + percentage
                                            + "%"
                            );

                            if (progressValue != null) {

                                progressValue.accept(
                                        percentage / 100.0
                                );
                            }

                        } else {

                            progress.accept(
                                    "Downloading update... "
                                            + formatBytes(
                                            downloaded
                                    )
                            );
                        }
                    }
                }

                if (progressValue != null
                        && contentLength > 0) {

                    progressValue.accept(1.0);
                }
            }
        }
    }

    private String readExpectedHash(
            Path checksumFile
    ) throws IOException {

        String contents =
                Files.readString(
                        checksumFile
                ).trim();

        if (contents.isBlank()) {

            throw new IOException(
                    "The SHA-256 checksum file was empty."
            );
        }

        String hash =
                contents
                        .split("\\s+")[0]
                        .trim();

        if (!hash.matches(
                "(?i)[0-9a-f]{64}"
        )) {

            throw new IOException(
                    "The update checksum was invalid."
            );
        }

        return hash;
    }

    private String calculateSha256(
            Path file
    ) throws IOException {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            try (InputStream input =
                         Files.newInputStream(
                                 file
                         )) {

                byte[] buffer =
                        new byte[8192];

                int read;

                while ((read =
                        input.read(buffer)) != -1) {

                    digest.update(
                            buffer,
                            0,
                            read
                    );
                }
            }

            return HexFormat.of()
                    .formatHex(
                            digest.digest()
                    );

        } catch (Exception e) {

            if (e instanceof IOException io) {
                throw io;
            }

            throw new IOException(
                    "Failed to verify the update.",
                    e
            );
        }
    }

    private String formatBytes(
            long bytes
    ) {

        if (bytes < 1024) {
            return bytes + " B";
        }

        if (bytes < 1024 * 1024) {
            return String.format(
                    "%.1f KB",
                    bytes / 1024.0
            );
        }

        return String.format(
                "%.1f MB",
                bytes / (1024.0 * 1024.0)
        );
    }
}

