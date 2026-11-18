package dev.itsdropper.vanta.launcher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.repair.CrashCaseRepository;
import dev.itsdropper.vanta.launcher.repair.FailureCategory;
import dev.itsdropper.vanta.launcher.repair.RepairDiagnosis;
import dev.itsdropper.vanta.launcher.repair.RepairDiagnosticEngine;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Durable, local-only launch timeline. Evidence is sanitized before it reaches
 * disk; this repository never performs network I/O or stores account/session data.
 */
public final class LaunchHistoryRepository {
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_RECORDS = 200;
    private static final int MAX_EVENTS_PER_RECORD = 24;
    private static final int MAX_EVIDENCE_CHARS = 48 * 1024;
    private static final long MAX_STORE_BYTES = 16L * 1024 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ConcurrentHashMap<Path, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();

    private static final Pattern PRIVATE_SESSION_LINE = Pattern.compile(
            "(?im)^.*(?:connecting to|joining|joined|server address|server ip|opening level|"
                    + "saving chunks|saving world|integrated server|loaded world|world directory).*$");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");

    public enum Outcome {
        PREPARING, STARTING, RUNNING, STOPPING, SUCCEEDED, FAILED, STOPPED,
        INSTANCE_DUPLICATED, VERSION_CHANGED, LOADER_CHANGED, MOD_INSTALLED, MOD_REMOVED, MOD_UPDATED,
        BACKUP_CREATED, BACKUP_RESTORED, RECOVERY_CREATED, RECOVERY_RESTORED, REPAIRED
    }

    public record Event(long timestampEpochMillis, Outcome outcome, String message) {
        public Event {
            Objects.requireNonNull(outcome, "outcome");
            message = safeText(message, 180);
        }
    }

    public record LaunchRecord(
            String id,
            String instanceId,
            String instanceName,
            String minecraftVersion,
            String loader,
            String loaderVersion,
            long startedAtEpochMillis,
            long finishedAtEpochMillis,
            Outcome outcome,
            Integer exitCode,
            String failureTitle,
            FailureCategory failureCategory,
            String fingerprint,
            String sanitizedLog,
            List<Event> events
    ) {
        public LaunchRecord {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(instanceId, "instanceId");
            Objects.requireNonNull(outcome, "outcome");
            instanceName = safeText(instanceName, 120);
            minecraftVersion = safeText(minecraftVersion, 40);
            loader = safeText(loader, 48);
            loaderVersion = safeText(loaderVersion, 48);
            failureTitle = safeText(failureTitle, 180);
            fingerprint = fingerprint == null ? "" : fingerprint;
            sanitizedLog = sanitizedLog == null ? "" : sanitizedLog;
            events = events == null ? List.of() : List.copyOf(events);
            if (sanitizedLog.length() > MAX_EVIDENCE_CHARS) {
                throw new IllegalArgumentException("Launch evidence exceeds its storage limit.");
            }
            if (events.size() > MAX_EVENTS_PER_RECORD) {
                throw new IllegalArgumentException("Launch event count exceeds its limit.");
            }
        }

        public long durationMillis() {
            long end = finishedAtEpochMillis > 0 ? finishedAtEpochMillis : System.currentTimeMillis();
            return Math.max(0, end - startedAtEpochMillis);
        }

        public boolean terminal() {
            return outcome != Outcome.PREPARING && outcome != Outcome.STARTING
                    && outcome != Outcome.RUNNING && outcome != Outcome.STOPPING;
        }
    }

    private record StoreDocument(int schemaVersion, List<LaunchRecord> launches) {
        private StoreDocument {
            launches = List.copyOf(launches);
        }
    }

    @FunctionalInterface
    private interface IoOperation<T> {
        T run() throws IOException;
    }

    private final Path storeFile;
    private final Path lockFile;

    public LaunchHistoryRepository() {
        this(dev.itsdropper.vanta.launcher.MinecraftLocator.getVantaDirectoryPath()
                .resolve("launch-history").resolve("history.json"));
    }

    public LaunchHistoryRepository(Path storeFile) {
        this.storeFile = Objects.requireNonNull(storeFile, "storeFile").toAbsolutePath().normalize();
        this.lockFile = this.storeFile.resolveSibling(this.storeFile.getFileName() + ".lock");
    }

    public String begin(Instance instance) throws IOException {
        Objects.requireNonNull(instance, "instance");
        if (instance.getId() == null || instance.getId().isBlank()) {
            throw new IllegalArgumentException("Instance id is required for launch history.");
        }
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        LaunchRecord record = new LaunchRecord(
                id,
                safeText(instance.getId(), 128),
                instance.getName(),
                instance.getMinecraftVersion(),
                instance.getLoader(),
                instance.getLoaderVersion(),
                now,
                0,
                Outcome.PREPARING,
                null,
                "",
                null,
                "",
                "",
                List.of(new Event(now, Outcome.PREPARING, "Launch requested"))
        );
        withLock(() -> {
            List<LaunchRecord> records = new ArrayList<>(readRecords());
            if (records.size() >= MAX_RECORDS) {
                int removable = -1;
                for (int i = 0; i < records.size(); i++) {
                    if (records.get(i).terminal()
                            && (removable < 0 || records.get(i).startedAtEpochMillis()
                            < records.get(removable).startedAtEpochMillis())) {
                        removable = i;
                    }
                }
                if (removable < 0) {
                    throw new IOException("Launch history is full and contains no completed record safe to rotate.");
                }
                records.remove(removable);
            }
            records.add(record);
            writeRecords(records);
            return null;
        });
        return id;
    }

    public LaunchRecord transition(String id, Outcome outcome, String message) throws IOException {
        if (outcome != Outcome.PREPARING && outcome != Outcome.STARTING
                && outcome != Outcome.RUNNING && outcome != Outcome.STOPPING) {
            throw new IllegalArgumentException("A non-terminal launch lifecycle state is required.");
        }
        return update(id, outcome, message, null, null, false);
    }

    public LaunchRecord finish(
            String id,
            Outcome outcome,
            String message,
            String diagnosticText,
            Integer exitCode
    ) throws IOException {
        if (outcome != Outcome.SUCCEEDED && outcome != Outcome.FAILED && outcome != Outcome.STOPPED) {
            throw new IllegalArgumentException("A terminal launch outcome is required.");
        }
        return update(id, outcome, message, diagnosticText, exitCode, true);
    }

    /** Records a completed instance operation without attaching launch diagnostics. */
    public LaunchRecord recordActivity(Instance instance, Outcome outcome, String message) throws IOException {
        Objects.requireNonNull(instance, "instance");
        if (instance.getId() == null || instance.getId().isBlank()) {
            throw new IllegalArgumentException("Instance id is required for timeline activity.");
        }
        if (outcome == null || !isActivityOutcome(outcome)) {
            throw new IllegalArgumentException("A supported instance activity outcome is required.");
        }
        long now = System.currentTimeMillis();
        String safeMessage = safeText(message, 180);
        LaunchRecord activity = new LaunchRecord(
                UUID.randomUUID().toString(),
                safeText(instance.getId(), 128),
                instance.getName(),
                instance.getMinecraftVersion(),
                instance.getLoader(),
                instance.getLoaderVersion(),
                now,
                now,
                outcome,
                null,
                "",
                null,
                "",
                "",
                List.of(new Event(now, outcome, safeMessage))
        );
        withLock(() -> {
            List<LaunchRecord> records = new ArrayList<>(readRecords());
            if (records.size() >= MAX_RECORDS) {
                int removable = -1;
                for (int i = 0; i < records.size(); i++) {
                    if (records.get(i).terminal()
                            && (removable < 0 || records.get(i).startedAtEpochMillis()
                            < records.get(removable).startedAtEpochMillis())) {
                        removable = i;
                    }
                }
                if (removable < 0) {
                    throw new IOException("Launch history is full and contains no completed record safe to rotate.");
                }
                records.remove(removable);
            }
            records.add(activity);
            writeRecords(records);
            return null;
        });
        return activity;
    }

    private static boolean isActivityOutcome(Outcome outcome) {
        return switch (outcome) {
            case INSTANCE_DUPLICATED, VERSION_CHANGED, LOADER_CHANGED, MOD_INSTALLED, MOD_REMOVED, MOD_UPDATED,
                    BACKUP_CREATED, BACKUP_RESTORED, RECOVERY_CREATED, RECOVERY_RESTORED, REPAIRED -> true;
            default -> false;
        };
    }

    public List<LaunchRecord> list() throws IOException {
        return withLock(() -> readRecords().stream()
                .sorted(Comparator.comparingLong(LaunchRecord::startedAtEpochMillis).reversed())
                .toList());
    }

    public LaunchRecord find(String id) throws IOException {
        if (id == null || id.isBlank()) return null;
        return withLock(() -> readRecords().stream()
                .filter(record -> record.id().equals(id))
                .findFirst().orElse(null));
    }

    private LaunchRecord update(
            String id,
            Outcome outcome,
            String message,
            String diagnosticText,
            Integer exitCode,
            boolean terminal
    ) throws IOException {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Launch id is required.");
        Objects.requireNonNull(outcome, "outcome");
        return withLock(() -> {
            List<LaunchRecord> records = new ArrayList<>(readRecords());
            for (int i = 0; i < records.size(); i++) {
                LaunchRecord current = records.get(i);
                if (!current.id().equals(id)) continue;
                if (current.terminal()) {
                    if (terminal && current.outcome() == outcome) return current;
                    throw new IllegalStateException("A completed launch record cannot be changed.");
                }

                long now = System.currentTimeMillis();
                List<Event> events = new ArrayList<>(current.events());
                events.add(new Event(now, outcome, message));
                if (events.size() > MAX_EVENTS_PER_RECORD) {
                    events = new ArrayList<>(events.subList(events.size() - MAX_EVENTS_PER_RECORD,
                            events.size()));
                }

                String evidence = diagnosticText == null
                        ? current.sanitizedLog()
                        : sanitizeEvidence(diagnosticText);
                List<RepairDiagnosis> diagnoses = outcome == Outcome.FAILED && !evidence.isBlank()
                        ? RepairDiagnosticEngine.diagnose(evidence) : List.of();
                FailureCategory category = outcome == Outcome.FAILED
                        ? diagnoses.stream()
                                .map(RepairDiagnosis::category)
                                .filter(value -> value != null && value != FailureCategory.UNKNOWN)
                                .findFirst().orElse(current.failureCategory())
                        : current.failureCategory();
                String fingerprint = evidence.isBlank() ? current.fingerprint() : fingerprint(evidence);
                String title = outcome == Outcome.FAILED
                        ? safeText(message, 180) : current.failureTitle();

                LaunchRecord updated = new LaunchRecord(
                        current.id(), current.instanceId(), current.instanceName(),
                        current.minecraftVersion(), current.loader(), current.loaderVersion(),
                        current.startedAtEpochMillis(), terminal ? now : 0,
                        outcome, exitCode, title, category, fingerprint, evidence, events
                );
                records.set(i, updated);
                writeRecords(records);
                return updated;
            }
            throw new IllegalArgumentException("Launch history record does not exist.");
        });
    }

    public static String sanitizeEvidence(String input) {
        if (input == null || input.isBlank()) return "";
        String safe = CrashCaseRepository.sanitize(input);
        safe = PRIVATE_SESSION_LINE.matcher(safe).replaceAll("[REDACTED_SESSION_DETAIL]");
        safe = CONTROL_CHARS.matcher(safe).replaceAll("");
        if (safe.length() > MAX_EVIDENCE_CHARS) {
            safe = safe.substring(0, MAX_EVIDENCE_CHARS) + "\n[TRUNCATED: launch-history evidence limit]";
        }
        return safe.trim();
    }

    private static String safeText(String input, int limit) {
        if (input == null || input.isBlank()) return "";
        String safe = CrashCaseRepository.sanitize(input);
        safe = CONTROL_CHARS.matcher(safe).replaceAll(" ");
        safe = safe.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return safe.length() > limit ? safe.substring(0, limit) : safe;
    }

    private static String fingerprint(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }

    private List<LaunchRecord> readRecords() throws IOException {
        if (Files.isSymbolicLink(storeFile)) throw new IOException("Launch history cannot be a symbolic link.");
        if (!Files.exists(storeFile, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (!Files.isRegularFile(storeFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Launch history path is not a regular file.");
        }
        long size = Files.size(storeFile);
        if (size > MAX_STORE_BYTES) throw new IOException("Launch history exceeds its storage limit.");
        if (size == 0) return List.of();
        byte[] bytes;
        try (var input = Files.newInputStream(storeFile)) {
            bytes = input.readNBytes((int) MAX_STORE_BYTES + 1);
        }
        if (bytes.length > MAX_STORE_BYTES) throw new IOException("Launch history exceeds its storage limit.");
        StoreDocument document = JSON.readValue(bytes, StoreDocument.class);
        if (document.schemaVersion() != SCHEMA_VERSION) {
            throw new IOException("Unsupported launch history schema: " + document.schemaVersion());
        }
        if (document.launches().size() > MAX_RECORDS) {
            throw new IOException("Launch history contains too many records.");
        }
        for (LaunchRecord record : document.launches()) {
            if (record.id().isBlank() || record.instanceId().isBlank()
                    || record.sanitizedLog().length() > MAX_EVIDENCE_CHARS
                    || record.events().size() > MAX_EVENTS_PER_RECORD
                    || (!record.fingerprint().isBlank() && !record.fingerprint().matches("[a-f0-9]{64}"))) {
                throw new IOException("Launch history contains an invalid record.");
            }
        }
        return document.launches();
    }

    private void writeRecords(List<LaunchRecord> records) throws IOException {
        if (Files.isSymbolicLink(storeFile)) throw new IOException("Launch history cannot be a symbolic link.");
        Files.createDirectories(storeFile.getParent());
        restrictOwnerOnly(storeFile.getParent(), true);
        byte[] bytes = JSON.writerWithDefaultPrettyPrinter()
                .writeValueAsBytes(new StoreDocument(SCHEMA_VERSION, records));
        if (bytes.length > MAX_STORE_BYTES) throw new IOException("Launch history reached its storage limit.");
        atomicWrite(storeFile, bytes);
    }

    private <T> T withLock(IoOperation<T> operation) throws IOException {
        Files.createDirectories(lockFile.getParent());
        if (Files.isSymbolicLink(lockFile.getParent()) || Files.isSymbolicLink(lockFile)) {
            throw new IOException("Launch history storage path cannot contain symbolic links.");
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
        Path temporary = Files.createTempFile(target.getParent(), ".vanta-launch-history-", ".tmp");
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
            // Windows uses inherited NTFS ACLs from the Vanta data directory.
        }
    }
}
