package org.example.launcher.modrinth;

import java.util.List;

public class InstalledMod {

    private final String modId;
    private final String version;
    private final String filename;
    private final List<DependencyRequirement> dependencies;
    private final List<DependencyRequirement> breaks;

    public InstalledMod(
            String modId,
            String version,
            String filename,
            List<DependencyRequirement> dependencies,
            List<DependencyRequirement> breaks
    ) {
        this.modId = modId;
        this.version = version;
        this.filename = filename;
        this.dependencies = dependencies;
        this.breaks = breaks;
    }

    public String getModId() {
        return modId;
    }

    public String getVersion() {
        return version;
    }

    public String getFilename() {
        return filename;
    }

    public List<DependencyRequirement> getDependencies() {
        return dependencies;
    }

    public List<DependencyRequirement> getBreaks() {
        return breaks;
    }
}