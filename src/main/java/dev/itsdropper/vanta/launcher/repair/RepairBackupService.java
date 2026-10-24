package dev.itsdropper.vanta.launcher.repair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Small rollback journal for instance metadata touched by repair.
 * It intentionally excludes worlds, mods and other user content because
 * the managed-installation repair routine must not rewrite those files.
 */
public final class RepairBackupService {
    private static final List<String> PROTECTED_FILES =
            List.of("instance.json", "minecraft/version.json");
    private static final int MAX_BACKUPS = 5;

    private RepairBackupService() {}

    public static Path createSnapshot(Path instanceDirectory) throws IOException {
        if (instanceDirectory == null) throw new IllegalArgumentException("Instance directory is required.");
        Path root = instanceDirectory.resolve(".vanta-repair-backups");
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(java.time.ZoneOffset.UTC).format(Instant.now());
        Path snapshot = root.resolve(stamp + "-" + Long.toUnsignedString(System.nanoTime()));
        Files.createDirectories(snapshot);
        List<String> copied = new ArrayList<>();
        for (String relative : PROTECTED_FILES) {
            Path source = instanceDirectory.resolve(relative);
            if (!Files.isRegularFile(source)) continue;
            Path destination = snapshot.resolve(relative);
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
            copied.add(relative);
        }
        Files.write(snapshot.resolve("manifest.txt"), copied, StandardCharsets.UTF_8);
        pruneOldSnapshots(root);
        return snapshot;
    }

    public static boolean restoreLatest(Path instanceDirectory) throws IOException {
        if (instanceDirectory == null) throw new IllegalArgumentException("Instance directory is required.");
        Path root = instanceDirectory.resolve(".vanta-repair-backups");
        if (!Files.isDirectory(root)) return false;
        Path latest;
        try (Stream<Path> entries = Files.list(root)) {
            latest = entries.filter(Files::isDirectory)
                    .filter(p -> Files.isRegularFile(p.resolve("manifest.txt")))
                    .max(Comparator.comparingLong(RepairBackupService::modified))
                    .orElse(null);
        }
        if (latest == null) return false;
        List<String> files = Files.readAllLines(latest.resolve("manifest.txt"), StandardCharsets.UTF_8);
        Path normalizedInstance = instanceDirectory.toAbsolutePath().normalize();
        Path normalizedSnapshot = latest.toAbsolutePath().normalize();
        boolean restoredAny = false;

        // A manifest is data, not authority: accept only exact known metadata paths,
        // and require each snapshot source to remain inside the selected snapshot.
        for (String relative : files) {
            if (!PROTECTED_FILES.contains(relative)) continue;

            Path source = normalizedSnapshot.resolve(relative).normalize();
            Path target = normalizedInstance.resolve(relative).normalize();
            if (!source.startsWith(normalizedSnapshot)
                    || !target.startsWith(normalizedInstance)
                    || !Files.isRegularFile(source)) {
                continue;
            }

            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
            restoredAny = true;
        }
        return restoredAny;
    }

    private static long modified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException ignored) { return 0L; }
    }

    private static void pruneOldSnapshots(Path root) throws IOException {
        if (!Files.isDirectory(root)) return;
        List<Path> snapshots;
        try (Stream<Path> entries = Files.list(root)) {
            snapshots = entries.filter(Files::isDirectory)
                    .sorted(Comparator.comparingLong(RepairBackupService::modified).reversed())
                    .toList();
        }
        for (int i = MAX_BACKUPS; i < snapshots.size(); i++) {
            Path old = snapshots.get(i);
            try (Stream<Path> walk = Files.walk(old)) {
                for (Path item : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(item);
                }
            }
        }
    }
}
