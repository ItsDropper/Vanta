package dev.itsdropper.vanta.launcher.modrinth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModrinthLoaderIdsTest {
    @Test
    void unsupportedCustomLoadersUseVersionMetadataInsteadOfSearchFacets() {
        assertTrue(ModrinthClient.loaderFacetForSearch("Stein").isEmpty());
        assertTrue(ModrinthClient.loaderFacetForSearch("NilLoader").isEmpty());
        assertEquals(java.util.List.of("java-agent"),
                ModrinthClient.loaderFacetForSearch("VantaLoader"));
        assertEquals(java.util.List.of("neoforge"),
                ModrinthClient.loaderFacetForSearch("NeoForge"));
    }

    @Test
    void mapsVantaLoaderToItsModrinthAgentIdentifier() {
        assertEquals("java-agent", ModrinthLoaderIds.normalize("VantaLoader"));
        assertEquals("stein", ModrinthLoaderIds.normalize("Stein"));
    }

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
    void resolverUsesCanonicalLoaderIdsForVersionFiltering() {
        var resolver = new dev.itsdropper.vanta.launcher.service.ModrinthDependencyResolver(
                new ModrinthClient(), new InstalledModScanner());
        assertEquals("legacyfabric", resolver.normalizeLoader("Legacy Fabric"));
        assertEquals("neoforge", resolver.normalizeLoader("NeoForge"));
        assertEquals("nilloader", resolver.normalizeLoader("NilLoader"));
        assertEquals("bta-babric", resolver.normalizeLoader("BTA Babric"));
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
