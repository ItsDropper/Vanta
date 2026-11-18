package dev.itsdropper.vanta.launcher.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class UpdateServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void normalizesExplicitVersionTags() {
        assertEquals("1.2.3", UpdateService.normalizeTargetVersion("v1.2.3"));
        assertEquals("1.2.3", UpdateService.normalizeTargetVersion("1.2.3"));
    }

    @Test
    void rejectsVersionsThatCouldAlterReleaseApiPath() {
        for (String value : new String[]{"", "v", "../latest", "1.2.3?per_page=100",
                "1.2", "1.2.3-rc.1", "01.2.3", "1.02.3", "1.2.03"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> UpdateService.normalizeTargetVersion(value), value);
        }
    }

    @Test
    void malformedReleaseTagsAreNotReportedAsMissingPackages() {
        UpdateService service = new UpdateService();
        var release = read("""
                {"tag_name":"not-a-version","assets":[]}
                """);
        assertThrows(IOException.class, () -> service.parseLatestRelease(release, "1.2.3"));
    }

    @Test
    void validReleaseWithoutCurrentPlatformPackageReturnsNoUpdate() throws Exception {
        UpdateService service = new UpdateService();
        var release = read("""
                {"tag_name":"v1.2.4","body":"Release notes","assets":[]}
                """);
        UpdateInfo info = service.parseLatestRelease(release, "1.2.3");
        assertEquals("1.2.3", info.getLatestVersion());
        assertNull(info.getDownloadUrl());
        assertFalse(info.isUpdateAvailable());
    }

    @Test
    void duplicateJsonKeysAreRejectedByStrictParserConfiguration() {
        var strictMapper = UpdateService.createMetadataMapper();
        assertThrows(IOException.class,
                () -> strictMapper.readTree("{\"tag_name\":\"v1.2.3\",\"tag_name\":\"v9.9.9\"}"));
    }

    private static com.fasterxml.jackson.databind.JsonNode read(String value) {
        try {
            return JSON.readTree(value);
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }

    // Retain the manual live-service diagnostic entry point for maintainers.
    public static void main(String[] args) throws Exception {
        UpdateChecker checker = new UpdateChecker();
        UpdateInfo info = checker.check().join();
        if (info == null || info.getLatestVersion() == null || info.getLatestVersion().isBlank()) {
            throw new AssertionError("UpdateService returned no release version.");
        }
        System.out.println("Current version: " + info.getCurrentVersion());
        System.out.println("Latest version: " + info.getLatestVersion());
        System.out.println("Update available: " + info.isUpdateAvailable());
        System.out.println("UpdateService test passed.");
    }
}
