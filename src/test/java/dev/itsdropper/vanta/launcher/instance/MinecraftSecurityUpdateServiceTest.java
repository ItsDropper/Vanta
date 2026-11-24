package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftSecurityUpdateServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void identifiesMinecraftVersionsThatUseTheModernFontEngine() {
        assertFalse(MinecraftSecurityUpdateService.requiresModernFontEngine("1.20.4"));
        assertFalse(MinecraftSecurityUpdateService.requiresModernFontEngine("1.8.9"));
        assertFalse(MinecraftSecurityUpdateService.requiresModernFontEngine("b1.7.3"));
        assertFalse(MinecraftSecurityUpdateService.requiresModernFontEngine("a1.2.6"));
        assertTrue(MinecraftSecurityUpdateService.requiresModernFontEngine("1.20.5"));
        assertTrue(MinecraftSecurityUpdateService.requiresModernFontEngine("1.21.11"));
        assertTrue(MinecraftSecurityUpdateService.requiresModernFontEngine("26.3"));
        assertTrue(MinecraftSecurityUpdateService.requiresModernFontEngine("26.4-snapshot-10"));
    }

    @Test
    void requiresMojangsPatchedFreetypeBuild() {
        assertTrue(MinecraftSecurityUpdateService.isPatchedFreeTypeVersion("3.4.3-mojang-2.15.0"));
        assertTrue(MinecraftSecurityUpdateService.isPatchedFreeTypeVersion("3.4.3-mojang-2.16.0"));
        assertFalse(MinecraftSecurityUpdateService.isPatchedFreeTypeVersion("3.4.3"));
        assertFalse(MinecraftSecurityUpdateService.isPatchedFreeTypeVersion("3.4.3-mojang-2.14.3"));
        assertFalse(MinecraftSecurityUpdateService.isPatchedFreeTypeVersion("3.4.3-mojang-2.15.0-rc1"));
    }

    @Test
    void rebasesOfficialFreetypeLibraryWithoutRemovingOtherDependencies() throws Exception {
        ObjectNode instance = (ObjectNode) JSON.readTree("""
                {
                  "libraries": [
                    {"name":"org.lwjgl:lwjgl:3.4.3"},
                    {"name":"net.fabricmc:fabric-loader:0.18.1"},
                    {"name":"org.lwjgl:lwjgl-freetype:3.4.3"},
                    {"name":"com.example:loader-addon:1.0.0"}
                  ]
                }
                """);
        JsonNode official = JSON.readTree("""
                [
                  {"name":"org.lwjgl:lwjgl:3.4.4"},
                  {"name":"org.lwjgl:lwjgl-freetype:3.4.3-mojang-2.15.0"},
                  {"name":"org.lwjgl:lwjgl-glfw:3.4.4"}
                ]
                """);

        MinecraftSecurityUpdateService.replaceFreetypeLibraries(instance, official);

        JsonNode libraries = instance.path("libraries");
        assertEquals(4, libraries.size());
        assertEquals("org.lwjgl:lwjgl:3.4.3", libraries.get(0).path("name").asText());
        assertEquals("net.fabricmc:fabric-loader:0.18.1", libraries.get(1).path("name").asText());
        assertEquals("org.lwjgl:lwjgl-freetype:3.4.3-mojang-2.15.0", libraries.get(2).path("name").asText());
        assertEquals("com.example:loader-addon:1.0.0", libraries.get(3).path("name").asText());
        assertTrue(libraries.toString().contains("com.example:loader-addon:1.0.0"));
    }

    @Test
    void keepsOtherLibrariesAndReplacesOnlyOldFreetype() throws Exception {
        ObjectNode instance = (ObjectNode) JSON.readTree("""
                {
                  "libraries": [
                    {"name":"net.fabricmc:fabric-loader:0.18.1"},
                    {"name":"org.lwjgl:lwjgl-freetype:3.4.3"},
                    {"name":"com.example:loader-addon:1.0.0"}
                  ]
                }
                """);
        JsonNode official = JSON.readTree("""
                [{"name":"org.lwjgl:lwjgl-freetype:3.4.3-mojang-2.15.0"}]
                """);

        MinecraftSecurityUpdateService.replaceFreetypeLibraries(instance, official);

        JsonNode libraries = instance.path("libraries");
        assertEquals(3, libraries.size());
        assertEquals("net.fabricmc:fabric-loader:0.18.1", libraries.get(0).path("name").asText());
        assertEquals("org.lwjgl:lwjgl-freetype:3.4.3-mojang-2.15.0", libraries.get(1).path("name").asText());
        assertEquals("com.example:loader-addon:1.0.0", libraries.get(2).path("name").asText());
    }
}
