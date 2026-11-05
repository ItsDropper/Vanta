package dev.itsdropper.vanta.launcher.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UpdaterLauncherPlatformTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsWritableInstallParent() throws Exception {
        Path application = temporaryDirectory.resolve("install/Vanta");
        Files.createDirectories(application);
        assertDoesNotThrow(() -> UpdaterLauncher.verifyWritableInstallParent(application));
    }

    @Test
    void rejectsInstallWithoutAnExistingParent() {
        Path application = temporaryDirectory.resolve("missing/Vanta");
        assertThrows(java.io.IOException.class,
                () -> UpdaterLauncher.verifyWritableInstallParent(application));
    }

    @Test
    void identifiesMacAppTranslocationPaths() {
        Path translocated = temporaryDirectory.resolve(
                "private/var/folders/AppTranslocation/session/d/Vanta.app");
        Path normal = temporaryDirectory.resolve("Applications/Vanta.app");
        assertTrue(ApplicationLocator.isTranslocatedApplication(translocated));
        assertFalse(ApplicationLocator.isTranslocatedApplication(normal));
    }

    @Test
    void copiesCompleteWindowsImageWithoutStandaloneJavaExecutable() throws Exception {
        Path source = temporaryDirectory.resolve("installed/Vanta");
        Path launcher = createFile(source.resolve("Vanta.exe"), "native-launcher");
        createFile(source.resolve("runtime/bin/java.dll"), "jvm");
        createFile(source.resolve("runtime/lib/modules"), "modules");
        createFile(source.resolve("app/Vanta.cfg"), "main-class=dev.itsdropper.vanta.Main");
        launcher.toFile().setExecutable(true, false);

        Path destination = temporaryDirectory.resolve("temp-updater/application");
        UpdaterLauncher.copyApplicationImage(source, destination);

        assertEquals("native-launcher", Files.readString(destination.resolve("Vanta.exe")));
        assertEquals("jvm", Files.readString(destination.resolve("runtime/bin/java.dll")));
        assertEquals("modules", Files.readString(destination.resolve("runtime/lib/modules")));
        assertEquals("main-class=dev.itsdropper.vanta.Main",
                Files.readString(destination.resolve("app/Vanta.cfg")));
        assertTrue(Files.isExecutable(destination.resolve("Vanta.exe")));
        assertTrue(ApplicationLocator.isSupportedInstallation(destination));
    }

    @Test
    void copiesLinuxAndMacStyleImageLayouts() throws Exception {
        Path linux = temporaryDirectory.resolve("source-linux");
        Path linuxLauncher = createFile(linux.resolve("bin/Vanta"), "linux-launcher");
        createFile(linux.resolve("lib/runtime/lib/modules"), "modules");
        createFile(linux.resolve("lib/app/Vanta.cfg"), "linux-app");
        linuxLauncher.toFile().setExecutable(true, false);

        Path linuxCopy = temporaryDirectory.resolve("copies/linux");
        UpdaterLauncher.copyApplicationImage(linux, linuxCopy);
        assertTrue(ApplicationLocator.isSupportedInstallation(linuxCopy));
        assertTrue(Files.isExecutable(linuxCopy.resolve("bin/Vanta")));

        Path mac = temporaryDirectory.resolve("source-mac/Vanta.app");
        Path macLauncher = createFile(mac.resolve("Contents/MacOS/Vanta"), "mac-launcher");
        createFile(mac.resolve("Contents/runtime/Contents/Home/lib/modules"), "modules");
        createFile(mac.resolve("Contents/app/Vanta.cfg"), "mac-app");
        macLauncher.toFile().setExecutable(true, false);

        Path macCopy = temporaryDirectory.resolve("copies/Vanta.app");
        UpdaterLauncher.copyApplicationImage(mac, macCopy);
        assertTrue(ApplicationLocator.isSupportedInstallation(macCopy));
        assertTrue(Files.isExecutable(macCopy.resolve("Contents/MacOS/Vanta")));
    }

    @Test
    void rejectsCopyDestinationInsideOrAboveSource() throws Exception {
        Path source = temporaryDirectory.resolve("source");
        createFile(source.resolve("Vanta.exe"), "launcher");

        assertThrows(java.io.IOException.class,
                () -> UpdaterLauncher.copyApplicationImage(source, source.resolve("nested")));
        assertThrows(java.io.IOException.class,
                () -> UpdaterLauncher.copyApplicationImage(source, temporaryDirectory));
    }

    @Test
    void rejectsSymbolicLinksEscapingApplicationImage() throws Exception {
        Path source = temporaryDirectory.resolve("source");
        createFile(source.resolve("Vanta.exe"), "launcher");
        Path outside = createFile(temporaryDirectory.resolve("outside/secret"), "outside");
        Path link = source.resolve("external-link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException unavailable) {
            return;
        }

        assertThrows(java.io.IOException.class,
                () -> UpdaterLauncher.copyApplicationImage(source, temporaryDirectory.resolve("copy")));
    }

    private Path createFile(Path path, String value) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, value);
        return path;
    }
}
