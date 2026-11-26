package dev.itsdropper.vanta.launcher.bundle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Applies a user's bundle and per-mod selections to a compatible profile.
 * The resolver is deterministic and performs no network or filesystem work.
 */
public final class VantaBundleResolver {

    public Resolution resolve(
            VantaBundleManifest manifest,
            String minecraftVersion,
            String loader,
            Set<String> selectedBundleIds,
            Set<String> selectedModProjectIds
    ) throws IOException {
        VantaBundleRepository.validate(manifest);
        if (!manifest.minecraftVersions().contains(minecraftVersion)) {
            throw new IOException("Profile " + manifest.profileId()
                    + " does not support Minecraft " + minecraftVersion + ".");
        }
        if (loader == null || !manifest.loader().equalsIgnoreCase(loader)) {
            throw new IOException("Profile " + manifest.profileId()
                    + " requires the " + manifest.loader() + " loader.");
        }

        Set<String> requestedBundles = selectedBundleIds == null ? Set.of() : Set.copyOf(selectedBundleIds);
        Set<String> requestedMods = selectedModProjectIds == null ? Set.of() : Set.copyOf(selectedModProjectIds);
        Map<String, VantaBundleManifest.Bundle> bundlesById = new LinkedHashMap<>();
        for (VantaBundleManifest.Bundle bundle : manifest.bundles()) {
            bundlesById.put(bundle.id(), bundle);
        }

        for (String id : requestedBundles) {
            if (!bundlesById.containsKey(id)) {
                throw new IOException("Unknown bundle selected: " + id);
            }
        }

        Set<String> selectedBundles = new LinkedHashSet<>();
        for (VantaBundleManifest.Bundle bundle : manifest.bundles()) {
            if (bundle.required() || bundle.defaultEnabled() || requestedBundles.contains(bundle.id())) {
                selectedBundles.add(bundle.id());
            }
        }
        // An explicitly unchecked default bundle must stay unchecked. Callers
        // represent this by supplying the complete selected set, including
        // only currently enabled bundles, rather than an additive selection.
        if (selectedBundleIds != null) {
            selectedBundles.removeIf(id -> {
                VantaBundleManifest.Bundle bundle = bundlesById.get(id);
                return !bundle.required() && bundle.defaultEnabled() && !requestedBundles.contains(id);
            });
            selectedBundles.addAll(requestedBundles);
        }

        Map<String, ResolvedMod> resolved = new LinkedHashMap<>();
        Set<String> knownProjects = new LinkedHashSet<>();
        for (String bundleId : selectedBundles) {
            VantaBundleManifest.Bundle bundle = bundlesById.get(bundleId);
            for (VantaBundleManifest.Mod mod : bundle.mods()) {
                knownProjects.add(mod.projectId());
                if (!mod.required() && !mod.defaultEnabled()
                        && !requestedMods.contains(mod.projectId())) {
                    continue;
                }
                ResolvedMod candidate = new ResolvedMod(
                        mod.projectId(), mod.versionId(), bundle.id(), mod.required());
                ResolvedMod previous = resolved.get(mod.projectId());
                if (previous != null && !java.util.Objects.equals(previous.versionId(), candidate.versionId())) {
                    throw new IOException("Conflicting pinned versions for Modrinth project " + mod.projectId()
                            + " in bundles " + previous.bundleId() + " and " + candidate.bundleId() + ".");
                }
                if (previous == null) {
                    resolved.put(mod.projectId(), candidate);
                } else if (candidate.required() && !previous.required()) {
                    resolved.put(mod.projectId(), new ResolvedMod(
                            previous.projectId(), previous.versionId(), previous.bundleId(), true));
                }
            }
        }

        for (String projectId : requestedMods) {
            if (!knownProjects.contains(projectId)) {
                throw new IOException("Selected mod is not part of an enabled bundle: " + projectId);
            }
        }

        List<ResolvedMod> mods = new ArrayList<>(resolved.values());
        return new Resolution(manifest.profileId(), manifest.profileVersion(),
                List.copyOf(selectedBundles), List.copyOf(mods));
    }

    public record ResolvedMod(String projectId, String versionId, String bundleId, boolean required) {
    }

    public record Resolution(
            String profileId,
            String profileVersion,
            List<String> selectedBundleIds,
            List<ResolvedMod> mods
    ) {
        public Resolution {
            selectedBundleIds = List.copyOf(selectedBundleIds);
            mods = List.copyOf(mods);
        }
    }
}
