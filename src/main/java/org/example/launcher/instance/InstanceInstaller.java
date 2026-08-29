package org.example.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.launcher.model.Instance;

public class InstanceInstaller {

    // =============================================================
    // VANILLA
    // =============================================================

    public static Instance installVanilla(
            String name,
            String minecraftVersion
    ) throws Exception {

        System.out.println(
                "Installing Minecraft "
                        + minecraftVersion
        );

        JsonNode metadata =
                MinecraftVersionResolver.downloadMetadata(
                        minecraftVersion
                );

        Instance instance =
                InstanceManager.createInstance(
                        name,
                        minecraftVersion,
                        "Vanilla",
                        null
                );

        try {

            MinecraftFileInstaller.installClient(
                    instance,
                    metadata
            );

            MinecraftFileInstaller.installLibraries(
                    metadata
            );

            NativeInstaller.extract(
                    instance,
                    metadata
            );

            AssetInstaller.install(
                    instance,
                    metadata
            );

            MinecraftVersionResolver.saveMetadata(
                    instance,
                    metadata
            );

            System.out.println(
                    "Minecraft installation complete."
            );

            return instance;

        } catch (Exception e) {

            try {
                InstanceManager.deleteInstance(
                        instance
                );
            } catch (Exception ignored) {
            }

            throw e;
        }
    }

    // =============================================================
    // FABRIC
    // =============================================================

    public static Instance installFabric(
            String name,
            String minecraftVersion
    ) throws Exception {

        /*
         * Find the latest Fabric Loader compatible
         * with this Minecraft version.
         */
        String loaderVersion =
                FabricInstaller.findLatestLoaderVersion(
                        minecraftVersion
                );

        System.out.println(
                "Installing Fabric "
                        + loaderVersion
                        + " for Minecraft "
                        + minecraftVersion
        );

        JsonNode vanillaMetadata =
                MinecraftVersionResolver.downloadMetadata(
                        minecraftVersion
                );

        Instance instance =
                InstanceManager.createInstance(
                        name,
                        minecraftVersion,
                        "Fabric",
                        loaderVersion
                );

        try {

            // -----------------------------------------------------
            // VANILLA FILES
            // -----------------------------------------------------

            MinecraftFileInstaller.installClient(
                    instance,
                    vanillaMetadata
            );

            MinecraftFileInstaller.installLibraries(
                    vanillaMetadata
            );

            NativeInstaller.extract(
                    instance,
                    vanillaMetadata
            );

            AssetInstaller.install(
                    instance,
                    vanillaMetadata
            );

            // -----------------------------------------------------
            // FABRIC
            // -----------------------------------------------------

            JsonNode fabricProfile =
                    FabricInstaller.downloadProfile(
                            minecraftVersion,
                            loaderVersion
                    );

            FabricInstaller.installLibraries(
                    fabricProfile
            );

            JsonNode merged =
                    FabricInstaller.mergeProfile(
                            vanillaMetadata,
                            fabricProfile
                    );

            // -----------------------------------------------------
            // SAVE
            // -----------------------------------------------------

            MinecraftVersionResolver.saveMetadata(
                    instance,
                    merged
            );

            System.out.println(
                    "Fabric installation complete."
            );

            return instance;

        } catch (Exception e) {

            try {
                InstanceManager.deleteInstance(
                        instance
                );
            } catch (Exception ignored) {
            }

            throw e;
        }
    }
}

