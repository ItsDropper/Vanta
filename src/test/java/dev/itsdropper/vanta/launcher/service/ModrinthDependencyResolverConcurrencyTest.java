package dev.itsdropper.vanta.launcher.service;

import dev.itsdropper.vanta.launcher.modrinth.InstalledModScanner;
import dev.itsdropper.vanta.launcher.modrinth.ModrinthClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModrinthDependencyResolverConcurrencyTest {

    @Test
    void graphResolutionAndCacheClearingShareAnExclusiveResolverLock() throws Exception {
        assertTrue(Modifier.isSynchronized(
                ModrinthDependencyResolver.class.getMethod(
                        "resolveModGraph",
                        dev.itsdropper.vanta.launcher.model.Instance.class,
                        List.class,
                        String.class,
                        List.class
                ).getModifiers()
        ), "A resolver instance must not execute overlapping graph resolutions.");

        assertTrue(Modifier.isSynchronized(
                ModrinthDependencyResolver.class.getMethod("clearCaches").getModifiers()
        ), "Cache clearing must wait until an active graph resolution completes.");
    }

    @Test
    void cachesUsedByConcurrentPrefetchAreConcurrentMaps() throws Exception {
        ModrinthDependencyResolver resolver =
                new ModrinthDependencyResolver(new ModrinthClient(), new InstalledModScanner());

        assertConcurrentCache(resolver, "versionCache");
        assertConcurrentCache(resolver, "modMetadataCache");
        assertConcurrentCache(resolver, "modIdProjectCache");
        assertConcurrentCache(resolver, "fabricApiModuleCache");
    }

    private void assertConcurrentCache(ModrinthDependencyResolver resolver, String fieldName)
            throws Exception {
        Field field = ModrinthDependencyResolver.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        assertInstanceOf(ConcurrentMap.class, field.get(resolver),
                fieldName + " must support concurrent access.");
    }
}
