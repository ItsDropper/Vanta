package dev.itsdropper.vanta.launcher.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Persisted description of an ordered multi-loader stack.
 *
 * This is intentionally a configuration model, not an assertion that every
 * combination is launch-compatible. A stack becomes installable only when
 * each layer has a registered installer and a verified launch strategy.
 */
public final class LoaderStackConfiguration {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private List<Layer> layers = new ArrayList<>();

    public LoaderStackConfiguration() {
    }

    public LoaderStackConfiguration(List<Layer> layers) {
        this.layers = layers == null ? new ArrayList<>() : new ArrayList<>(layers);
        validate();
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public List<Layer> getLayers() {
        return List.copyOf(layers);
    }

    public void setLayers(List<Layer> layers) {
        this.layers = layers == null ? new ArrayList<>() : new ArrayList<>(layers);
        validate();
    }

    /** Returns layers in deterministic launch order without mutating storage. */
    public List<Layer> getOrderedLayers() {
        validate();
        return layers.stream()
                .sorted(Comparator.comparingInt(Layer::order)
                        .thenComparing(Layer::id))
                .toList();
    }

    public void validate() {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported loader stack schema version: " + schemaVersion);
        }
        if (layers == null || layers.isEmpty()) {
            throw new IllegalStateException("A loader stack must contain at least one layer.");
        }

        Set<String> ids = new HashSet<>();
        for (Layer layer : layers) {
            if (layer == null) {
                throw new IllegalStateException("Loader stack contains a null layer.");
            }
            if (layer.id() == null || !layer.id().matches("[a-z][a-z0-9_-]{1,63}")) {
                throw new IllegalStateException("Loader layer ID is invalid.");
            }
            if (!ids.add(layer.id())) {
                throw new IllegalStateException("Duplicate loader layer: " + layer.id());
            }
            if (layer.version() == null || layer.version().isBlank()) {
                throw new IllegalStateException("Loader layer " + layer.id() + " has no version.");
            }
            if (layer.type() == null) {
                throw new IllegalStateException("Loader layer " + layer.id() + " has no type.");
            }
            if (layer.launchTweakers() != null) {
                for (String tweaker : layer.launchTweakers()) {
                    if (tweaker == null || !tweaker.matches("[A-Za-z_$][A-Za-z0-9_$.]*")) {
                        throw new IllegalStateException("Invalid launch tweaker in layer " + layer.id() + ".");
                    }
                }
            }
        }
    }

    public void save(Path path) throws IOException {
        validate();
        Path target = path.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        MAPPER.writeValue(target.toFile(), this);
    }

    public static LoaderStackConfiguration load(Path path) throws IOException {
        LoaderStackConfiguration configuration =
                MAPPER.readValue(path.toFile(), LoaderStackConfiguration.class);
        configuration.validate();
        return configuration;
    }

    public enum LayerType {
        PROFILE_OVERLAY,
        MOD,
        TWEAKER
    }

    public record Layer(
            String id,
            String version,
            LayerType type,
            int order,
            List<String> launchTweakers
    ) {
        public Layer {
            launchTweakers = launchTweakers == null ? List.of() : List.copyOf(launchTweakers);
        }
    }
}
