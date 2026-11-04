package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiteLoaderInstallerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void buildsLaunchWrapperOverlayFromOfficialStableMetadata() throws Exception {
        JsonNode metadata = MAPPER.readTree("""
                {
                  "repo": {"url": "http://dl.liteloader.com/versions/"},
                  "artefacts": {
                    "com.mumfrey:liteloader": {
                      "latest": {
                        "version": "1.7.10_04",
                        "file": "liteloader-1.7.10.jar",
                        "md5": "63ada46e033d0cb6782bada09ad5ca4e",
                        "libraries": [
                          {"name": "net.minecraft:launchwrapper:1.11"},
                          {"name": "org.ow2.asm:asm-all:5.0.3"}
                        ],
                        "tweakClass": "com.mumfrey.liteloader.launch.LiteLoaderTweaker"
                      }
                    }
                  }
                }
                """);

        JsonNode overlay = LiteLoaderInstaller.buildOverlay("1.7.10", metadata);

        assertEquals(LiteLoaderInstaller.LAUNCHWRAPPER_MAIN, overlay.path("mainClass").asText());
        assertEquals("com.mumfrey:liteloader:1.7.10_04",
                overlay.path("libraries").get(0).path("name").asText());
        assertEquals("https://dl.liteloader.com/versions/",
                overlay.path("libraries").get(0).path("url").asText());
        assertEquals(3, overlay.path("libraries").size());
        assertTrue(overlay.path("liteLoaderArtifact").asText().endsWith(".jar"));
    }

    @Test
    void rejectsVersionsWithoutStableArtifacts() throws Exception {
        JsonNode metadata = MAPPER.readTree("""
                {
                  "repo": {"url": "http://dl.liteloader.com/versions/"},
                  "snapshots": {
                    "com.mumfrey:liteloader": {
                      "latest": {"version": "1.8.9-SNAPSHOT", "file": "liteloader.jar", "libraries": []}
                    }
                  }
                }
                """);

        assertThrows(IllegalArgumentException.class,
                () -> LiteLoaderInstaller.buildOverlay("1.8.9", metadata));
    }

    @Test
    void rejectsUntrustedRepositories() throws Exception {
        JsonNode metadata = MAPPER.readTree("""
                {
                  "repo": {"url": "http://example.invalid/repository/"},
                  "artefacts": {
                    "com.mumfrey:liteloader": {
                      "latest": {
                        "version": "1.7.10_04",
                        "file": "liteloader.jar",
                        "md5": "63ada46e033d0cb6782bada09ad5ca4e",
                        "libraries": []
                      }
                    }
                  }
                }
                """);

        assertThrows(IllegalStateException.class,
                () -> LiteLoaderInstaller.buildOverlay("1.7.10", metadata));
    }
}
