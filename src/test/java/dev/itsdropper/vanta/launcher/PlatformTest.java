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
    void doesNotMisclassifyOtherUnixFamiliesAsLinux() {
        assertEquals(Platform.OperatingSystem.UNKNOWN, Platform.operatingSystem("FreeBSD"));
        assertEquals(Platform.OperatingSystem.UNKNOWN, Platform.operatingSystem("OpenBSD"));
        assertEquals(Platform.OperatingSystem.UNKNOWN, Platform.operatingSystem("SunOS"));
        assertEquals(Platform.OperatingSystem.UNKNOWN, Platform.operatingSystem("AIX"));
    }

    @Test
    void resolvesWindowsRoamingDataDirectory() {
        Path home = Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        Path roaming = home.resolve("windows-roaming");
        assertEquals(roaming.resolve("Vanta").normalize(),
                MinecraftLocator.resolveVantaDirectory("Windows 11", home.toString(),
                        roaming.toString(), null));
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
        assertTrue(xdgDataHome.isAbsolute());
        assertTrue(MinecraftLocator.resolveVantaDirectory(
                "Linux", "/home/test", null, xdgDataHome.toString())
                .endsWith(Path.of("vanta-xdg-data", "Vanta")));
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
        assertThrows(IllegalStateException.class,
                () -> MinecraftLocator.resolveVantaDirectory("Windows 11", "/home/test", "relative-appdata", null));
        assertThrows(IllegalStateException.class,
                () -> MinecraftLocator.resolveVantaDirectory("Linux", "relative-home", null, null));
        assertThrows(IllegalStateException.class,
                () -> MinecraftLocator.resolveVantaDirectory("Mac OS X", "relative-home", null, null));
    }

    @Test
    void selectsNativeJavaExecutableName() {
        assertEquals("java.exe", Platform.javaExecutableName("Windows 11"));
        assertEquals("java", Platform.javaExecutableName("Linux"));
        assertEquals("java", Platform.javaExecutableName("Mac OS X"));
    }

    @Test
    void mapsSupportedReleaseTargetsAndRejectsUntestedCombinations() {
        assertEquals("windows-x64", Platform.distributionId("Windows 11", "AMD64"));
        assertEquals("linux-x64", Platform.distributionId("Linux", "x86_64"));
        assertEquals("linux-aarch64", Platform.distributionId("Linux", "aarch64"));
        assertEquals("macos-x64", Platform.distributionId("Mac OS X", "x86_64"));
        assertEquals("macos-aarch64", Platform.distributionId("Darwin", "arm64"));
        assertThrows(UnsupportedOperationException.class,
                () -> Platform.distributionId("Windows 11", "aarch64"));
        assertThrows(UnsupportedOperationException.class,
                () -> Platform.distributionId("FreeBSD", "x86_64"));
        assertThrows(UnsupportedOperationException.class,
                () -> Platform.javaExecutableName("FreeBSD"));
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
