package dev.itsdropper.vanta.launcher.update;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class UpdateHttpRetryTest {
    @Test
    void retriesTransientServerResponsesUntilReleaseServerRecovers() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/release", exchange -> {
            int attempt = requests.incrementAndGet();
            byte[] body = (attempt < 3 ? "temporarily unavailable" : "release")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(attempt < 3 ? 503 : 200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/release"))
                    .GET()
                    .build();

            HttpResponse<java.io.InputStream> response = UpdateHttpRetry.send(client, request);
            try (var body = response.body()) {
                assertEquals(200, response.statusCode());
                assertEquals("release", new String(body.readAllBytes(), StandardCharsets.UTF_8));
            }
            assertEquals(3, requests.get(), "The client should retry two transient 503 responses");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retriesOnlyTransientHttpStatuses() {
        for (int status : new int[]{408, 425, 429, 500, 502, 503, 504}) {
            assertTrue(UpdateHttpRetry.isRetryableStatus(status), "Expected retryable: " + status);
        }
        for (int status : new int[]{200, 301, 400, 401, 403, 404, 409, 422}) {
            assertFalse(UpdateHttpRetry.isRetryableStatus(status), "Must not retry: " + status);
        }
    }

    @Test
    void retryBackoffIsBounded() {
        assertEquals(500, UpdateHttpRetry.retryDelayMillis(1));
        assertEquals(1_000, UpdateHttpRetry.retryDelayMillis(2));
        assertEquals(2_000, UpdateHttpRetry.retryDelayMillis(3));
        assertEquals(4_000, UpdateHttpRetry.retryDelayMillis(4));
        assertEquals(4_000, UpdateHttpRetry.retryDelayMillis(20));
    }
}
