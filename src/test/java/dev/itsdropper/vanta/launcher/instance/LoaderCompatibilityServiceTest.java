package dev.itsdropper.vanta.launcher.instance;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LoaderCompatibilityServiceTest {
    @Test
    void successfulLookupReportsAvailableVersion() {
        var result = LoaderCompatibilityService.evaluate(() -> "0.16.9");
        assertEquals(LoaderCompatibilityService.Status.AVAILABLE, result.status());
        assertEquals("0.16.9", result.loaderVersion());
    }

    @Test
    void explicitNoMatchingReleaseReportsUnsupported() {
        var result = LoaderCompatibilityService.evaluate(() -> {
            throw new IllegalArgumentException("No loader release for this Minecraft version.");
        });
        assertEquals(LoaderCompatibilityService.Status.UNSUPPORTED, result.status());
        assertNull(result.loaderVersion());
    }

    @Test
    void emptyMetadataResultReportsUnsupported() {
        var result = LoaderCompatibilityService.evaluate(() -> null);
        assertEquals(LoaderCompatibilityService.Status.UNSUPPORTED, result.status());
    }

    @Test
    void transportFailureIsNotMisreportedAsUnsupported() {
        var result = LoaderCompatibilityService.evaluate(() -> {
            throw new IOException("Metadata host is unavailable.");
        });
        assertEquals(LoaderCompatibilityService.Status.CHECK_FAILED, result.status());
        assertNull(result.loaderVersion());
    }

    @Test
    void wrappedTransportFailureIsNotMisreportedAsUnsupported() {
        var result = LoaderCompatibilityService.evaluate(() -> {
            throw new IllegalStateException("Metadata lookup failed.", new IOException("Connection reset."));
        });
        assertEquals(LoaderCompatibilityService.Status.CHECK_FAILED, result.status());
    }

    @Test
    void unexpectedRuntimeFailureRemainsRetryable() {
        var result = LoaderCompatibilityService.evaluate(() -> {
            throw new RuntimeException("Unexpected metadata format.");
        });
        assertEquals(LoaderCompatibilityService.Status.CHECK_FAILED, result.status());
    }

    @Test
    void reportPreservesOrderAndSeparatesFailedChecks() {
        java.util.Map<String, LoaderCompatibilityService.Result> results = new java.util.LinkedHashMap<>();
        results.put("Fabric", new LoaderCompatibilityService.Result(
                LoaderCompatibilityService.Status.AVAILABLE, "0.16.9"));
        results.put("Quilt", new LoaderCompatibilityService.Result(
                LoaderCompatibilityService.Status.CHECK_FAILED, null));
        results.put("Forge", new LoaderCompatibilityService.Result(
                LoaderCompatibilityService.Status.UNSUPPORTED, null));
        results.put("Vanilla", new LoaderCompatibilityService.Result(
                LoaderCompatibilityService.Status.AVAILABLE, "vanilla"));
        var report = new LoaderCompatibilityService.Report("1.21.11", results);

        assertEquals(java.util.List.of("Fabric", "Vanilla"), report.availableLoaders());
        assertEquals(java.util.List.of("Quilt"), report.failedLoaders());
        org.junit.jupiter.api.Assertions.assertTrue(report.hasFailures());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> report.results().put("Rift",
                        new LoaderCompatibilityService.Result(
                                LoaderCompatibilityService.Status.AVAILABLE, "1.0")));
    }
}