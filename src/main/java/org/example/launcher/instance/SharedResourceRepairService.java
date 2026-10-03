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

        if (repairLibraries) {
            deleteZeroByteFiles(org.example.launcher.MinecraftLocator.getLibrariesDirectory());
        }

        if (repairAssets) {
            deleteZeroByteFiles(org.example.launcher.MinecraftLocator.getVantaDirectory().resolve("assets"));
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

    private static void deleteZeroByteFiles(java.nio.file.Path root) throws java.io.IOException {
        if (root == null || !java.nio.file.Files.isDirectory(root)) {
            return;
        }

        try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(root)) {
            paths.filter(java.nio.file.Files::isRegularFile)
                    .filter(path -> {
                        try {
                            return java.nio.file.Files.size(path) == 0;
                        } catch (java.io.IOException ignored) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            java.nio.file.Files.deleteIfExists(path);
                        } catch (java.io.IOException ignored) {
                            // A locked file will be reported by the State scan after repair.
                        }
                    });
        }
    }
}
