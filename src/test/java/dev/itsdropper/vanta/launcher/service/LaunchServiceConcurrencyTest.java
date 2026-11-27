package dev.itsdropper.vanta.launcher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class LaunchServiceConcurrencyTest {

    @Test
    void stateAndProcessGettersDoNotWaitForTheLaunchMonitor() throws Exception {
        LaunchService service = new LaunchService(null);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            synchronized (service) {
                Future<?> read = executor.submit(() -> {
                    assertEquals(LaunchService.LaunchState.IDLE, service.getState());
                    assertFalse(service.isRunning());
                    assertNull(service.getProcess());
                    assertNull(service.getRunningInstance());
                    assertNull(service.getRunningServerTarget());
                    assertNull(service.getLastFailure());
                    assertNull(service.getFailedInstance());
                });

                // Launch preparation holds the service monitor while doing I/O.
                // UI-facing getters must remain responsive during that period.
                read.get(1, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void oneConfigFabricModIdMapsToItsCanonicalModrinthSlug() {
        assertEquals("oneconfig", ModrinthService.modrinthSlugForDependency("oneconfigv1"));
        assertEquals("oneconfig", ModrinthService.modrinthSlugForDependency("OneConfigV1"));
        assertEquals("sodium", ModrinthService.modrinthSlugForDependency("sodium"));
    }
}
