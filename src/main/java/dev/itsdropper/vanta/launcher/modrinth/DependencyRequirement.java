package dev.itsdropper.vanta.launcher.modrinth;

public class DependencyRequirement {
    private final String modId;
    private final String versionConstraint;
    private final boolean optional;

    public DependencyRequirement(String modId, String versionConstraint) {
        this(modId, versionConstraint, false);
    }

    public DependencyRequirement(String modId, String versionConstraint, boolean optional) {
        this.modId = modId;
        this.versionConstraint = versionConstraint == null || versionConstraint.isBlank()
                ? "*" : versionConstraint.trim();
        this.optional = optional;
    }

    public String getModId() { return modId; }
    public String getVersionConstraint() { return versionConstraint; }
    public boolean isOptional() { return optional; }
}
