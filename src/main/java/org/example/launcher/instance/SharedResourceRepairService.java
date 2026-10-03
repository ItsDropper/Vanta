package org.example.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.launcher.model.Instance;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Repairs Vanta's shared Minecraft libraries and assets from the
 * versions actually installed in the launcher.
 *
 * This deliberately does not touch instance user data.
 */
public final class SharedResourceRepairService {

    private SharedResourceRepairService() {
    }

    public static void repair(
            List<Instance> instances,
            boolean repairLibraries,
            boolean repairAssets
    ) throws Exception {
        if (instances == null || instances.isEmpty()) {
            throw new IllegalStateException(
                    "No installed Minecraft versions are available to rebuild shared resources."
            );
        }

        if (!repairLibraries && !repairAssets) {
            return;
        }

        Set<String> versions = new LinkedHashSet<>();
        for (Instance instance : instances) {
            if (instance != null
                    && instance.getMinecraftVersion() != null
                    && !instance.getMinecraftVersion().isBlank()) {
                versions.add(instance.getMinecraftVersion().trim());
            }
        }

        if (versions.isEmpty()) {
            throw new IllegalStateException(
                    "No Minecraft versions are available for shared resource repair."
            );
        }

        for (String version : versions) {
            JsonNode metadata =
                    MinecraftVersionResolver.downloadMetadata(version);

            if (repairLibraries) {
                MinecraftFileInstaller.installLibraries(metadata);
            }

            if (repairAssets) {
                AssetInstaller.install(metadata);
            }
        }
    }
}
