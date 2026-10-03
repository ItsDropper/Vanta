package org.example.launcher.instance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.example.launcher.MinecraftLocator;
import org.example.launcher.model.Instance;
import org.example.launcher.model.InstanceSettings;
import org.example.ui.LauncherSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

        Path instancesDirectory =
                MinecraftLocator.getInstancesDirectory();

        List<Instance> instances =
                new ArrayList<>();

        if (!Files.isDirectory(instancesDirectory)) {
            return instances;
        }

        try (var stream = Files.list(instancesDirectory)) {

            stream
                    .filter(Files::isDirectory)
                    .forEach(directory -> {

                        Path installingMarker =
                                directory.resolve(
                                        INSTALLING_MARKER
                                );

                        /*
                         * An installation was interrupted or is
                         * still in progress. Do not expose it as
                         * a usable instance.
                         */
                        if (Files.exists(installingMarker)) {
                            System.out.println(
                                    "Skipping incomplete instance: "
                                            + directory
                            );
                            return;
                        }

                        Path metadata =
                                directory.resolve(
                                        "instance.json"
                                );

                        if (!Files.exists(metadata)) {
                            return;
                        }

                        try {

                            Instance instance =
                                    MAPPER.readValue(
                                            metadata.toFile(),
                                            Instance.class
                                    );

                            instances.add(instance);

                        } catch (Exception e) {

                            System.err.println(
                                    "Failed to load instance: "
                                            + directory
                            );

                            e.printStackTrace();
                        }
                    });

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Failed to discover Vanta instances.",
                    e
            );
        }

        instances.sort(
                Comparator.comparing(
                        Instance::getName,
                        String.CASE_INSENSITIVE_ORDER
                )
        );

        return instances;
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

        Instance updatedInstance =
                new Instance(
                        instance.getId(),
                        instance.getName(),
                        minecraftVersion,
                        instance.getLoader(),
                        instance.getLoaderVersion(),
                        instance.getDirectory(),
                        instance.getIcon()
                );

        saveInstance(
                updatedInstance
        );

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

        MAPPER.writeValue(
                metadata.toFile(),
                instance
        );
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
        String id = createId(requestedName);
        Path target = MinecraftLocator.getInstancesDirectory().resolve(id);
        Files.createDirectories(target);
        try (var stream = Files.walk(source.getDirectory())) {
            stream.forEach(path -> {
                try {
                    Path relative = source.getDirectory().relativize(path);
                    if (relative.toString().equals(INSTALLING_MARKER)) return;
                    Path destination = target.resolve(relative);
                    if (Files.isDirectory(path)) Files.createDirectories(destination);
                    else Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                } catch (IOException e) { throw new RuntimeException(e); }
            });
        } catch (RuntimeException e) {
            deleteInstanceQuietly(target);
            if (e.getCause() instanceof IOException io) throw io;
            throw e;
        }
        Instance duplicate = new Instance(id, requestedName.trim(), source.getMinecraftVersion(), source.getLoader(),
                source.getLoaderVersion(), target, source.getIcon());
        saveInstance(duplicate);
        return duplicate;
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