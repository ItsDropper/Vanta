package dev.itsdropper.vanta.launcher.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.TimeUnit;

/** Bounded retry policy for transient GitHub release/API failures. */
final class UpdateHttpRetry {
    private static final int MAX_ATTEMPTS = 4;
    private static final long INITIAL_DELAY_MILLIS = 500;
    private static final long MAX_DELAY_MILLIS = 4_000;

    private UpdateHttpRetry() {
    }

    static HttpResponse<InputStream> send(
            HttpClient client,
            HttpRequest request
    ) throws IOException, InterruptedException {
        IOException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            final HttpResponse<InputStream> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (IOException failure) {
                lastFailure = failure;
                if (attempt == MAX_ATTEMPTS) throw failure;
                pause(attempt);
                continue;
            }

            if (!isRetryableStatus(response.statusCode()) || attempt == MAX_ATTEMPTS) {
                return response;
            }

            try {
                response.body().close();
            } catch (IOException ignored) {
                // The response is discarded; preserve the retry decision.
            }
            pause(attempt);
        }

        throw lastFailure == null
                ? new IOException("Vanta could not contact the release server.")
                : lastFailure;
    }

    static boolean isRetryableStatus(int statusCode) {
        return statusCode == 408 || statusCode == 425 || statusCode == 429
                || statusCode == 500 || statusCode == 502
                || statusCode == 503 || statusCode == 504;
    }

    static long retryDelayMillis(int failedAttempt) {
        if (failedAttempt <= 0) return INITIAL_DELAY_MILLIS;
        long multiplier = 1L << Math.min(failedAttempt - 1, 10);
        return Math.min(MAX_DELAY_MILLIS, INITIAL_DELAY_MILLIS * multiplier);
    }

    private static void pause(int failedAttempt) throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(retryDelayMillis(failedAttempt));
    }
}
