package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BabricInstallerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void buildsBabricOverlayOnTopOfFabricLoaderBootstrap() throws Exception {
        JsonNode fabricProfile = MAPPER.readTree("""
                {
                  "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
                  "libraries": []
                }
                """);

        JsonNode overlay = BabricInstaller.buildOverlay(fabricProfile);

        assertEquals(
                "net.fabricmc.loader.impl.launch.knot.KnotClient",
                overlay.path("mainClass").asText());
        assertEquals(13, overlay.path("libraries").size());
        assertTrue(overlay.path("libraries").toString().contains("babric:intermediary-upstream:b1.7.3"));
        assertTrue(overlay.path("minecraftArguments").asText().contains("${auth_session}"));
    }

    @Test
    void convertsLegacyLwjglNativeMapIntoDownloadClassifiers() throws Exception {
        JsonNode parsed = MAPPER.readTree("""
                {
                  "name": "org.lwjgl.lwjgl:lwjgl-platform:2.9.4+legacyfabric.17",
                  "url": "https://maven.legacyfabric.net/",
                  "natives": {
                    "windows": "natives-windows",
                    "linux": "natives-linux",
                    "osx": "natives-osx"
                  }
                }
                """);

        ObjectNode library = (ObjectNode) parsed;
        BabricInstaller.normalizeLegacyNativeLibrary(library);

        assertTrue(library.path("downloads").path("classifiers").has("natives-windows"));
        assertEquals(
                "org/lwjgl/lwjgl/lwjgl-platform/2.9.4+legacyfabric.17/"
                        + "lwjgl-platform-2.9.4+legacyfabric.17-natives-windows.jar",
                library.path("downloads").path("classifiers")
                        .path("natives-windows").path("path").asText());
        assertTrue(library.path("downloads").path("classifiers")
                .path("natives-windows").path("url").asText()
                .startsWith("https://repo.legacyfabric.net/legacyfabric/"));
        assertTrue(library.path("downloads").path("classifiers").has("natives-linux"));
        assertTrue(library.path("downloads").path("classifiers").has("natives-osx"));
    }

    @Test
    void replacesIncompleteArtifactMetadataForNativeOnlyLibraries() throws Exception {
        ObjectNode library = (ObjectNode) MAPPER.readTree("""
                {
                  "name": "org.lwjgl.lwjgl:lwjgl-platform:2.9.4+legacyfabric.17",
                  "url": "https://repo.legacyfabric.net/legacyfabric/",
                  "natives": {"windows": "natives-windows"},
                  "downloads": {"artifact": {}}
                }
                """);

        BabricInstaller.normalizeLegacyNativeLibrary(library);

        assertTrue(library.path("downloads").path("artifact").isMissingNode());
        assertTrue(library.path("downloads").path("classifiers")
                .path("natives-windows").path("path").asText()
                .endsWith("-natives-windows.jar"));
    }

    @Test
    void usesTheLoaderVersionPinnedByTheOfficialBabricInstance() throws Exception {
        assertEquals("0.19.5", BabricInstaller.findLatestLoaderVersion("b1.7.3"));
    }

    @Test
    void usesSupportedFabricMetaGameVersionForLegacyBabricBootstrap() {
        assertEquals("1.21.11", BabricInstaller.fabricProfileReferenceGameVersion());
    }

    @Test
    void rejectsMinecraftVersionsOutsideTheVerifiedBabricTarget() {
        assertThrows(IllegalArgumentException.class,
                () -> BabricInstaller.findLatestLoaderVersion("1.20.1"));
    }

    @Test
    void rejectsFabricProfilesWithoutALaunchBootstrap() throws Exception {
        JsonNode invalidProfile = MAPPER.readTree("""
                {"libraries":[]}
                """);

        assertThrows(IllegalStateException.class,
                () -> BabricInstaller.buildOverlay(invalidProfile));
    }
}
