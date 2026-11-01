package dev.itsdropper.vanta.telemetry;

import dev.itsdropper.vanta.ui.LauncherSettings;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class TelemetryService {
    private static final URI EVENT_ENDPOINT = URI.create("https://vanta-telemetry.hugohakonen.workers.dev/event");
    private static final URI CRASH_ENDPOINT = URI.create("https://vanta-telemetry.hugohakonen.workers.dev/crash");
    private static final Set<String> USAGE_EVENTS = Set.of(
            "launcher_started", "view_home_opened", "view_instances_opened",
            "view_mods_opened", "view_settings_opened", "feature_instance_created",
            "feature_mod_install_used", "feature_backup_used", "feature_skin_change_used"
    );
    private static final int MAX_MESSAGE = 2_000;
    private static final int MAX_STACK = 12_000;
    private static final int MAX_EXCERPT = 8_000;
    private static final int MAX_FILE = 180_000;
    private static final Pattern THROWABLE = Pattern.compile(
            "^([A-Za-z_$][A-Za-z0-9_$.]*(?:Exception|Error|Throwable))(?::\\s*(.*))?$");
    private static final Pattern FRAME = Pattern.compile(
            "^(?:at\\s+.+|Caused by:\\s+.+|Suppressed:\\s+.+|\\.\\.\\.\\s+\\d+ more)$");
    private static final String APP_VERSION = loadVersion();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private static final AtomicBoolean HANDLER_INSTALLED = new AtomicBoolean();
    private static final AtomicBoolean LAUNCHER_CRASH_REPORTED = new AtomicBoolean();
    private static final Set<Path> REPORTED_FILES = ConcurrentHashMap.newKeySet();
    private static volatile Thread.UncaughtExceptionHandler telemetryDefaultHandler;

    private TelemetryService() {}

    public static void track(String eventName) {
        if (LauncherSettings.isUsageTelemetryEnabled() && isSupportedUsageEvent(eventName)) {
            postEvent(eventName).exceptionally(ignored -> null);
        }
    }

    public static CompletableFuture<Integer> testUsageDelivery() {
        if (!LauncherSettings.isUsageTelemetryEnabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Enable anonymous usage statistics before testing delivery."));
        }
        return postEvent("telemetry_test");
    }

    /** Sends a labelled synthetic crash row through the real crash endpoint. */
    public static CompletableFuture<Integer> testCrashDelivery() {
        if (!LauncherSettings.isCrashTelemetryEnabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Enable crash reporting before testing delivery."));
        }
        return postCrash(
                "VantaTelemetryTestException",
                "Synthetic crash-report delivery test. No Minecraft crash occurred.",
                "dev.itsdropper.vanta.telemetry.VantaTelemetryTestException: Synthetic test"
                        + "\n\tat dev.itsdropper.vanta.telemetry.TelemetryService.testCrashDelivery(TelemetryService.java:1)"
                        + "\n\tat dev.itsdropper.vanta.ui.views.SettingsView.buildDiagnosticsPage(SettingsView.java:1)",
                "Synthetic diagnostic payload. This is not a real Minecraft crash log."
        );
    }

    /**
     * Uploads only a newly generated crash report for this launch. Raw
     * latest.log files and unrelated system-detail sections are never uploaded.
     */
    public static CompletableFuture<Integer> reportMinecraftCrash(
            Path instanceDirectory, String processOutput, long launchStartedAtMillis,
            Set<String> previousCrashReportNames) {
        if (!LauncherSettings.isCrashTelemetryEnabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Crash reporting is disabled."));
        }
        if (instanceDirectory == null || launchStartedAtMillis <= 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Missing crash context."));
        }

        Path report = newestCrashReport(
                instanceDirectory.resolve("crash-reports"),
                launchStartedAtMillis,
                previousCrashReportNames == null ? Set.of() : previousCrashReportNames
        );
        String reportText = report == null ? "" : readBounded(report, MAX_FILE);
        CrashDetails details = extractCrashDetails(reportText);
        if (details.stackTrace().isBlank() && reportText.isBlank()) {
            details = extractCrashDetails(processOutput);
        }
        if (details.stackTrace().isBlank() && details.logExcerpt().isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("No new crash report or stack trace was found."));
        }
        if (report != null && !REPORTED_FILES.add(report.toAbsolutePath().normalize())) {
            return CompletableFuture.completedFuture(208);
        }
        return postCrash(details.errorType(), details.message(), details.stackTrace(), details.logExcerpt());
    }

    /** Best-effort reporting for uncaught exceptions in Vanta's own JVM. */
    public static void reportCrash(Throwable error) {
        if (error == null || !LauncherSettings.isCrashTelemetryEnabled()
                || !LAUNCHER_CRASH_REPORTED.compareAndSet(false, true)) return;
        postCrash(error.getClass().getName(), error.getMessage(), stackTrace(error), "")
                .exceptionally(ignored -> null);
    }

    public static void installUncaughtExceptionHandler() {
        if (HANDLER_INSTALLED.compareAndSet(false, true)) {
            Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            telemetryDefaultHandler = (thread, error) -> {
                reportCrash(error);
                if (previous != null) previous.uncaughtException(thread, error);
                else error.printStackTrace();
            };
            Thread.setDefaultUncaughtExceptionHandler(telemetryDefaultHandler);
        }
        Thread current = Thread.currentThread();
        Thread.UncaughtExceptionHandler previous = current.getUncaughtExceptionHandler();
        if (previous != telemetryDefaultHandler) {
            current.setUncaughtExceptionHandler((thread, error) -> {
                reportCrash(error);
                if (previous != null) previous.uncaughtException(thread, error);
                else error.printStackTrace();
            });
        }
    }

    static boolean isSupportedUsageEvent(String eventName) {
        return eventName != null && USAGE_EVENTS.contains(eventName);
    }

    static String sanitizeErrorType(String value) {
        if (value == null || value.isBlank()) return "UnknownError";
        String sanitized = value.split(":", 2)[0].trim().replaceAll("[^A-Za-z0-9_.$]", "");
        if (sanitized.isBlank()) return "UnknownError";
        return sanitized.length() > 120 ? sanitized.substring(0, 120) : sanitized;
    }

    static String sanitizeText(String value, int maxLength) {
        if (value == null || value.isBlank() || maxLength <= 0) return "";
        String text = value.replace("\r\n", "\n").replace('\r', '\n').replace("\0", "");
        text = text.replaceAll("(?i)(access[_ -]?token|refresh[_ -]?token|client[_ -]?secret|token|authorization|bearer)(\\s*[:=]\\s*)[^\\s,;]+", "$1$2[REDACTED]");
        text = text.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*", "Bearer [REDACTED]");
        text = text.replaceAll("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", "[REDACTED_EMAIL]");
        text = text.replaceAll("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b", "[REDACTED_UUID]");
        text = text.replaceAll("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b", "[REDACTED_IP]");
        text = text.replaceAll("(?i)[A-Z]:\\\\Users\\\\[^\\\\\\s\"']+", "[REDACTED_WINDOWS_PATH]");
        text = text.replaceAll("/(?:home|Users)/[^/\\s\"']+", "[REDACTED_HOME]");
        text = text.replaceAll("(?im)^\\s*(?:username|player name|player uuid|user uuid|access token|server address|server ip|connected to)\\s*[:=].*$", "[REDACTED_PRIVATE_FIELD]");
        text = text.replaceAll("(?i)(--username\\s+)\\S+", "$1[REDACTED]");
        text = text.replaceAll("(?i)([?&](?:token|access_token|session|key|password)=)[^&\\s]+", "$1[REDACTED]");
        return text.length() > maxLength ? text.substring(0, maxLength).trim() : text.trim();
    }

    private static CrashDetails extractCrashDetails(String source) {
        if (source == null || source.isBlank()) {
            return new CrashDetails("MinecraftCrash", "Minecraft exited unexpectedly.", "", "");
        }
        String errorType = "MinecraftCrash";
        String message = "Minecraft generated a crash report.";
        List<String> stack = new ArrayList<>();
        List<String> excerpt = new ArrayList<>();
        boolean foundThrowable = false;

        for (String raw : source.split("\\n")) {
            String line = raw.trim();
            if (foundThrowable && (line.startsWith("-- ") || line.isBlank())) break;
            if (line.startsWith("Description:")) {
                String description = line.substring("Description:".length()).trim();
                if (!description.isBlank()) {
                    message = description;
                    excerpt.add("Description: " + description);
                }
                continue;
            }
            Matcher matcher = THROWABLE.matcher(line);
            if (!foundThrowable && matcher.matches()) {
                foundThrowable = true;
                errorType = sanitizeErrorType(matcher.group(1));
                if (matcher.group(2) != null && !matcher.group(2).isBlank()) {
                    message = matcher.group(2).trim();
                }
                stack.add(line);
                excerpt.add(line);
                continue;
            }
            if (foundThrowable && FRAME.matcher(line).matches()) {
                if (stack.size() < 100) stack.add(line);
                if (excerpt.size() < 45) excerpt.add(line);
            } else if (foundThrowable && (line.startsWith("Caused by:")
                    || line.startsWith("Suppressed:"))) {
                if (stack.size() < 100) stack.add(line);
                if (excerpt.size() < 45) excerpt.add(line);
            } else if (foundThrowable && !line.isBlank()) {
                break;
            }
        }
        if (stack.isEmpty()) {
            for (String raw : source.split("\\n")) {
                String line = raw.trim();
                if (line.startsWith("Exception") || line.startsWith("Caused by:")
                        || line.startsWith("Suppressed:") || line.startsWith("at ")) {
                    if (excerpt.size() < 45) excerpt.add(line);
                }
            }
        }
        return new CrashDetails(
                errorType,
                sanitizeText(message, MAX_MESSAGE),
                sanitizeText(String.join("\n", stack), MAX_STACK),
                sanitizeText(String.join("\n", excerpt), MAX_EXCERPT)
        );
    }

    private static Path newestCrashReport(Path directory, long startedAt, Set<String> previousNames) {
        if (!Files.isDirectory(directory)) return null;
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".txt"))
                    .filter(path -> !previousNames.contains(path.getFileName().toString()))
                    .filter(path -> lastModified(path) >= startedAt - 2_000L)
                    .max(Comparator.comparingLong(TelemetryService::lastModified))
                    .orElse(null);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private static String readBounded(Path path, int maxChars) {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int count;
            while (result.length() < maxChars && (count = reader.read(
                    buffer, 0, Math.min(buffer.length, maxChars - result.length()))) >= 0) {
                result.append(buffer, 0, count);
            }
        } catch (IOException ignored) {}
        return result.toString();
    }

    private static String stackTrace(Throwable error) {
        StringBuilder result = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 8 && result.length() < MAX_STACK) {
            if (!result.isEmpty()) result.append("\nCaused by: ");
            result.append(current.getClass().getName());
            if (current.getMessage() != null) result.append(": ").append(current.getMessage());
            for (StackTraceElement frame : current.getStackTrace()) {
                if (result.length() >= MAX_STACK) break;
                result.append("\n\tat ").append(frame);
            }
            current = current.getCause();
        }
        return result.toString();
    }

    private static CompletableFuture<Integer> postCrash(
            String errorType, String message, String stack, String excerpt) {
        if (!LauncherSettings.isCrashTelemetryEnabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Crash reporting is disabled."));
        }
        String payload = "{"
                + "\"app_version\":\"" + jsonEscape(APP_VERSION) + "\","
                + "\"error_type\":\"" + jsonEscape(sanitizeErrorType(errorType)) + "\","
                + "\"exception_message\":\"" + jsonEscape(sanitizeText(message, MAX_MESSAGE)) + "\","
                + "\"stack_trace\":\"" + jsonEscape(sanitizeText(stack, MAX_STACK)) + "\","
                + "\"log_excerpt\":\"" + jsonEscape(sanitizeText(excerpt, MAX_EXCERPT)) + "\"}";
        return post(CRASH_ENDPOINT, payload);
    }

    private static CompletableFuture<Integer> postEvent(String eventName) {
        String payload = "{\"event_name\":\"" + jsonEscape(eventName)
                + "\",\"app_version\":\"" + jsonEscape(APP_VERSION) + "\"}";
        return post(EVENT_ENDPOINT, payload);
    }

    private static CompletableFuture<Integer> post(URI endpoint, String payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(endpoint)
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
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
                if (version.matches("[A-Za-z0-9.+_-]{1,40}") && !version.contains("$" + "{")) {
                    return version;
                }
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    private static String jsonEscape(String value) {
        StringBuilder result = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (c < 0x20) result.append(String.format("\\u%04x", (int) c));
                    else result.append(c);
                }
            }
        }
        return result.toString();
    }

    private record CrashDetails(String errorType, String message, String stackTrace, String logExcerpt) {}
}
