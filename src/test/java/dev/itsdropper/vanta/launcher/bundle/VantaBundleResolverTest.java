package dev.itsdropper.vanta.launcher.bundle;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VantaBundleResolverTest {
    private final VantaBundleResolver resolver = new VantaBundleResolver();

    @Test
    void selectsRequiredAndDefaultBundlesAndDeduplicatesSharedProjects() throws Exception {
        VantaBundleManifest manifest = manifest();

        VantaBundleResolver.Resolution result = resolver.resolve(
                manifest, "1.21.11", "fabric", null, null);

        assertEquals(Set.of("core", "performance"), Set.copyOf(result.selectedBundleIds()));
        assertEquals(2, result.mods().size());
        assertEquals("core", result.mods().get(0).bundleId());
    }

    @Test
    void allowsUserToDisableDefaultOptionalBundle() throws Exception {
        VantaBundleResolver.Resolution result = resolver.resolve(
                manifest(), "1.21.11", "fabric", Set.of(), null);

        assertEquals(Set.of("core"), Set.copyOf(result.selectedBundleIds()));
        assertEquals(1, result.mods().size());
    }

    @Test
    void allowsSelectingAnOptionalModInsideAnEnabledBundle() throws Exception {
        VantaBundleResolver.Resolution result = resolver.resolve(
                manifest(), "1.21.11", "fabric", Set.of("performance"), Set.of("optional-project"));

        assertTrue(result.mods().stream().anyMatch(mod -> mod.projectId().equals("optional-project")));
    }

    @Test
    void explicitModSelectionCanDisableDefaultEnabledOptionalMods() throws Exception {
        VantaBundleManifest withDefaultOptional = new VantaBundleManifest(1, "vanta-client", "1.0.0",
                java.util.List.of("1.21.11"), "fabric", java.util.List.of(
                new VantaBundleManifest.Bundle("core", "Core", "Required core.",
                        true, true, java.util.List.of(
                        new VantaBundleManifest.Mod("required-project", null, true, true),
                        new VantaBundleManifest.Mod("default-optional", null, false, true)))
        ));

        VantaBundleResolver.Resolution result = resolver.resolve(
                withDefaultOptional, "1.21.11", "fabric", Set.of("core"), Set.of());

        assertEquals(1, result.mods().size());
        assertEquals("required-project", result.mods().get(0).projectId());
    }


    @Test
    void installsGitHubReleaseComponentOnlyWhenItsOptionalBundleIsSelected() throws Exception {
        VantaBundleManifest manifest = new VantaBundleManifest(1, "vanta-client", "1.0.0",
                java.util.List.of("1.21.11"), "fabric", java.util.List.of(
                new VantaBundleManifest.Bundle("core", "Core", "Required core.", true, true,
                        java.util.List.of(new VantaBundleManifest.Mod("fabric-api", null, true, true))),
                new VantaBundleManifest.Bundle("vanta-client", "Vanta Client", "Optional client.", false, false,
                        java.util.List.of(new VantaBundleManifest.Mod("vanta-client", null, true, true,
                                new VantaBundleManifest.GitHubRelease("ItsDropper/Vanta-Client", "dropperutils-"))))
        ));

        VantaBundleResolver.Resolution defaultSelection =
                resolver.resolve(manifest, "1.21.11", "fabric", Set.of("core"), Set.of());
        assertEquals(1, defaultSelection.mods().size());

        VantaBundleResolver.Resolution optedIn =
                resolver.resolve(manifest, "1.21.11", "fabric", Set.of("core", "vanta-client"), Set.of());
        assertEquals(2, optedIn.mods().size());
        VantaBundleResolver.ResolvedMod client = optedIn.mods().get(1);
        assertEquals("vanta-client", client.projectId());
        assertEquals("ItsDropper/Vanta-Client", client.githubRelease().repository());
    }

    @Test
    void rejectsSelectingModsFromDisabledBundles() {
        IOException failure = assertThrows(IOException.class, () -> resolver.resolve(
                manifest(), "1.21.11", "fabric", Set.of(), Set.of("optional-project")));
        assertTrue(failure.getMessage().contains("not part of an enabled bundle"));
    }

    @Test
    void rejectsUnsupportedMinecraftAndLoader() {
        assertThrows(IOException.class, () -> resolver.resolve(
                manifest(), "1.20.1", "fabric", null, null));
        assertThrows(IOException.class, () -> resolver.resolve(
                manifest(), "1.21.11", "quilt", null, null));
    }

    @Test
    void rejectsUnknownBundleIds() {
        IOException failure = assertThrows(IOException.class, () -> resolver.resolve(
                manifest(), "1.21.11", "fabric", Set.of("missing"), null));
        assertTrue(failure.getMessage().contains("Unknown bundle selected"));
    }


    @Test
    void rejectsConflictingPinnedVersionsForSharedProject() {
        VantaBundleManifest conflicting = new VantaBundleManifest(1, "vanta-client", "1.0.0",
                java.util.List.of("1.21.11"), "fabric", java.util.List.of(
                new VantaBundleManifest.Bundle("core", "Vanta Core", "Required foundation.",
                        true, true, java.util.List.of(
                        new VantaBundleManifest.Mod("shared-project", "version-a", true, true))),
                new VantaBundleManifest.Bundle("performance", "Performance", "Optional optimizations.",
                        false, true, java.util.List.of(
                        new VantaBundleManifest.Mod("shared-project", "version-b", false, true)))
        ));

        IOException failure = assertThrows(IOException.class, () -> resolver.resolve(
                conflicting, "1.21.11", "fabric", null, null));
        assertTrue(failure.getMessage().contains("Conflicting pinned versions"));
    }

    @Test
    void requiredFlagWinsWhenProjectIsSharedAcrossBundles() throws Exception {
        VantaBundleManifest shared = new VantaBundleManifest(1, "vanta-client", "1.0.0",
                java.util.List.of("1.21.11"), "fabric", java.util.List.of(
                new VantaBundleManifest.Bundle("core", "Vanta Core", "Required foundation.",
                        true, true, java.util.List.of(
                        new VantaBundleManifest.Mod("shared-project", "same-version", false, true))),
                new VantaBundleManifest.Bundle("performance", "Performance", "Optional optimizations.",
                        false, true, java.util.List.of(
                        new VantaBundleManifest.Mod("shared-project", "same-version", true, true)))
        ));

        VantaBundleResolver.Resolution result = resolver.resolve(
                shared, "1.21.11", "fabric", null, null);

        assertEquals(1, result.mods().size());
        assertTrue(result.mods().get(0).required());
    }

    private static VantaBundleManifest manifest() {
        return new VantaBundleManifest(1, "vanta-client", "1.0.0",
                java.util.List.of("1.21.11"), "fabric", java.util.List.of(
                new VantaBundleManifest.Bundle("core", "Vanta Core", "Required foundation.",
                        true, true, java.util.List.of(
                        new VantaBundleManifest.Mod("core-project", "core-version", true, true))),
                new VantaBundleManifest.Bundle("performance", "Performance", "Optional optimizations.",
                        false, true, java.util.List.of(
                        new VantaBundleManifest.Mod("sodium-project", null, true, true),
                        new VantaBundleManifest.Mod("optional-project", null, false, false)))
        ));
    }
}
