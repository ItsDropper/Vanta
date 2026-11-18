package dev.itsdropper.vanta.launcher.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseManifestTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String MANIFEST = """
            {
              "schema_version": 1,
              "product": "vanta",
              "channel": "stable",
              "version": "1.3.3",
              "published_at": "2026-11-18T10:00:00Z",
              "min_launcher_version": "1.0.0",
              "release_notes_url": "https://github.com/ItsDropper/Vanta/releases/tag/v1.3.3",
              "artifacts": {
                "windows-x64": {
                  "file_name": "Vanta-1.3.3-windows-x64.zip",
                  "download_url": "https://github.com/ItsDropper/Vanta/releases/download/v1.3.3/Vanta-1.3.3-windows-x64.zip",
                  "size_bytes": 123456,
                  "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                }
              }
            }
            """;

    @Test
    void parsesMatchingPlatformArtifactAndSupportsGitBuildVersionSuffix() throws Exception {
        UpdateInfo info = ReleaseManifest.parse(JSON.readTree(MANIFEST), "1.3.2git", "windows-x64");
        assertEquals("1.3.3", info.getLatestVersion());
        assertEquals(123456, info.getSizeBytes());
        assertEquals("a".repeat(64), info.getSha256());
        assertNull(info.getChecksumUrl());
        assertEquals("1.0.0", info.getMinimumLauncherVersion());
        assertTrue(info.isUpdateAvailable());
    }

    @Test
    void returnsNoUpdateArtifactForUnsupportedPlatform() throws Exception {
        UpdateInfo info = ReleaseManifest.parse(JSON.readTree(MANIFEST), "1.3.2", "linux-x64");
        assertFalse(info.isUpdateAvailable());
        assertNull(info.getDownloadUrl());
    }

    @Test
    void rejectsUnexpectedArtifactHost() {
        String altered = MANIFEST.replace("https://github.com/ItsDropper",
                "https://evil.example/ItsDropper");
        assertThrows(IOException.class,
                () -> ReleaseManifest.parse(read(altered), "1.3.2", "windows-x64"));
    }

    @Test
    void rejectsArtifactUrlThatDoesNotMatchDeclaredFile() {
        String altered = MANIFEST.replace("Vanta-1.3.3-windows-x64.zip",
                "Vanta-1.3.2-windows-x64.zip");
        assertThrows(IOException.class,
                () -> ReleaseManifest.parse(read(altered), "1.3.2", "windows-x64"));
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        String altered = MANIFEST.replace("\"schema_version\": 1", "\"schema_version\": 2");
        assertThrows(IOException.class,
                () -> ReleaseManifest.parse(read(altered), "1.3.2", "windows-x64"));
    }

    @Test
    void rejectsUpdatesRequiringANewerLauncher() {
        String altered = MANIFEST.replace("\"min_launcher_version\": \"1.0.0\"",
                "\"min_launcher_version\": \"2.0.0\"");
        assertThrows(IOException.class,
                () -> ReleaseManifest.parse(read(altered), "1.3.2", "windows-x64"));
    }

    private static com.fasterxml.jackson.databind.JsonNode read(String value) {
        try {
            return JSON.readTree(value);
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }
}
