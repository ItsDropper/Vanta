package org.example.launcher.instance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.example.launcher.MinecraftLocator;
import org.example.launcher.model.Instance;
import org.example.launcher.model.InstanceSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class InstanceManager {

    private static final ObjectMapper MAPPER =
            new ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT);

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

        try {

            Files.list(instancesDirectory)
                    .filter(Files::isDirectory)
                    .forEach(directory -> {

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

        saveSettings(
                instance,
                new InstanceSettings()
        );

        return instance;
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

        Files.walk(directory)
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