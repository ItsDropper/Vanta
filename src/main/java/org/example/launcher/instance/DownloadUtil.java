package org.example.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

public class DownloadUtil {

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    private static final HttpClient HTTP =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

    private static final Duration REQUEST_TIMEOUT =
            Duration.ofMinutes(5);

    private static final int MAX_RETRIES = 3;

    // =============================================================
    // JSON
    // =============================================================

    public static JsonNode downloadJson(
            String url
    ) throws Exception {

        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {

            try {

                HttpRequest request =
                        HttpRequest.newBuilder(
                                        URI.create(url)
                                )
                                .timeout(REQUEST_TIMEOUT)
                                .GET()
                                .build();

                HttpResponse<String> response =
                        HTTP.send(
                                request,
                                HttpResponse.BodyHandlers.ofString()
                        );

                if (response.statusCode() < 200
                        || response.statusCode() >= 300) {

                    throw new IOException(
                            "HTTP "
                                    + response.statusCode()
                                    + " while downloading "
                                    + url
                    );
                }

                return MAPPER.readTree(
                        response.body()
                );

            } catch (Exception e) {

                lastException = e;

                if (attempt < MAX_RETRIES) {

                    sleepBeforeRetry(attempt);
                }
            }
        }

        throw new IOException(
                "Failed to download JSON after "
                        + MAX_RETRIES
                        + " attempts: "
                        + url,
                lastException
        );
    }

    // =============================================================
    // FILE
    // =============================================================

    public static void downloadFile(
            String url,
            Path target
    ) throws Exception {

        downloadFile(
                url,
                target,
                null
        );
    }

    // =============================================================
    // FILE WITH SHA-1 VERIFICATION
    // =============================================================

    public static void downloadFile(
            String url,
            Path target,
            String expectedSha1
    ) throws Exception {

        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {

            Path temporary =
                    target.resolveSibling(
                            target.getFileName()
                                    + ".download"
                    );

            try {

                System.out.println(
                        "Downloading (attempt "
                                + attempt
                                + "/"
                                + MAX_RETRIES
                                + "): "
                                + url
                );

                if (target.getParent() != null) {

                    Files.createDirectories(
                            target.getParent()
                    );
                }

                HttpRequest request =
                        HttpRequest.newBuilder(
                                        URI.create(url)
                                )
                                .timeout(REQUEST_TIMEOUT)
                                .GET()
                                .build();

                HttpResponse<InputStream> response =
                        HTTP.send(
                                request,
                                HttpResponse.BodyHandlers.ofInputStream()
                        );

                if (response.statusCode() < 200
                        || response.statusCode() >= 300) {

                    try {
                        response.body().close();
                    } catch (IOException ignored) {
                    }

                    throw new IOException(
                            "HTTP "
                                    + response.statusCode()
                                    + " while downloading "
                                    + url
                    );
                }

                try (InputStream input =
                             response.body()) {

                    Files.copy(
                            input,
                            temporary,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }

                // -------------------------------------------------
                // VERIFY SHA-1
                // -------------------------------------------------

                if (expectedSha1 != null
                        && !expectedSha1.isBlank()) {

                    String actualSha1 =
                            calculateSha1(
                                    temporary
                            );

                    if (!actualSha1.equalsIgnoreCase(
                            expectedSha1
                    )) {

                        throw new IOException(
                                "SHA-1 verification failed for "
                                        + target.getFileName()
                                        + ". Expected "
                                        + expectedSha1
                                        + " but got "
                                        + actualSha1
                        );
                    }
                }

                // -------------------------------------------------
                // ATOMIC REPLACEMENT
                // -------------------------------------------------

                try {

                    Files.move(
                            temporary,
                            target,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING
                    );

                } catch (java.nio.file.AtomicMoveNotSupportedException e) {

                    Files.move(
                            temporary,
                            target,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }

                return;

            } catch (Exception e) {

                lastException = e;

                try {
                    Files.deleteIfExists(
                            temporary
                    );
                } catch (IOException ignored) {
                }

                if (attempt < MAX_RETRIES) {

                    sleepBeforeRetry(attempt);
                }
            }
        }

        throw new IOException(
                "Failed to download file after "
                        + MAX_RETRIES
                        + " attempts: "
                        + url,
                lastException
        );
    }

    // =============================================================
    // SHA-1
    // =============================================================

    private static String calculateSha1(
            Path file
    ) throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-1"
                );

        try (InputStream input =
                     Files.newInputStream(file)) {

            byte[] buffer =
                    new byte[8192];

            int read;

            while ((read = input.read(buffer)) != -1) {

                digest.update(
                        buffer,
                        0,
                        read
                );
            }
        }

        byte[] hash =
                digest.digest();

        StringBuilder result =
                new StringBuilder(
                        hash.length * 2
                );

        for (byte value : hash) {

            result.append(
                    String.format(
                            "%02x",
                            value & 0xff
                    )
            );
        }

        return result.toString();
    }

    // =============================================================
    // RETRY
    // =============================================================

    private static void sleepBeforeRetry(
            int attempt
    ) {

        try {

            long delay =
                    1000L * attempt;

            Thread.sleep(delay);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new RuntimeException(
                    "Download retry interrupted.",
                    e
            );
        }
    }
}