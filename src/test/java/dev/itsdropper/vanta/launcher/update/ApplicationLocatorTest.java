package dev.itsdropper.vanta.launcher.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationLocatorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void recognizesWindowsJpackageImage() throws Exception {
        Path root = temporaryDirectory.resolve("Vanta-Windows");
        touch(root.resolve("Vanta.exe"));
        touch(root.resolve("app/Vanta.cfg"));
        touch(root.resolve("runtime/bin/java.exe"));

        assertTrue(ApplicationLocator.isSupportedInstallation(root));
        assertEquals(root.resolve("runtime"), ApplicationLocator.getRuntimeDirectory(root));
        assertEquals(root.resolve("app"), ApplicationLocator.getApplicationPayloadDirectory(root));
        assertEquals(root.resolve("Vanta.exe"), ApplicationLocator.getLauncherExecutable(root));
        assertEquals(root.resolve("app/updater"), ApplicationLocator.getUpdaterDirectory(root));
    }

    @Test
    void recognizesLinuxJpackageImage() throws Exception {
        Path root = temporaryDirectory.resolve("Vanta-Linux");
        touch(root.resolve("bin/Vanta"));
        touch(root.resolve("lib/app/Vanta.cfg"));
        touch(root.resolve("lib/runtime/bin/java"));

        assertTrue(ApplicationLocator.isSupportedInstallation(root));
        assertEquals(root.resolve("lib/runtime"), ApplicationLocator.getRuntimeDirectory(root));
        assertEquals(root.resolve("lib/app"), ApplicationLocator.getApplicationPayloadDirectory(root));
        assertEquals(root.resolve("bin/Vanta"), ApplicationLocator.getLauncherExecutable(root));
        assertEquals(root.resolve("lib/app/updater"), ApplicationLocator.getUpdaterDirectory(root));
    }

    @Test
    void recognizesMacOsApplicationBundle() throws Exception {
        Path root = temporaryDirectory.resolve("Vanta.app");
        touch(root.resolve("Contents/MacOS/Vanta"));
        touch(root.resolve("Contents/app/Vanta.cfg"));
        touch(root.resolve("Contents/runtime/bin/java"));

        assertTrue(ApplicationLocator.isSupportedInstallation(root));
        assertEquals(root.resolve("Contents/runtime"), ApplicationLocator.getRuntimeDirectory(root));
        assertEquals(root.resolve("Contents/app"), ApplicationLocator.getApplicationPayloadDirectory(root));
        assertEquals(root.resolve("Contents/MacOS/Vanta"), ApplicationLocator.getLauncherExecutable(root));
        assertEquals(root.resolve("Contents/app/updater"), ApplicationLocator.getUpdaterDirectory(root));
    }

    @Test
    void rejectsIncompleteOrUnknownApplicationImages() throws Exception {
        Path root = temporaryDirectory.resolve("not-vanta");
        touch(root.resolve("Vanta.exe"));
        assertFalse(ApplicationLocator.isSupportedInstallation(root));
        assertThrows(IllegalStateException.class, () -> ApplicationLocator.getRuntimeDirectory(root));
        assertThrows(IllegalStateException.class, () -> ApplicationLocator.getLauncherExecutable(root));
    }

    private static void touch(Path file) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "test");
        file.toFile().setExecutable(true, true);
    }
}
