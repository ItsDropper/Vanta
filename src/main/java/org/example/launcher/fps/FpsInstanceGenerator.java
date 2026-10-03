package org.example.launcher.fps;

import org.example.launcher.instance.InstanceInstaller;
import org.example.launcher.modrinth.ModrinthProject;
import org.example.launcher.model.Instance;
import org.example.launcher.service.ModrinthService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class FpsInstanceGenerator {

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

        if (minecraftVersion == null || minecraftVersion.isBlank()) {
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
            instance = InstanceInstaller.installFabric(
                    name.trim(),
                    minecraftVersion.trim()
            );

            ModrinthService modrinth = new ModrinthService();

            List<String> projectSlugs =
                    new ArrayList<>(BASE_MODS);

            boolean nvidiumRequested =
                    hardware.supportsNvidium();

            if (nvidiumRequested) {
                projectSlugs.add("nvidium");
            }

            int workers = Math.min(8, Math.max(1, projectSlugs.size()));
            ExecutorService executor =
                    Executors.newFixedThreadPool(workers);

            List<Future<ModrinthProject>> futures =
                    new ArrayList<>(projectSlugs.size());

            try {
                for (String slug : projectSlugs) {
                    futures.add(executor.submit(() -> {
                        try {
                            return modrinth.getProjectBySlug(slug);
                        } catch (IOException | InterruptedException e) {
                            throw new CompletionException(e);
                        }
                    }));
                }

                List<ModrinthProject> roots =
                        new ArrayList<>(projectSlugs.size());

                boolean nvidiumInstalled = false;

                for (int i = 0; i < futures.size(); i++) {
                    try {
                        ModrinthProject project = futures.get(i).get();

                        if (project != null) {
                            roots.add(project);

                            if (nvidiumRequested
                                    && "nvidium".equals(projectSlugs.get(i))) {
                                nvidiumInstalled = true;
                            }
                        }
                    } catch (ExecutionException e) {
                        Throwable cause = e.getCause();

                        if (cause instanceof CompletionException
                                && cause.getCause() != null) {
                            cause = cause.getCause();
                        }

                        if (cause instanceof InterruptedException interrupted) {
                            throw interrupted;
                        }

                        if (cause instanceof IOException io) {
                            throw new IOException(
                                    "Failed to resolve FPS mod '"
                                            + projectSlugs.get(i)
                                            + "' from Modrinth: "
                                            + io.getMessage(),
                                    io
                            );
                        }

                        throw new IOException(
                                "Failed to resolve FPS mod '"
                                        + projectSlugs.get(i)
                                        + "' from Modrinth.",
                                cause
                        );
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
            } finally {
                executor.shutdownNow();
            }

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
