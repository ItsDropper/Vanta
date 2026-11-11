package dev.itsdropper.vanta.launcher.update;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class UpdateDownloaderSecurityTest {

    @Test
    void acceptsPlainNumericReleaseVersions() throws Exception {
        assertEquals("1.3.2", UpdateDownloader.normalizeVersion("v1.3.2"));
        assertEquals("1.3.2", UpdateDownloader.normalizeVersion("1.3.2"));
        assertEquals("1.3", UpdateDownloader.normalizeVersion("V1.3"));
    }

    @Test
    void rejectsVersionsThatCouldEscapeTheUpdateDirectory() {
        assertThrows(IOException.class, () -> UpdateDownloader.normalizeVersion("../1.3.2"));
        assertThrows(IOException.class, () -> UpdateDownloader.normalizeVersion("1.3.2/../../other"));
        assertThrows(IOException.class, () -> UpdateDownloader.normalizeVersion(""));
        assertThrows(IOException.class, () -> UpdateDownloader.normalizeVersion(null));
    }

    @Test
    void onlyAcceptsHttpsGithubReleaseAssetUrls() throws Exception {
        URI uri = UpdateDownloader.validateInitialDownloadUrl(
                "https://github.com/ItsDropper/Vanta/releases/download/v1.3.2/Vanta.zip");
        assertEquals("github.com", uri.getHost());

        assertThrows(IOException.class,
                () -> UpdateDownloader.validateInitialDownloadUrl("http://github.com/update.zip"));
        assertThrows(IOException.class,
                () -> UpdateDownloader.validateInitialDownloadUrl("https://example.com/update.zip"));
        assertThrows(IOException.class,
                () -> UpdateDownloader.validateInitialDownloadUrl("https://github.com.example.com/update.zip"));
        assertThrows(IOException.class,
                () -> UpdateDownloader.validateInitialDownloadUrl("https://user@github.com/update.zip"));
        assertThrows(IOException.class,
                () -> UpdateDownloader.validateInitialDownloadUrl("https://github.com:8443/update.zip"));
        assertThrows(IOException.class,
                () -> UpdateDownloader.validateInitialDownloadUrl("not a URL"));
    }
}
