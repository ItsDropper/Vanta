package dev.itsdropper.vanta.telemetry;

import dev.itsdropper.vanta.ui.LauncherSettings;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal, opt-in telemetry transport. It has no access to account or session
 * storage and sends only fixed-schema, allowlisted payloads.
 */
public final class TelemetryService {

    private static final URI EVENT_ENDPOINT =
            URI.create("https://vanta-telemetry.hugohakonen.workers.dev/event");
    private static final URI CRASH_ENDPOINT =
            URI.create("https://vanta-telemetry.hugohakonen.workers.dev/crash");

    private static final Set<String> USAGE_EVENTS = Set.of(
            "launcher_started",
            "view_home_opened",
            "view_instances_opened",
            "view_mods_opened",
            "view_settings_opened"
    );

    private static final String APP_VERSION = loadVersion();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private static final AtomicBoolean HANDLER_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean CRASH_REPORTED = new AtomicBoolean();

    private TelemetryService() {
    }

    public static void track(String eventName) {
        if (!LauncherSettings.isUsageTelemetryEnabled()
                || !isSupportedUsageEvent(eventName)) {
            return;
        }
        postEvent(eventName).exceptionally(ignored -> null);
    }

    /**
     * Sends a synthetic telemetry_test event only with usage consent.
     * The backend must allowlist this event and exclude it from usage aggregates.
     */
    public static CompletableFuture<Integer> testUsageDelivery() {
        if (!LauncherSettings.isUsageTelemetryEnabled()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Enable anonymous usage statistics before testing delivery.")
            );
        }
        return postEvent("telemetry_test");
    }

    public static void reportCrash(Throwable error) {
        if (error == null
                || !LauncherSettings.isCrashTelemetryEnabled()
                || !CRASH_REPORTED.compareAndSet(false, true)) {
            return;
        }

        String errorType = sanitizeErrorType(error.getClass().getSimpleName());
        String payload = "{\"app_version\":\"" + jsonEscape(APP_VERSION)
                + "\",\"error_type\":\"" + jsonEscape(errorType) + "\"}";
        post(CRASH_ENDPOINT, payload).exceptionally(ignored -> null);
    }

    /**
     * Best-effort uncaught-exception reporting. Preserve the previous handler
     * so telemetry never suppresses normal JVM crash handling.
     */
    public static void installUncaughtExceptionHandler() {
        if (!HANDLER_INSTALLED.compareAndSet(false, true)) {
            return;
        }

        Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            reportCrash(error);
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                error.printStackTrace();
            }
        });
    }

    static boolean isSupportedUsageEvent(String eventName) {
        return eventName != null && USAGE_EVENTS.contains(eventName);
    }

    static String sanitizeErrorType(String value) {
        if (value == null || value.isBlank()) {
            return "UnknownError";
        }
        String sanitized = value.replaceAll("[^A-Za-z0-9_.$]", "");
        if (sanitized.isBlank()) {
            return "UnknownError";
        }
        return sanitized.length() > 120 ? sanitized.substring(0, 120) : sanitized;
    }

    private static CompletableFuture<Integer> postEvent(String eventName) {
        String payload = "{\"event_name\":\"" + jsonEscape(eventName)
                + "\",\"app_version\":\"" + jsonEscape(APP_VERSION) + "\"}";
        return post(EVENT_ENDPOINT, payload);
    }

    private static CompletableFuture<Integer> post(URI endpoint, String payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(endpoint)
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            return HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenApply(HttpResponse::statusCode);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static String loadVersion() {
        Properties properties = new Properties();
        try (InputStream stream = TelemetryService.class.getResourceAsStream("/version.properties")) {
            if (stream != null) {
                properties.load(stream);
                String version = properties.getProperty("version", "unknown").trim();
                if (version.matches("[A-Za-z0-9.+_-]{1,40}") && !version.contains("${")) {
                    return version;
                }
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
