package dev.itsdropper.vanta.launcher.update;

import dev.itsdropper.vanta.launcher.Platform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdateServicePlatformTest {
    @Test
    void createsVersionScopedSignedManifestUrlsForLinux() {
        assertEquals("linux-x64", Platform.distributionId("Linux", "amd64"));
        assertEquals("https://github.com/ItsDropper/Vanta/releases/download/v1.2.7/manifest.json",
                UpdateService.releaseManifestUrl("v1.2.7"));
        assertEquals("https://github.com/ItsDropper/Vanta/releases/download/v1.2.7/manifest.sig",
                UpdateService.releaseSignatureUrl("1.2.7"));
    }

    @Test
    void createsVersionScopedSignedManifestUrlsForWindowsAndMacos() {
        assertEquals("windows-x64", Platform.distributionId("Windows 11", "AMD64"));
        assertEquals("macos-aarch64", Platform.distributionId("Darwin", "arm64"));
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
}
