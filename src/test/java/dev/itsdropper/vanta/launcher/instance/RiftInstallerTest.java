package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiftInstallerTest {
    @Test
    void supportsOnlyOfficialMinecraft113Release() {
        assertEquals("1.0.4-66", RiftInstaller.findLatestLoaderVersion("1.13"));
        assertThrows(IllegalArgumentException.class,
                () -> RiftInstaller.findLatestLoaderVersion("1.13.1"));
        assertThrows(IllegalArgumentException.class,
                () -> RiftInstaller.findLatestLoaderVersion("1.13.2"));
        assertThrows(IllegalArgumentException.class,
                () -> RiftInstaller.findLatestLoaderVersion("1.12.2"));
    }

    @Test
    void buildsOriginalLaunchWrapperProfile() {
        JsonNode profile = RiftInstaller.buildOverlay("1.13");
        assertEquals("net.minecraft.launchwrapper.Launch",
                profile.path("mainClass").asText());
        assertEquals("org.dimdev:rift:1.0.4-66",
                profile.path("libraries").get(0).path("name").asText());
        assertEquals("org.dimdev:mixin:0.7.11-SNAPSHOT",
                profile.path("libraries").get(1).path("name").asText());
        assertTrue(profile.path("libraries").toString().contains("net.minecraft:launchwrapper:1.12"));
    }
}
