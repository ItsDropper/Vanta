package dev.itsdropper.vanta.launcher.service;

import dev.itsdropper.vanta.launcher.instance.InstalledModManager;
import dev.itsdropper.vanta.launcher.instance.InstalledModRecord;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Provides an instance-scoped rollback boundary around a multi-mod installation.
 *
 * Individual downloads retain their existing staging/verification logic. This
 * layer snapshots Vanta-managed files and the installed-mod registry so a later
 * failure cannot leave earlier graph entries committed.
 */
final class ModrinthGraphInstallTransaction {
    private static final Map<Path, ReentrantLock> INSTANCE_LOCKS = new ConcurrentHashMap<>();

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

        Path instanceDirectory = instance.getDirectory().toAbsolutePath().normalize();
        ReentrantLock instanceLock = INSTANCE_LOCKS.computeIfAbsent(
                instanceDirectory, ignored -> new ReentrantLock());
        instanceLock.lock();
        try {
            return executeLocked(instance, operation, instanceDirectory);
        } finally {
            instanceLock.unlock();
        }
    }

    private static <T> T executeLocked(
            Instance instance, Operation<T> operation, Path instanceDirectory
    ) throws IOException, InterruptedException {
        Path modsDirectory = instanceDirectory.resolve("mods").normalize();
        if (!modsDirectory.startsWith(instanceDirectory)) {
            throw new IOException("Instance mods directory resolves outside the instance.");
        }

        List<InstalledModRecord> originalRegistry = new ArrayList<>(InstalledModManager.load(instance));
        Path transactionDirectory = Files.createTempDirectory(
                instanceDirectory, ".vanta-graph-transaction-" + UUID.randomUUID() + "-");
        Map<Path, Path> backups = new LinkedHashMap<>();
        Set<Path> originalTrackedPaths = new HashSet<>();

        Throwable transactionFailure = null;
        try {
            if (Files.exists(modsDirectory, LinkOption.NOFOLLOW_LINKS)
                    && Files.isSymbolicLink(modsDirectory)) {
                throw new IOException("Refusing to transact through a symbolic-link mods directory.");
            }

            int backupIndex = 0;
            for (InstalledModRecord record : originalRegistry) {
                if (record == null || record.getFilename() == null || record.getFilename().isBlank()) {
                    continue;
                }
                Path trackedFile = resolveTrackedPath(modsDirectory, record.getFilename());
                if (!originalTrackedPaths.add(trackedFile)) {
                    continue;
                }
                if (!Files.exists(trackedFile, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                if (Files.isSymbolicLink(trackedFile)
                        || !Files.isRegularFile(trackedFile, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Refusing to back up a non-regular tracked mod file: "
                            + trackedFile.getFileName());
                }
                Path backup = transactionDirectory.resolve("tracked-" + backupIndex++);
                Files.copy(trackedFile, backup, StandardCopyOption.COPY_ATTRIBUTES);
                backups.put(trackedFile, backup);
            }

            try {
                return operation.run();
            } catch (IOException | InterruptedException | RuntimeException | Error failure) {
                transactionFailure = failure;
                rollback(instance, modsDirectory, originalRegistry, backups, failure);
                throw failure;
            }
        } catch (IOException | InterruptedException | RuntimeException | Error failure) {
            transactionFailure = failure;
            throw failure;
        } finally {
            try {
                deleteTree(transactionDirectory);
            } catch (IOException cleanupFailure) {
                if (transactionFailure != null) {
                    transactionFailure.addSuppressed(cleanupFailure);
                } else {
                    throw cleanupFailure;
                }
            }
        }
    }

    private static void rollback(
            Instance instance,
            Path modsDirectory,
            List<InstalledModRecord> originalRegistry,
            Map<Path, Path> backups,
            Throwable originalFailure
    ) {
        try {
            List<InstalledModRecord> currentRegistry = InstalledModManager.load(instance);
            Set<Path> originalExistingFiles = backups.keySet();

            // Remove only files currently owned by Vanta's registry and absent
            // from the pre-transaction snapshot. Untracked user files are untouched.
            for (InstalledModRecord record : currentRegistry) {
                if (record == null || record.getFilename() == null || record.getFilename().isBlank()) {
                    continue;
                }
                Path currentFile = resolveTrackedPath(modsDirectory, record.getFilename());
                if (!originalExistingFiles.contains(currentFile)
                        && Files.exists(currentFile, LinkOption.NOFOLLOW_LINKS)) {
                    if (Files.isSymbolicLink(currentFile)
                            || !Files.isRegularFile(currentFile, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Refusing to remove a non-regular transaction output: "
                                + currentFile.getFileName());
                    }
                    Files.delete(currentFile);
                }
            }

            // Restore all original managed artifacts before restoring their registry.
            for (Map.Entry<Path, Path> backup : backups.entrySet()) {
                Path destination = backup.getKey();
                if (Files.isSymbolicLink(destination)) {
                    throw new IOException("Tracked mod path became a symbolic link during rollback: "
                            + destination.getFileName());
                }
                Files.copy(backup.getValue(), destination, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
            }
            InstalledModManager.save(instance, originalRegistry);
        } catch (Exception rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
        }
    }

    private static Path resolveTrackedPath(Path modsDirectory, String filename) throws IOException {
        if (filename.indexOf('/') >= 0 || filename.indexOf('\\\\') >= 0) {
            throw new IOException("Installed-mod registry must contain a filename, not a path: " + filename);
        }
        Path supplied = Path.of(filename);
        if (supplied.isAbsolute() || supplied.getNameCount() != 1
                || ".".equals(filename) || "..".equals(filename)) {
            throw new IOException("Installed-mod registry contains an unsafe filename: " + filename);
        }
        Path resolved = modsDirectory.resolve(supplied).normalize();
        if (!resolved.startsWith(modsDirectory) || resolved.equals(modsDirectory)) {
            throw new IOException("Installed-mod registry contains an unsafe filename: " + filename);
        }
        return resolved;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted((left, right) ->
                    right.getNameCount() - left.getNameCount()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
