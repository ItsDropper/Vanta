package dev.itsdropper.vanta.launcher.repair;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.MinecraftLocator;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Privacy-first, local-only crash case inbox.
 *
 * Nothing in this class performs network I/O. Logs are sanitized before they
 * are persisted; only explicitly confirmed cases can be exported as training
 * examples. Raw source files are never modified or deleted.
 */
public final class CrashCaseRepository {
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_CASES = 1_000;
    private static final int MAX_LOG_CHARS = 96 * 1024;
    private static final long MAX_STORE_BYTES = 32L * 1024 * 1024;
    private static final long MAX_IMPORT_BYTES = 2L * 1024 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]+=*");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(\\b(?:access[_-]?token|refresh[_-]?token|client[_-]?secret|password|passwd|session[_-]?id|"
                    + "authorization|xuid|user[_-]?properties|proxy[_-]?password)\\b\\s*[:=]\\s*)"
                    + "(?:\"[^\"]*\"|'[^']*'|[^\\s,;]+)");
    private static final Pattern CLI_SECRET = Pattern.compile(
            "(?i)(--(?:accessToken|clientId|uuid|username|userType|userProperties)\\s+)\\S+");
    private static final Pattern QUERY_SECRET = Pattern.compile(
            "(?i)([?&](?:access_token|refresh_token|token|session|key|password|client_secret)=)[^&\\s]+");
    private static final Pattern UUID_VALUE = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b");
    private static final Pattern IPV4 = Pattern.compile(
            "\\b(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}\\b");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern WINDOWS_PATH = Pattern.compile(
            "(?i)\\b[A-Z]:\\\\(?:[^\\r\\n\\t\\\"<>|]+)");
    private static final Pattern UNIX_HOME = Pattern.compile(
            "(?<![A-Za-z0-9])/(?:home|Users|mnt|private/var)/[^\\r\\n\\t]+");
    private static final Pattern PRIVATE_LINE = Pattern.compile(
            "(?im)^\\s*(?:username|player name|player uuid|user uuid|access token|refresh token|"
                    + "server address|server ip|connected to|user home|user\.home)\\s*[:=].*$");
    private static final Pattern TIMESTAMP = Pattern.compile(
            "\\b\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d+)?\\b");
    private static final Pattern THREAD_ID = Pattern.compile("(?i)\\bthread[- ]#?\\d+\\b");

    public enum ReviewStatus { PENDING, CONFIRMED, REJECTED }

    public record CrashCase(
            String id,
            long createdAtEpochMillis,
            String fingerprint,
            ReviewStatus status,
            FailureCategory proposedCategory,
            FailureCategory confirmedCategory,
            int occurrences,
            String sanitizedLog
    ) {
        public CrashCase {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(fingerprint, "fingerprint");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(sanitizedLog, "sanitizedLog");
            if (occurrences < 1) throw new IllegalArgumentException("occurrences must be positive");
            if (status == ReviewStatus.CONFIRMED && confirmedCategory == null) {
                throw new IllegalArgumentException("Confirmed cases require a category.");
            }
            if (status != ReviewStatus.CONFIRMED && confirmedCategory != null) {
                throw new IllegalArgumentException("Only confirmed cases may have a confirmed category.");
            }
        }
    }

    private record StoreDocument(int schemaVersion, List<CrashCase> cases) {
        private StoreDocument {
            cases = List.copyOf(cases);
        }
    }

    private final Path storeFile;
    private final Path lockFile;

    public CrashCaseRepository() {
        this(MinecraftLocator.getVantaDirectoryPath().resolve("crash-case-review").resolve("cases.json"));
    }

    public CrashCaseRepository(Path storeFile) {
        this.storeFile = Objects.requireNonNull(storeFile, "storeFile").toAbsolutePath().normalize();
        this.lockFile = this.storeFile.resolveSibling(this.storeFile.getFileName() + ".lock");
    }

    public List<CrashCase> listCases() throws IOException {
        return withLock(this::readCases);
    }

    public int pendingCount() throws IOException {
        return (int) listCases().stream().filter(item -> item.status() == ReviewStatus.PENDING).count();
    }

    public CrashCase recordForReview(String log, FailureCategory proposedCategory) throws IOException {
        String sanitized = sanitize(log);
        if (sanitized.isBlank()) throw new IllegalArgumentException("The crash log contains no usable diagnostic text.");
        String fingerprint = fingerprint(sanitized);

        return withLock(() -> {
            List<CrashCase> cases = new ArrayList<>(readCases());
            for (int i = 0; i < cases.size(); i++) {
                CrashCase existing = cases.get(i);
                if (existing.fingerprint().equals(fingerprint)) {
                    CrashCase updated = new CrashCase(existing.id(), existing.createdAtEpochMillis(),
                            existing.fingerprint(), existing.status(), existing.proposedCategory(),
                            existing.confirmedCategory(), Math.addExact(existing.occurrences(), 1),
                            existing.sanitizedLog());
                    cases.set(i, updated);
                    writeCases(cases);
                    return updated;
                }
            }

            if (cases.size() >= MAX_CASES) {
                int rejected = -1;
                for (int i = 0; i < cases.size(); i++) {
                    if (cases.get(i).status() == ReviewStatus.REJECTED
                            && (rejected < 0 || cases.get(i).createdAtEpochMillis()
                            < cases.get(rejected).createdAtEpochMillis())) rejected = i;
                }
                if (rejected < 0) throw new IOException("Crash case inbox is full. Review or export existing cases first.");
                cases.remove(rejected);
            }

            CrashCase created = new CrashCase(UUID.randomUUID().toString(),
                    System.currentTimeMillis(), fingerprint, ReviewStatus.PENDING,
                    proposedCategory, null, 1, sanitized);
            cases.add(created);
            writeCases(cases);
            return created;
        });
    }

    public CrashCase importLog(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        Path normalized = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) throw new IOException("Selected crash log is not a regular file.");
        long size = Files.size(normalized);
        if (size <= 0 || size > MAX_IMPORT_BYTES) {
            throw new IOException("Crash log must be between 1 byte and 2 MiB.");
        }
        byte[] bytes = Files.readAllBytes(normalized);
        String log = new String(bytes, StandardCharsets.UTF_8);
        return recordForReview(log, null);
    }

    public CrashCase confirm(String id, FailureCategory category) throws IOException {
        Objects.requireNonNull(category, "category");
        if (category == FailureCategory.UNKNOWN) throw new IllegalArgumentException("UNKNOWN is not a training label.");
        return updateReview(id, ReviewStatus.CONFIRMED, category);
    }

    public CrashCase reject(String id) throws IOException {
        return updateReview(id, ReviewStatus.REJECTED, null);
    }

    public int exportConfirmedCorpus(Path destination) throws IOException {
        Objects.requireNonNull(destination, "destination");
        Path target = destination.toAbsolutePath().normalize();
        return withLock(() -> {
            List<CrashCase> cases = readCases();
            List<String> rows = cases.stream()
                    .filter(item -> item.status() == ReviewStatus.CONFIRMED)
                    .sorted(Comparator.comparing(CrashCase::fingerprint))
                    .map(item -> item.confirmedCategory().name() + "\t"
                            + oneLine(item.sanitizedLog(), 8_192))
                    .toList();
            if (rows.isEmpty()) throw new IOException("There are no confirmed crash cases to export.");
            Path parent = target.getParent();
            if (parent == null) throw new IOException("Export destination has no parent directory.");
            Files.createDirectories(parent);
            atomicWrite(target, ("# Reviewed Vanta crash cases; generated locally.\n"
                    + String.join("\n", rows) + "\n").getBytes(StandardCharsets.UTF_8));
            return rows.size();
        });
    }

    public static String sanitize(String source) {
        if (source == null || source.isBlank()) return "";
        String text = source.replace("\r\n", "\n").replace('\r', '\n');
        text = BEARER.matcher(text).replaceAll("Bearer [REDACTED]");
        text = SECRET_ASSIGNMENT.matcher(text).replaceAll("$1[REDACTED]");
        text = CLI_SECRET.matcher(text).replaceAll("$1[REDACTED]");
        text = QUERY_SECRET.matcher(text).replaceAll("$1[REDACTED]");
        text = PRIVATE_LINE.matcher(text).replaceAll("[REDACTED_PRIVATE_FIELD]");
        text = UUID_VALUE.matcher(text).replaceAll("[REDACTED_UUID]");
        text = IPV4.matcher(text).replaceAll("[REDACTED_IP]");
        text = EMAIL.matcher(text).replaceAll("[REDACTED_EMAIL]");
        text = WINDOWS_PATH.matcher(text).replaceAll("[REDACTED_PATH]");
        text = UNIX_HOME.matcher(text).replaceAll("[REDACTED_PATH]");
        text = text.replaceAll("(?i)\\b[A-Z]:\\\\Users\\\\[^\\\\/\\s]+", "[REDACTED_USER_PATH]");
        text = text.replaceAll("(?i)(-Duser\\.(?:name|home)=)\\S+", "$1[REDACTED]");
        text = text.replaceAll("(?i)(--server(?:Address|Ip)\\s+)\\S+", "$1[REDACTED]");
        text = text.replaceAll("(?i)(https?://[^\\s?]+)\\?[^\\s]+", "$1?[REDACTED_QUERY]");
        if (text.length() > MAX_LOG_CHARS) {
            text = text.substring(0, MAX_LOG_CHARS) + "\n[TRUNCATED: local crash-case size limit]";
        }
        return text.trim();
    }

    public static String fingerprint(String sanitizedLog) {
        String normalized = TIMESTAMP.matcher(sanitizedLog.toLowerCase(Locale.ROOT)).replaceAll("[time]");
        normalized = THREAD_ID.matcher(normalized).replaceAll("thread");
        normalized = normalized.replaceAll("(?m)^\\s*at\\s+.*?\\(([^():]+):(\\d+)\\)\\s*$", "at $1");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }

    private CrashCase updateReview(String id, ReviewStatus status, FailureCategory category) throws IOException {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Case id is required.");
        return withLock(() -> {
            List<CrashCase> cases = new ArrayList<>(readCases());
            for (int i = 0; i < cases.size(); i++) {
                CrashCase existing = cases.get(i);
                if (!existing.id().equals(id)) continue;
                if (existing.status() != ReviewStatus.PENDING) {
                    throw new IllegalStateException("Only pending crash cases can be reviewed.");
                }
                CrashCase reviewed = new CrashCase(existing.id(), existing.createdAtEpochMillis(),
                        existing.fingerprint(), status, existing.proposedCategory(), category,
                        existing.occurrences(), existing.sanitizedLog());
                cases.set(i, reviewed);
                writeCases(cases);
                return reviewed;
            }
            throw new IllegalArgumentException("Crash case does not exist.");
        });
    }

    private List<CrashCase> readCases() throws IOException {
        if (!Files.exists(storeFile)) return List.of();
        long size = Files.size(storeFile);
        if (size > MAX_STORE_BYTES) throw new IOException("Crash case store exceeds its safety limit.");
        if (size == 0) return List.of();
        StoreDocument document = JSON.readValue(Files.readAllBytes(storeFile), StoreDocument.class);
        if (document.schemaVersion() != SCHEMA_VERSION) {
            throw new IOException("Unsupported crash case store schema: " + document.schemaVersion());
        }
        if (document.cases().size() > MAX_CASES) throw new IOException("Crash case store exceeds its record limit.");
        for (CrashCase item : document.cases()) {
            if (item.sanitizedLog().length() > MAX_LOG_CHARS
                    || !item.fingerprint().matches("[a-f0-9]{64}")) {
                throw new IOException("Crash case store contains an invalid record.");
            }
        }
        return document.cases();
    }

    private void writeCases(List<CrashCase> cases) throws IOException {
        byte[] bytes = JSON.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(new StoreDocument(SCHEMA_VERSION, cases));
        if (bytes.length > MAX_STORE_BYTES) throw new IOException("Crash case store reached its size limit.");
        Files.createDirectories(storeFile.getParent());
        atomicWrite(storeFile, bytes);
    }

    private <T> T withLock(IoOperation<T> operation) throws IOException {
        Files.createDirectories(lockFile.getParent());
        try (FileChannel channel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            return operation.run();
        }
    }

    private static void atomicWrite(Path target, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), ".vanta-crash-case-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String oneLine(String text, int maxChars) {
        String value = text.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
                .replaceAll("\\s+", " ").trim();
        return value.length() > maxChars ? value.substring(0, maxChars) : value;
    }

    @FunctionalInterface
    private interface IoOperation<T> {
        T run() throws IOException;
    }
}
