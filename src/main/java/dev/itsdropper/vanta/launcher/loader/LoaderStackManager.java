package dev.itsdropper.vanta.launcher.loader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.instance.FabricInstaller;
import dev.itsdropper.vanta.launcher.instance.NativeInstaller;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists and applies an instance's ordered loader profile stack.
 * The vanilla/primary loader metadata is snapshotted once so subsequent
 * edits rebuild from a stable base instead of repeatedly appending libraries
 * and launch tweakers.
 */
public final class LoaderStackManager {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CONFIG_FILE = "loader-stack.json";
    private static final String BASE_METADATA_FILE = "version.base.json";

    private LoaderStackManager() {
    }

    public static Path configurationPath(Instance instance) {
        requireInstance(instance);
        return instance.getDirectory().resolve(CONFIG_FILE);
    }

    public static LoaderStackConfiguration load(Instance instance) throws IOException {
        Path file = configurationPath(instance);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        LoaderStackConfiguration configuration = LoaderStackConfiguration.load(file);
        validateProfileFiles(instance, configuration);
        return configuration;
    }

    public static void validateForLaunch(Instance instance) {
        try {
            load(instance);
        } catch (Exception e) {
            throw new IllegalStateException("Instance loader stack is invalid: " + e.getMessage(), e);
        }
    }

    public static void apply(Instance instance, LoaderStackConfiguration configuration) throws Exception {
        requireInstance(instance);
        if (configuration == null) {
            throw new IllegalArgumentException("Loader stack configuration is required.");
        }
        configuration.validate();

        Path minecraftDirectory = instance.getDirectory().resolve("minecraft");
        Path versionFile = minecraftDirectory.resolve("version.json");
        Path baseFile = minecraftDirectory.resolve(BASE_METADATA_FILE);
        if (!Files.isRegularFile(versionFile)) {
            throw new IllegalStateException("Instance version metadata is missing.");
        }

        if (!Files.exists(baseFile)) {
            Files.copy(versionFile, baseFile);
        }
        JsonNode base = MAPPER.readTree(baseFile.toFile());
        if (base == null || !base.isObject()) {
            throw new IllegalStateException("Base Minecraft metadata is invalid.");
        }

        List<JsonNode> overlays = new ArrayList<>();
        for (LoaderStackConfiguration.Layer layer : configuration.getOrderedLayers()) {
            if (layer.type() != LoaderStackConfiguration.LayerType.PROFILE_OVERLAY) {
                continue;
            }
            if (layer.profileFile() == null) {
                throw new IllegalStateException(
                        "Profile overlay layer '" + layer.id() + "' has no profile JSON file.");
            }
            Path profilePath = resolveProfilePath(instance, layer.profileFile());
            JsonNode profile = MAPPER.readTree(profilePath.toFile());
            if (profile == null || !profile.isObject()) {
                throw new IllegalStateException("Invalid profile JSON: " + layer.profileFile());
            }
            if (!profile.hasNonNull("mainClass")
                    || !profile.path("mainClass").isTextual()
                    || profile.path("mainClass").asText().isBlank()) {
                ((ObjectNode) profile).put("mainClass", base.path("mainClass").asText());
            }
            FabricInstaller.installLibraries(profile);
            overlays.add(profile);
        }

        JsonNode merged = LoaderProfileStackMerger.merge(base, overlays, configuration);
        Path temporary = versionFile.resolveSibling("version.json.tmp");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), merged);
        try {
            Files.move(temporary, versionFile, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, versionFile, StandardCopyOption.REPLACE_EXISTING);
        }

        try {
            NativeInstaller.extract(instance, merged);
            configuration.save(configurationPath(instance));
        } catch (Exception failure) {
            Files.copy(baseFile, versionFile, StandardCopyOption.REPLACE_EXISTING);
            throw failure;
        }
    }

    public static void clear(Instance instance) throws IOException {
        requireInstance(instance);
        Path minecraftDirectory = instance.getDirectory().resolve("minecraft");
        Path baseFile = minecraftDirectory.resolve(BASE_METADATA_FILE);
        Path versionFile = minecraftDirectory.resolve("version.json");
        if (Files.isRegularFile(baseFile)) {
            Files.copy(baseFile, versionFile, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(baseFile);
        }
        Files.deleteIfExists(configurationPath(instance));
    }

    private static void validateProfileFiles(
            Instance instance,
            LoaderStackConfiguration configuration
    ) throws IOException {
        for (LoaderStackConfiguration.Layer layer : configuration.getOrderedLayers()) {
            if (layer.type() == LoaderStackConfiguration.LayerType.PROFILE_OVERLAY) {
                if (layer.profileFile() == null
                        || !Files.isRegularFile(resolveProfilePath(instance, layer.profileFile()))) {
                    throw new IOException("Missing loader profile for layer '" + layer.id() + "'.");
                }
            }
        }
    }

    private static Path resolveProfilePath(Instance instance, String relativePath) throws IOException {
        Path root = instance.getDirectory().resolve("loader-profiles").toAbsolutePath().normalize();
        Path path = instance.getDirectory().resolve(relativePath).toAbsolutePath().normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw new IOException("Loader profile path is missing or escapes the instance profile directory.");
        }
        return path;
    }

    private static void requireInstance(Instance instance) {
        if (instance == null || instance.getDirectory() == null) {
            throw new IllegalArgumentException("A valid instance is required.");
        }
    }
}
