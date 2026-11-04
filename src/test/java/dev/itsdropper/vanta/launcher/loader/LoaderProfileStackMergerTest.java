package dev.itsdropper.vanta.launcher.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoaderProfileStackMergerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void mergesLibrariesAndPreservesExplicitTweakerOrder() throws Exception {
        JsonNode base = MAPPER.readTree("""
                {
                  "mainClass": "net.minecraft.client.main.Main",
                  "inheritsFrom": "1.12.2",
                  "minecraftArguments": "--username Steve",
                  "libraries": [{"name":"org.example:base:1.0"}]
                }
                """);
        JsonNode forge = MAPPER.readTree("""
                {
                  "mainClass": "net.minecraft.launchwrapper.Launch",
                  "minecraftArguments": "--tweakClass \\"net.minecraftforge.fml.common.launcher.FMLTweaker\\"",
                  "libraries": [{"name":"org.example:forge:2.0"}]
                }
                """);
        JsonNode legacy = MAPPER.readTree("""
                {
                  "mainClass": "net.minecraft.launchwrapper.Launch",
                  "minecraftArguments": "--tweakClass com.mumfrey.liteloader.launch.LiteLoaderTweaker",
                  "libraries": [{"name":"org.example:base:1.1"}]
                }
                """);

        JsonNode merged = LoaderProfileStackMerger.merge(
                base,
                List.of(forge, legacy),
                List.of("example.extra.FinalTweaker"));

        assertEquals(2, merged.path("libraries").size());
        assertEquals("org.example:base:1.1", merged.path("libraries").get(0).path("name").asText());
        assertTrue(merged.path("minecraftArguments").asText()
                .indexOf("FMLTweaker") < merged.path("minecraftArguments").asText()
                .indexOf("LiteLoaderTweaker"));
        assertTrue(merged.path("minecraftArguments").asText().endsWith(
                "--tweakClass \\"example.extra.FinalTweaker\\""));
        assertFalse(merged.has("inheritsFrom"));
    }

    @Test
    void rejectsProfilesWithIncompatibleMainClasses() throws Exception {
        JsonNode base = MAPPER.readTree("""
                {"mainClass":"example.VanillaMain","libraries":[]}
                """);
        JsonNode overlay = MAPPER.readTree("""
                {"mainClass":"example.OtherMain","libraries":[]}
                """);

        assertThrows(IllegalStateException.class,
                () -> LoaderProfileStackMerger.merge(base, List.of(overlay), List.of()));
    }
}
