package org.example.launcher.model;

import java.util.List;

public class InstancePreset {

    private final String id;
    private final String name;
    private final String description;
    private final List<String> minecraftVersions;
    private final String loader;
    private final List<String> mods;

    public InstancePreset(
            String id,
            String name,
            String description,
            List<String> minecraftVersions,
            String loader,
            List<String> mods
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.minecraftVersions = minecraftVersions;
        this.loader = loader;
        this.mods = mods;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getMinecraftVersions() {
        return minecraftVersions;
    }

    public String getLoader() {
        return loader;
    }

    public List<String> getMods() {
        return mods;
    }
}

