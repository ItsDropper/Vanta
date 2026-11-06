package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import dev.itsdropper.vanta.launcher.model.Instance;

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
                        || text.contains("modloader bootstrap class missing")
                        || text.contains("nilloader agent is missing or invalid")
                        || text.contains("vantaloader agent is missing or invalid")
                        || text.contains("stein's java 25 runtime is missing")
                        || text.contains("official minecraft client mappings are missing")
                        || text.contains("minecraft mappings") && text.contains("missing")
                        || text.contains("rift library is missing")
                        || text.contains("rift artifact")
                        || text.contains("native classifier not found")
                        || text.contains("asset index missing")
                        || text.contains("asset index not found")
                        || text.contains("invalid or corrupt jarfile")
                        || text.contains("zip end header not found")
                        || text.contains("invalid loc header")
                        || text.contains("corrupt archive")
                        || text.contains("minecraft client jar is corrupt")
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
        // Instance metadata stores display names (for example "Legacy Fabric"),
        // while installer dispatch uses canonical IDs ("legacyfabric").
        String loaderKey = loader.replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);

        if (loaderKey.equals("stein") && !SteinInstaller.supportsMinecraftVersion(minecraftVersion)) {
            throw new IllegalStateException("Stein instances support Minecraft 1.8.9 only.");
        }

        System.out.println(
                "[Vanta Repair] Repairing "
                        + loader
                        + " instance "
                        + instance.getName()
        );

        // Preserve user-managed mods/configuration before repair. This is separate
        // from the metadata rollback snapshot below and deliberately excludes worlds.
        InstanceRecoveryService.RecoveryPoint recoveryPoint =
                InstanceRecoveryService.createRecoveryPoint(instance, "Automatic snapshot before instance repair");
        System.out.println("[Vanta Repair] User-content recovery point: " + recoveryPoint.id());

        // Save a rollback snapshot before touching instance metadata.
        Path backup = dev.itsdropper.vanta.launcher.repair.RepairBackupService
                .createSnapshot(instance.getDirectory());
        System.out.println("[Vanta Repair] Metadata rollback snapshot: " + backup);

        JsonNode vanillaMetadata =
                MinecraftVersionResolver.downloadMetadata(
                        minecraftVersion
                );

        ensureInstanceDirectories(instance);

        if (loaderKey.equals("stein")) {
            repairStein(instance, vanillaMetadata);
            return;
        }

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

        if (loaderKey.equals("fabric")) {
            repairFabric(
                    instance,
                    vanillaMetadata
            );
        } else if (loaderKey.equals("legacyfabric")) {
            repairLegacyFabric(
                    instance,
                    vanillaMetadata
            );
        } else if (loaderKey.equals("forge")) {
            repairForge(
                    instance
            );
        } else if (loaderKey.equals("quilt")) {
            repairQuilt(
                    instance,
                    vanillaMetadata
            );
        } else if (loaderKey.equals("babric")) {
            repairBabric(instance, vanillaMetadata);
        } else if (loaderKey.equals("nilloader")) {
            NilLoaderInstaller.install(instance);
            MinecraftVersionResolver.saveMetadata(instance, vanillaMetadata);
        } else if (loaderKey.equals("vantaloader")) {
            // Repair is also the explicit update path: resolve the current main
            // commit to an immutable release tag and persist that exact version.
            String latestLoaderVersion = VantaLoaderInstaller.findLatestLoaderVersion();
            Instance updated = new Instance(
                    instance.getId(), instance.getName(), instance.getMinecraftVersion(),
                    instance.getLoader(), latestLoaderVersion, instance.getDirectory());
            MinecraftVersionResolver.saveMetadata(updated, vanillaMetadata);
            MinecraftMappingsInstaller.install(updated, vanillaMetadata);
            VantaLoaderInstaller.install(updated);
            if (!latestLoaderVersion.equals(instance.getLoaderVersion())) {
                InstanceManager.saveInstance(updated);
            }
            instance = updated;
        } else if (loaderKey.equals("modloader")) {
            MinecraftVersionResolver.saveMetadata(instance, vanillaMetadata);
            ModLoaderInstaller.install(instance);
        } else if (loaderKey.equals("rift")) {
            dev.itsdropper.vanta.launcher.loader.LoaderStackManager.clear(instance);
            MinecraftVersionResolver.saveMetadata(instance, vanillaMetadata);
            RiftInstaller.install(instance);
        } else if (loaderKey.equals("liteloader")) {
            dev.itsdropper.vanta.launcher.loader.LoaderStackManager.clear(instance);
            MinecraftVersionResolver.saveMetadata(instance, vanillaMetadata);
            String repairedLoaderVersion = LiteLoaderInstaller.install(instance);
            if (!repairedLoaderVersion.equals(instance.getLoaderVersion())) {
                InstanceManager.saveInstance(new Instance(
                        instance.getId(), instance.getName(), instance.getMinecraftVersion(),
                        instance.getLoader(), repairedLoaderVersion, instance.getDirectory()));
            }
        } else if (loaderKey.equals("vanilla")) {
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

        if (!loaderKey.equals("liteloader") && !loaderKey.equals("rift") && !loaderKey.equals("stein")) {
            dev.itsdropper.vanta.launcher.loader.LoaderStackManager.refreshBaseAndApply(instance);
        }

        InstanceManager.markInstallationComplete(
                instance
        );
    }

    private static void repairStein(
            Instance instance,
            JsonNode vanillaMetadata
    ) throws Exception {
        Path instanceDirectory = instance.getDirectory().toAbsolutePath().normalize();
        Path rollback = Files.createTempDirectory(instanceDirectory, ".stein-repair-rollback-");
        Path minecraftDirectory = instanceDirectory.resolve("minecraft");
        Path steinDirectory = instanceDirectory.resolve("stein");

        Path client = minecraftDirectory.resolve("client.jar");
        Path versionMetadata = minecraftDirectory.resolve("version.json");
        Path runtime = steinDirectory.resolve("runtime");
        Path installMetadata = steinDirectory.resolve("install.json");
        Path natives = instanceDirectory.resolve("natives");

        Path backupClient = rollback.resolve("client.jar");
        Path backupVersion = rollback.resolve("version.json");
        Path backupRuntime = rollback.resolve("runtime");
        Path backupInstall = rollback.resolve("install.json");
        Path backupNatives = rollback.resolve("natives");

        try {
            copyFileIfPresent(client, backupClient);
            copyFileIfPresent(versionMetadata, backupVersion);
            copyTreeIfPresent(runtime, backupRuntime);
            copyFileIfPresent(installMetadata, backupInstall);
            copyTreeIfPresent(natives, backupNatives);

            try {
                MinecraftFileInstaller.installClient(instance, vanillaMetadata);
                MinecraftFileInstaller.installLibraries(vanillaMetadata);
                NativeInstaller.extract(instance, vanillaMetadata);
                AssetInstaller.install(instance, vanillaMetadata);

                String repairedVersion = SteinInstaller.install(instance, vanillaMetadata);
                Instance repaired = instance;
                if (!repairedVersion.equals(instance.getLoaderVersion())) {
                    repaired = new Instance(
                            instance.getId(), instance.getName(), instance.getMinecraftVersion(),
                            instance.getLoader(), repairedVersion, instance.getDirectory());
                    InstanceManager.saveInstance(repaired);
                }
                InstanceManager.markInstallationComplete(repaired);
            } catch (Exception failure) {
                restoreFile(backupClient, client, failure);
                restoreFile(backupVersion, versionMetadata, failure);
                restoreTree(backupRuntime, runtime, failure);
                restoreFile(backupInstall, installMetadata, failure);
                restoreTree(backupNatives, natives, failure);
                throw failure;
            }
        } finally {
            deleteTree(rollback);
        }
    }

    private static void copyFileIfPresent(Path source, Path destination) throws Exception {
        if (Files.isSymbolicLink(source)) {
            throw new java.io.IOException("Refusing to snapshot a symbolic link during Stein repair: " + source);
        }
        if (Files.isRegularFile(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination, java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static void copyTreeIfPresent(Path source, Path destination) throws Exception {
        if (!Files.exists(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (Files.isSymbolicLink(source)) {
            throw new java.io.IOException("Refusing to snapshot a symbolic link during Stein repair: " + source);
        }
        Files.walkFileTree(source, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(
                    Path directory, java.nio.file.attribute.BasicFileAttributes attributes
            ) throws java.io.IOException {
                if (Files.isSymbolicLink(directory)) {
                    throw new java.io.IOException("Symbolic link found in Stein repair backup: " + directory);
                }
                Files.createDirectories(destination.resolve(source.relativize(directory)));
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(
                    Path file, java.nio.file.attribute.BasicFileAttributes attributes
            ) throws java.io.IOException {
                if (Files.isSymbolicLink(file)) {
                    throw new java.io.IOException("Symbolic link found in Stein repair backup: " + file);
                }
                Files.copy(file, destination.resolve(source.relativize(file)),
                        java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }

    private static void restoreFile(Path backup, Path target, Exception failure) {
        try {
            if (Files.isRegularFile(backup, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(target.getParent());
                Path temporary = target.resolveSibling(target.getFileName() + ".stein-restore");
                Files.copy(backup, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
                try {
                    Files.move(temporary, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                Files.deleteIfExists(target);
            }
        } catch (Exception rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static void restoreTree(Path backup, Path target, Exception failure) {
        try {
            deleteTree(target);
            if (Files.isDirectory(backup, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                copyTreeIfPresent(backup, target);
            }
        } catch (Exception rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private static void deleteTree(Path root) throws java.io.IOException {
        if (!Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(
                    Path file, java.nio.file.attribute.BasicFileAttributes attributes
            ) throws java.io.IOException {
                Files.deleteIfExists(file);
                return java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult postVisitDirectory(
                    Path directory, java.io.IOException failure
            ) throws java.io.IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.deleteIfExists(directory);
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
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

    private static void repairLegacyFabric(
            Instance instance,
            JsonNode vanillaMetadata
    ) throws Exception {

        String loaderVersion = instance.getLoaderVersion();
        if (loaderVersion == null || loaderVersion.isBlank()) {
            loaderVersion = LegacyFabricInstaller.findLatestLoaderVersion(
                    instance.getMinecraftVersion()
            );
        }

        JsonNode profile = LegacyFabricInstaller.downloadProfile(
                instance.getMinecraftVersion(),
                loaderVersion
        );

        // Legacy Fabric profiles can contain pre-Mojang native metadata.
        // downloadProfile normalizes those entries before library installation.
        LegacyFabricInstaller.installLibraries(profile);

        JsonNode merged = LegacyFabricInstaller.mergeProfile(
                vanillaMetadata,
                profile
        );

        MinecraftVersionResolver.saveMetadata(instance, merged);
        NativeInstaller.extract(instance, merged);

        if (!loaderVersion.equals(instance.getLoaderVersion())) {
            Instance repaired = new Instance(
                    instance.getId(),
                    instance.getName(),
                    instance.getMinecraftVersion(),
                    instance.getLoader(),
                    loaderVersion,
                    instance.getDirectory()
            );
            InstanceManager.saveInstance(repaired);
        }
    }

    private static void repairQuilt(
            Instance instance,
            JsonNode vanillaMetadata
    ) throws Exception {
        // Always resolve the current stable loader for this exact Minecraft
        // version. Reusing a stale pinned version is what left old Quilt builds
        // (and their incompatible ASM) in existing instances.
        String loaderVersion =
                QuiltInstaller.findLatestLoaderVersion(instance.getMinecraftVersion());

        JsonNode profile =
                QuiltInstaller.downloadProfile(
                        instance.getMinecraftVersion(),
                        loaderVersion
                );

        QuiltInstaller.installLibraries(profile);

        JsonNode merged =
                QuiltInstaller.mergeProfile(
                        vanillaMetadata,
                        profile
                );

        MinecraftVersionResolver.saveMetadata(
                instance,
                merged
        );

        if (!loaderVersion.equals(instance.getLoaderVersion())) {
            System.out.println(
                    "[Vanta Repair] Updating Quilt Loader "
                            + (instance.getLoaderVersion() == null
                            || instance.getLoaderVersion().isBlank()
                            ? "(unknown)" : instance.getLoaderVersion())
                            + " -> " + loaderVersion
            );

            Instance repaired = new Instance(
                    instance.getId(),
                    instance.getName(),
                    instance.getMinecraftVersion(),
                    instance.getLoader(),
                    loaderVersion,
                    instance.getDirectory()
            );

            InstanceManager.saveInstance(repaired);
        }
    }

    private static void repairBabric(
            Instance instance,
            JsonNode vanillaMetadata
    ) throws Exception {
        String loaderVersion = instance.getLoaderVersion();
        if (loaderVersion == null || loaderVersion.isBlank()) {
            loaderVersion = BabricInstaller.findLatestLoaderVersion(instance.getMinecraftVersion());
        }

        JsonNode fabricProfile = BabricInstaller.downloadFabricProfile(
                instance.getMinecraftVersion(), loaderVersion);
        JsonNode babricOverlay = BabricInstaller.downloadOverlay(fabricProfile);
        BabricInstaller.installLibraries(fabricProfile, babricOverlay);
        JsonNode merged = BabricInstaller.mergeProfile(vanillaMetadata, fabricProfile, babricOverlay);
        MinecraftFileInstaller.installLibraries(merged);
        MinecraftVersionResolver.saveMetadata(instance, merged);
        NativeInstaller.extract(instance, merged);

        if (!loaderVersion.equals(instance.getLoaderVersion())) {
            Instance repaired = new Instance(
                    instance.getId(), instance.getName(), instance.getMinecraftVersion(),
                    instance.getLoader(), loaderVersion, instance.getDirectory());
            InstanceManager.saveInstance(repaired);
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
