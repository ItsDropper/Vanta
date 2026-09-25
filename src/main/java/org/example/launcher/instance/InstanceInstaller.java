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

            // -----------------------------------------------------
            // VANILLA FILES
            // -----------------------------------------------------

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

            // -----------------------------------------------------
            // METADATA
            // -----------------------------------------------------

            MinecraftVersionResolver.saveMetadata(
                    instance,
                    metadata
            );

            // -----------------------------------------------------
            // COMPLETE
            // -----------------------------------------------------

            InstanceManager.markInstallationComplete(
                    instance
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

            // -----------------------------------------------------
            // COMPLETE
            // -----------------------------------------------------

            InstanceManager.markInstallationComplete(
                    instance
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

    public static Instance updateMinecraftVersion(
            Instance instance,
            String minecraftVersion
    ) throws Exception {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        if (minecraftVersion == null
                || minecraftVersion.isBlank()) {

            throw new IllegalArgumentException(
                    "Minecraft version cannot be empty."
            );
        }

        minecraftVersion =
                minecraftVersion.trim();

        System.out.println(
                "[Vanta Repair] Updating Minecraft version: "
                        + instance.getMinecraftVersion()
                        + " -> "
                        + minecraftVersion
        );

        /*
         * ---------------------------------------------------------
         * 1. DOWNLOAD TARGET MINECRAFT METADATA
         * ---------------------------------------------------------
         */

        JsonNode vanillaMetadata =
                MinecraftVersionResolver.downloadMetadata(
                        minecraftVersion
                );

        /*
         * ---------------------------------------------------------
         * 2. INSTALL TARGET MINECRAFT RUNTIME
         * ---------------------------------------------------------
         *
         * These methods are SHA-1 verified and only replace files
         * when the existing file does not match the target metadata.
         */

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

        /*
         * ---------------------------------------------------------
         * 3. REBUILD LOADER PROFILE
         * ---------------------------------------------------------
         */

        JsonNode finalMetadata =
                vanillaMetadata;

        String loader =
                instance.getLoader();

        String loaderVersion =
                instance.getLoaderVersion();

        if ("Fabric".equalsIgnoreCase(loader)) {

            if (loaderVersion == null
                    || loaderVersion.isBlank()) {

                throw new IllegalStateException(
                        "Fabric Loader version is missing."
                );
            }

            System.out.println(
                    "[Vanta Repair] Using Fabric Loader "
                            + loaderVersion
            );

            JsonNode fabricProfile =
                    FabricInstaller.downloadProfile(
                            minecraftVersion,
                            loaderVersion
                    );

            FabricInstaller.installLibraries(
                    fabricProfile
            );

            finalMetadata =
                    FabricInstaller.mergeProfile(
                            vanillaMetadata,
                            fabricProfile
                    );
        }

        /*
         * ---------------------------------------------------------
         * 4. SAVE FINAL VERSION METADATA
         * ---------------------------------------------------------
         */

        MinecraftVersionResolver.saveMetadata(
                instance,
                finalMetadata
        );

        /*
         * ---------------------------------------------------------
         * 5. UPDATE INSTANCE METADATA
         * ---------------------------------------------------------
         */

        Instance updatedInstance =
                InstanceManager.updateMinecraftVersion(
                        instance,
                        minecraftVersion
                );

        System.out.println(
                "[Vanta Repair] Minecraft version updated successfully."
        );

        return updatedInstance;
    }
}