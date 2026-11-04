package dev.itsdropper.vanta.launcher.modrinth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModrinthLoaderIdsTest {
    @Test
    void normalizesVantaLoaderDisplayNamesToModrinthIds() {
        assertEquals("legacyfabric", ModrinthLoaderIds.normalize("Legacy Fabric"));
        assertEquals("liteloader", ModrinthLoaderIds.normalize("LiteLoader"));
        assertEquals("rift", ModrinthLoaderIds.normalize("Rift"));
        assertEquals("nilloader", ModrinthLoaderIds.normalize("NilLoader"));
        assertEquals("modloader", ModrinthLoaderIds.normalize("ModLoader"));
        assertEquals("neoforge", ModrinthLoaderIds.normalize("NeoForge"));
    }

    @Test
    void matchesExactAndMinorFamilyMinecraftVersions() {
        assertTrue(ModrinthLoaderIds.supportsGameVersion(
                java.util.List.of("1.20.1", "1.20.2"), "1.20"));
        assertTrue(ModrinthLoaderIds.supportsGameVersion(
                java.util.List.of("1.20.1"), "1.20.1"));
        assertFalse(ModrinthLoaderIds.supportsGameVersion(
                java.util.List.of("1.19.4"), "1.20"));
    }

    @Test
    void onlyUsesKnownModrinthLoaderFacets() {
        assertTrue(ModrinthLoaderIds.supportsSearchFacet("Rift"));
        assertTrue(ModrinthLoaderIds.supportsSearchFacet("LiteLoader"));
        assertTrue(ModrinthLoaderIds.supportsSearchFacet("Legacy Fabric"));
        assertFalse(ModrinthLoaderIds.supportsSearchFacet("NilLoader"));
        assertFalse(ModrinthLoaderIds.supportsSearchFacet("ModLoader"));
    }
}
