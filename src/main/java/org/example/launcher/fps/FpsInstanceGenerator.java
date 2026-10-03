package org.example.launcher.fps;

import org.example.launcher.instance.InstanceInstaller;
import org.example.launcher.modrinth.ModrinthProject;
import org.example.launcher.model.Instance;
import org.example.launcher.service.ModrinthService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class FpsInstanceGenerator {

    /*
     * This is intentionally a compatibility-first performance stack.
     * Modrinth resolves exact versions for the selected Minecraft version.
     *
     * Nvidium is added only after Vanta positively identifies a compatible
     * NVIDIA GPU. It is not installed speculatively.
     */
    private static final List<String> BASE_MODS = List.of(
            "fabric-api",
            "sodium",
            "lithium",
            "ferrite-core",
            "immediatelyfast",
            "entityculling",
            "moreculling",
            "dynamic-fps",
            "badoptimizations",
            "better-block-entities",
            "modernfix-mvus",
            "sodium-extra",
            "particle-core",
            "reeses-sodium-options",
            "modmenu"
    );

    private FpsInstanceGenerator() {
    }

    public static Result generate(
            String name,
            String minecraftVersion
    ) throws Exception {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Instance name cannot be blank."
            );
        }

        if (minecraftVersion == null
                || minecraftVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "Minecraft version cannot be blank."
            );
        }

        FpsHardwareProfile hardware =
                FpsHardwareDetector.detect();

        FpsLearningEngine.Selection learning =
                FpsLearningEngine.select(
                        hardware,
                        minecraftVersion
                );

        Instance instance = null;

        try {
            instance =
                    InstanceInstaller.installFabric(
                            name.trim(),
                            minecraftVersion.trim()
                    );

            ModrinthService modrinth =
                    new ModrinthService();

            List<ModrinthProject> roots =
                    new ArrayList<>();

            for (String slug : BASE_MODS) {
                try {
                    ModrinthProject project =
                            modrinth.getProjectBySlug(slug);

                    if (project != null) {
                        roots.add(project);
                    }
                } catch (IOException e) {
                    throw new IOException(
                            "Failed to resolve FPS mod '" + slug + "' from Modrinth: "
                                    + e.getMessage(),
                            e
                    );
                }
            }

            boolean nvidiumInstalled = false;

            if (hardware.supportsNvidium()) {
                ModrinthProject nvidium =
                        modrinth.getProjectBySlug(
                                "nvidium"
                        );

                if (nvidium != null) {
                    roots.add(nvidium);
                    nvidiumInstalled = true;
                }
            }

            List<ModrinthService.ResolvedMod> resolved =
                    modrinth.resolveModGraph(
                            instance,
                            roots,
                            null,
                            List.of()
                    );

            modrinth.installResolvedGraph(
                    instance,
                    resolved
            );

            FpsOptionsOptimizer.optimize(
                    instance,
                    hardware,
                    learning.profile()
            );

            return new Result(
                    instance,
                    hardware,
                    nvidiumInstalled,
                    resolved.size(),
                    learning.profile(),
                    learning.trainingSamples(),
                    learning.learned()
            );

        } catch (Exception e) {
            if (instance != null) {
                try {
                    org.example.launcher.instance.InstanceManager
                            .deleteInstance(instance);
                } catch (Exception cleanupError) {
                    e.addSuppressed(cleanupError);
                }
            }

            throw e;
        }
    }

    public record Result(
            Instance instance,
            FpsHardwareProfile hardware,
            boolean nvidiumInstalled,
            int installedModCount,
            FpsTuningProfile tuningProfile,
            int trainingSamples,
            boolean learned
    ) {
    }
}
