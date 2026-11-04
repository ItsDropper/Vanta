package dev.itsdropper.vanta.launcher.instance;

import dev.itsdropper.vanta.launcher.MinecraftLocator;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Local recovery points for replaceable instance content. Worlds and other
 * user-generated data are deliberately excluded.
 */
public final class InstanceRecoveryService {
    private static final List<String> RECOVERED_DIRECTORIES =
            List.of("mods", "config", "resourcepacks", "shaderpacks");
    private static final int MAX_ENTRIES = 100_000;
    private static final long MAX_EXPANDED_BYTES = 20L * 1024 * 1024 * 1024;
    private static final int DEFAULT_RETENTION = 5;

    public record RecoveryPoint(
            String id,
            long createdAtEpochMillis,
            String reason,
            long expandedBytes,
            int fileCount
    ) { }

    private InstanceRecoveryService() { }

    public static RecoveryPoint createRecoveryPoint(Instance instance, String reason) throws IOException {
        return createRecoveryPoint(instance, reason, MinecraftLocator.getVantaDirectoryPath());
    }

    static RecoveryPoint createRecoveryPoint(Instance instance, String reason, Path vantaDirectory)
            throws IOException {
        Path instanceDirectory = requireInstanceDirectory(instance);
        String instanceId = safeInstanceId(instance.getId());
        Path recoveryDirectory = recoveryDirectory(instanceId, vantaDirectory);
        Files.createDirectories(recoveryDirectory);

        String id = Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path destination = recoveryDirectory.resolve(id + ".zip");
        Path temporary = recoveryDirectory.resolve(id + ".zip.in-progress");
        String safeReason = reason == null || reason.isBlank() ? "Manual recovery point" : reason.trim();

        long expandedBytes = 0;
        int fileCount = 0;
        try {
            try (OutputStream output = Files.newOutputStream(temporary);
                 ZipOutputStream zip = new ZipOutputStream(output)) {
                zip.putNextEntry(new ZipEntry("recovery-point.txt"));
                zip.write(("Vanta recovery point\nInstance: " + instance.getName()
                        + "\nCreated: " + Instant.now()
                        + "\nReason: " + safeReason + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();

                byte[] buffer = new byte[64 * 1024];
                for (String directoryName : RECOVERED_DIRECTORIES) {
                    Path directory = instanceDirectory.resolve(directoryName);
                    zip.putNextEntry(new ZipEntry(directoryName + "/"));
                    zip.closeEntry();
                    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) continue;

                    List<Path> files;
                    try (var walk = Files.walk(directory)) {
                        files = walk.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                                .sorted(Comparator.comparing(path -> directory.relativize(path).toString()))
                                .toList();
                    }
                    for (Path file : files) {
                        Path relative = directory.relativize(file);
                        String entryName = directoryName + "/" + relative.toString().replace('\\', '/');
                        zip.putNextEntry(new ZipEntry(entryName));
                        try (InputStream input = Files.newInputStream(file)) {
                            int read;
                            while ((read = input.read(buffer)) != -1) {
                                expandedBytes += read;
                                if (expandedBytes > MAX_EXPANDED_BYTES) {
                                    throw new IOException("Recovery point exceeds the 20 GiB safety limit.");
                                }
                                zip.write(buffer, 0, read);
                            }
                        }
                        zip.closeEntry();
                        fileCount++;
                        if (fileCount > MAX_ENTRIES) throw new IOException("Recovery point contains too many files.");
                    }
                }
            }

            inspectArchive(temporary);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination);
            }

            RecoveryPoint point = new RecoveryPoint(id, parseTimestamp(id), safeReason, expandedBytes, fileCount);
            try {
                pruneRecoveryPoints(instanceId, DEFAULT_RETENTION);
            } catch (IOException cleanupFailure) {
                System.err.println("Recovery point was saved, but retention cleanup failed: "
                        + cleanupFailure.getMessage());
            }
            return point;
        } catch (Exception failure) {
            Files.deleteIfExists(temporary);
            Files.deleteIfExists(destination);
            if (failure instanceof IOException io) throw io;
            throw new IOException("Could not create an instance recovery point.", failure);
        }
    }

    public static List<RecoveryPoint> listRecoveryPoints(Instance instance) throws IOException {
        return listRecoveryPoints(instance, MinecraftLocator.getVantaDirectoryPath());
    }

    static List<RecoveryPoint> listRecoveryPoints(Instance instance, Path vantaDirectory) throws IOException {
        Path directory = recoveryDirectory(safeInstanceId(instance == null ? null : instance.getId()), vantaDirectory);
        if (!Files.isDirectory(directory)) return List.of();

        List<RecoveryPoint> points = new ArrayList<>();
        try (var files = Files.list(directory)) {
            for (Path archive : files.filter(path -> path.getFileName().toString().endsWith(".zip")).toList()) {
                try {
                    points.add(readPoint(archive));
                } catch (IOException invalid) {
                    // A damaged point is left untouched and is not offered for restore.
                }
            }
        }
        points.sort(Comparator.comparingLong(RecoveryPoint::createdAtEpochMillis).reversed());
        return List.copyOf(points);
    }

    /**
     * Restores only the four managed directories. The new tree is fully staged
     * before publication; current directories are retained for rollback until
     * all replacements have succeeded.
     */
    public static void restoreRecoveryPoint(Instance instance, String recoveryPointId) throws IOException {
        restoreRecoveryPoint(instance, recoveryPointId, MinecraftLocator.getVantaDirectoryPath());
    }

    static void restoreRecoveryPoint(Instance instance, String recoveryPointId, Path vantaDirectory)
            throws IOException {
        Path instanceDirectory = requireInstanceDirectory(instance);
        String instanceId = safeInstanceId(instance.getId());
        if (recoveryPointId == null || !recoveryPointId.matches("[0-9]{13}-[a-f0-9]{8}")) {
            throw new IllegalArgumentException("Invalid recovery point identifier.");
        }
        Path recoveryDirectory = recoveryDirectory(instanceId, vantaDirectory);
        Path archive = recoveryDirectory.resolve(recoveryPointId + ".zip").normalize();
        if (!archive.startsWith(recoveryDirectory) || !Files.isRegularFile(archive)) {
            throw new IOException("Recovery point no longer exists.");
        }
        inspectArchive(archive);

        Path transaction = Files.createTempDirectory(instanceDirectory, ".vanta-recovery-");
        Path staged = transaction.resolve("staged");
        Path rollback = transaction.resolve("rollback");
        Files.createDirectories(staged);
        Files.createDirectories(rollback);
        List<String> movedOld = new ArrayList<>();
        List<String> published = new ArrayList<>();
        boolean committed = false;
        boolean rollbackComplete = false;

        try {
            extractManagedDirectories(archive, staged);
            for (String directoryName : RECOVERED_DIRECTORIES) {
                Path current = instanceDirectory.resolve(directoryName);
                Path old = rollback.resolve(directoryName);
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                    move(current, old);
                    movedOld.add(directoryName);
                }
                Path replacement = staged.resolve(directoryName);
                Files.createDirectories(replacement);
                move(replacement, current);
                published.add(directoryName);
            }
            committed = true;
        } catch (Exception failure) {
            IOException rollbackFailure = null;
            for (int i = published.size() - 1; i >= 0; i--) {
                String name = published.get(i);
                try {
                    deleteTree(instanceDirectory.resolve(name));
                } catch (IOException ex) {
                    rollbackFailure = ex;
                }
            }
            for (int i = movedOld.size() - 1; i >= 0; i--) {
                String name = movedOld.get(i);
                try {
                    move(rollback.resolve(name), instanceDirectory.resolve(name));
                } catch (IOException ex) {
                    rollbackFailure = ex;
                }
            }
            rollbackComplete = rollbackFailure == null;
            if (rollbackFailure != null) failure.addSuppressed(rollbackFailure);
            if (failure instanceof IOException io) throw io;
            throw new IOException("Recovery restore failed; Vanta attempted to roll back the changed directories.", failure);
        } finally {
            if (committed || rollbackComplete) {
                deleteTree(transaction);
            }
        }
    }

    private static void extractManagedDirectories(Path archive, Path stagedRoot) throws IOException {
        Set<String> seen = new HashSet<>();
        long expanded = 0;
        int entries = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES + 16) throw new IOException("Recovery archive contains too many entries.");
                String raw = entry.getName();
                if (raw == null || raw.isBlank() || raw.indexOf((char) 0) >= 0) {
                    throw new IOException("Recovery archive contains an invalid path.");
                }
                String normalized = raw.replace('\\', '/');
                Path relative = Path.of(normalized).normalize();
                if (relative.isAbsolute() || relative.startsWith("..") || normalized.startsWith("/")
                        || normalized.matches("^[A-Za-z]:.*")) {
                    throw new IOException("Recovery archive contains an unsafe path.");
                }
                String key = relative.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (!seen.add(key)) throw new IOException("Recovery archive contains duplicate paths.");
                if (key.equals("recovery-point.txt")) {
                    while (zip.read(buffer) != -1) { /* consume and CRC-check */ }
                    zip.closeEntry();
                    continue;
                }
                String top = relative.getNameCount() == 0 ? "" : relative.getName(0).toString();
                if (!RECOVERED_DIRECTORIES.contains(top)) {
                    throw new IOException("Recovery archive contains an unmanaged path: " + raw);
                }
                Path output = stagedRoot.resolve(relative).normalize();
                if (!output.startsWith(stagedRoot)) throw new IOException("Recovery path escapes staging.");
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    try (OutputStream out = Files.newOutputStream(output)) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            expanded += read;
                            if (expanded > MAX_EXPANDED_BYTES) throw new IOException("Recovery archive exceeds its size limit.");
                            out.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        } catch (java.util.zip.ZipException ex) {
            throw new IOException("Recovery archive is corrupt or failed its CRC check.", ex);
        } catch (java.nio.file.InvalidPathException ex) {
            throw new IOException("Recovery archive contains an invalid path.", ex);
        }
    }

    private static RecoveryPoint readPoint(Path archive) throws IOException {
        inspectArchive(archive);
        long timestamp = parseTimestamp(archive.getFileName().toString().replaceFirst("\\.zip$", ""));
        long expandedBytes = 0;
        int files = 0;
        String reason = "Recovery point";
        byte[] buffer = new byte[4096];
        try (InputStream input = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("recovery-point.txt")) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        if (out.size() + read > 16 * 1024) throw new IOException("Recovery metadata is too large.");
                        out.write(buffer, 0, read);
                    }
                    String metadata = out.toString(java.nio.charset.StandardCharsets.UTF_8);
                    for (String line : metadata.split("\\R")) {
                        if (line.startsWith("Reason: ")) reason = line.substring("Reason: ".length());
                    }
                } else if (!entry.isDirectory()) {
                    files++;
                    int read;
                    while ((read = zip.read(buffer)) != -1) expandedBytes += read;
                }
                zip.closeEntry();
            }
        }
        return new RecoveryPoint(archive.getFileName().toString().replaceFirst("\\.zip$", ""),
                timestamp, reason, expandedBytes, files);
    }

    private static void inspectArchive(Path archive) throws IOException {
        Set<String> names = new HashSet<>();
        long expanded = 0;
        int entries = 0;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES + 16) throw new IOException("Recovery archive contains too many entries.");
                String name = entry.getName();
                if (name == null || name.isBlank() || name.indexOf((char) 0) >= 0) throw new IOException("Invalid recovery archive path.");
                String normalized = name.replace('\\', '/');
                Path relative = Path.of(normalized).normalize();
                if (relative.isAbsolute() || relative.startsWith("..") || normalized.startsWith("/")
                        || normalized.matches("^[A-Za-z]:.*")) throw new IOException("Unsafe recovery archive path.");
                String key = relative.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (!names.add(key)) throw new IOException("Duplicate recovery archive path.");
                if (!key.equals("recovery-point.txt")) {
                    String top = relative.getNameCount() == 0 ? "" : relative.getName(0).toString();
                    if (!RECOVERED_DIRECTORIES.contains(top)
                            || (relative.getNameCount() == 1 && !entry.isDirectory())) {
                        throw new IOException("Unexpected recovery archive entry.");
                    }
                }
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > MAX_EXPANDED_BYTES) throw new IOException("Recovery archive exceeds its size limit.");
                }
                zip.closeEntry();
            }
        } catch (java.util.zip.ZipException ex) {
            throw new IOException("Recovery archive is corrupt or failed its CRC check.", ex);
        } catch (java.nio.file.InvalidPathException ex) {
            throw new IOException("Recovery archive contains an invalid path.", ex);
        }
        if (!names.contains("recovery-point.txt")) throw new IOException("Recovery point metadata is missing.");
        for (String directory : RECOVERED_DIRECTORIES) {
            if (!names.contains(directory)) throw new IOException("Recovery point is missing " + directory + ".");
        }
    }

    private static void pruneRecoveryPoints(String instanceId, int retention) throws IOException {
        Path directory = recoveryDirectory(instanceId);
        try (var files = Files.list(directory)) {
            List<Path> archives = files.filter(path -> path.getFileName().toString().matches("[0-9]{13}-[a-f0-9]{8}\\.zip"))
                    .sorted(Comparator.comparingLong((Path path) -> parseTimestamp(path.getFileName().toString())).reversed())
                    .toList();
            for (int i = retention; i < archives.size(); i++) Files.deleteIfExists(archives.get(i));
        }
    }

    private static Path recoveryDirectory(String instanceId) {
        return recoveryDirectory(instanceId, MinecraftLocator.getVantaDirectoryPath());
    }

    private static Path recoveryDirectory(String instanceId, Path vantaDirectory) {
        if (vantaDirectory == null) throw new IllegalArgumentException("Vanta data directory is required.");
        return vantaDirectory.toAbsolutePath().normalize()
                .resolve("recovery-points").resolve(instanceId).normalize();
    }

    private static Path requireInstanceDirectory(Instance instance) throws IOException {
        if (instance == null || instance.getDirectory() == null || instance.getId() == null) {
            throw new IllegalArgumentException("A valid instance is required.");
        }
        Path directory = instance.getDirectory().toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) throw new IOException("Instance directory does not exist.");
        return directory;
    }

    private static String safeInstanceId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9._-]{1,100}") || id.equals(".") || id.equals("..")) {
            throw new IllegalArgumentException("Invalid instance identifier.");
        }
        return id;
    }

    private static long parseTimestamp(String id) {
        try {
            return Long.parseLong(id.substring(0, id.indexOf('-')));
        } catch (RuntimeException ex) {
            return 0L;
        }
    }

    private static void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
