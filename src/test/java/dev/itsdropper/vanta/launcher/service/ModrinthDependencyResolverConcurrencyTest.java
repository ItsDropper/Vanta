package dev.itsdropper.vanta.launcher.service;

import dev.itsdropper.vanta.launcher.instance.InstalledMod;
import dev.itsdropper.vanta.launcher.modrinth.ModrinthVersion;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

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
        assertConcurrentCache("versionCache");
        assertConcurrentCache("modMetadataCache");
        assertConcurrentCache("modIdProjectCache");
        assertConcurrentCache("fabricApiModuleCache");
    }

    private void assertConcurrentCache(String fieldName) throws Exception {
        Field field = ModrinthDependencyResolver.class.getDeclaredField(fieldName);
        assertTrue(ConcurrentMap.class.isAssignableFrom(field.getType())
                        || ConcurrentMap.class.isAssignableFrom(
                                field.getType().getInterfaces().length == 0
                                        ? field.getType()
                                        : field.getType().getInterfaces()[0]),
                fieldName + " must support concurrent access.");
    }
}
