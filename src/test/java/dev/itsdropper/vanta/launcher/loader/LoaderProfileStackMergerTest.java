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
                  "minecraftArguments": "--tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker",
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
        String arguments = merged.path("minecraftArguments").asText();
        assertTrue(arguments.indexOf("FMLTweaker") < arguments.indexOf("LiteLoaderTweaker"));
        assertTrue(arguments.endsWith("--tweakClass \"example.extra.FinalTweaker\""));
        assertEquals("net.minecraft.launchwrapper.Launch", merged.path("mainClass").asText());
        assertFalse(merged.has("inheritsFrom"));
    }

    @Test
    void preservesDifferentLibraryVersionsWhenOperatingSystemRulesAreDisjoint() throws Exception {
        JsonNode base = MAPPER.readTree("""
                {
                  "mainClass":"net.minecraft.launchwrapper.Launch",
                  "libraries":[{
                    "name":"org.lwjgl.lwjgl:lwjgl:2.9.1",
                    "rules":[
                      {"action":"allow"},
                      {"action":"disallow","os":{"name":"osx"}}
                    ]
                  }]
                }
                """);
        JsonNode macOverlay = MAPPER.readTree("""
                {
                  "mainClass":"net.minecraft.launchwrapper.Launch",
                  "libraries":[{
                    "name":"org.lwjgl.lwjgl:lwjgl:2.9.2-nightly-20140822",
                    "rules":[
                      {"action":"allow","os":{"name":"osx"}}
                    ]
                  }]
                }
                """);

        JsonNode merged = LoaderProfileStackMerger.merge(
                base, List.of(macOverlay), List.of());

        assertEquals(2, merged.path("libraries").size());
        assertEquals("org.lwjgl.lwjgl:lwjgl:2.9.1",
                merged.path("libraries").get(0).path("name").asText());
        assertEquals("org.lwjgl.lwjgl:lwjgl:2.9.2-nightly-20140822",
                merged.path("libraries").get(1).path("name").asText());
    }

    @Test
    void rejectsProfilesWithIncompatibleMainClasses() throws Exception {
        JsonNode base = MAPPER.readTree("""
                {"mainClass":"example.VanillaMain","libraries":[]}
                """);
        JsonNode firstOverlay = MAPPER.readTree("""
                {"mainClass":"example.LaunchWrapperMain","libraries":[]}
                """);
        JsonNode conflictingOverlay = MAPPER.readTree("""
                {"mainClass":"example.OtherMain","libraries":[]}
                """);

        assertThrows(IllegalStateException.class,
                () -> LoaderProfileStackMerger.merge(
                        base, List.of(firstOverlay, conflictingOverlay), List.of()));
    }
}
