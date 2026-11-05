package dev.itsdropper.vanta.launcher;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PlatformTest {
    @Test
    void detectsSupportedOperatingSystems() {
        assertEquals(Platform.OperatingSystem.WINDOWS, Platform.operatingSystem("Windows 11"));
        assertEquals(Platform.OperatingSystem.LINUX, Platform.operatingSystem("Linux"));
        assertEquals(Platform.OperatingSystem.MACOS, Platform.operatingSystem("Mac OS X"));
        assertEquals(Platform.OperatingSystem.MACOS, Platform.operatingSystem("Darwin"));
        assertEquals(Platform.OperatingSystem.UNKNOWN, Platform.operatingSystem("Plan 9"));
    }

    @Test
    void resolvesWindowsRoamingDataDirectory() {
        assertEquals(Path.of("C:/Users/test/AppData/Roaming/Vanta").toAbsolutePath().normalize(),
                MinecraftLocator.resolveVantaDirectory("Windows 11", "C:/Users/test",
                        "C:/Users/test/AppData/Roaming", null));
    }

    @Test
    void resolvesMacApplicationSupportDirectory() {
        assertTrue(MinecraftLocator.resolveVantaDirectory("Mac OS X", "/Users/test", null, null)
                .endsWith(Path.of("Library", "Application Support", "Vanta")));
    }

    @Test
    void resolvesLinuxXdgDataDirectory() {
        Path xdgDataHome = Path.of(System.getProperty("java.io.tmpdir"), "vanta-xdg-data")
                .toAbsolutePath().normalize();
        assertEquals(xdgDataHome.resolve("Vanta"),
                MinecraftLocator.resolveVantaDirectory("Linux", "/home/test", null, xdgDataHome.toString()));
    }

    @Test
    void fallsBackToLinuxLocalShareAndIgnoresRelativeXdgOverride() {
        assertTrue(MinecraftLocator.resolveVantaDirectory("Linux", "/home/test", null, "relative")
                .endsWith(Path.of("home", "test", ".local", "share", "Vanta")));
    }

    @Test
    void rejectsUnknownOperatingSystemsAndMissingRequiredHomes() {
        assertThrows(UnsupportedOperationException.class,
                () -> MinecraftLocator.resolveVantaDirectory("Plan 9", "/home/test", null, null));
        assertThrows(IllegalStateException.class,
                () -> MinecraftLocator.resolveVantaDirectory("Linux", null, null, null));
        assertThrows(IllegalStateException.class,
                () -> MinecraftLocator.resolveVantaDirectory("Windows 11", "/home/test", null, null));
    }

    @Test
    void selectsNativeJavaExecutableName() {
        assertEquals("java.exe", Platform.javaExecutableName("Windows 11"));
        assertEquals("java", Platform.javaExecutableName("Linux"));
        assertEquals("java", Platform.javaExecutableName("Mac OS X"));
    }

    @Test
    void mapsSupportedAdoptiumPlatformsAndArchitectures() {
        assertEquals("windows", Platform.adoptiumOsForTest("Windows 11"));
        assertEquals("linux", Platform.adoptiumOsForTest("Linux"));
        assertEquals("mac", Platform.adoptiumOsForTest("Mac OS X"));
        assertEquals("x64", Platform.adoptiumArchitecture("AMD64"));
        assertEquals("aarch64", Platform.adoptiumArchitecture("aarch64"));
        assertThrows(UnsupportedOperationException.class,
                () -> Platform.adoptiumArchitecture("riscv64"));
        assertThrows(UnsupportedOperationException.class,
                () -> Platform.adoptiumArchitecture("x86"));
    }
}
