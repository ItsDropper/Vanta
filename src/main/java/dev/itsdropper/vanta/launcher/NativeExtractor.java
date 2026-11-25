package dev.itsdropper.vanta.launcher;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts platform-native binaries from verified library archives.
 *
 * Archive limits are deliberately conservative: native dependency archives
 * should be small, and a corrupt or hostile archive must not be able to exhaust
 * disk space or keep the launcher busy decompressing unbounded data.
 */
public class NativeExtractor {
    static final int MAX_ARCHIVE_ENTRIES = 4_096;
    static final long MAX_ENTRY_BYTES = 128L * 1024 * 1024;
    static final long MAX_ARCHIVE_EXPANDED_BYTES = 512L * 1024 * 1024;
    private static final int COPY_BUFFER_BYTES = 32 * 1024;

    public static void extract(Path nativeJar, Path outputFolder) throws Exception {
        if (nativeJar == null) {
            throw new IllegalArgumentException("Native JAR cannot be null.");
        }
        if (!Files.isRegularFile(nativeJar)) {
            throw new IllegalStateException("Native JAR does not exist: " + nativeJar);
        }
        if (outputFolder == null || Files.isSymbolicLink(outputFolder)) {
            throw new IllegalArgumentException("Native output directory must be a real directory.");
        }
        Files.createDirectories(outputFolder);

        long expandedBytes = 0L;
        int entryCount = 0;
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        try (InputStream input = Files.newInputStream(nativeJar);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                    if (++entryCount > MAX_ARCHIVE_ENTRIES) {
                        throw new IllegalStateException("Native archive contains too many entries.");
                    }
                    validateDeclaredEntrySize(entry.getSize());
                    String name = entry.getName();
                    boolean nativeFile = !entry.isDirectory()
                            && name != null && isNativeFileForCurrentPlatform(name);
                    Path output = nativeFile ? outputFolder.resolve(safeNativeFileName(name)) : null;

                    long entryBytes = 0L;
                    try (OutputStream out = nativeFile
                            ? Files.newOutputStream(output, StandardOpenOption.CREATE,
                                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
                            : OutputStream.nullOutputStream()) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            entryBytes += read;
                            expandedBytes += read;
                            validateExpandedSizes(entryBytes, expandedBytes);
                            out.write(buffer, 0, read);
                        }
                    }
                    if (entry.getSize() >= 0 && entryBytes != entry.getSize()) {
                        throw new IllegalStateException("Native archive entry size does not match its directory record.");
                    }
            }
        }
    }

    static void validateDeclaredEntrySize(long size) {
        if (size > MAX_ENTRY_BYTES) {
            throw new IllegalStateException("Native archive entry exceeds the permitted uncompressed size.");
        }
    }

    static void validateExpandedSizes(long entryBytes, long archiveBytes) {
        if (entryBytes > MAX_ENTRY_BYTES) {
            throw new IllegalStateException("Native archive entry exceeded the permitted uncompressed size.");
        }
        if (archiveBytes > MAX_ARCHIVE_EXPANDED_BYTES) {
            throw new IllegalStateException("Native archive exceeded the permitted total uncompressed size.");
        }
    }

    static String safeNativeFileName(String entryName) {
        if (entryName == null || entryName.isBlank()
                || entryName.indexOf(0) >= 0
                || entryName.indexOf(':') >= 0) {
            throw new IllegalStateException("Native archive contains an invalid native filename.");
        }
        String normalized = entryName.replace((char) 92, '/');
        if (normalized.startsWith("/")
                || java.util.Arrays.stream(normalized.split("/", -1))
                .anyMatch(segment -> segment.equals(".") || segment.equals(".."))) {
            throw new IllegalStateException("Native archive contains a path traversal entry.");
        }
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (fileName.isBlank() || fileName.equals(".") || fileName.equals("..")
                || fileName.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException("Native archive contains an unsafe native filename.");
        }
        return fileName;
    }

    private static boolean isNativeFileForCurrentPlatform(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return switch (Platform.operatingSystem()) {
            case WINDOWS -> lower.endsWith(".dll");
            case LINUX -> lower.endsWith(".so") || lower.matches(".*\\.so\\.[0-9.]+$");
            case MACOS -> lower.endsWith(".dylib") || lower.endsWith(".jnilib")
                    || lower.endsWith(".so");
            case UNKNOWN -> false;
        };
    }

    public static void extractAll(List<Path> jars, Path outputFolder) throws Exception {
        if (outputFolder == null) {
            throw new IllegalArgumentException("Native output directory is required.");
        }
        Path target = outputFolder.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(target)) {
            throw new IllegalStateException("Native output directory cannot be a symbolic link.");
        }
        if (Files.exists(target) && !Files.isDirectory(target)) {
            throw new IllegalStateException("Native output path is not a directory: " + target);
        }

        Path parent = target.getParent();
        if (parent == null) throw new IllegalStateException("Native output directory has no parent.");
        Files.createDirectories(parent);

        String suffix = java.util.UUID.randomUUID().toString();
        Path staging = parent.resolve(target.getFileName() + ".staging-" + suffix);
        Path backup = parent.resolve(target.getFileName() + ".backup-" + suffix);
        boolean backedUp = false;
        boolean published = false;

        try {
            Files.createDirectory(staging);
            if (jars != null) {
                for (Path jar : jars) {
                    if (jar != null) extract(jar, staging);
                }
            }

            if (Files.exists(target)) {
                moveDirectory(target, backup);
                backedUp = true;
            }
            moveDirectory(staging, target);
            published = true;
        } catch (Exception failure) {
            if (backedUp && !Files.exists(target) && Files.exists(backup)) {
                try {
                    moveDirectory(backup, target);
                    backedUp = false;
                } catch (Exception rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        } finally {
            deleteTreeQuietly(staging);
            if (published && backedUp) deleteTreeQuietly(backup);
        }
    }

    private static void moveDirectory(Path source, Path destination) throws java.io.IOException {
        try {
            Files.move(source, destination, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(source, destination);
        }
    }

    private static void deleteTreeQuietly(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (Exception ignored) {
            // Stale staging/backup directories are harmless and can be inspected manually.
        }
    }
}
