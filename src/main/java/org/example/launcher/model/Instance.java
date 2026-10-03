package org.example.launcher.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.nio.file.Path;

public class Instance {

    private final String id;
    private final String name;
    private final String minecraftVersion;
    private final String loader;
    private final String loaderVersion;
    private final Path directory;
    private final String icon;

    public Instance(
            String id,
            String name,
            String minecraftVersion,
            String loader,
            String loaderVersion,
            Path directory
    ) {
        this(id, name, minecraftVersion, loader, loaderVersion, directory, "SHIELD");
    }

    @JsonCreator
    public Instance(
            @JsonProperty("id")
            String id,

            @JsonProperty("name")
            String name,

            @JsonProperty("minecraftVersion")
            String minecraftVersion,

            @JsonProperty("loader")
            String loader,

            @JsonProperty("loaderVersion")
            String loaderVersion,

            @JsonProperty("directory")
            Path directory,

            @JsonProperty("icon")
            String icon
    ) {

        this.id = id;
        this.name = name;
        this.minecraftVersion = minecraftVersion;
        this.loader = loader;
        this.loaderVersion = loaderVersion;
        this.directory = directory;
        this.icon = icon == null || icon.isBlank() ? "SHIELD" : icon;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getMinecraftVersion() {
        return minecraftVersion;
    }

    public String getLoader() {
        return loader;
    }

    public String getLoaderVersion() {
        return loaderVersion;
    }

    public Path getDirectory() {
        return directory;
    }

    public String getIcon() {
        return icon;
    }

    public Instance withIcon(String newIcon) {
        return new Instance(id, name, minecraftVersion, loader, loaderVersion, directory, newIcon);
    }

    @JsonIgnore
    public String getDisplayLoader() {

        if (loaderVersion == null
                || loaderVersion.isBlank()) {

            return loader;
        }

        return loader + " " + loaderVersion;
    }

    @Override
    public String toString() {
        return name;
    }

}

