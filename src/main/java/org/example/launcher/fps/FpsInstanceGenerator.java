package org.example.launcher.fps;

import org.example.launcher.instance.InstanceInstaller;
import org.example.launcher.modrinth.ModrinthProject;
import org.example.launcher.model.Instance;
import org.example.launcher.service.ModrinthService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class FpsInstanceGenerator {

    private static final List<String> BASE_MODS = List.of(
            "fabric-api",
            "sodium",
            "lithium",
            "ferrite-core",
            "immediatelyfast",
            "entityculling",
            "moreculling",
            "dynamic-fps"
    );

    private FpsInstanceGenerator() {
    }

    public static Result generate(
            String name,
            String minecraftVersion
    ) throws Exception {

        FpsHardwareProfile hardware =
                FpsHardwareDetector.detect();

        Instance instance =
                InstanceInstaller.installFabric(
                        name,
                        minecraftVersion
                );

        ModrinthService modrinth =
                new ModrinthService();

        List<ModrinthProject> roots =
                new ArrayList<>();

        for (String slug : BASE_MODS) {
            ModrinthProject project =
                    modrinth.getProjectBySlug(slug);

            if (project == null) {
                throw new IOException(
                        "Required FPS mod was not found on Modrinth: "
                                + slug
                );
            }

            roots.add(project);
        }

        boolean nvidiumInstalled = false;

        if (hardware.supportsNvidium()) {
            ModrinthProject nvidium =
                    modrinth.getProjectBySlug("nvidium");

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
                hardware
        );

        return new Result(
                instance,
                hardware,
                nvidiumInstalled,
                resolved.size()
        );
    }

    public record Result(
            Instance instance,
            FpsHardwareProfile hardware,
            boolean nvidiumInstalled,
            int installedModCount
    ) {
    }
}
