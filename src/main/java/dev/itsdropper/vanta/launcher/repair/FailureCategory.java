package dev.itsdropper.vanta.launcher.repair;

public enum FailureCategory {

    MOD_DEPENDENCY,
    MOD_CONFLICT,
    MOD_MIXIN_FAILURE,
    MOD_CRASH,
    DUPLICATE_MOD,

    MISSING_LIBRARY,
    CORRUPT_LIBRARY,
    MISSING_ASSET,

    JAVA_RUNTIME,
    AUTHENTICATION,
    GRAPHICS,
    JAVA_VERSION,
    JVM_ARGUMENTS,

    LOADER,
    MINECRAFT_VERSION,

    NATIVE,
    CLASSPATH,

    CONFIGURATION,
    INSTALLATION,

    DISK_SPACE,
    PERMISSION,
    NETWORK,

    UNKNOWN;

    /**
     * Resolves both canonical diagnosis categories and the legacy display
     * labels emitted by LaunchFailureParser.
     */
    public static FailureCategory fromRepairType(String type) {
        if (type == null || type.isBlank()) return UNKNOWN;
        String normalized = type.trim().toUpperCase(java.util.Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return switch (normalized) {
            case "MISSING_DEPENDENCY", "DEPENDENCY_CONFLICT" -> MOD_DEPENDENCY;
            case "MOD_INCOMPATIBILITY" -> MOD_CONFLICT;
            case "SODIUM_NEOFORGE_INCOMPATIBILITY" -> MOD_MIXIN_FAILURE;
            case "MINECRAFT_VERSION_CONFLICT" -> MINECRAFT_VERSION;
            case "GENERAL_DIAGNOSTIC" -> UNKNOWN;
            default -> {
                try {
                    yield valueOf(normalized);
                } catch (IllegalArgumentException unknown) {
                    yield UNKNOWN;
                }
            }
        };
    }
}