package dev.itsdropper.vanta.launcher.environment;

import java.util.List;

/**
 * Portable description of the resolved, locally observed parts of a Vanta instance.
 *
 * <p>Paths are intentionally relative (filenames only). Machine-specific Java paths,
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
        List<EnvironmentArtifact> environmentFiles
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public EnvironmentLockfile {
        mods = mods == null ? List.of() : List.copyOf(mods);
        environmentFiles = environmentFiles == null ? List.of() : List.copyOf(environmentFiles);
    }

    /** Files from resourcepacks/, shaderpacks/, and config/, stored with portable relative paths. */
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
