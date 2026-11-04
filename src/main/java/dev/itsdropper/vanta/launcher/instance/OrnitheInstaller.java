package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Client profile integration for the official Ornithe Meta v2 API.
 *
 * Ornithe's loader profile is installed as an ordinary version-metadata
 * overlay, while the vanilla client, libraries, natives and assets remain
 * owned by Vanta's normal instance installation pipeline.
 */
public final class OrnitheInstaller {
    private static final String META_URL = "https://meta.ornithemc.net/v2/versions/loader/";

    private OrnitheInstaller() {
    }

    public static String findLatestLoaderVersion(String minecraftVersion) throws Exception {
        String gameVersion = encodeSegment(minecraftVersion, "Minecraft version");
        JsonNode versions = DownloadUtil.downloadJson(META_URL + gameVersion);
        if (versions == null || !versions.isArray() || versions.isEmpty()) {
            throw new IllegalStateException(
                    "No Ornithe Loader versions found for Minecraft " + minecraftVersion + ".");
        }

        String fallback = null;
        for (JsonNode entry : versions) {
            JsonNode loader = entry == null ? null : entry.get("loader");
            JsonNode version = loader == null ? null : loader.get("version");
            if (version == null || !version.isTextual() || version.asText().isBlank()) {
                continue;
            }

            if (fallback == null) {
                fallback = version.asText();
            }

            JsonNode stable = entry.get("stable");
            if (stable != null && stable.isBoolean() && stable.asBoolean()) {
                return version.asText();
            }
        }

        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException(
                "Ornithe Meta returned no valid loader versions for Minecraft " + minecraftVersion + ".");
    }

    public static JsonNode downloadProfile(String minecraftVersion, String loaderVersion) throws Exception {
        String gameVersion = encodeSegment(minecraftVersion, "Minecraft version");
        String encodedLoaderVersion = encodeSegment(loaderVersion, "Ornithe Loader version");
        JsonNode profile = DownloadUtil.downloadJson(
                META_URL + gameVersion + "/" + encodedLoaderVersion + "/profile/json");
        validateProfile(profile);
        return profile;
    }

    public static void installLibraries(JsonNode profile) throws Exception {
        validateProfile(profile);
        FabricInstaller.installLibraries(profile);
    }

    public static JsonNode mergeProfile(JsonNode vanilla, JsonNode ornitheProfile) {
        validateProfile(ornitheProfile);
        return FabricInstaller.mergeProfile(vanilla, ornitheProfile);
    }

    private static void validateProfile(JsonNode profile) {
        if (profile == null || !profile.isObject()) {
            throw new IllegalStateException("Ornithe launcher profile is invalid.");
        }

        JsonNode mainClass = profile.get("mainClass");
        if (mainClass == null || !mainClass.isTextual() || mainClass.asText().isBlank()) {
            throw new IllegalStateException("Ornithe launcher profile is missing mainClass.");
        }

        JsonNode libraries = profile.get("libraries");
        if (libraries != null && !libraries.isArray()) {
            throw new IllegalStateException("Ornithe launcher profile libraries are invalid.");
        }
    }

    private static String encodeSegment(String value, String label) {
        if (value == null || value.isBlank()
                || value.contains("/") || value.contains("\\\\") || value.contains("..")) {
            throw new IllegalArgumentException(label + " is invalid.");
        }
        return URLEncoder.encode(value.trim(), StandardCharsets.UTF_8)
                .replace("+", "%20");
    }
}
