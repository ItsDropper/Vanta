package dev.itsdropper.vanta.launcher.update;

import dev.itsdropper.vanta.launcher.Platform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdateServicePlatformTest {
    @Test
    void createsVersionScopedSignedManifestUrlsForLinux() throws Exception {
        assertEquals("linux-x64", distributionId("Linux", "amd64"));
        assertEquals("https://github.com/ItsDropper/Vanta/releases/download/v1.2.7/manifest.json",
                UpdateService.releaseManifestUrl("v1.2.7"));
        assertEquals("https://github.com/ItsDropper/Vanta/releases/download/v1.2.7/manifest.sig",
                UpdateService.releaseSignatureUrl("1.2.7"));
    }

    @Test
    void createsVersionScopedSignedManifestUrlsForWindowsAndMacos() throws Exception {
        assertEquals("windows-x64", distributionId("Windows 11", "AMD64"));
        assertEquals("macos-aarch64", distributionId("Darwin", "arm64"));
        assertEquals("https://github.com/ItsDropper/Vanta/releases/download/v1.2.8/manifest.json",
                UpdateService.releaseManifestUrl("1.2.8"));
    }

    @Test
    void rejectsUnsafeVersionsBeforeConstructingReleaseUrls() {
        for (String version : new String[]{"../latest", "1.2.3?download=1", "01.2.3", "v"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> UpdateService.releaseManifestUrl(version), version);
            assertThrows(IllegalArgumentException.class,
                    () -> UpdateService.releaseSignatureUrl(version), version);
        }
    }

    private static String distributionId(String os, String arch) {
        String previousOs = System.getProperty("os.name");
        String previousArch = System.getProperty("os.arch");
        try {
            System.setProperty("os.name", os);
            System.setProperty("os.arch", arch);
            return Platform.distributionId();
        } finally {
            restore("os.name", previousOs);
            restore("os.arch", previousArch);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }
}
