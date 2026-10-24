package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.MinecraftLocator;
import dev.itsdropper.vanta.ui.LauncherSettings;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

    private static final HttpClient HTTP_HTTP1 =
            HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

    private static final Duration REQUEST_TIMEOUT =
            Duration.ofMinutes(5);

    private static int maxRetries() { return LauncherSettings.getDownloadRetries(); }

    private static <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        if (LauncherSettings.isHttp2Enabled()) return HTTP.send(request, handler);
        // HTTP/1.1 is the compatibility mode. Java's HttpClient manages
        // connection pooling internally; this setting controls protocol preference.
        return HTTP_HTTP1.send(request, handler);
    }

    // =============================================================
    // JSON
    // =============================================================

    public static JsonNode downloadJson(
            String url
    ) throws Exception {

        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries(); attempt++) {

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

                if (attempt < maxRetries()) {

                    sleepBeforeRetry(attempt);
                }
            }
        }

        throw new IOException(
                "Failed to download JSON after "
                        + maxRetries()
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

        if (target.getParent() != null) Files.createDirectories(target.getParent());
        if (LauncherSettings.isDownloadCacheEnabled() && expectedSha1 != null && !expectedSha1.isBlank()) {
            Path cached = cachePath(expectedSha1);
            if (Files.isRegularFile(cached) && expectedSha1.equalsIgnoreCase(calculateSha1(cached))) {
                Files.copy(cached, target, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("Reused verified download cache: " + target.getFileName());
                return;
            }
        }
        if (LauncherSettings.isSegmentedDownloadsEnabled()) {
            try { if (downloadSegmented(url, target, expectedSha1)) return; }
            catch (Exception ex) { System.err.println("Segmented transfer unavailable; falling back: " + ex.getMessage()); }
        }
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries(); attempt++) {

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
                                + maxRetries()
                                + "): "
                                + url
                );

                if (target.getParent() != null) {

                    Files.createDirectories(
                            target.getParent()
                    );
                }

                long existingBytes = LauncherSettings.isResumableDownloadsEnabled()
                        && Files.isRegularFile(temporary) ? Files.size(temporary) : 0L;
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url))
                        .timeout(REQUEST_TIMEOUT).GET();
                if (existingBytes > 0) requestBuilder.header("Range", "bytes=" + existingBytes + "-");
                HttpRequest request = requestBuilder.build();
                HttpResponse<InputStream> response = send(
                        request, HttpResponse.BodyHandlers.ofInputStream());

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

                boolean append = existingBytes > 0 && response.statusCode() == 206;
                if (existingBytes > 0 && response.statusCode() != 206) existingBytes = 0;
                try (InputStream input = response.body();
                     OutputStream output = Files.newOutputStream(temporary,
                             java.nio.file.StandardOpenOption.CREATE,
                             append ? java.nio.file.StandardOpenOption.APPEND
                                     : java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                    input.transferTo(output);
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

                if (LauncherSettings.isDownloadCacheEnabled() && expectedSha1 != null && !expectedSha1.isBlank()) {
                    Path cached = cachePath(expectedSha1);
                    Files.createDirectories(cached.getParent());
                    Files.copy(target, cached, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (Exception e) {
                lastException = e;
                if (!LauncherSettings.isResumableDownloadsEnabled()
                        || (e.getMessage() != null && e.getMessage().contains("SHA-1 verification failed"))) {
                    try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
                }

                if (attempt < maxRetries()) {

                    sleepBeforeRetry(attempt);
                }
            }
        }

        throw new IOException(
                "Failed to download file after "
                        + maxRetries()
                        + " attempts: "
                        + url,
                lastException
        );
    }

    private static Path cachePath(String sha1) {
        return MinecraftLocator.getVantaDirectory().resolve("download-cache").resolve("sha1")
                .resolve(sha1.toLowerCase(java.util.Locale.ROOT));
    }

    private static boolean downloadSegmented(String url, Path target, String expectedSha1) throws Exception {
        HttpRequest head = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<Void> metadata = send(head, HttpResponse.BodyHandlers.discarding());
        if (metadata.statusCode() < 200 || metadata.statusCode() >= 300) return false;
        long length = metadata.headers().firstValueAsLong("Content-Length").orElse(-1L);
        boolean ranges = metadata.headers().firstValue("Accept-Ranges")
                .map(v -> v.toLowerCase(java.util.Locale.ROOT).contains("bytes")).orElse(false);
        if (!ranges || length < 32L * 1024 * 1024) return false;
        final int segments = 4;
        Path staging = target.resolveSibling(target.getFileName() + ".segments");
        Files.createDirectories(staging);
        ExecutorService pool = Executors.newFixedThreadPool(segments);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < segments; i++) {
                final int index = i; final long begin = (length * i) / segments;
                final long finish = (length * (i + 1)) / segments - 1;
                futures.add(pool.submit(() -> {
                    Path part = staging.resolve("part-" + index);
                    HttpRequest range = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT)
                            .header("Range", "bytes=" + begin + "-" + finish).GET().build();
                    HttpResponse<InputStream> response = send(range, HttpResponse.BodyHandlers.ofInputStream());
                    if (response.statusCode() != 206) {
                        try { response.body().close(); } catch (IOException ignored) { }
                        throw new IOException("Server did not honor range request (HTTP " + response.statusCode() + ")");
                    }
                    try (InputStream in = response.body()) { Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING); }
                    if (Files.size(part) != finish - begin + 1) throw new IOException("Incomplete segment " + index);
                    return null;
                }));
            }
            for (Future<?> future : futures) future.get();
        } finally { pool.shutdownNow(); }
        Path assembled = target.resolveSibling(target.getFileName() + ".download");
        try {
            try (OutputStream out = Files.newOutputStream(assembled, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                for (int i = 0; i < segments; i++) {
                    try (InputStream in = Files.newInputStream(staging.resolve("part-" + i))) { in.transferTo(out); }
                }
            }
            if (Files.size(assembled) != length) throw new IOException("Segmented download size mismatch");
            if (expectedSha1 != null && !expectedSha1.isBlank() && !expectedSha1.equalsIgnoreCase(calculateSha1(assembled)))
                throw new IOException("Segmented download checksum mismatch");
            try { Files.move(assembled, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ex) { Files.move(assembled, target, StandardCopyOption.REPLACE_EXISTING); }
            if (LauncherSettings.isDownloadCacheEnabled() && expectedSha1 != null && !expectedSha1.isBlank()) {
                Path cached = cachePath(expectedSha1); Files.createDirectories(cached.getParent());
                Files.copy(target, cached, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } finally {
            Files.deleteIfExists(assembled);
            try (var walk = Files.walk(staging)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) { } });
            }
        }
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