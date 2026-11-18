package dev.itsdropper.vanta.launcher.update;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;

/**
 * Removes the isolated updater image after the updater JVM has stopped using it.
 * The cleanup runs in a daemon thread so it can never keep the launcher alive.
 */
public final class UpdaterWorkspaceCleanup {
    private static final String WORKSPACE_PREFIX = "session-";

    private UpdaterWorkspaceCleanup() {
    }

    public static void schedule(Path workspace, long updaterPid) {
        if (updaterPid <= 0 || !isManagedWorkspace(workspace)) {
            return;
        }

        Thread cleanup = new Thread(() -> {
            try {
                ProcessHandle.of(updaterPid).ifPresent(process -> {
                    try {
                        process.onExit().get(5, TimeUnit.MINUTES);
                    } catch (Exception stillRunning) {
                        return;
                    }
                });
                deleteWorkspace(workspace);
            } catch (IOException failure) {
                System.err.println("[Vanta Updater Cleanup] " + failure.getMessage());
            }
        }, "vanta-updater-workspace-cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    static boolean isManagedWorkspace(Path workspace) {
        if (workspace == null) {
            return false;
        }
        Path root = Path.of(System.getProperty("java.io.tmpdir"), "Vanta-updater")
                .toAbsolutePath().normalize();
        Path candidate = workspace.toAbsolutePath().normalize();
        return root.equals(candidate.getParent())
                && candidate.getFileName() != null
                && candidate.getFileName().toString().startsWith(WORKSPACE_PREFIX)
                && !Files.isSymbolicLink(candidate);
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
