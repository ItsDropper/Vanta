package dev.itsdropper.vanta.launcher.update;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.IOException;
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
        try (ServerSocket server = new ServerSocket(0)) {
            Thread responder = new Thread(() -> {
                try {
                    for (int i = 0; i < 3; i++) {
                        try (Socket socket = server.accept()) {
                            BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                            String line;
                            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                                // Consume the complete HTTP request headers.
                            }
                            int attempt = requests.incrementAndGet();
                            byte[] body = (attempt < 3 ? "temporarily unavailable" : "release")
                                    .getBytes(StandardCharsets.UTF_8);
                            String status = attempt < 3 ? "503 Service Unavailable" : "200 OK";
                            OutputStream output = socket.getOutputStream();
                            output.write(("HTTP/1.1 " + status + "\r\n"
                                    + "Content-Length: " + body.length + "\r\n"
                                    + "Connection: close\r\n\r\n")
                                    .getBytes(StandardCharsets.US_ASCII));
                            output.write(body);
                            output.flush();
                        }
                    }
                } catch (IOException failure) {
                    throw new AssertionError("Local retry test server failed", failure);
                }
            }, "vanta-update-retry-test-server");
            responder.setDaemon(true);
            responder.start();

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + server.getLocalPort() + "/release"))
                    .GET()
                    .build();

            HttpResponse<java.io.InputStream> response = UpdateHttpRetry.send(client, request);
            try (var body = response.body()) {
                assertEquals(200, response.statusCode());
                assertEquals("release", new String(body.readAllBytes(), StandardCharsets.UTF_8));
            }
            responder.join(5_000);
            assertFalse(responder.isAlive(), "The local test server should finish all retry responses");
            assertEquals(3, requests.get(), "The client should retry two transient 503 responses");
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
