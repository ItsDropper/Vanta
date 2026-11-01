package dev.itsdropper.vanta.launcher.environment;

import java.util.List;

/**
 * Deterministic comparison between a saved environment lockfile and a live instance.
 */
public record EnvironmentDriftReport(
        String baselineGeneratedAt,
        String observedAt,
        List<Change> changes
) {
    public EnvironmentDriftReport {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }

    public boolean isClean() {
        return changes.isEmpty();
    }

    public enum ChangeType {
        MINECRAFT_VERSION_CHANGED,
        LOADER_CHANGED,
        JAVA_REQUIREMENT_CHANGED,
        MOD_ADDED,
        MOD_REMOVED,
        MOD_MODIFIED,
        ENVIRONMENT_FILE_ADDED,
        ENVIRONMENT_FILE_REMOVED,
        ENVIRONMENT_FILE_MODIFIED
    }

    public record Change(
            ChangeType type,
            String filename,
            String expectedSha256,
            String actualSha256,
            String description
    ) {
    }
}
