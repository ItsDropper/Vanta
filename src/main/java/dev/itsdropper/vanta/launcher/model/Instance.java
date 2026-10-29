package dev.itsdropper.vanta.launcher.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.file.Path;
import java.util.Locale;

public class Instance {

    public static final String ENGINE_JAVA = "JAVA";
    public static final String ENGINE_HORIZON = "HORIZON";

    private final String id;
    private final String name;
    private final String minecraftVersion;
    private final String loader;
    private final String loaderVersion;
    private final Path directory;
    private final String icon;
    private final String engine;

    public Instance(
            String id,
            String name,
            String minecraftVersion,
            String loader,
            String loaderVersion,
            Path directory
    ) {
        this(id, name, minecraftVersion, loader, loaderVersion, directory, "SHIELD", ENGINE_JAVA);
    }

    public Instance(
            String id,
            String name,
            String minecraftVersion,
            String loader,
            String loaderVersion,
            Path directory,
            String icon
    ) {
        this(id, name, minecraftVersion, loader, loaderVersion, directory, icon, ENGINE_JAVA);
    }

    @JsonCreator
    public Instance(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("minecraftVersion") String minecraftVersion,
            @JsonProperty("loader") String loader,
            @JsonProperty("loaderVersion") String loaderVersion,
            @JsonProperty("directory") Path directory,
            @JsonProperty("icon") String icon,
            @JsonProperty("engine") String engine
    ) {
        this.id = id;
        this.name = name;
        this.minecraftVersion = minecraftVersion;
        this.loader = loader;
        this.loaderVersion = loaderVersion;
        this.directory = directory;
        this.icon = icon == null || icon.isBlank() ? "SHIELD" : icon;
        this.engine = normalizeEngine(engine);
    }

    private static String normalizeEngine(String value) {
        if (value == null || value.isBlank()) {
            return ENGINE_JAVA;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!ENGINE_JAVA.equals(normalized) && !ENGINE_HORIZON.equals(normalized)) {
            throw new IllegalArgumentException("Unsupported instance engine: " + value);
        }
        return normalized;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getMinecraftVersion() { return minecraftVersion; }
    public String getLoader() { return loader; }
    public String getLoaderVersion() { return loaderVersion; }
    public Path getDirectory() { return directory; }
    public String getIcon() { return icon; }
    public String getEngine() { return engine; }

    @JsonIgnore
    public boolean isHorizon() {
        return ENGINE_HORIZON.equals(engine);
    }

    public Instance withIcon(String newIcon) {
        return new Instance(id, name, minecraftVersion, loader, loaderVersion, directory, newIcon, engine);
    }

    @JsonIgnore
    public String getDisplayLoader() {
        if (isHorizon()) {
            return "Horizon · Rust (experimental)";
        }
        if (loaderVersion == null || loaderVersion.isBlank()) {
            return loader;
        }
        return loader + " " + loaderVersion;
    }

    @Override
    public String toString() {
        return name;
    }
}
