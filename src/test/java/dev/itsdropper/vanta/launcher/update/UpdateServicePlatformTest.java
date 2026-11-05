package dev.itsdropper.vanta.launcher.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class UpdateServicePlatformTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void selectsOnlyTheMatchingLinuxReleaseAndChecksum() throws Exception {
        withPlatform("Linux", "amd64", () -> {
            JsonNode release = JSON.readTree("""
                    {
                      "tag_name": "v1.2.7",
                      "assets": [
                        {"name":"Vanta-1.2.7-windows-x64.zip","browser_download_url":"https://example.invalid/windows.zip"},
                        {"name":"Vanta-1.2.7-windows-x64.zip.sha256","browser_download_url":"https://example.invalid/windows.sha256"},
                        {"name":"Vanta-1.2.7-linux-x64.zip","browser_download_url":"https://example.invalid/linux.zip"},
                        {"name":"Vanta-1.2.7-linux-x64.zip.sha256","browser_download_url":"https://example.invalid/linux.sha256"}
                      ]
                    }
                    """);
            String[] selected = new UpdateService().findUpdateAssets(release);
            assertEquals("https://example.invalid/linux.zip", selected[0]);
            assertEquals("https://example.invalid/linux.sha256", selected[1]);
        });
    }

    @Test
    void reportsNoUpdateWhenLatestReleaseHasNoCompatiblePlatformPackage() throws Exception {
        withPlatform("Linux", "amd64", () -> {
            JsonNode release = JSON.readTree("""
                    {
                      "tag_name": "v1.2.8",
                      "body": "Release notes",
                      "assets": [
                        {"name":"Vanta-1.2.8-windows-x64.zip","browser_download_url":"https://example.invalid/windows.zip"},
                        {"name":"Vanta-1.2.8-windows-x64.zip.sha256","browser_download_url":"https://example.invalid/windows.sha256"}
                      ]
                    }
                    """);
            UpdateInfo info = new UpdateService().parseLatestRelease(release, "1.2.7");
            assertFalse(info.isUpdateAvailable());
            assertEquals("1.2.7", info.getLatestVersion());
            assertNull(info.getDownloadUrl());
        });
    }

    @Test
    void doesNotOfferLegacyWindowsPackageToLinuxOrMac() throws Exception {
        withPlatform("Linux", "amd64", () -> {
            JsonNode release = JSON.readTree("""
                    {
                      "tag_name": "v1.2.7",
                      "assets": [
                        {"name":"Vanta-1.2.7.zip","browser_download_url":"https://example.invalid/windows.zip"},
                        {"name":"Vanta-1.2.7.zip.sha256","browser_download_url":"https://example.invalid/windows.sha256"}
                      ]
                    }
                    """);
            assertThrows(IOException.class, () -> new UpdateService().findUpdateAssets(release));
        });
    }

    @Test
    void retainsLegacyWindowsPackageFallback() throws Exception {
        withPlatform("Windows 11", "AMD64", () -> {
            JsonNode release = JSON.readTree("""
                    {
                      "tag_name": "v1.2.7",
                      "assets": [
                        {"name":"Vanta-1.2.7.zip","browser_download_url":"https://example.invalid/windows.zip"},
                        {"name":"Vanta-1.2.7.zip.sha256","browser_download_url":"https://example.invalid/windows.sha256"}
                      ]
                    }
                    """);
            String[] selected = new UpdateService().findUpdateAssets(release);
            assertEquals("https://example.invalid/windows.zip", selected[0]);
            assertEquals("https://example.invalid/windows.sha256", selected[1]);
        });
    }

    private static void withPlatform(String os, String arch, ThrowingRunnable action) throws Exception {
        String previousOs = System.getProperty("os.name");
        String previousArch = System.getProperty("os.arch");
        try {
            System.setProperty("os.name", os);
            System.setProperty("os.arch", arch);
            action.run();
        } finally {
            restore("os.name", previousOs);
            restore("os.arch", previousArch);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
