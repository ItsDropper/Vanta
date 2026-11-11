package dev.itsdropper.vanta.launcher.service;

import dev.itsdropper.vanta.launcher.instance.InstalledModManager;
import dev.itsdropper.vanta.launcher.instance.InstalledModRecord;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Instance-scoped transaction boundary for Modrinth mod graph installations.
 *
 * A durable journal is prepared before any managed mod path is changed. If the
 * process exits before the committed marker is atomically published, the next
 * install recovers the snapshot before doing further work.
 */
final class ModrinthGraphInstallTransaction {
    private static final String DIRECTORY_PREFIX = ".vanta-graph-transaction-";
    private static final String JOURNAL_NAME = "journal.properties";
    private static final String OWNER_NAME = "OWNER";
    private static final String OWNER_CONTENT = "VANTA_MODRINTH_GRAPH_TRANSACTION_V1";
    private static final String COMMITTED_NAME = "COMMITTED";
    private static final String ROLLED_BACK_NAME = "ROLLED_BACK";
    private static final Map<Path, LockEntry> INSTANCE_LOCKS = new ConcurrentHashMap<>();
    private static final ThreadLocal<JournalContext> ACTIVE_JOURNAL = new ThreadLocal<>();

    private ModrinthGraphInstallTransaction() {
    }

    @FunctionalInterface
    interface Operation<T> {
        T run() throws IOException, InterruptedException;
    }

    static <T> T execute(Instance instance, Operation<T> operation)
            throws IOException, InterruptedException {
        if (instance == null || instance.getDirectory() == null) {
            throw new IllegalArgumentException("A valid instance is required.");
        }
        if (operation == null) {
            throw new IllegalArgumentException("A graph installation operation is required.");
        }

        Path root = instance.getDirectory().toAbsolutePath().normalize();
        LockEntry entry = INSTANCE_LOCKS.compute(root, (ignored, existing) -> {
            LockEntry selected = existing == null ? new LockEntry() : existing;
            selected.users++;
            return selected;
        });
        entry.lock.lock();
        try {
            Path lockPath = root.resolve(".vanta-mod-install.lock");
            if (Files.isSymbolicLink(lockPath)) {
                throw new IOException("Refusing to use a symbolic-link Modrinth transaction lock.");
            }
            try (FileChannel channel = FileChannel.open(lockPath,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                recoverPendingTransactions(instance, root);
                return executeLocked(instance, operation, root);
            }
        } finally {
            entry.lock.unlock();
            INSTANCE_LOCKS.computeIfPresent(root, (ignored, current) -> {
                current.users--;
                return current.users == 0 ? null : current;
            });
        }
    }

    private static <T> T executeLocked(
            Instance instance, Operation<T> operation, Path root
    ) throws IOException, InterruptedException {
        Path mods = root.resolve("mods").normalize();
        if (!mods.startsWith(root)) {
            throw new IOException("Instance mods directory resolves outside the instance.");
        }
        if (Files.exists(mods, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(mods)) {
            throw new IOException("Refusing to transact through a symbolic-link mods directory.");
        }
        Files.createDirectories(mods);

        Path registry = root.resolve("vanta-mods.json");
        boolean registryExisted = Files.exists(registry, LinkOption.NOFOLLOW_LINKS);
        if (registryExisted && (Files.isSymbolicLink(registry)
                || !Files.isRegularFile(registry, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Refusing to transact with a non-regular installed-mod registry.");
        }

        List<InstalledModRecord> originalRegistry = new ArrayList<>(InstalledModManager.load(instance));
        Path transactionDirectory = Files.createTempDirectory(root, DIRECTORY_PREFIX);
        Path ownerMarker = transactionDirectory.resolve(OWNER_NAME);
        Files.writeString(ownerMarker, OWNER_CONTENT, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        forceFile(ownerMarker);
        Map<Path, Path> backups = new LinkedHashMap<>();
        Properties journal = new Properties();
        journal.setProperty("registryExisted", Boolean.toString(registryExisted));
        Path registryBackup = transactionDirectory.resolve("registry-backup.json");

        Throwable transactionFailure = null;
        boolean preserveJournal = false;
        try {
            if (registryExisted) {
                Files.copy(registry, registryBackup, StandardCopyOption.COPY_ATTRIBUTES);
                forceFile(registryBackup);
                journal.setProperty("registryBackup", registryBackup.getFileName().toString());
            }

            int backupIndex = 0;
            for (InstalledModRecord record : originalRegistry) {
                if (record == null || record.getFilename() == null || record.getFilename().isBlank()) {
                    continue;
                }
                Path tracked = resolveTrackedPath(mods, record.getFilename());
                if (backups.containsKey(tracked) || !Files.exists(tracked, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (Files.isSymbolicLink(tracked)
                        || !Files.isRegularFile(tracked, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Refusing to back up a non-regular tracked mod file: "
                            + tracked.getFileName());
                }
                Path backup = transactionDirectory.resolve("tracked-" + backupIndex++);
                Files.copy(tracked, backup, StandardCopyOption.COPY_ATTRIBUTES);
                forceFile(backup);
                backups.put(tracked, backup);
                journal.setProperty("backup." + (backupIndex - 1) + ".filename",
                        tracked.getFileName().toString());
                journal.setProperty("backup." + (backupIndex - 1) + ".file",
                        backup.getFileName().toString());
            }
            journal.setProperty("backupCount", Integer.toString(backups.size()));
            journal.setProperty("managedCount", "0");
            writeJournal(transactionDirectory, journal);

            JournalContext context = new JournalContext(mods, transactionDirectory, journal);
            ACTIVE_JOURNAL.set(context);
            try {
                T result = operation.run();
                publishCommittedMarker(transactionDirectory);
                return result;
            } catch (IOException | InterruptedException | RuntimeException | Error failure) {
                transactionFailure = failure;
                try {
                    rollbackFromJournal(mods, transactionDirectory, journal, registry);
                    publishStateMarker(transactionDirectory, ROLLED_BACK_NAME);
                } catch (IOException rollbackFailure) {
                    preserveJournal = true;
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            } finally {
                ACTIVE_JOURNAL.remove();
            }
        } catch (IOException | InterruptedException | RuntimeException | Error failure) {
            transactionFailure = failure;
            throw failure;
        } finally {
            if (!preserveJournal) try {
                deleteTree(transactionDirectory);
            } catch (IOException cleanupFailure) {
                if (transactionFailure != null) {
                    transactionFailure.addSuppressed(cleanupFailure);
                } else {
                    System.err.println("Modrinth graph committed, but its journal could not be removed: "
                            + cleanupFailure.getMessage());
                }
            } else {
                System.err.println("Modrinth rollback is incomplete; recovery journal retained at "
                        + transactionDirectory);
            }
        }
    }

    /**
     * Records a path before the installer creates, moves, or replaces it. The
     * intent is durably journaled first, closing the publish-before-registry gap.
     */
    static void registerManagedPath(Instance instance, Path path) throws IOException {
        JournalContext context = ACTIVE_JOURNAL.get();
        if (context == null) {
            throw new IOException("Mod installation attempted outside a graph transaction.");
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(context.modsDirectory)) {
            throw new IOException("Transaction output escapes the instance mods directory.");
        }
        Path filename = normalized.getFileName();
        if (filename == null || filename.toString().isBlank()) {
            throw new IOException("Transaction output has no filename.");
        }
        context.addManagedPath(filename.toString());
    }

    private static void recoverPendingTransactions(Instance instance, Path root) throws IOException {
        try (var entries = Files.list(root)) {
            for (Path directory : entries.filter(path ->
                    path.getFileName().toString().startsWith(DIRECTORY_PREFIX)).toList()) {
                if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Unsafe Modrinth transaction journal entry: " + directory.getFileName());
                }
                Path ownerMarker = directory.resolve(OWNER_NAME);
                if (!Files.isRegularFile(ownerMarker, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(ownerMarker)
                        || !OWNER_CONTENT.equals(Files.readString(ownerMarker))) {
                    // Never delete an arbitrary user directory merely because its name
                    // resembles one of our transaction directories.
                    continue;
                }
                if (Files.exists(directory.resolve(COMMITTED_NAME), LinkOption.NOFOLLOW_LINKS)
                        || Files.exists(directory.resolve(ROLLED_BACK_NAME), LinkOption.NOFOLLOW_LINKS)) {
                    deleteTree(directory);
                    continue;
                }
                Path journalPath = directory.resolve(JOURNAL_NAME);
                if (!Files.isRegularFile(journalPath, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(journalPath)) {
                    // A journal is published only after every backup is complete and before
                    // the operation starts. An unprepared directory is safe to discard.
                    deleteTree(directory);
                    continue;
                }
                Properties journal = readJournal(journalPath);
                Path mods = root.resolve("mods").normalize();
                rollbackFromJournal(mods, directory, journal, root.resolve("vanta-mods.json"));
                publishStateMarker(directory, ROLLED_BACK_NAME);
                deleteTree(directory);
            }
        }
    }

    private static void rollbackFromJournal(
            Path mods, Path transactionDirectory, Properties journal,
            Path registry
    ) throws IOException {
            int managedCount = parseCount(journal, "managedCount");
            Set<String> originalNames = new HashSet<>();
            int backupCount = parseCount(journal, "backupCount");
            for (int i = 0; i < backupCount; i++) {
                String filename = required(journal, "backup." + i + ".filename");
                resolveTrackedPath(mods, filename);
                originalNames.add(filename);
            }

            for (int i = 0; i < managedCount; i++) {
                String filename = required(journal, "managed." + i);
                Path managed = resolveTrackedPath(mods, filename);
                if (!originalNames.contains(filename) && Files.exists(managed, LinkOption.NOFOLLOW_LINKS)) {
                    if (Files.isSymbolicLink(managed)
                            || !Files.isRegularFile(managed, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Refusing to remove a non-regular transaction output: " + filename);
                    }
                    Files.delete(managed);
                }
            }

            for (int i = 0; i < backupCount; i++) {
                String filename = required(journal, "backup." + i + ".filename");
                String backupName = required(journal, "backup." + i + ".file");
                Path backup = transactionDirectory.resolve(backupName).normalize();
                if (!backup.startsWith(transactionDirectory)
                        || !Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(backup)) {
                    throw new IOException("Transaction backup is missing or unsafe: " + backupName);
                }
                Path destination = resolveTrackedPath(mods, filename);
                if (Files.isSymbolicLink(destination)) {
                    throw new IOException("Tracked mod path became a symbolic link during rollback: " + filename);
                }
                Files.copy(backup, destination, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
            }

            if (Boolean.parseBoolean(required(journal, "registryExisted"))) {
                String backupName = required(journal, "registryBackup");
                Path backup = transactionDirectory.resolve(backupName).normalize();
                if (!backup.startsWith(transactionDirectory)
                        || !Files.isRegularFile(backup, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(backup) || Files.isSymbolicLink(registry)) {
                    throw new IOException("The original installed-mod registry cannot be safely restored.");
                }
                Files.copy(backup, registry, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
            } else {
                if (Files.isSymbolicLink(registry)) {
                    throw new IOException("Refusing to remove a symbolic-link installed-mod registry.");
                }
                Files.deleteIfExists(registry);
            }
        } catch (IOException failure) {
            throw failure;
        }
    }

    private static void writeJournal(Path directory, Properties journal) throws IOException {
        Path temporary = directory.resolve(JOURNAL_NAME + ".tmp");
        try (var output = Files.newOutputStream(temporary,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            journal.store(output, "Vanta Modrinth graph transaction; do not edit");
        }
        forceFile(temporary);
        moveReplace(temporary, directory.resolve(JOURNAL_NAME));
    }

    private static Properties readJournal(Path path) throws IOException {
        Properties journal = new Properties();
        try (var input = Files.newInputStream(path)) {
            journal.load(input);
        }
        required(journal, "registryExisted");
        parseCount(journal, "backupCount");
        parseCount(journal, "managedCount");
        return journal;
    }

    private static void publishCommittedMarker(Path directory) throws IOException {
        publishStateMarker(directory, COMMITTED_NAME);
    }

    private static void publishStateMarker(Path directory, String markerName) throws IOException {
        Path temporary = directory.resolve(markerName + ".tmp");
        try (FileChannel channel = FileChannel.open(temporary,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            channel.write(java.nio.ByteBuffer.wrap(markerName.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            channel.force(true);
        }
        moveReplace(temporary, directory.resolve(markerName));
    }

    private static void moveReplace(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void forceFile(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static int parseCount(Properties journal, String key) throws IOException {
        String value = required(journal, key);
        try {
            int count = Integer.parseInt(value);
            if (count < 0 || count > 100_000) throw new NumberFormatException("out of range");
            return count;
        } catch (NumberFormatException invalid) {
            throw new IOException("Invalid transaction journal count: " + key, invalid);
        }
    }

    private static String required(Properties journal, String key) throws IOException {
        String value = journal.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IOException("Transaction journal is incomplete: " + key);
        }
        return value;
    }

    private static Path resolveTrackedPath(Path mods, String filename) throws IOException {
        if (filename.indexOf('/') >= 0 || filename.indexOf('\\') >= 0) {
            throw new IOException("Installed-mod registry must contain a filename, not a path: " + filename);
        }
        Path supplied = Path.of(filename);
        if (supplied.isAbsolute() || supplied.getNameCount() != 1
                || ".".equals(filename) || "..".equals(filename)) {
            throw new IOException("Installed-mod registry contains an unsafe filename: " + filename);
        }
        Path resolved = mods.resolve(supplied).normalize();
        if (!resolved.startsWith(mods) || resolved.equals(mods)) {
            throw new IOException("Installed-mod registry contains an unsafe filename: " + filename);
        }
        return resolved;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted((left, right) ->
                    right.getNameCount() - left.getNameCount()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static final class JournalContext {
        private final Path modsDirectory;
        private final Path directory;
        private final Properties journal;

        private JournalContext(Path modsDirectory, Path directory, Properties journal) {
            this.modsDirectory = modsDirectory;
            this.directory = directory;
            this.journal = journal;
        }

        private synchronized void addManagedPath(String filename) throws IOException {
            int count = parseCount(journal, "managedCount");
            for (int i = 0; i < count; i++) {
                if (filename.equals(journal.getProperty("managed." + i))) return;
            }
            journal.setProperty("managed." + count, filename);
            journal.setProperty("managedCount", Integer.toString(count + 1));
            writeJournal(directory, journal);
        }
    }

    private static final class LockEntry {
        private final ReentrantLock lock = new ReentrantLock();
        private int users;
    }
}
