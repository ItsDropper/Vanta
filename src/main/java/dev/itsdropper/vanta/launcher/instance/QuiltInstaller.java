package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;

/** Client-side installer integration for the official Quilt Meta API. */
public final class QuiltInstaller {
    private static final String META_URL = "https://meta.quiltmc.org/v3/versions/loader/";

    private QuiltInstaller() {
    }

    public static String findLatestLoaderVersion(String minecraftVersion) throws Exception {
        requireSegment(minecraftVersion, "Minecraft version");
        JsonNode versions = DownloadUtil.downloadJson(META_URL + minecraftVersion);
        if (versions == null || !versions.isArray() || versions.isEmpty()) {
            throw new IllegalStateException("No Quilt Loader versions found for Minecraft " + minecraftVersion + ".");
        }

        String fallback = null;
        String latestStable = null;
        for (JsonNode entry : versions) {
            if (entry == null || !entry.isObject()) continue;
            JsonNode loader = entry.get("loader");
            JsonNode versionNode = loader == null ? null : loader.get("version");
            if (versionNode == null || !versionNode.isTextual() || versionNode.asText().isBlank()) continue;

            String version = versionNode.asText();
            if (fallback == null) fallback = version;

            // Quilt Meta's loader-version entries do not expose the "stable"
            // flag at the top level. Stable releases have numeric versions;
            // prereleases carry a suffix such as "-beta.9".
            if (version.matches("[0-9]+(?:\\.[0-9]+)*")
                    && (latestStable == null || compareNumericVersions(version, latestStable) > 0)) {
                latestStable = version;
            }
        }
        if (latestStable != null) return latestStable;
        if (fallback != null) return fallback;
        throw new IllegalStateException("Quilt Meta returned no valid loader versions for Minecraft " + minecraftVersion + ".");
    }

    private static int compareNumericVersions(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int count = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < count; i++) {
            java.math.BigInteger leftPart = i < leftParts.length
                    ? new java.math.BigInteger(leftParts[i]) : java.math.BigInteger.ZERO;
            java.math.BigInteger rightPart = i < rightParts.length
                    ? new java.math.BigInteger(rightParts[i]) : java.math.BigInteger.ZERO;
            int comparison = leftPart.compareTo(rightPart);
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    public static JsonNode downloadProfile(String minecraftVersion, String loaderVersion) throws Exception {
        requireSegment(minecraftVersion, "Minecraft version");
        requireSegment(loaderVersion, "Quilt Loader version");
        JsonNode profile = DownloadUtil.downloadJson(
                META_URL + minecraftVersion + "/" + loaderVersion + "/profile/json");
        validateProfile(profile);
        return profile;
    }

    public static void installLibraries(JsonNode profile) throws Exception {
        validateProfile(profile);
        FabricInstaller.installLibraries(profile);
    }

    public static JsonNode mergeProfile(JsonNode vanilla, JsonNode quiltProfile) {
        validateProfile(quiltProfile);
        return FabricInstaller.mergeProfile(vanilla, quiltProfile);
    }

    private static void validateProfile(JsonNode profile) {
        if (profile == null || !profile.isObject()) {
            throw new IllegalStateException("Quilt launcher profile is invalid.");
        }
        JsonNode mainClass = profile.get("mainClass");
        if (mainClass == null || !mainClass.isTextual() || mainClass.asText().isBlank()) {
            throw new IllegalStateException("Quilt launcher profile is missing mainClass.");
        }
        JsonNode libraries = profile.get("libraries");
        if (libraries != null && !libraries.isArray()) {
            throw new IllegalStateException("Quilt launcher profile libraries are invalid.");
        }
    }

    private static void requireSegment(String value, String label) {
        if (value == null || !value.matches("[A-Za-z0-9._+-]+")) {
            throw new IllegalArgumentException(label + " is invalid.");
        }
    }
}
