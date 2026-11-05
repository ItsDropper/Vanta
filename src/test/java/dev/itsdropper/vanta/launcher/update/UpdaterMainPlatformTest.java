package dev.itsdropper.vanta.launcher.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class UpdaterMainPlatformTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsOnlyReadinessMarkerInsidePrivateHelperWorkspace() throws Exception {
        Path workspace = temporaryDirectory.resolve("session-123");
        Path helperApplication = workspace.resolve("application");
        Path readyFile = workspace.resolve("updater-ready");

        assertEquals(readyFile.toAbsolutePath().normalize(),
                UpdaterMain.validateReadinessFile(helperApplication, readyFile));
        assertThrows(java.io.IOException.class,
                () -> UpdaterMain.validateReadinessFile(helperApplication,
                        temporaryDirectory.resolve("updater-ready")));
        assertThrows(java.io.IOException.class,
                () -> UpdaterMain.validateReadinessFile(helperApplication,
                        workspace.resolve("unexpected-marker")));
    }

    @Test
    void restoresLinuxLauncherAndRuntimeExecutablePermissions() throws Exception {
        assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("windows"));
        String previous = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Linux");
            Path root = temporaryDirectory.resolve("Vanta");
            Path launcher = createFile(root.resolve("bin/Vanta"));
            Path java = createFile(root.resolve("lib/runtime/bin/java"));
            Path spawnHelper = createFile(root.resolve("lib/runtime/lib/jspawnhelper"));
            createFile(root.resolve("lib/app/Vanta.cfg"));

            launcher.toFile().setExecutable(false, false);
            java.toFile().setExecutable(false, false);
            spawnHelper.toFile().setExecutable(false, false);
            assertFalse(Files.isExecutable(launcher));
            assertFalse(Files.isExecutable(java));
            assertFalse(Files.isExecutable(spawnHelper));

            UpdaterMain.restoreExecutablePermissions(root);

            assertTrue(Files.isExecutable(launcher));
            assertTrue(Files.isExecutable(java));
            assertTrue(Files.isExecutable(spawnHelper));
        } finally {
            restoreOs(previous);
        }
    }

    @Test
    void restoresMacOsBundleLauncherAndNestedRuntimePermissions() throws Exception {
        assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("windows"));
        String previous = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Mac OS X");
            Path root = temporaryDirectory.resolve("Vanta.app");
            Path launcher = createFile(root.resolve("Contents/MacOS/Vanta"));
            Path modules = createFile(root.resolve("Contents/runtime/Contents/Home/lib/modules"));
            Path spawnHelper = createFile(root.resolve("Contents/runtime/Contents/Home/lib/jspawnhelper"));
            createFile(root.resolve("Contents/app/Vanta.cfg"));

            launcher.toFile().setExecutable(false, false);
            modules.toFile().setExecutable(false, false);
            spawnHelper.toFile().setExecutable(false, false);
            UpdaterMain.restoreExecutablePermissions(root);

            assertTrue(Files.isExecutable(launcher));
            assertFalse(Files.isExecutable(modules));
            assertTrue(Files.isExecutable(spawnHelper));
        } finally {
            restoreOs(previous);
        }
    }

    @Test
    void restoresLinuxLauncherWithoutStandaloneJavaCommand() throws Exception {
        assumeFalse(System.getProperty("os.name", "").toLowerCase().contains("windows"));
        String previous = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Linux");
            Path root = temporaryDirectory.resolve("Vanta-Linux-MinimalRuntime");
            Path launcher = createFile(root.resolve("bin/Vanta"));
            createFile(root.resolve("lib/runtime/lib/modules"));
            createFile(root.resolve("lib/app/Vanta.cfg"));
            launcher.toFile().setExecutable(false, false);

            UpdaterMain.restoreExecutablePermissions(root);

            assertTrue(Files.isExecutable(launcher));
            assertTrue(ApplicationLocator.isSupportedInstallation(root));
            assertEquals(root.resolve("lib/runtime"),
                    ApplicationLocator.getRuntimeDirectory(root));
        } finally {
            restoreOs(previous);
        }
    }

    private Path createFile(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "test");
        return file;
    }

    private static void restoreOs(String previous) {
        if (previous == null) System.clearProperty("os.name");
        else System.setProperty("os.name", previous);
    }
}
