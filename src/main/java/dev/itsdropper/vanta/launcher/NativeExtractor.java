package dev.itsdropper.vanta.launcher;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class NativeExtractor {

    public static void extract(
            Path nativeJar,
            Path outputFolder
    ) throws Exception {

        if (nativeJar == null) {
            throw new IllegalArgumentException(
                    "Native JAR cannot be null."
            );
        }

        if (!Files.isRegularFile(nativeJar)) {
            throw new IllegalStateException(
                    "Native JAR does not exist: "
                            + nativeJar
            );
        }

        Files.createDirectories(
                outputFolder
        );

        try (InputStream input =
                     Files.newInputStream(nativeJar);
             ZipInputStream zip =
                     new ZipInputStream(input)) {

            ZipEntry entry;

            while ((entry = zip.getNextEntry()) != null) {

                try {

                    if (entry.isDirectory()) {
                        continue;
                    }

                    String name =
                            entry.getName();

                    if (name == null || !isNativeFileForCurrentPlatform(name)) {
                        continue;
                    }

                    /*
                     * Only extract the filename itself.
                     *
                     * Native libraries are loaded from one
                     * flat directory, so directory structure
                     * inside the JAR is not needed.
                     */
                    Path fileName =
                            Path.of(name)
                                    .getFileName();

                    if (fileName == null) {
                        continue;
                    }

                    String dllName =
                            fileName.toString();

                    /*
                     * Extra safety: make absolutely sure the
                     * resulting filename cannot contain path
                     * separators.
                     */
                    if (dllName.contains("/")
                            || dllName.contains("\\")) {

                        throw new IllegalStateException(
                                "Unsafe native library name: "
                                        + name
                        );
                    }

                    Path output =
                            outputFolder.resolve(
                                    dllName
                            );

                    Files.copy(
                            zip,
                            output,
                            StandardCopyOption.REPLACE_EXISTING
                    );

                } finally {

                    zip.closeEntry();
                }
            }
        }
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