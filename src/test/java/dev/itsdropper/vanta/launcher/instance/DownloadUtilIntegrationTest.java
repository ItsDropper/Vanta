package dev.itsdropper.vanta.launcher.instance;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.itsdropper.vanta.ui.LauncherSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class DownloadUtilIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private HttpServer server;
    private boolean previousCache;
    private boolean previousSegmented;
    private boolean previousResumable;
    private int previousRetries;

    @BeforeEach
    void configureIsolatedTransferSettings() {
        previousCache = LauncherSettings.isDownloadCacheEnabled();
        previousSegmented = LauncherSettings.isSegmentedDownloadsEnabled();
        previousResumable = LauncherSettings.isResumableDownloadsEnabled();
        previousRetries = LauncherSettings.getDownloadRetries();
        LauncherSettings.setDownloadCacheEnabled(false);
        LauncherSettings.setSegmentedDownloadsEnabled(false);
        LauncherSettings.setResumableDownloadsEnabled(true);
        LauncherSettings.setDownloadRetries(1);
    }

    @AfterEach
    void restoreSettingsAndStopServer() {
        if (server != null) server.stop(0);
        LauncherSettings.setDownloadCacheEnabled(previousCache);
        LauncherSettings.setSegmentedDownloadsEnabled(previousSegmented);
        LauncherSettings.setResumableDownloadsEnabled(previousResumable);
        LauncherSettings.setDownloadRetries(previousRetries);
    }

    @Test
    void downloadsAndPublishesVerifiedArtifact() throws Exception {
        byte[] payload = "Vanta download integration payload".getBytes(StandardCharsets.UTF_8);
        String url = serve(exchange -> writeResponse(exchange, 200, payload));
        Path target = temporaryDirectory.resolve("artifact.jar");
        Path abandonedSegments = target.resolveSibling("artifact.jar.segments-stale");
        Path abandonedAssembly = target.resolveSibling("artifact.jar.download-stale");
        Files.createDirectories(abandonedSegments);
        Files.writeString(abandonedSegments.resolve("part-0"), "stale");
        Files.writeString(abandonedAssembly, "stale");

        DownloadUtil.downloadFileInternal(url, target, sha1(payload), 0L);

        assertArrayEquals(payload, Files.readAllBytes(target));
        assertFalse(Files.exists(target.resolveSibling("artifact.jar.download")));
        assertFalse(Files.exists(abandonedSegments));
        assertFalse(Files.exists(abandonedAssembly));
    }

    @Test
    void resumesFromExistingPartialFileUsingValidatedRange() throws Exception {
        byte[] payload = "a resumable Vanta artifact payload".getBytes(StandardCharsets.UTF_8);
        int offset = 9;
        String url = serve(exchange -> {
            assertEquals("bytes=" + offset + "-", exchange.getRequestHeaders().getFirst("Range"));
            byte[] remainder = java.util.Arrays.copyOfRange(payload, offset, payload.length);
            exchange.getResponseHeaders().set("Content-Range",
                    "bytes " + offset + "-" + (payload.length - 1) + "/" + payload.length);
            writeResponse(exchange, 206, remainder);
        });
        Path target = temporaryDirectory.resolve("resumed.jar");
        Files.write(target.resolveSibling("resumed.jar.download"),
                java.util.Arrays.copyOf(payload, offset));

        DownloadUtil.downloadFileInternal(url, target, sha1(payload), 0L);

        assertArrayEquals(payload, Files.readAllBytes(target));
    }

    @Test
    void restartsFromZeroWhenServerRejectsStaleResumeOffset() throws Exception {
        byte[] payload = "current artifact bytes".getBytes(StandardCharsets.UTF_8);
        java.util.concurrent.atomic.AtomicInteger requests = new java.util.concurrent.atomic.AtomicInteger();
        String url = serve(exchange -> {
            if (exchange.getRequestHeaders().getFirst("Range") != null) {
                requests.incrementAndGet();
                exchange.getResponseHeaders().set("Content-Range", "bytes */" + payload.length);
                exchange.sendResponseHeaders(416, -1);
                exchange.close();
                return;
            }
            requests.incrementAndGet();
            writeResponse(exchange, 200, payload);
        });
        Path target = temporaryDirectory.resolve("stale-resume.jar");
        Files.write(target.resolveSibling("stale-resume.jar.download"),
                "an obsolete and longer partial file".getBytes(StandardCharsets.UTF_8));

        DownloadUtil.downloadFileInternal(url, target, sha1(payload), 0L);

        assertArrayEquals(payload, Files.readAllBytes(target));
        assertEquals(2, requests.get());
    }

    @Test
    void rejectsResumeResponseWithWrongStartingOffset() throws Exception {
        byte[] payload = "a resumable Vanta artifact payload".getBytes(StandardCharsets.UTF_8);
        int offset = 9;
        String url = serve(exchange -> {
            byte[] remainder = java.util.Arrays.copyOfRange(payload, offset, payload.length);
            exchange.getResponseHeaders().set("Content-Range",
                    "bytes " + (offset - 1) + "-" + (payload.length - 1) + "/" + payload.length);
            writeResponse(exchange, 206, remainder);
        });
        Path target = temporaryDirectory.resolve("wrong-range.jar");
        byte[] original = "existing-good-target".getBytes(StandardCharsets.UTF_8);
        Files.write(target, original);
        Files.write(target.resolveSibling("wrong-range.jar.download"),
                java.util.Arrays.copyOf(payload, offset));

        assertThrows(IOException.class,
                () -> DownloadUtil.downloadFileInternal(url, target, sha1(payload), 0L));

        assertArrayEquals(original, Files.readAllBytes(target));
    }

    @Test
    void rejectsUnsolicitedPartialResponse() throws Exception {
        byte[] original = "existing-good-target".getBytes(StandardCharsets.UTF_8);
        String url = serve(exchange -> {
            exchange.getResponseHeaders().set("Content-Range", "bytes 0-4/10");
            writeResponse(exchange, 206, "short".getBytes(StandardCharsets.UTF_8));
        });
        Path target = temporaryDirectory.resolve("partial.jar");
        Files.write(target, original);

        assertThrows(IOException.class,
                () -> DownloadUtil.downloadFileInternal(url, target, null, 0L));

        assertArrayEquals(original, Files.readAllBytes(target));
    }

    @Test
    void checksumFailureDoesNotReplaceExistingTarget() throws Exception {
        byte[] original = "existing-good-target".getBytes(StandardCharsets.UTF_8);
        byte[] incorrect = "downloaded-but-not-the-expected-artifact".getBytes(StandardCharsets.UTF_8);
        String url = serve(exchange -> writeResponse(exchange, 200, incorrect));
        Path target = temporaryDirectory.resolve("checksum.jar");
        Files.write(target, original);
        String wrongSha1 = sha1("different artifact".getBytes(StandardCharsets.UTF_8));

        assertThrows(IOException.class,
                () -> DownloadUtil.downloadFileInternal(url, target, wrongSha1, 0L));

        assertArrayEquals(original, Files.readAllBytes(target));
    }

    @Test
    void permanentHttpFailureDoesNotReplaceExistingTarget() throws Exception {
        byte[] original = "known-good-existing-artifact".getBytes(StandardCharsets.UTF_8);
        String url = serve(exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        Path target = temporaryDirectory.resolve("existing.jar");
        Files.write(target, original);

        assertThrows(IOException.class,
                () -> DownloadUtil.downloadFileInternal(url, target, null, 0L));

        assertArrayEquals(original, Files.readAllBytes(target));
    }

    @Test
    void truncatedResponseDoesNotReplaceExistingTarget() throws Exception {
        byte[] original = "known-good-existing-artifact".getBytes(StandardCharsets.UTF_8);
        byte[] truncated = "short".getBytes(StandardCharsets.UTF_8);
        String url = serve(exchange -> {
            exchange.sendResponseHeaders(200, truncated.length + 20L);
            try (var output = exchange.getResponseBody()) {
                output.write(truncated);
            }
        });
        Path target = temporaryDirectory.resolve("truncated.jar");
        Files.write(target, original);

        assertThrows(IOException.class,
                () -> DownloadUtil.downloadFileInternal(url, target, null, 0L));

        assertArrayEquals(original, Files.readAllBytes(target));
    }

    private String serve(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/artifact", handler);
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/artifact";
    }

    private static void writeResponse(HttpExchange exchange, int status, byte[] payload)
            throws IOException {
        exchange.sendResponseHeaders(status, payload.length);
        try (var output = exchange.getResponseBody()) {
            output.write(payload);
        }
    }

    private static String sha1(byte[] payload) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-1").digest(payload));
    }
}
