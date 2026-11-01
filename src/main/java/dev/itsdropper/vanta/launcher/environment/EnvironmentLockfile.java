package dev.itsdropper.vanta.launcher.environment;

import java.util.List;

/**
 * Portable description of the resolved, locally observed parts of a Vanta instance.
 *
 * <p>Artifact paths are intentionally relative to their managed directory roots. Machine-specific Java paths,
 * account data, worlds, and private server details do not belong in this manifest.</p>
 */
public record EnvironmentLockfile(
        int schemaVersion,
        String generatedAt,
        String instanceId,
        String minecraftVersion,
        String loader,
        String loaderVersion,
        Integer javaMajorVersion,
        List<ModArtifact> mods,
        List<EnvironmentArtifact> environmentFiles,
        Integer requiredJavaMajorVersion
) {
    public static final int CURRENT_SCHEMA_VERSION = 4;

    /** Backward-compatible constructor for callers creating schema 1-3 fixtures or records. */
    public EnvironmentLockfile(
            int schemaVersion,
            String generatedAt,
            String instanceId,
            String minecraftVersion,
            String loader,
            String loaderVersion,
            Integer javaMajorVersion,
            List<ModArtifact> mods,
            List<EnvironmentArtifact> environmentFiles
    ) {
        this(schemaVersion, generatedAt, instanceId, minecraftVersion, loader, loaderVersion,
                javaMajorVersion, mods, environmentFiles, null);
    }

    public EnvironmentLockfile {
        mods = mods == null ? List.of() : List.copyOf(mods);
        environmentFiles = environmentFiles == null ? List.of() : List.copyOf(environmentFiles);
    }

    /** Files from instance folders and resolved Minecraft runtime roots, stored with portable relative paths. */
    public record EnvironmentArtifact(
            String category,
            String relativePath,
            long sizeBytes,
            String sha256
    ) {
    }

    public record ModArtifact(
            String filename,
            long sizeBytes,
            String sha256,
            String modId,
            String modVersion
    ) {
    }
}
