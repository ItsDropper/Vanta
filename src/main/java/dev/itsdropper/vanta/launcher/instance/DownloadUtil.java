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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;

public class DownloadUtil {

    private static final class DownloadHttpException extends IOException {
        private final int statusCode;
        private final String retryAfter;

        private DownloadHttpException(int statusCode, String url, String retryAfter) {
            super("HTTP " + statusCode + " while downloading " + url);
            this.statusCode = statusCode;
            this.retryAfter = retryAfter;
        }
    }

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

    private static final Map<Path, DownloadLock> DOWNLOAD_LOCKS = new HashMap<>();

    private static final class DownloadLock {
        private final ReentrantLock lock = new ReentrantLock();
        private int users;
    }

    private static Path processLockPath(Path target) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(target.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder key = new StringBuilder(digest.length * 2);
        for (byte value : digest) key.append(String.format("%02x", value & 0xff));
        return MinecraftLocator.getVantaDirectory()
                .resolve("download-locks").resolve(key + ".lock");
    }

    private static DownloadLock acquireDownloadLock(Path target) {
        DownloadLock entry;
        synchronized (DOWNLOAD_LOCKS) {
            entry = DOWNLOAD_LOCKS.computeIfAbsent(target, ignored -> new DownloadLock());
            entry.users++;
        }
        entry.lock.lock();
        return entry;
    }

    private static void releaseDownloadLock(Path target, DownloadLock entry) {
        entry.lock.unlock();
        synchronized (DOWNLOAD_LOCKS) {
            entry.users--;
            if (entry.users == 0) {
                DOWNLOAD_LOCKS.remove(target, entry);
            }
        }
    }

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

        validateDownloadRequest(url, null);
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
                        send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {

                    throw new DownloadHttpException(response.statusCode(), url,
                            response.headers().firstValue("Retry-After").orElse(null));
                }

                return MAPPER.readTree(
                        response.body()
                );

            } catch (Exception e) {
                lastException = e;
                if (!isRetryableDownloadFailure(e)) break;
                if (attempt < maxRetries()) sleepBeforeRetry(attempt, e);
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
        validateDownloadRequest(url, expectedSha1);
        if (target == null) {
            throw new IllegalArgumentException("Download target is required.");
        }
        Path lockKey = target.toAbsolutePath().normalize();
        DownloadLock downloadLock = acquireDownloadLock(lockKey);
        try {
            Path processLockPath = processLockPath(lockKey);
            Files.createDirectories(processLockPath.getParent());
            try (FileChannel channel = FileChannel.open(processLockPath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                long taskId = dev.itsdropper.vanta.launcher.download.DownloadCenterService
                        .getInstance().begin(url, target, expectedSha1);
                try {
                    downloadFileInternal(url, target, expectedSha1, taskId);
                    long size = Files.isRegularFile(target) ? Files.size(target) : 0L;
                    dev.itsdropper.vanta.launcher.download.DownloadCenterService
                            .getInstance().complete(taskId, size);
                } catch (Exception failure) {
                    dev.itsdropper.vanta.launcher.download.DownloadCenterService
                            .getInstance().fail(taskId, failure);
                    throw failure;
                }
            }
        } finally {
            releaseDownloadLock(lockKey, downloadLock);
        }
    }

    private static void downloadFileInternal(
            String url,
            Path target,
            String expectedSha1,
            long taskId
    ) throws Exception {

        if (target.getParent() != null) Files.createDirectories(target.getParent());
        if (LauncherSettings.isDownloadCacheEnabled() && expectedSha1 != null && !expectedSha1.isBlank()) {
            Path cached = cachePath(expectedSha1);
            if (Files.isRegularFile(cached) && expectedSha1.equalsIgnoreCase(calculateSha1(cached))) {
                publishVerifiedFile(cached, target);
                System.out.println("Reused verified download cache: " + target.getFileName());
                return;
            }
        }
        if (LauncherSettings.isSegmentedDownloadsEnabled()) {
            try { if (downloadSegmented(url, target, expectedSha1, taskId)) return; }
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

                if (response.statusCode() != 200 && response.statusCode() != 206) {

                    try {
                        response.body().close();
                    } catch (IOException ignored) {
                    }

                    throw new DownloadHttpException(response.statusCode(), url,
                            response.headers().firstValue("Retry-After").orElse(null));
                }

                boolean append = existingBytes > 0 && response.statusCode() == 206;
                String contentRange = response.headers().firstValue("Content-Range").orElse(null);
                long expectedFinalSize = -1L;
                long expectedResponseBytes = -1L;
                if (append) {
                    validateContentRange(contentRange, existingBytes, -1L, -1L);
                    expectedFinalSize = contentRangeTotal(contentRange);
                    long rangeLength = contentRangeEnd(contentRange) - existingBytes + 1L;
                    expectedResponseBytes = rangeLength;
                    long declaredRangeLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                    if (declaredRangeLength >= 0 && declaredRangeLength != rangeLength) {
                        try { response.body().close(); } catch (IOException ignored) { }
                        throw new IOException("Resume response length does not match Content-Range.");
                    }
                } else {
                    // A partial response without a matching local prefix is not a complete file.
                    if (response.statusCode() == 206) {
                        try { response.body().close(); } catch (IOException ignored) { }
                        throw new IOException("Server returned an unsolicited partial response.");
                    }
                    if (existingBytes > 0) existingBytes = 0;
                }
                long responseLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                if (!append) {
                    expectedFinalSize = responseLength;
                    expectedResponseBytes = responseLength;
                }
                long totalBytes = expectedFinalSize;
                long receivedBytes = append ? existingBytes : 0L;
                long responseBytes = 0L;
                long lastProgressNanos = 0L;
                byte[] buffer = new byte[64 * 1024];
                try (InputStream input = response.body();
                     OutputStream output = Files.newOutputStream(temporary,
                             java.nio.file.StandardOpenOption.CREATE,
                             append ? java.nio.file.StandardOpenOption.APPEND
                                     : java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        if (expectedResponseBytes >= 0
                                && responseBytes + read > expectedResponseBytes) {
                            throw new IOException("Download response exceeded its declared byte range.");
                        }
                        output.write(buffer, 0, read);
                        responseBytes += read;
                        receivedBytes += read;
                        long now = System.nanoTime();
                        if (now - lastProgressNanos >= 200_000_000L) {
                            dev.itsdropper.vanta.launcher.download.DownloadCenterService
                                    .getInstance().progress(taskId, receivedBytes, totalBytes);
                            lastProgressNanos = now;
                        }
                    }
                }
                if (responseLength >= 0 && responseBytes != responseLength) {
                    throw new IOException("Download response was truncated or exceeded Content-Length.");
                }
                if (expectedFinalSize >= 0 && receivedBytes != expectedFinalSize) {
                    throw new IOException("Downloaded file size does not match the server's declared total length.");
                }
                dev.itsdropper.vanta.launcher.download.DownloadCenterService
                        .getInstance().verifying(taskId, receivedBytes, totalBytes);

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

                if (LauncherSettings.isDownloadCacheEnabled()
                        && expectedSha1 != null && !expectedSha1.isBlank()) {
                    try {
                        publishCacheEntry(target, expectedSha1);
                    } catch (IOException cacheFailure) {
                        System.err.println("Could not save download cache entry: " + cacheFailure.getMessage());
                    }
                }
                return;
            } catch (Exception e) {
                lastException = e;
                boolean permanentFailure = !isRetryableDownloadFailure(e);
                boolean invalidArtifact = e.getMessage() != null
                        && (e.getMessage().contains("SHA-1 verification failed")
                        || e.getMessage().contains("size does not match"));
                if (!LauncherSettings.isResumableDownloadsEnabled()
                        || permanentFailure || invalidArtifact) {
                    try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
                }
                if (permanentFailure) break;
                if (attempt < maxRetries()) sleepBeforeRetry(attempt, e);
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
        if (sha1 == null || !sha1.matches("(?i)[0-9a-f]{40}")) {
            throw new IllegalArgumentException("Invalid SHA-1 cache key.");
        }
        return MinecraftLocator.getVantaDirectory().resolve("download-cache").resolve("sha1")
                .resolve(sha1.toLowerCase(java.util.Locale.ROOT));
    }

    private static boolean downloadSegmented(
            String url, Path target, String expectedSha1, long taskId
    ) throws Exception {
        HttpRequest head = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .method("HEAD", HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<Void> metadata = send(head, HttpResponse.BodyHandlers.discarding());
        if (metadata.statusCode() < 200 || metadata.statusCode() >= 300) return false;
        long length = metadata.headers().firstValueAsLong("Content-Length").orElse(-1L);
        boolean ranges = metadata.headers().firstValue("Accept-Ranges")
                .map(v -> v.toLowerCase(java.util.Locale.ROOT).contains("bytes")).orElse(false);
        if (!ranges || length < 32L * 1024 * 1024) return false;

        final int segments = 4;
        Path staging = target.resolveSibling(
                target.getFileName() + ".segments-" + java.util.UUID.randomUUID());
        Path assembled = target.resolveSibling(
                target.getFileName() + ".download-" + java.util.UUID.randomUUID());
        ExecutorService pool = Executors.newFixedThreadPool(segments);
        List<Future<?>> futures = new ArrayList<>();
        java.util.concurrent.atomic.AtomicLong received = new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicLong lastProgressNanos = new java.util.concurrent.atomic.AtomicLong();
        try {
            Files.createDirectories(staging);
            for (int i = 0; i < segments; i++) {
                final int index = i;
                final long begin = segmentStart(length, index, segments);
                final long finish = segmentEnd(length, index, segments);
                futures.add(pool.submit(() -> {
                    Path part = staging.resolve("part-" + index);
                    HttpRequest range = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT)
                            .header("Range", "bytes=" + begin + "-" + finish).GET().build();
                    HttpResponse<InputStream> response = send(range, HttpResponse.BodyHandlers.ofInputStream());
                    if (response.statusCode() != 206) {
                        try { response.body().close(); } catch (IOException ignored) { }
                        throw new IOException("Server did not honor range request (HTTP " + response.statusCode() + ")");
                    }
                    validateContentRange(
                            response.headers().firstValue("Content-Range").orElse(null),
                            begin, finish, length);
                    long expectedPartSize = finish - begin + 1;
                    long declaredPartSize = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                    if (declaredPartSize >= 0 && declaredPartSize != expectedPartSize) {
                        try { response.body().close(); } catch (IOException ignored) { }
                        throw new IOException("Segment Content-Length does not match its requested range.");
                    }
                    long actualPartSize = 0L;
                    byte[] buffer = new byte[64 * 1024];
                    try (InputStream in = response.body();
                         OutputStream out = Files.newOutputStream(part,
                                 StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            if (actualPartSize + read > expectedPartSize) {
                                throw new IOException("Segment exceeded its requested byte range: " + index);
                            }
                            out.write(buffer, 0, read);
                            actualPartSize += read;
                            long totalReceived = received.addAndGet(read);
                            long now = System.nanoTime();
                            long previous = lastProgressNanos.get();
                            if (now - previous >= 200_000_000L
                                    && lastProgressNanos.compareAndSet(previous, now)) {
                                dev.itsdropper.vanta.launcher.download.DownloadCenterService
                                        .getInstance().progress(taskId, totalReceived, length);
                            }
                        }
                    }
                    if (actualPartSize != expectedPartSize) {
                        throw new IOException("Incomplete segment " + index);
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) future.get();
            pool.shutdown();
            if (!pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                pool.shutdownNow();
                throw new IOException("Segment download workers did not terminate.");
            }

            dev.itsdropper.vanta.launcher.download.DownloadCenterService
                    .getInstance().verifying(taskId, received.get(), length);
            try (OutputStream out = Files.newOutputStream(assembled,
                    java.nio.file.StandardOpenOption.CREATE_NEW,
                    java.nio.file.StandardOpenOption.WRITE)) {
                for (int i = 0; i < segments; i++) {
                    try (InputStream in = Files.newInputStream(staging.resolve("part-" + i))) {
                        in.transferTo(out);
                    }
                }
            }
            if (Files.size(assembled) != length) {
                throw new IOException("Segmented download size mismatch.");
            }
            if (expectedSha1 != null && !expectedSha1.isBlank()
                    && !expectedSha1.equalsIgnoreCase(calculateSha1(assembled))) {
                throw new IOException("Segmented download checksum mismatch.");
            }
            try {
                Files.move(assembled, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(assembled, target, StandardCopyOption.REPLACE_EXISTING);
            }
            if (LauncherSettings.isDownloadCacheEnabled()
                    && expectedSha1 != null && !expectedSha1.isBlank()) {
                try {
                    publishCacheEntry(target, expectedSha1);
                } catch (IOException cacheFailure) {
                    System.err.println("Could not save download cache entry: " + cacheFailure.getMessage());
                }
            }
            return true;
        } catch (Exception failure) {
            for (Future<?> future : futures) future.cancel(true);
            throw failure;
        } finally {
            pool.shutdownNow();
            boolean workersStopped = false;
            try {
                workersStopped = pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            try { Files.deleteIfExists(assembled); }
            catch (IOException cleanupFailure) {
                System.err.println("Could not remove temporary download assembly: " + cleanupFailure.getMessage());
            }
            if (workersStopped) {
                deleteTreeBestEffort(staging);
            } else {
                System.err.println("Segment workers did not stop; preserving staging directory for safe cleanup: " + staging);
            }
        }
    }

    private static void deleteTreeBestEffort(Path directory) {
        if (directory == null || !Files.exists(directory)) return;
        try (var walk = Files.walk(directory)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (IOException failure) {
                    System.err.println("Could not remove download staging entry: " + failure.getMessage());
                }
            });
        } catch (IOException failure) {
            System.err.println("Could not inspect download staging directory: " + failure.getMessage());
        }
    }

    private static void publishVerifiedFile(Path source, Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = target.resolveSibling(
                target.getFileName() + "." + java.util.UUID.randomUUID() + ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary, target,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void publishCacheEntry(Path source, String sha1) throws IOException {
        Path cached = cachePath(sha1);
        Files.createDirectories(cached.getParent());
        Path temporary = cached.resolveSibling(cached.getFileName() + "." + java.util.UUID.randomUUID() + ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.COPY_ATTRIBUTES);
            try {
                Files.move(temporary, cached,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, cached, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static long segmentStart(long totalLength, int segmentIndex, int segmentCount) {
        validateSegmentArguments(totalLength, segmentIndex, segmentCount);
        long baseSize = totalLength / segmentCount;
        long remainder = totalLength % segmentCount;
        return baseSize * segmentIndex + Math.min(segmentIndex, remainder);
    }

    static long segmentEnd(long totalLength, int segmentIndex, int segmentCount) {
        validateSegmentArguments(totalLength, segmentIndex, segmentCount);
        long baseSize = totalLength / segmentCount;
        long remainder = totalLength % segmentCount;
        return baseSize * (segmentIndex + 1L)
                + Math.min(segmentIndex + 1L, remainder) - 1L;
    }

    private static void validateSegmentArguments(
            long totalLength, int segmentIndex, int segmentCount
    ) {
        if (totalLength < 1 || segmentCount < 1 || segmentCount > totalLength
                || segmentIndex < 0 || segmentIndex >= segmentCount) {
            throw new IllegalArgumentException("Invalid segmented download range.");
        }
    }

    static boolean isRetryableHttpStatus(int statusCode) {
        return statusCode == 408 || statusCode == 425 || statusCode == 429
                || statusCode >= 500 && statusCode <= 599;
    }

    private static boolean isRetryableDownloadFailure(Exception failure) {
        if (failure instanceof DownloadHttpException httpFailure) {
            return isRetryableHttpStatus(httpFailure.statusCode);
        }
        if (failure instanceof InterruptedException) return false;
        return failure instanceof IOException;
    }

    static void validateDownloadRequest(String url, String expectedSha1) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Download URL is required.");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Download URL is invalid.", invalid);
        }
        if (uri.getHost() == null
                || !("https".equalsIgnoreCase(uri.getScheme())
                || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException("Downloads require a valid HTTP or HTTPS URL.");
        }
        if (expectedSha1 != null && !expectedSha1.isBlank()
                && !expectedSha1.matches("(?i)[0-9a-f]{40}")) {
            throw new IllegalArgumentException("Expected SHA-1 must contain exactly 40 hexadecimal characters.");
        }
    }

    private static long contentRangeEnd(String header) throws IOException {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)^bytes ([0-9]+)-([0-9]+)/([0-9]+)$")
                .matcher(header == null ? "" : header.trim());
        if (!matcher.matches()) throw new IOException("Invalid Content-Range header.");
        try { return Long.parseLong(matcher.group(2)); }
        catch (NumberFormatException invalid) { throw new IOException("Invalid Content-Range end.", invalid); }
    }

    private static long contentRangeTotal(String header) throws IOException {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)^bytes ([0-9]+)-([0-9]+)/([0-9]+)$")
                .matcher(header == null ? "" : header.trim());
        if (!matcher.matches()) throw new IOException("Invalid Content-Range header.");
        try { return Long.parseLong(matcher.group(3)); }
        catch (NumberFormatException invalid) { throw new IOException("Invalid Content-Range total.", invalid); }
    }

    static void validateContentRange(
            String header, long expectedStart, long expectedEnd, long expectedTotal
    ) throws IOException {
        if (header == null) {
            throw new IOException("Server omitted the Content-Range header.");
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)^bytes ([0-9]+)-([0-9]+)/([0-9]+)$")
                .matcher(header.trim());
        if (!matcher.matches()) {
            throw new IOException("Server returned an invalid Content-Range header.");
        }
        long start;
        long end;
        long total;
        try {
            start = Long.parseLong(matcher.group(1));
            end = Long.parseLong(matcher.group(2));
            total = Long.parseLong(matcher.group(3));
        } catch (NumberFormatException invalid) {
            throw new IOException("Server returned an invalid Content-Range value.", invalid);
        }
        if (start != expectedStart || end < start
                || (expectedEnd >= 0 && end != expectedEnd)
                || total <= end
                || (expectedTotal >= 0 && total != expectedTotal)) {
            throw new IOException("Server returned a byte range that does not match the requested range.");
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

    private static void sleepBeforeRetry(int attempt, Exception failure) {
        long delay = retryDelayMillis(attempt, failure);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Download retry interrupted.", interrupted);
        }
    }

    static long retryDelayMillis(int attempt, Exception failure) {
        if (failure instanceof DownloadHttpException httpFailure
                && httpFailure.retryAfter != null) {
            String value = httpFailure.retryAfter.trim();
            try {
                long seconds = Long.parseLong(value);
                return Math.max(0L, Math.min(120_000L, seconds * 1000L));
            } catch (NumberFormatException ignored) {
                try {
                    java.time.Instant retryAt = java.time.ZonedDateTime
                            .parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                            .toInstant();
                    long millis = java.time.Duration.between(java.time.Instant.now(), retryAt).toMillis();
                    return Math.max(0L, Math.min(120_000L, millis));
                } catch (java.time.DateTimeException invalidDate) {
                    // Ignore malformed Retry-After and use bounded exponential backoff.
                }
            }
        }
        int exponent = Math.max(0, Math.min(5, attempt - 1));
        long exponential = Math.min(30_000L, 1_000L << exponent);
        return exponential + java.util.concurrent.ThreadLocalRandom.current().nextLong(0, 251);
    }
}