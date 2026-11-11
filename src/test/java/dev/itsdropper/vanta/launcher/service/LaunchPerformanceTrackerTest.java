package dev.itsdropper.vanta.launcher.service;

import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LaunchPerformanceTrackerTest {

    @AfterEach
    void clearSamples() {
        LaunchPerformanceTracker.clear();
    }

    @Test
    void calculatesSeparateMediansForBaselineAndExperimentalModdedLaunches() {
        Instance modded = new Instance("fabric-test", "Fabric test", "1.21.11",
                "Fabric", "0.18.2", Path.of("build/test-instance"));

        LaunchPerformanceTracker.record(modded, false, 100_000_000L);
        LaunchPerformanceTracker.record(modded, false, 300_000_000L);
        LaunchPerformanceTracker.record(modded, true, 80_000_000L);
        LaunchPerformanceTracker.record(modded, true, 120_000_000L);

        LaunchPerformanceTracker.Summary summary = LaunchPerformanceTracker.summary(true);

        assertEquals(2, summary.baselineSamples());
        assertEquals(2, summary.optimizedSamples());
        assertEquals(200, summary.baselineMedianMillis());
        assertEquals(100, summary.optimizedMedianMillis());
    }

    @Test
    void keepsVanillaControlGroupSeparateFromModdedInstances() {
        Instance modded = new Instance("fabric-test", "Fabric test", "1.21.11",
                "Fabric", "0.18.2", Path.of("build/test-instance"));
        Instance vanilla = new Instance("vanilla-test", "Vanilla test", "1.21.11",
                "Vanilla", "", Path.of("build/vanilla-instance"));

        LaunchPerformanceTracker.record(modded, false, 150_000_000L);
        LaunchPerformanceTracker.record(vanilla, true, 50_000_000L);

        assertEquals(1, LaunchPerformanceTracker.summary(true).baselineSamples());
        assertEquals(1, LaunchPerformanceTracker.summary(false).optimizedSamples());
        assertEquals(0, LaunchPerformanceTracker.summary(false).baselineSamples());
    }
}
