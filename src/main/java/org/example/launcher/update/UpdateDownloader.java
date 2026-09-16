package org.example.launcher.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;

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
            UpdateInfo updateInfo
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

        downloadFile(
                updateInfo.getDownloadUrl(),
                temporaryZip
        );

        downloadFile(
                updateInfo.getChecksumUrl(),
                checksumFile
        );

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
                    "Vanta update failed SHA-256 verification."
            );
        }

        Files.move(
                temporaryZip,
                zip,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
        );

        Files.deleteIfExists(
                checksumFile
        );

        return zip;
    }

    private void downloadFile(
            String url,
            Path destination
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
                    "Download failed with HTTP "
                            + response.statusCode()
            );
        }

        try (InputStream input =
                     response.body()) {

            Files.copy(
                    input,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
            );
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
                    "SHA-256 checksum file was empty."
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
                    "Invalid SHA-256 checksum."
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
                         Files.newInputStream(file)) {

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
                    "Failed to calculate SHA-256.",
                    e
            );
        }
    }
}