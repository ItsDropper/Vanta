package dev.itsdropper.vanta.launcher.bundle;

import java.util.List;

/**
 * Per-instance record of a user's Vanta profile choices and Vanta-managed
 * installation ownership. User-installed mods are intentionally not tracked
 * here and must never be inferred from the instance mods directory.
 */
public record VantaBundleState(
        int schemaVersion,
        String profileId,
        String profileVersion,
        List<String> selectedBundleIds,
        List<String> selectedModProjectIds,
        List<String> installedManagedProjectIds,
        String dismissedOfferVersion
) {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public VantaBundleState {
        selectedBundleIds = selectedBundleIds == null ? List.of() : List.copyOf(selectedBundleIds);
        selectedModProjectIds = selectedModProjectIds == null ? List.of() : List.copyOf(selectedModProjectIds);
        installedManagedProjectIds = installedManagedProjectIds == null
                ? List.of() : List.copyOf(installedManagedProjectIds);
    }
}
