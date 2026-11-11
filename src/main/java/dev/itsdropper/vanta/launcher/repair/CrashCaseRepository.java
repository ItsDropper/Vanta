package dev.itsdropper.vanta.launcher.repair;

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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Privacy-first, local-only crash case inbox.
 *
 * Nothing in this class performs network I/O. Logs are sanitized before they
 * are persisted; only explicitly confirmed cases can be exported as training
 * examples. Raw source files are never modified or deleted.
 */
public final class CrashCaseRepository {
    private static final int SCHEMA_VERSION = 2;
    private static final int MAX_CASES = 256;
    private static final int MAX_LOG_CHARS = 96 * 1024;
    private static final long MAX_STORE_BYTES = 32L * 1024 * 1024;
    private static final long MAX_IMPORT_BYTES = 2L * 1024 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ConcurrentHashMap<Path, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();

    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]+=*");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(\\b(?:access[_-]?token|refresh[_-]?token|\\btoken|client[_-]?secret|password|passwd|session[_-]?id|"
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
    private static final Pattern IPV6 = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])(?=[0-9a-f:]*[a-f])(?:[0-9a-f]{0,4}:){2,7}[0-9a-f]{0,4}(?![A-Za-z0-9])");
    private static final Pattern MAC_ADDRESS = Pattern.compile(
            "(?i)\\b(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}\\b");
    private static final Pattern WINDOWS_PATH = Pattern.compile(
            "(?i)\\b[A-Z]:\\\\(?:[^\\r\\n\\t\\\"<>|]+)");
    private static final Pattern UNIX_HOME = Pattern.compile(
            "(?<![A-Za-z0-9])/(?:home|Users|mnt|private/var)/[^\\r\\n\\t]+");
    private static final Pattern USER_ASSIGNMENT = Pattern.compile(
            "(?i)(\\b(?:username|user\\.name|player[_ -]?name)\\b\\s*[:=]\\s*)[^\\s,;]+");
    private static final Pattern PRIVATE_LINE = Pattern.compile(
            "(?im)^\\s*(?:username|player name|player uuid|user uuid|access token|refresh token|"
                    + "server address|server ip|connected to|user home|user\\.home)\\s*[:=].*$");
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
            String sanitizedLog,
            String verifiedFix
    ) {
        public CrashCase {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(fingerprint, "fingerprint");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(sanitizedLog, "sanitizedLog");
            verifiedFix = verifiedFix == null ? "" : verifiedFix;
            if (verifiedFix.length() > 2_000) throw new IllegalArgumentException("Verified fix notes exceed 2,000 characters.");
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

    /**
     * Returns manually confirmed fix notes for the requested failure family.
     * Results are references only; callers must not execute them automatically.
     */
    public List<VerifiedFix> findVerifiedFixes(FailureCategory category, int limit) throws IOException {
        if (category == null || category == FailureCategory.UNKNOWN || limit <= 0) return List.of();
        int boundedLimit = Math.min(limit, 25);
        return listCases().stream()
                .filter(item -> item.status() == ReviewStatus.CONFIRMED
                        && item.confirmedCategory() == category
                        && !item.verifiedFix().isBlank())
                .sorted(Comparator.comparingInt(CrashCase::occurrences).reversed()
                        .thenComparing(CrashCase::fingerprint))
                .limit(boundedLimit)
                .map(item -> new VerifiedFix(item.fingerprint(), item.confirmedCategory(),
                        item.occurrences(), item.verifiedFix(), item.sanitizedLog()))
                .toList();
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
                            existing.sanitizedLog(), existing.verifiedFix());
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
                    proposedCategory, null, 1, sanitized, "");
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
        byte[] bytes;
        try (var input = Files.newInputStream(normalized)) {
            bytes = input.readNBytes((int) MAX_IMPORT_BYTES + 1);
        }
        if (bytes.length > MAX_IMPORT_BYTES) {
            throw new IOException("Crash log exceeded the 2 MiB import limit while being read.");
        }
        String log = new String(bytes, StandardCharsets.UTF_8);
        FailureCategory proposed = RepairDiagnosticEngine.diagnose(log).stream()
                .map(RepairDiagnosis::category)
                .filter(category -> category != FailureCategory.UNKNOWN)
                .findFirst().orElse(null);
        return recordForReview(log, proposed);
    }

    public CrashCase confirm(String id, FailureCategory category) throws IOException {
        return confirm(id, category, "");
    }

    public CrashCase confirm(String id, FailureCategory category, String verifiedFix) throws IOException {
        Objects.requireNonNull(category, "category");
        if (category == FailureCategory.UNKNOWN) throw new IllegalArgumentException("UNKNOWN is not a training label.");
        String safeFix = sanitize(verifiedFix);
        if (safeFix.length() > 2_000) safeFix = safeFix.substring(0, 2_000);
        return updateReview(id, ReviewStatus.CONFIRMED, category, safeFix);
    }

    public CrashCase reject(String id) throws IOException {
        return updateReview(id, ReviewStatus.REJECTED, null, "");
    }

    public CrashCase correctConfirmed(String id, FailureCategory category, String verifiedFix) throws IOException {
        Objects.requireNonNull(category, "category");
        if (category == FailureCategory.UNKNOWN) throw new IllegalArgumentException("UNKNOWN is not a training label.");
        String safeFix = sanitize(verifiedFix);
        if (safeFix.length() > 2_000) safeFix = safeFix.substring(0, 2_000);
        final String correctedFix = safeFix;
        return withLock(() -> {
            List<CrashCase> cases = new ArrayList<>(readCases());
            for (int i = 0; i < cases.size(); i++) {
                CrashCase existing = cases.get(i);
                if (!existing.id().equals(id)) continue;
                if (existing.status() != ReviewStatus.CONFIRMED) {
                    throw new IllegalStateException("Only confirmed crash cases can have their labels corrected.");
                }
                CrashCase corrected = new CrashCase(existing.id(), existing.createdAtEpochMillis(),
                        existing.fingerprint(), ReviewStatus.CONFIRMED, existing.proposedCategory(),
                        category, existing.occurrences(), existing.sanitizedLog(), correctedFix);
                cases.set(i, corrected);
                writeCases(cases);
                return corrected;
            }
            throw new IllegalArgumentException("Crash case does not exist.");
        });
    }

    public CrashCase reopenRejected(String id) throws IOException {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Case id is required.");
        return withLock(() -> {
            List<CrashCase> cases = new ArrayList<>(readCases());
            for (int i = 0; i < cases.size(); i++) {
                CrashCase existing = cases.get(i);
                if (!existing.id().equals(id)) continue;
                if (existing.status() != ReviewStatus.REJECTED) {
                    throw new IllegalStateException("Only rejected crash cases can be reopened.");
                }
                CrashCase reopened = new CrashCase(existing.id(), existing.createdAtEpochMillis(),
                        existing.fingerprint(), ReviewStatus.PENDING, existing.proposedCategory(),
                        null, existing.occurrences(), existing.sanitizedLog(), "");
                cases.set(i, reopened);
                writeCases(cases);
                return reopened;
            }
            throw new IllegalArgumentException("Crash case does not exist.");
        });
    }

    public int exportConfirmedCorpus(Path destination) throws IOException {
        Objects.requireNonNull(destination, "destination");
        Path target = destination.toAbsolutePath().normalize();
        if (target.equals(storeFile) || target.equals(lockFile)) {
            throw new IOException("Training export cannot overwrite the local crash case store.");
        }
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
        String bounded = source;
        if (bounded.length() > MAX_LOG_CHARS) {
            String marker = "\n[TRUNCATED MIDDLE: local crash-case size limit]\n";
            int contentBudget = MAX_LOG_CHARS - marker.length();
            int headLength = contentBudget * 2 / 3;
            int tailLength = contentBudget - headLength;
            bounded = bounded.substring(0, headLength)
                    + marker
                    + bounded.substring(bounded.length() - tailLength);
        }
        String text = bounded.replace("\r\n", "\n").replace('\r', '\n');
        text = BEARER.matcher(text).replaceAll("Bearer [REDACTED]");
        text = SECRET_ASSIGNMENT.matcher(text).replaceAll("$1[REDACTED]");
        text = USER_ASSIGNMENT.matcher(text).replaceAll("$1[REDACTED]");
        text = CLI_SECRET.matcher(text).replaceAll("$1[REDACTED]");
        text = QUERY_SECRET.matcher(text).replaceAll("$1[REDACTED]");
        text = PRIVATE_LINE.matcher(text).replaceAll("[REDACTED_PRIVATE_FIELD]");
        text = UUID_VALUE.matcher(text).replaceAll("[REDACTED_UUID]");
        text = IPV4.matcher(text).replaceAll("[REDACTED_IP]");
        text = IPV6.matcher(text).replaceAll("[REDACTED_IP]");
        text = MAC_ADDRESS.matcher(text).replaceAll("[REDACTED_MAC]");
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

    public int exportVerifiedFixes(Path destination) throws IOException {
        Objects.requireNonNull(destination, "destination");
        Path target = destination.toAbsolutePath().normalize();
        if (target.equals(storeFile) || target.equals(lockFile)) {
            throw new IOException("Fix export cannot overwrite the local crash case store.");
        }
        return withLock(() -> {
            List<VerifiedFix> fixes = readCases().stream()
                    .filter(item -> item.status() == ReviewStatus.CONFIRMED && !item.verifiedFix().isBlank())
                    .sorted(Comparator.comparing(CrashCase::fingerprint))
                    .map(item -> new VerifiedFix(item.fingerprint(), item.confirmedCategory(),
                            item.occurrences(), item.verifiedFix(), item.sanitizedLog()))
                    .toList();
            if (fixes.isEmpty()) throw new IOException("There are no confirmed fix notes to export.");
            Files.createDirectories(target.getParent());
            atomicWrite(target, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(fixes));
            return fixes.size();
        });
    }

    public record VerifiedFix(
            String fingerprint,
            FailureCategory category,
            int occurrences,
            String verifiedFix,
            String sanitizedLog
    ) { }

    private CrashCase updateReview(String id, ReviewStatus status, FailureCategory category,
                                   String verifiedFix) throws IOException {
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
                        existing.occurrences(), existing.sanitizedLog(), verifiedFix);
                cases.set(i, reviewed);
                writeCases(cases);
                return reviewed;
            }
            throw new IllegalArgumentException("Crash case does not exist.");
        });
    }

    private List<CrashCase> readCases() throws IOException {
        if (Files.isSymbolicLink(storeFile)) throw new IOException("Crash case store cannot be a symbolic link.");
        if (!Files.exists(storeFile)) return List.of();
        long size = Files.size(storeFile);
        if (size > MAX_STORE_BYTES) throw new IOException("Crash case store exceeds its safety limit.");
        if (size == 0) return List.of();
        byte[] stored;
        try (var input = Files.newInputStream(storeFile)) {
            stored = input.readNBytes((int) MAX_STORE_BYTES + 1);
        }
        if (stored.length > MAX_STORE_BYTES) throw new IOException("Crash case store exceeds its safety limit.");
        StoreDocument document = JSON.readValue(stored, StoreDocument.class);
        if (document.schemaVersion() < 1 || document.schemaVersion() > SCHEMA_VERSION) {
            throw new IOException("Unsupported crash case store schema: " + document.schemaVersion());
        }
        if (document.cases().size() > MAX_CASES) throw new IOException("Crash case store exceeds its record limit.");
        for (CrashCase item : document.cases()) {
            if (item.sanitizedLog().length() > MAX_LOG_CHARS || item.verifiedFix().length() > 2_000
                    || !item.fingerprint().matches("[a-f0-9]{64}")) {
                throw new IOException("Crash case store contains an invalid record.");
            }
        }
        return document.cases();
    }

    private void writeCases(List<CrashCase> cases) throws IOException {
        if (Files.isSymbolicLink(storeFile)) throw new IOException("Crash case store cannot be a symbolic link.");
        Files.createDirectories(storeFile.getParent());
        restrictOwnerOnly(storeFile.getParent(), true);
        byte[] bytes = JSON.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(new StoreDocument(SCHEMA_VERSION, cases));
        if (bytes.length > MAX_STORE_BYTES) throw new IOException("Crash case store reached its size limit.");
        atomicWrite(storeFile, bytes);
    }

    private <T> T withLock(IoOperation<T> operation) throws IOException {
        Files.createDirectories(lockFile.getParent());
        if (Files.isSymbolicLink(lockFile.getParent()) || Files.isSymbolicLink(lockFile)) {
            throw new IOException("Crash case storage path cannot contain symbolic links.");
        }
        restrictOwnerOnly(lockFile.getParent(), true);
        ReentrantLock processLock = JVM_LOCKS.computeIfAbsent(lockFile, ignored -> new ReentrantLock());
        processLock.lock();
        try (FileChannel channel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            restrictOwnerOnly(lockFile, false);
            return operation.run();
        } finally {
            processLock.unlock();
        }
    }

    private static void atomicWrite(Path target, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(target.getParent(), ".vanta-crash-case-", ".tmp");
        try {
            restrictOwnerOnly(temporary, false);
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

    private static void restrictOwnerOnly(Path path, boolean directory) throws IOException {
        Set<java.nio.file.attribute.PosixFilePermission> permissions = directory
                ? Set.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                        java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE)
                : Set.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException unsupported) {
            // Windows ACLs inherit from the user's Vanta data directory.
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
