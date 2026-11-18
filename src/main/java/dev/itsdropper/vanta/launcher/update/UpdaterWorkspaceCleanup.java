package dev.itsdropper.vanta.launcher.update;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;
import java.util.List;

/**
 * Removes the isolated updater image after the updater JVM has stopped using it.
 * The cleanup runs in a daemon thread so it can never keep the launcher alive.
 */
public final class UpdaterWorkspaceCleanup {
    private static final String WORKSPACE_PREFIX = "session-";
    private static final String OWNER_FILE = "updater-owner";
    private static final long STALE_WORKSPACE_AGE_MS = TimeUnit.DAYS.toMillis(1);

    private UpdaterWorkspaceCleanup() {
    }

    public static void schedule(Path workspace, long updaterPid, long updaterStartTime) {
        if (updaterPid <= 0 || updaterStartTime <= 0 || !isManagedWorkspace(workspace)) {
            return;
        }

        Thread cleanup = new Thread(() -> {
            try {
                ProcessHandle process = ProcessHandle.of(updaterPid).orElse(null);
                if (process != null && matchesStartTime(process, updaterStartTime)) {
                    try {
                        process.onExit().get(5, TimeUnit.MINUTES);
                    } catch (Exception stillRunning) {
                        return;
                    }
                }
                deleteWorkspace(workspace);
            } catch (IOException failure) {
                System.err.println("[Vanta Updater Cleanup] " + failure.getMessage());
            }
        }, "vanta-updater-workspace-cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    private static boolean matchesStartTime(ProcessHandle process, long expectedStartTime) {
        if (expectedStartTime <= 0) return false;
        return process.info().startInstant()
                .map(start -> start.toEpochMilli() == expectedStartTime)
                .orElse(false);
    }

    static void writeOwner(Path workspace, ProcessHandle updater) throws IOException {
        if (!isManagedWorkspace(workspace) || updater == null || !updater.isAlive()) {
            throw new IOException("Cannot register an updater outside its managed workspace.");
        }
        long startTime = updater.info().startInstant()
                .map(java.time.Instant::toEpochMilli).orElse(-1L);
        if (startTime <= 0) throw new IOException("Operating system did not provide a stable updater process identity.");
        Files.writeString(workspace.resolve(OWNER_FILE),
                updater.pid() + System.lineSeparator() + startTime + System.lineSeparator());
    }

    public static void scheduleStaleWorkspaceSweep() {
        Thread sweep = new Thread(() -> {
            try {
                cleanupStaleWorkspaces();
            } catch (IOException failure) {
                System.err.println("[Vanta Updater Cleanup] " + failure.getMessage());
            }
        }, "vanta-stale-updater-workspace-sweep");
        sweep.setDaemon(true);
        sweep.start();
    }

    static void cleanupStaleWorkspaces() throws IOException {
        Path root = Path.of(System.getProperty("java.io.tmpdir"), "Vanta-updater");
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var children = Files.list(root)) {
            for (Path workspace : children.filter(Files::isDirectory).toList()) {
                if (!isManagedWorkspace(workspace)) {
                    continue;
                }
                Path ownerFile = workspace.resolve(OWNER_FILE);
                if (Files.isRegularFile(ownerFile)) {
                    try {
                        List<String> owner = Files.readAllLines(ownerFile);
                        long pid = Long.parseLong(owner.get(0));
                        long startTime = Long.parseLong(owner.get(1));
                        if (ProcessHandle.of(pid)
                                .filter(process -> matchesStartTime(process, startTime))
                                .isPresent()) {
                            continue;
                        }
                        deleteWorkspace(workspace);
                        continue;
                    } catch (RuntimeException | IOException invalidOwner) {
                        // An invalid owner record is only removed after the stale-age threshold.
                    }
                }
                long age = System.currentTimeMillis()
                        - Files.getLastModifiedTime(workspace).toMillis();
                if (age >= STALE_WORKSPACE_AGE_MS) {
                    deleteWorkspace(workspace);
                }
            }
        }
    }

    static boolean isManagedWorkspace(Path workspace) {
        if (workspace == null) {
            return false;
        }
        Path root = Path.of(System.getProperty("java.io.tmpdir"), "Vanta-updater")
                .toAbsolutePath().normalize();
        Path candidate = workspace.toAbsolutePath().normalize();
        if (!root.equals(candidate.getParent())
                || candidate.getFileName() == null
                || !candidate.getFileName().toString().startsWith(WORKSPACE_PREFIX)
                || Files.isSymbolicLink(candidate)
                || !Files.isDirectory(root)) {
            return false;
        }
        try {
            return root.toRealPath().equals(candidate.getParent().toRealPath());
        } catch (IOException unavailable) {
            return false;
        }
    }

    static void deleteWorkspace(Path workspace) throws IOException {
        if (!isManagedWorkspace(workspace) || !Files.exists(workspace)) {
            return;
        }
        Files.walkFileTree(workspace, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
