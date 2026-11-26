package dev.itsdropper.vanta.launcher.bundle;

import java.util.List;

/**
 * Versioned, declarative description of a Vanta profile and its independently
 * selectable feature bundles. This is deliberately separate from InstancePreset:
 * presets describe starter configurations, while manifests describe maintained
 * profile content over time.
 */
public record VantaBundleManifest(
        int schemaVersion,
        String profileId,
        String profileVersion,
        List<String> minecraftVersions,
        String loader,
        List<Bundle> bundles
) {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public VantaBundleManifest {
        minecraftVersions = minecraftVersions == null ? List.of() : List.copyOf(minecraftVersions);
        bundles = bundles == null ? List.of() : List.copyOf(bundles);
    }

    public record Bundle(
            String id,
            String name,
            String description,
            boolean required,
            boolean defaultEnabled,
            List<Mod> mods
    ) {
        public Bundle {
            mods = mods == null ? List.of() : List.copyOf(mods);
        }
    }

    /**
     * A Modrinth project reference. versionId may be omitted to let the
     * compatibility resolver choose a suitable release; when present it pins
     * the profile to that exact Modrinth version.
     */
    public record Mod(
            String projectId,
            String versionId,
            boolean required,
            boolean defaultEnabled,
            GitHubRelease githubRelease
    ) {
        public Mod(String projectId, String versionId, boolean required, boolean defaultEnabled) {
            this(projectId, versionId, required, defaultEnabled, null);
        }
    }

    /** A platform-specific artifact published in the latest GitHub release. */
    public record GitHubRelease(String repository, String assetPrefix) {
    }
}
