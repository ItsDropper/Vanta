package dev.itsdropper.vanta.launcher.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Publishes immutable, content-verified files without storing the bytes twice when the file system allows it.
 *
 * <p>Every operation builds the result beside the destination and swaps it in with a single rename, so a reader
 * never observes a partial file and a failure never damages the existing destination. Hard links are only ever
 * made for files that are replaced rather than rewritten in place, which is how the launcher writes artifacts.
 */
public final class FileLinker {

    /** How a file ended up at its destination. */
    public enum Method { ALREADY_LINKED, HARD_LINK, COPY }

    /** Volume pairs where a hard link already failed, so later calls go straight to copying. */
    private static final Map<List<FileStore>, Boolean> UNSUPPORTED = new ConcurrentHashMap<>();

    private FileLinker() {
    }

    /**
     * Atomically makes {@code target} hold the bytes of {@code source}, preferring a hard link and falling back to
     * a copy across volumes, on file systems without link support, or when the link count limit is reached.
     */
    public static Method publish(Path source, Path target) throws IOException {
        Path from = source.toAbsolutePath().normalize();
        Path to = target.toAbsolutePath().normalize();
        if (Files.exists(to) && Files.isSameFile(from, to)) {
            return Method.ALREADY_LINKED;
        }
        Path parent = to.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = to.resolveSibling(to.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Method method = createLinkOrCopy(from, temporary);
            moveIntoPlace(temporary, to);
            return method;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Replaces {@code duplicate} with a hard link to {@code canonical} and returns whether that happened. Unlike
     * {@link #publish} this never copies, because a copy would not reclaim any space. The duplicate is left
     * untouched on every failure, including a file that is locked by a running game.
     */
    public static boolean replaceWithLink(Path canonical, Path duplicate) {
        Path from = canonical.toAbsolutePath().normalize();
        Path to = duplicate.toAbsolutePath().normalize();
        Path temporary = to.resolveSibling(to.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            if (Files.isSameFile(from, to)) {
                return false;
            }
            Files.createLink(temporary, from);
            moveIntoPlace(temporary, to);
            return true;
        } catch (IOException | UnsupportedOperationException | SecurityException failure) {
            return false;
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // A stray temporary file is harmless and is overwritten by nothing else.
            }
        }
    }

    private static Method createLinkOrCopy(Path from, Path temporary) throws IOException {
        List<FileStore> volumes = volumePair(from, temporary);
        if (volumes == null || !UNSUPPORTED.containsKey(volumes)) {
            try {
                Files.createLink(temporary, from);
                return Method.HARD_LINK;
            } catch (UnsupportedOperationException | IOException failure) {
                // Different volume, FAT or network share, or too many links: fall back to a copy.
                if (volumes != null) {
                    UNSUPPORTED.put(volumes, Boolean.TRUE);
                }
                Files.deleteIfExists(temporary);
            }
        }
        Files.copy(from, temporary, StandardCopyOption.REPLACE_EXISTING);
        return Method.COPY;
    }

    private static void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static List<FileStore> volumePair(Path source, Path temporary) {
        try {
            Path parent = temporary.getParent();
            return parent == null ? null : List.of(Files.getFileStore(source), Files.getFileStore(parent));
        } catch (IOException | RuntimeException failure) {
            return null;
        }
    }
}
