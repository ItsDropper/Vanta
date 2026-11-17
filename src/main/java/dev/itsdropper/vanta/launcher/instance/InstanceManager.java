package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import dev.itsdropper.vanta.launcher.MinecraftLocator;
import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.model.InstanceSettings;
import dev.itsdropper.vanta.launcher.service.ModrinthInstallService;
import dev.itsdropper.vanta.ui.LauncherSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class InstanceManager {

    private static final ObjectMapper MAPPER =
            new ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT);

    private static final String INSTALLING_MARKER =
            ".installing";

    // =============================================================
    // DISCOVER
    // =============================================================

    public static List<Instance> discoverInstances() {
        return discoverInstances(false, null);
    }

    /**
     * Discovers every instance with enough intact metadata to rebuild its
     * managed installation, including instances left behind by an interrupted
     * install. This list is for explicit repair only; incomplete instances are
     * never exposed to the normal launcher/library flow.
     */
    public static List<Instance> discoverRepairableInstances() {
        return discoverRepairableInstances(null);
    }

    public static List<Instance> discoverRepairableInstances(Consumer<String> skippedInstanceReporter) {
        return discoverInstances(true, skippedInstanceReporter);
    }

    private static List<Instance> discoverInstances(
            boolean includeInterrupted,
            Consumer<String> skippedInstanceReporter
    ) {
        Path instancesDirectory = MinecraftLocator.getInstancesDirectory();
        List<Instance> instances = new ArrayList<>();
        if (!Files.isDirectory(instancesDirectory)) return instances;

        try (var stream = Files.list(instancesDirectory)) {
            stream.filter(Files::isDirectory).forEach(directory -> {
                if (!includeInterrupted && Files.exists(directory.resolve(INSTALLING_MARKER))) {
                    return;
                }

                Path metadata = directory.resolve("instance.json");
                if (!Files.isRegularFile(metadata)) {
                    if (includeInterrupted && skippedInstanceReporter != null) {
                        skippedInstanceReporter.accept("Could not automatically repair instance folder '"
                                + directory.getFileName() + "': instance.json is missing.");
                    }
                    return;
                }

                try {
                    Instance stored = MAPPER.readValue(metadata.toFile(), Instance.class);
                    if (!hasRepairMetadata(stored)) {
                        throw new IOException("Instance metadata is missing its ID, name, Minecraft version, or loader.");
                    }

                    // The directory being scanned is authoritative. Never let a
                    // stale metadata path redirect repair into another instance.
                    Instance instance = new Instance(
                            stored.getId(), stored.getName(), stored.getMinecraftVersion(),
                            stored.getLoader(), stored.getLoaderVersion(), directory.toAbsolutePath().normalize(),
                            stored.getIcon());
                    try {
                        ModrinthInstallService.recoverPendingTransactions(instance);
                    } catch (Exception transactionFailure) {
                        if (!includeInterrupted) {
                            throw transactionFailure;
                        }
                        // Explicit repair must still be offered when a Modrinth
                        // transaction journal is itself damaged. The repair path
                        // can rebuild managed game files without silently hiding
                        // the entire instance from the user.
                        System.err.println("Pending Modrinth transaction recovery failed for "
                                + directory + "; retaining it in the repair queue.");
                        transactionFailure.printStackTrace();
                    }
                    instances.add(instance);
                } catch (Exception failure) {
                    String message = "Could not automatically repair instance folder '"
                            + directory.getFileName() + "': "
                            + (failure.getMessage() == null
                            ? failure.getClass().getSimpleName() : failure.getMessage());
                    System.err.println(message);
                    failure.printStackTrace();
                    if (skippedInstanceReporter != null) {
                        skippedInstanceReporter.accept(message);
                    }
                }
            });
        } catch (IOException failure) {
            throw new IllegalStateException("Failed to discover Vanta instances.", failure);
        }

        instances.sort(Comparator.comparing(Instance::getName, String.CASE_INSENSITIVE_ORDER));
        return instances;
    }

    static boolean hasRepairMetadata(Instance instance) {
        return instance != null
                && instance.getId() != null && !instance.getId().isBlank()
                && instance.getName() != null && !instance.getName().isBlank()
                && instance.getMinecraftVersion() != null && !instance.getMinecraftVersion().isBlank()
                && instance.getLoader() != null && !instance.getLoader().isBlank();
    }

    // =============================================================
    // CREATE
    // =============================================================

    public static Instance createInstance(
            String name,
            String minecraftVersion,
            String loader,
            String loaderVersion
    ) throws IOException {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Instance name cannot be empty."
            );
        }

        if (minecraftVersion == null
                || minecraftVersion.isBlank()) {

            throw new IllegalArgumentException(
                    "Minecraft version cannot be empty."
            );
        }

        if (loader == null || loader.isBlank()) {

            throw new IllegalArgumentException(
                    "Loader cannot be empty."
            );
        }

        String id =
                createId(name);

        Path directory =
                MinecraftLocator
                        .getInstancesDirectory()
                        .resolve(id);

        if (Files.exists(directory)) {

            throw new IllegalStateException(
                    "An instance with this ID already exists."
            );
        }

        Files.createDirectories(
                directory
        );

        /*
         * Mark the instance as incomplete immediately.
         *
         * If Vanta crashes, loses power, or is force-closed
         * during installation, discoverInstances() will ignore
         * this directory on the next startup.
         */
        markInstallationStarted(
                directory
        );

        // ---------------------------------------------------------
        // INSTANCE DIRECTORIES
        // ---------------------------------------------------------

        createDirectory(directory, "mods");
        createDirectory(directory, "config");
        createDirectory(directory, "resourcepacks");
        createDirectory(directory, "shaderpacks");
        createDirectory(directory, "saves");
        createDirectory(directory, "logs");
        createDirectory(directory, "screenshots");
        createDirectory(directory, "natives");

        // ---------------------------------------------------------
        // INSTANCE
        // ---------------------------------------------------------

        Instance instance =
                new Instance(
                        id,
                        name,
                        minecraftVersion,
                        loader,
                        loaderVersion,
                        directory
                );

        saveInstance(instance);

        // ---------------------------------------------------------
        // DEFAULT SETTINGS
        // ---------------------------------------------------------

        InstanceSettings defaults = new InstanceSettings();
        defaults.setRamMb(LauncherSettings.getDefaultRamMb());
        defaults.setWidth(LauncherSettings.getDefaultWidth());
        defaults.setHeight(LauncherSettings.getDefaultHeight());
        defaults.setFullscreen(LauncherSettings.isDefaultFullscreen());
        defaults.setJavaPath(LauncherSettings.getDefaultJavaPath());
        defaults.setJavaArguments(LauncherSettings.getDefaultJavaArguments());

        saveSettings(
                instance,
                defaults
        );

        return instance;
    }

    // =============================================================
    // INSTALLATION STATE
    // =============================================================

    public static void markInstallationStarted(
            Instance instance
    ) throws IOException {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        markInstallationStarted(
                instance.getDirectory()
        );
    }

    private static void markInstallationStarted(
            Path directory
    ) throws IOException {

        Files.createFile(
                directory.resolve(
                        INSTALLING_MARKER
                )
        );
    }

    public static void markInstallationComplete(
            Instance instance
    ) throws IOException {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        Path marker =
                instance.getDirectory()
                        .resolve(
                                INSTALLING_MARKER
                        );

        Files.deleteIfExists(
                marker
        );
    }

    public static boolean isInstallationInProgress(
            Instance instance
    ) {

        if (instance == null) {
            return false;
        }

        return Files.exists(
                instance.getDirectory()
                        .resolve(
                                INSTALLING_MARKER
                        )
        );
    }

    public static Instance updateMinecraftVersion(
            Instance instance,
            String minecraftVersion
    ) throws IOException {
        return updateMinecraftVersion(
                instance,
                minecraftVersion,
                instance == null ? null : instance.getLoaderVersion()
        );
    }

    public static Instance updateMinecraftVersion(
            Instance instance,
            String minecraftVersion,
            String loaderVersion
    ) throws IOException {
        if (instance == null) {
            throw new IllegalArgumentException("Instance cannot be null.");
        }
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            throw new IllegalArgumentException("Minecraft version cannot be empty.");
        }

        Instance updatedInstance = new Instance(
                instance.getId(),
                instance.getName(),
                minecraftVersion,
                instance.getLoader(),
                loaderVersion,
                instance.getDirectory(),
                instance.getIcon()
        );
        saveInstance(updatedInstance);
        return updatedInstance;
    }

    // =============================================================
    // SAVE INSTANCE
    // =============================================================

    public static void saveInstance(
            Instance instance
    ) throws IOException {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        Path directory =
                instance.getDirectory();

        Files.createDirectories(
                directory
        );

        Path metadata =
                directory.resolve(
                        "instance.json"
                );
        Path temporary = directory.resolve(
                "instance.json." + java.util.UUID.randomUUID() + ".tmp");

        try {
            MAPPER.writeValue(temporary.toFile(), instance);
            try {
                Files.move(temporary, metadata,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, metadata, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    // =============================================================
    // LOAD SETTINGS
    // =============================================================

    public static InstanceSettings loadSettings(
            Instance instance
    ) throws IOException {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        Path settingsFile =
                instance.getDirectory()
                        .resolve("settings.json");

        if (!Files.exists(settingsFile)) {

            InstanceSettings settings =
                    new InstanceSettings();

            saveSettings(
                    instance,
                    settings
            );

            return settings;
        }

        return MAPPER.readValue(
                settingsFile.toFile(),
                InstanceSettings.class
        );
    }

    // =============================================================
    // SAVE SETTINGS
    // =============================================================

    public static void saveSettings(
            Instance instance,
            InstanceSettings settings
    ) throws IOException {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        if (settings == null) {
            throw new IllegalArgumentException(
                    "Settings cannot be null."
            );
        }

        Path directory =
                instance.getDirectory();

        Files.createDirectories(
                directory
        );

        Path settingsFile =
                directory.resolve(
                        "settings.json"
                );

        MAPPER.writeValue(
                settingsFile.toFile(),
                settings
        );
    }

    // =============================================================
    // INSTANCE MANAGEMENT
    // =============================================================

    public static Instance renameInstance(Instance instance, String newName) throws IOException {
        if (instance == null) throw new IllegalArgumentException("Instance cannot be null.");
        if (newName == null || newName.isBlank()) throw new IllegalArgumentException("Instance name cannot be empty.");
        Instance renamed = new Instance(instance.getId(), newName.trim(), instance.getMinecraftVersion(),
                instance.getLoader(), instance.getLoaderVersion(), instance.getDirectory(), instance.getIcon());
        saveInstance(renamed);
        return renamed;
    }

    public static Instance setInstanceIcon(Instance instance, String icon) throws IOException {
        if (instance == null) throw new IllegalArgumentException("Instance cannot be null.");
        String safeIcon = icon == null || icon.isBlank() ? "SHIELD" : icon.trim().toUpperCase();
        Instance updated = new Instance(instance.getId(), instance.getName(), instance.getMinecraftVersion(),
                instance.getLoader(), instance.getLoaderVersion(), instance.getDirectory(), safeIcon);
        saveInstance(updated);
        return updated;
    }

    public static Instance duplicateInstance(Instance source, String requestedName) throws IOException {
        if (source == null) throw new IllegalArgumentException("Source instance cannot be null.");
        if (requestedName == null || requestedName.isBlank()) throw new IllegalArgumentException("Instance name cannot be empty.");

        Path sourceDirectory = source.getDirectory();
        if (sourceDirectory == null
                || Files.isSymbolicLink(sourceDirectory)
                || !Files.isDirectory(sourceDirectory, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Source instance directory does not exist or is not a regular directory.");
        }

        Path instancesRoot = MinecraftLocator.getInstancesDirectory().toAbsolutePath().normalize();
        String id = createId(requestedName);
        Path target = instancesRoot.resolve(id).normalize();
        if (!target.startsWith(instancesRoot) || target.equals(sourceDirectory.toAbsolutePath().normalize())) {
            throw new IOException("Could not allocate a safe duplicate destination.");
        }

        // createDirectory is intentional: it atomically claims the destination
        // instead of allowing two concurrent duplicate operations to merge.
        Files.createDirectory(target);
        Path marker = target.resolve(INSTALLING_MARKER);
        boolean completed = false;
        try {
            Files.createFile(marker);
            copyInstanceFiles(sourceDirectory, target);

            Instance duplicate = new Instance(
                    id,
                    requestedName.trim(),
                    source.getMinecraftVersion(),
                    source.getLoader(),
                    source.getLoaderVersion(),
                    target,
                    source.getIcon()
            );
            saveInstance(duplicate);
            Files.delete(marker);
            completed = true;
            return duplicate;
        } finally {
            if (!completed) deleteInstanceQuietly(target);
        }
    }

    /**
     * Copies an instance tree without following symbolic links. A symlink may
     * point outside the instance (including to user data), so cloning it would
     * either leak unrelated files into the clone or preserve a broken/external
     * reference. Refuse the clone rather than silently changing its contents.
     *
     * Runtime-only logs and screenshots remain excluded as in the original
     * duplicate workflow. All other regular files, including worlds and configs,
     * are copied into the independent destination.
     */
    static void copyInstanceFiles(Path sourceDirectory, Path targetDirectory) throws IOException {
        Path source = sourceDirectory.toAbsolutePath().normalize();
        Path target = targetDirectory.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(source)
                || !Files.isDirectory(source, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Source instance directory is not a regular directory.");
        }
        if (Files.isSymbolicLink(target)
                || !Files.isDirectory(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || target.startsWith(source)) {
            throw new IOException("Duplicate destination is not a safe independent directory.");
        }

        try (var walk = Files.walk(source)) {
            for (Path path : walk.sorted().toList()) {
                if (path.equals(source)) continue;

                Path relative = source.relativize(path);
                String first = relative.getName(0).toString();
                if (relative.toString().equals(INSTALLING_MARKER)
                        || first.equals("logs")
                        || first.equals("screenshots")) {
                    continue;
                }

                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Cannot duplicate an instance containing a symbolic link: " + relative);
                }

                Path destination = target.resolve(relative).normalize();
                if (!destination.startsWith(target)) {
                    throw new IOException("Instance path escapes the duplicate destination: " + relative);
                }

                if (Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(destination);
                } else if (Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES,
                            java.nio.file.LinkOption.NOFOLLOW_LINKS);
                } else {
                    throw new IOException("Cannot duplicate unsupported filesystem entry: " + relative);
                }
            }
        }
    }

    private static void deleteInstanceQuietly(Path directory) {
        if (!Files.exists(directory)) return;
        try (var stream = Files.walk(directory)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    // =============================================================
    // DELETE
    // =============================================================

    public static void deleteInstance(
            Instance instance
    ) throws IOException {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        Path directory =
                instance.getDirectory();

        if (!Files.exists(directory)) {
            return;
        }

        try (var stream = Files.walk(directory)) {

            stream
                    .sorted(
                            Comparator.reverseOrder()
                    )
                    .forEach(path -> {

                        try {

                            Files.deleteIfExists(
                                    path
                            );

                        } catch (IOException e) {

                            throw new RuntimeException(
                                    "Failed to delete: "
                                            + path,
                                    e
                            );
                        }
                    });
        }
    }

    // =============================================================
    // ID
    // =============================================================

    private static String createId(
            String name
    ) {

        String id =
                name
                        .trim()
                        .toLowerCase()
                        .replaceAll(
                                "[^a-z0-9]+",
                                "-"
                        )
                        .replaceAll(
                                "^-+|-+$",
                                ""
                        );

        if (id.isBlank()) {
            id = "instance";
        }

        String base =
                id;

        int counter = 2;

        Path instances =
                MinecraftLocator
                        .getInstancesDirectory();

        while (
                Files.exists(
                        instances.resolve(id)
                )
        ) {

            id =
                    base
                            + "-"
                            + counter;

            counter++;
        }

        return id;
    }

    // =============================================================
    // DIRECTORY
    // =============================================================

    private static void createDirectory(
            Path instanceDirectory,
            String name
    ) throws IOException {

        Files.createDirectories(
                instanceDirectory.resolve(name)
        );
    }
}