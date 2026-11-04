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

        JsonNode fallback = null;
        for (JsonNode entry : versions) {
            if (entry == null || !entry.isObject()) continue;
            JsonNode loader = entry.get("loader");
            JsonNode version = loader == null ? null : loader.get("version");
            if (version == null || !version.isTextual() || version.asText().isBlank()) continue;
            if (fallback == null) fallback = version;
            JsonNode stable = entry.get("stable");
            if (stable != null && stable.isBoolean() && stable.asBoolean()) return version.asText();
        }
        if (fallback != null) return fallback.asText();
        throw new IllegalStateException("Quilt Meta returned no valid loader versions for Minecraft " + minecraftVersion + ".");
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
