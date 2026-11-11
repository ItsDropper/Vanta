package dev.itsdropper.vanta.launcher.service;

import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LaunchPerformanceTrackerTest {

    @TempDir
    Path temporaryDirectory;

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

        LaunchPerformanceTracker.Summary summary = LaunchPerformanceTracker.summaryForInstance("fabric-test");

        assertEquals(2, summary.baselineSamples());
        assertEquals(2, summary.optimizedSamples());
        assertEquals(200, summary.baselineMedianMillis());
        assertEquals(100, summary.optimizedMedianMillis());
    }

    @Test
    void keepsNoModControlSeparateFromModdedInstances() throws IOException {
        Path moddedDirectory = temporaryDirectory.resolve("fabric");
        Files.createDirectories(moddedDirectory.resolve("mods"));
        Files.createFile(moddedDirectory.resolve("mods").resolve("sample-mod.jar"));
        Instance modded = new Instance("fabric-test", "Fabric test", "1.21.11",
                "Fabric", "0.18.2", moddedDirectory);
        Instance vanilla = new Instance("vanilla-test", "Vanilla test", "1.21.11",
                "Vanilla", "", temporaryDirectory.resolve("vanilla"));

        LaunchPerformanceTracker.record(modded, false, 150_000_000L);
        LaunchPerformanceTracker.record(vanilla, true, 50_000_000L);

        assertEquals(1, LaunchPerformanceTracker.summaryForInstance("fabric-test").baselineSamples());
        assertEquals(1, LaunchPerformanceTracker.summaryForInstance("vanilla-test").optimizedSamples());
        assertEquals(0, LaunchPerformanceTracker.summaryForInstance("vanilla-test").baselineSamples());
        assertEquals("fabric-test", LaunchPerformanceTracker.summaryForMostRecent(true).instanceId());
        assertEquals("vanilla-test", LaunchPerformanceTracker.summaryForMostRecent(false).instanceId());
    }
}
