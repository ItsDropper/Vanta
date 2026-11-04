package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
