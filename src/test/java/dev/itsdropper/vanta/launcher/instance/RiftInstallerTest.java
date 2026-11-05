package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiftInstallerTest {
    @Test
    void supportsOnlyOfficialMinecraft113Release() {
        assertEquals("1.0.4-87", RiftInstaller.findLatestLoaderVersion("1.13"));
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
        assertEquals("org.dimdev:rift:1.0.4-87",
                profile.path("libraries").get(0).path("name").asText());
        assertEquals("org.spongepowered:mixin:0.7.11-SNAPSHOT",
                profile.path("libraries").get(1).path("name").asText());
        assertEquals(RiftInstaller.MIXIN_REPOSITORY,
                profile.path("libraries").get(1).path("url").asText());
        assertEquals(
                "https://repo.spongepowered.org/repository/maven-public/org/spongepowered/mixin/"
                        + "0.7.11-SNAPSHOT/mixin-0.7.11-20180703.121122-1.jar",
                RiftInstaller.MIXIN_ARTIFACT_URL);
        assertTrue(profile.path("libraries").toString().contains("net.minecraft:launchwrapper:1.12"));
        assertTrue(profile.path("libraries").toString().contains("org.ow2.asm:asm-tree:6.2"));
    }
}
