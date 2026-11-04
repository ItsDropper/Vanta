package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Legacy Fabric integration backed by the project's official Meta v2 API.
 * This deliberately remains separate from modern Fabric: the supported game
 * versions and loader builds are maintained by Legacy Fabric.
 */
public final class LegacyFabricInstaller {
    private static final String META_URL = "https://meta.legacyfabric.net/v2/versions/loader/";

    private LegacyFabricInstaller() {
    }

    public static String findLatestLoaderVersion(String minecraftVersion) throws Exception {
        String gameVersion = encodeSegment(minecraftVersion, "Minecraft version");
        JsonNode versions = DownloadUtil.downloadJson(META_URL + gameVersion);
        if (versions == null || !versions.isArray() || versions.isEmpty()) {
            throw new IllegalStateException(
                    "No Legacy Fabric Loader versions found for Minecraft " + minecraftVersion + ".");
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

            JsonNode stable = loader.get("stable");
            if (stable == null) {
                stable = entry.get("stable");
            }
            if (stable != null && stable.isBoolean() && stable.asBoolean()) {
                return version.asText();
            }
        }

        if (fallback != null) {
            return fallback;
        }
        throw new IllegalStateException(
                "Legacy Fabric Meta returned no valid loader versions for Minecraft " + minecraftVersion + ".");
    }

    public static JsonNode downloadProfile(String minecraftVersion, String loaderVersion) throws Exception {
        String gameVersion = encodeSegment(minecraftVersion, "Minecraft version");
        String encodedLoaderVersion = encodeSegment(loaderVersion, "Legacy Fabric Loader version");
        JsonNode profile = DownloadUtil.downloadJson(
                META_URL + gameVersion + "/" + encodedLoaderVersion + "/profile/json");
        validateProfile(profile);
        return profile;
    }

    public static void installLibraries(JsonNode profile) throws Exception {
        validateProfile(profile);
        FabricInstaller.installLibraries(profile);
    }

    public static JsonNode mergeProfile(JsonNode vanilla, JsonNode profile) {
        validateProfile(profile);
        com.fasterxml.jackson.databind.node.ObjectNode merged =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        FabricInstaller.mergeProfile(vanilla, profile);

        JsonNode minecraftArguments = profile.get("minecraftArguments");
        if (minecraftArguments != null && minecraftArguments.isTextual()) {
            merged.set("minecraftArguments", minecraftArguments.deepCopy());
        }
        JsonNode arguments = profile.get("arguments");
        if (arguments != null && arguments.isObject()) {
            merged.set("arguments", arguments.deepCopy());
        }
        return merged;
    }

    private static void validateProfile(JsonNode profile) {
        if (profile == null || !profile.isObject()) {
            throw new IllegalStateException("Legacy Fabric launcher profile is invalid.");
        }
        JsonNode mainClass = profile.get("mainClass");
        if (mainClass == null || !mainClass.isTextual() || mainClass.asText().isBlank()) {
            throw new IllegalStateException("Legacy Fabric launcher profile is missing mainClass.");
        }
        JsonNode libraries = profile.get("libraries");
        if (libraries != null && !libraries.isArray()) {
            throw new IllegalStateException("Legacy Fabric launcher profile libraries are invalid.");
        }
    }

    private static String encodeSegment(String value, String label) {
        if (value == null || value.isBlank()
                || value.contains("/") || value.contains("\\") || value.contains("..")) {
            throw new IllegalArgumentException(label + " is invalid.");
        }
        return URLEncoder.encode(value.trim(), StandardCharsets.UTF_8).replace("+", "%20");
    }
}
