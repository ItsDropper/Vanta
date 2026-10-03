package org.example.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.launcher.model.Instance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Repairs only Vanta-managed Minecraft installation files.
 *
 * User content such as mods, configs, saves, resource packs and
 * shader packs is deliberately left untouched.
 */
public final class InstanceRepairService {

    private InstanceRepairService() {
    }

    public static boolean isRepairableInstallationFailure(
            Throwable failure
    ) {
        if (failure == null) {
            return false;
        }

        Throwable current = failure;

        while (current != null) {
            String message = current.getMessage();

            if (message != null && !message.isBlank()) {
                String text = message.toLowerCase(Locale.ROOT);

                if (text.contains("library not found")
                        || text.contains("version metadata not found")
                        || text.contains("minecraft client jar not found")
                        || text.contains("native library not found")
                        || text.contains("native classifier not found")
                        || text.contains("forge installed, but no forge version metadata")
                        || text.contains("missing forge")
                        || text.contains("forge")
                                && text.contains("universal.jar")) {
                    return true;
                }
            }

            current = current.getCause();
        }

        return false;
    }

    public static void repair(
            Instance instance
    ) throws Exception {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        String minecraftVersion =
                requireValue(
                        instance.getMinecraftVersion(),
                        "Minecraft version"
                );

        String loader =
                requireValue(
                        instance.getLoader(),
                        "Loader"
                );

        System.out.println(
                "[Vanta Repair] Repairing "
                        + loader
                        + " instance "
                        + instance.getName()
        );

        JsonNode vanillaMetadata =
                MinecraftVersionResolver.downloadMetadata(
                        minecraftVersion
                );

        ensureInstanceDirectories(instance);

        /*
         * These operations are checksum-aware. Existing healthy files
         * are kept; missing or corrupted vanilla files are downloaded.
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

        if (loader.equalsIgnoreCase("fabric")) {
            repairFabric(
                    instance,
                    vanillaMetadata
            );
        } else if (loader.equalsIgnoreCase("forge")) {
            repairForge(
                    instance
            );
        } else if (loader.equalsIgnoreCase("vanilla")) {
            MinecraftVersionResolver.saveMetadata(
                    instance,
                    vanillaMetadata
            );
        } else {
            throw new IllegalStateException(
                    "Unsupported loader for automatic repair: "
                            + loader
            );
        }

        InstanceManager.markInstallationComplete(
                instance
        );
    }

    private static void repairFabric(
            Instance instance,
            JsonNode vanillaMetadata
    ) throws Exception {

        String loaderVersion =
                instance.getLoaderVersion();

        if (loaderVersion == null
                || loaderVersion.isBlank()) {
            loaderVersion =
                    FabricInstaller.findLatestLoaderVersion(
                            instance.getMinecraftVersion()
                    );
        }

        JsonNode profile =
                FabricInstaller.downloadProfile(
                        instance.getMinecraftVersion(),
                        loaderVersion
                );

        FabricInstaller.installLibraries(
                profile
        );

        JsonNode merged =
                FabricInstaller.mergeProfile(
                        vanillaMetadata,
                        profile
                );

        MinecraftVersionResolver.saveMetadata(
                instance,
                merged
        );

        /*
         * Repair stale instance metadata too. The directory and ID
         * stay exactly the same.
         */
        if (!loaderVersion.equals(instance.getLoaderVersion())) {
            Instance repaired =
                    new Instance(
                            instance.getId(),
                            instance.getName(),
                            instance.getMinecraftVersion(),
                            instance.getLoader(),
                            loaderVersion,
                            instance.getDirectory()
                    );

            InstanceManager.saveInstance(
                    repaired
            );
        }
    }

    private static void repairForge(
            Instance instance
    ) throws Exception {

        String loaderVersion =
                instance.getLoaderVersion();

        if (loaderVersion == null
                || loaderVersion.isBlank()) {
            loaderVersion =
                    ForgeInstaller.findLatestLoaderVersion(
                            instance.getMinecraftVersion()
                    );
        }

        ForgeInstaller.installForge(
                instance,
                loaderVersion
        );

        if (!loaderVersion.equals(instance.getLoaderVersion())) {
            Instance repaired =
                    new Instance(
                            instance.getId(),
                            instance.getName(),
                            instance.getMinecraftVersion(),
                            instance.getLoader(),
                            loaderVersion,
                            instance.getDirectory()
                    );

            InstanceManager.saveInstance(
                    repaired
            );
        }
    }

    private static void ensureInstanceDirectories(
            Instance instance
    ) throws Exception {

        Path directory =
                instance.getDirectory();

        Files.createDirectories(directory);

        String[] directories = {
                "mods",
                "config",
                "resourcepacks",
                "shaderpacks",
                "saves",
                "logs",
                "screenshots",
                "natives"
        };

        for (String name : directories) {
            Files.createDirectories(
                    directory.resolve(name)
            );
        }
    }

    private static String requireValue(
            String value,
            String name
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    name + " is missing from the instance metadata."
            );
        }

        return value.trim();
    }
}
