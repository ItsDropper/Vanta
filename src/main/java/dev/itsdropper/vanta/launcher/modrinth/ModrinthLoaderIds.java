package dev.itsdropper.vanta.launcher.modrinth;

import java.util.Locale;
import java.util.Set;

/**
 * Maps Vanta's display names to the loader identifiers used by Modrinth
 * version metadata and search facets.
 */
public final class ModrinthLoaderIds {
    private static final Set<String> SEARCH_FACET_LOADERS = Set.of(
            "fabric",
            "forge",
            "neoforge",
            "quilt",
            "liteloader",
            "rift",
            "babric",
            "legacyfabric",
            "ornithe",
            "bta-babric",
            "java-agent"
    );

    private ModrinthLoaderIds() {
    }

    public static String normalize(String loader) {
        if (loader == null || loader.isBlank()) {
            return "";
        }

        String normalized = loader.trim().toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "");

        return switch (normalized) {
            case "btababric" -> "bta-babric";
            case "javaagent" -> "java-agent";
            case "legacyfabric" -> "legacyfabric";
            case "nilloader" -> "nilloader";
            case "risugamimodloader", "risugamismodloader", "modloader" -> "modloader";
            case "lite", "liteloader" -> "liteloader";
            case "riftloader", "rift" -> "rift";
            case "neoforge" -> "neoforge";
            case "vanilla", "minecraft" -> "minecraft";
            default -> normalized;
        };
    }

    public static boolean supportsGameVersion(java.util.List<String> supportedVersions, String requestedVersion) {
        if (supportedVersions == null || requestedVersion == null || requestedVersion.isBlank()) {
            return false;
        }
        if (supportedVersions.contains(requestedVersion)) {
            return true;
        }
        String familyPrefix = requestedVersion + ".";
        return supportedVersions.stream()
                .filter(version -> version != null && !version.isBlank())
                .anyMatch(version -> version.startsWith(familyPrefix));
    }

    /**
     * Whether the loader has a first-class Modrinth loader/category tag.
     * Unknown/custom loaders must be filtered against version metadata
     * instead of sending an unsupported category facet.
     */
    public static boolean supportsSearchFacet(String loader) {
        return SEARCH_FACET_LOADERS.contains(normalize(loader));
    }
}
