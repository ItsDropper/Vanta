package org.example.launcher.modrinth;

public class DependencyRequirement {

    private final String modId;
    private final String versionConstraint;

    public DependencyRequirement(
            String modId,
            String versionConstraint
    ) {
        this.modId = modId;
        this.versionConstraint = versionConstraint;
    }

    public String getModId() {
        return modId;
    }

    public String getVersionConstraint() {
        return versionConstraint;
    }
}