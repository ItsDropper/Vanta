package dev.itsdropper.vanta.launcher.instance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class SteinModManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesPublisherIndexAndValidatesRequiredFields() throws Exception {
        String source = "https://example.org/releases/latest/download/steinmods.json";
        String json = """
                {
                  "format": 1,
                  "name": "Example Publisher",
                  "author": "Example Author",
                  "homepage": "https://example.org",
                  "source": "https://example.org/releases/latest/download/steinmods.json",
                  "mods": [{
                    "id": "example-mod",
                    "modId": "examplemod",
                    "name": "Example Mod",
                    "file": "ExampleMod.steinmod",
                    "version": "1.2.3",
                    "url": "https://example.org/releases/download/v1.2.3/ExampleMod.steinmod",
                    "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "loader": "0.1.28",
                    "description": "An example Stein mod."
                  }]
                }
                """;

        List<SteinModManager.Release> releases = SteinModManager.parseIndex(
                json.getBytes(StandardCharsets.UTF_8), source);

        assertEquals(1, releases.size());
        SteinModManager.Release release = releases.getFirst();
        assertEquals("example-mod", release.id());
        assertEquals("examplemod", release.modId());
        assertEquals("Example Publisher", release.sourceName());
        assertEquals("0.1.28", release.loader());
        assertEquals(source, release.sourceUrl());
    }

    @Test
    void rejectsInvalidIndexFormatAndUnsafeDownloadUrls() {
        String invalidFormat = """
                {"format":2,"name":"Publisher","author":"Author","mods":[{}]}
                """;
        assertThrows(IOException.class, () -> SteinModManager.parseIndex(
                invalidFormat.getBytes(StandardCharsets.UTF_8),
                "https://example.org/steinmods.json"));

        String unsafeUrl = """
                {
                  "format":1,"name":"Publisher","author":"Author","mods":[{
                    "id":"example","modId":"example","name":"Example",
                    "file":"Example.steinmod","version":"1.0.0",
                    "url":"http://example.org/Example.steinmod",
                    "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "loader":"0.1.28"
                  }]
                }
                """;
        assertThrows(IOException.class, () -> SteinModManager.parseIndex(
                unsafeUrl.getBytes(StandardCharsets.UTF_8),
                "https://example.org/steinmods.json"));
    }

    @Test
    void validatesEmbeddedSteinManifestAndRejectsPathTraversal() throws Exception {
        Path archive = temporaryDirectory.resolve("Example.steinmod");
        createArchive(archive, """
                {"id":"examplemod","name":"Example Mod","version":"1.2.3","loader":"0.1.28"}
                """, false);

        SteinModManager.validateArchive(archive, release("examplemod", "1.2.3", "0.1.28"));

        Path unsafeArchive = temporaryDirectory.resolve("Unsafe.steinmod");
        createArchive(unsafeArchive, """
                {"id":"examplemod","name":"Example Mod","version":"1.2.3","loader":"0.1.28"}
                """, true);
        assertThrows(IOException.class, () -> SteinModManager.validateArchive(
                unsafeArchive, release("examplemod", "1.2.3", "0.1.28")));
    }

    @Test
    void rejectsManifestIdentityVersionAndLoaderMismatches() throws Exception {
        Path archive = temporaryDirectory.resolve("Mismatch.steinmod");
        createArchive(archive, """
                {"id":"othermod","name":"Example Mod","version":"1.2.4","loader":"0.1.99"}
                """, false);

        assertThrows(IOException.class, () -> SteinModManager.validateArchive(
                archive, release("examplemod", "1.2.3", "0.1.28")));
    }

    @Test
    void comparesNumericLoaderVersionsRatherThanLexicographically() throws Exception {
        assertTrue(SteinModManager.compareVersions("0.1.31", "0.1.28") > 0);
        assertEquals(0, SteinModManager.compareVersions("0.1.28", "0.1.28.0"));
        assertTrue(SteinModManager.compareVersions("0.1.9", "0.1.28") < 0);
    }

    private static SteinModManager.Release release(String modId, String version, String loader) {
        return new SteinModManager.Release(
                "example-mod", modId, "Example Mod", "Example.steinmod", version,
                "https://example.org/Example.steinmod",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                loader, "Example description", "Example Publisher",
                "https://example.org/steinmods.json");
    }

    private static void createArchive(Path archive, String manifest, boolean traversal)
            throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(java.nio.file.Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("steinmod.json"));
            output.write(manifest.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            if (traversal) {
                output.putNextEntry(new ZipEntry("../payload.txt"));
                output.write("unsafe".getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }
}
