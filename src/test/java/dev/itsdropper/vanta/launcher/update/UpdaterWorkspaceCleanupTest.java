package dev.itsdropper.vanta.launcher.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class UpdaterWorkspaceCleanupTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void onlyAcceptsDirectManagedSessionDirectories() throws Exception {
        Path root = Path.of(System.getProperty("java.io.tmpdir"), "Vanta-updater");
        Files.createDirectories(root);
        assertTrue(UpdaterWorkspaceCleanup.isManagedWorkspace(root.resolve("session-test")));
        assertFalse(UpdaterWorkspaceCleanup.isManagedWorkspace(root.resolve("other")));
        assertFalse(UpdaterWorkspaceCleanup.isManagedWorkspace(temporaryDirectory.resolve("session-test")));
        assertFalse(UpdaterWorkspaceCleanup.isManagedWorkspace(root.resolve("session-test/nested")));
    }

    @Test
    void deletesWorkspaceRecursivelyWithoutFollowingExternalLinks() throws Exception {
        Path root = Path.of(System.getProperty("java.io.tmpdir"), "Vanta-updater");
        Path workspace = root.resolve("session-test-" + UUID.randomUUID());
        Path outside = temporaryDirectory.resolve("keep.txt");
        Files.createDirectories(workspace.resolve("application/nested"));
        Files.writeString(workspace.resolve("application/nested/file.txt"), "temporary");
        Files.writeString(outside, "keep");

        Path link = workspace.resolve("external");
        try {
            Files.createSymbolicLink(link, temporaryDirectory);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException unavailable) {
            // The recursive deletion assertion still covers the platform's supported path.
        }

        UpdaterWorkspaceCleanup.deleteWorkspace(workspace);

        assertFalse(Files.exists(workspace));
        assertEquals("keep", Files.readString(outside));
    }

    @Test
    void staleSweepRemovesAbandonedWorkspacesButPreservesActiveUpdater() throws Exception {
        Path root = Path.of(System.getProperty("java.io.tmpdir"), "Vanta-updater");
        Files.createDirectories(root);
        Path stale = root.resolve("session-stale-test-" + UUID.randomUUID());
        Files.createDirectories(stale);
        Files.setLastModifiedTime(stale, java.nio.file.attribute.FileTime.fromMillis(1));

        Path active = root.resolve("session-active-test-" + UUID.randomUUID());
        Files.createDirectories(active);
        UpdaterWorkspaceCleanup.writeOwner(active, ProcessHandle.current());
        Files.setLastModifiedTime(active, java.nio.file.attribute.FileTime.fromMillis(1));

        UpdaterWorkspaceCleanup.cleanupStaleWorkspaces();

        assertFalse(Files.exists(stale), "Abandoned workspaces older than one day should be removed");
        assertTrue(Files.exists(active), "A workspace owned by a live updater must be preserved");
        UpdaterWorkspaceCleanup.deleteWorkspace(active);
    }

    @Test
    void scheduledCleanupWaitsUntilUpdaterProcessHasExited() throws Exception {
        Path root = Path.of(System.getProperty("java.io.tmpdir"), "Vanta-updater");
        Path workspace = root.resolve("session-test-" + UUID.randomUUID());
        Files.createDirectories(workspace);
        Files.writeString(workspace.resolve("marker"), "temporary");

        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
        String classPath = String.join(File.pathSeparator,
                System.getProperty("java.class.path"),
                Path.of(UpdaterWorkspaceCleanupTest.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()).toString(),
                Path.of(UpdaterWorkspaceCleanup.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()).toString());
        Process updater = new ProcessBuilder(javaExecutable.toString(), "-cp", classPath,
                Child.class.getName())
                .redirectErrorStream(true)
                .start();

        long updaterStartTime = updater.info().startInstant()
                .map(java.time.Instant::toEpochMilli).orElse(-1L);
        UpdaterWorkspaceCleanup.schedule(workspace, updater.pid(), updaterStartTime);
        assertTrue(Files.exists(workspace), "Workspace must remain while updater is alive");
        assertTrue(updater.waitFor(10, TimeUnit.SECONDS), "Test updater process did not exit");

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (Files.exists(workspace) && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertFalse(Files.exists(workspace), "Workspace should be removed after updater exits");
    }

    public static final class Child {
        public static void main(String[] args) throws Exception {
            Thread.sleep(600);
        }
    }
}
