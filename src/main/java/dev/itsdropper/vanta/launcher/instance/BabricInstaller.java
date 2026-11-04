package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.itsdropper.vanta.launcher.loader.LoaderProfileStackMerger;

/**
 * Babric client integration for the currently documented b1.7.3 setup.
 *
 * Babric's published Prism instance is a layered profile: a Babric patch,
 * Babric intermediary mappings, and a Fabric Loader component. The installer
 * mirrors that composition instead of pretending Babric has a separate
 * Fabric-Meta endpoint.
 */
public final class BabricInstaller {
    public static final String SUPPORTED_GAME_VERSION = "b1.7.3";
    private static final String INTERMEDIARY_REPOSITORY =
            "https://maven.glass-launcher.net/babric/";
    private static final String BABRIC_ARGUMENTS =
            "${auth_player_name} ${auth_session} --gameDir ${game_directory} --assetsDir ${game_assets}";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BabricInstaller() {
    }

    public static String findLatestLoaderVersion(String minecraftVersion) throws Exception {
        requireSupportedVersion(minecraftVersion);
        // Babric's upstream Prism profile composes Babric b1.7.3 with the
        // Fabric Loader component. Query Fabric Meta for the compatible build.
        return FabricInstaller.findLatestLoaderVersion(SUPPORTED_GAME_VERSION);
    }

    public static JsonNode downloadProfile(String minecraftVersion, String loaderVersion) throws Exception {
        requireSupportedVersion(minecraftVersion);
        if (loaderVersion == null || loaderVersion.isBlank()) {
            throw new IllegalArgumentException("Babric's Fabric Loader version cannot be empty.");
        }
        JsonNode fabricProfile = FabricInstaller.downloadProfile(
                SUPPORTED_GAME_VERSION, loaderVersion.trim());
        return buildOverlay(fabricProfile);
    }

    public static JsonNode downloadFabricProfile(String minecraftVersion, String loaderVersion)
            throws Exception {
        requireSupportedVersion(minecraftVersion);
        JsonNode profile = FabricInstaller.downloadProfile(SUPPORTED_GAME_VERSION, loaderVersion);
        validateFabricBootstrap(profile);
        return profile;
    }

    public static void installLibraries(JsonNode fabricProfile, JsonNode babricOverlay) throws Exception {
        validateFabricBootstrap(fabricProfile);
        validateBabricOverlay(babricOverlay);
        FabricInstaller.installLibraries(fabricProfile);
        FabricInstaller.installLibraries(babricOverlay);
    }

    public static JsonNode mergeProfile(
            JsonNode vanilla,
            JsonNode fabricProfile,
            JsonNode babricOverlay
    ) {
        validateFabricBootstrap(fabricProfile);
        validateBabricOverlay(babricOverlay);
        JsonNode fabricMerged = FabricInstaller.mergeProfile(vanilla, fabricProfile);
        ObjectNode merged = (ObjectNode) LoaderProfileStackMerger.merge(
                fabricMerged, java.util.List.of(babricOverlay), java.util.List.of());
        merged.put("minecraftArguments", BABRIC_ARGUMENTS);
        merged.put("type", "old_beta");
        merged.remove("inheritsFrom");
        return merged;
    }

    public static JsonNode buildOverlay(JsonNode fabricProfile) throws Exception {
        validateFabricBootstrap(fabricProfile);
        ObjectNode overlay = (ObjectNode) MAPPER.readTree("""
                {
                  "minecraftArguments": "${auth_player_name} ${auth_session} --gameDir ${game_directory} --assetsDir ${game_assets}",
                  "libraries": [
                    {"name":"babric:intermediary-upstream:b1.7.3","url":"https://maven.glass-launcher.net/babric/"},
                    {"name":"babric:log4j-config:1.0.0","url":"https://maven.glass-launcher.net/babric/"},
                    {"name":"net.minecrell:terminalconsoleappender:1.2.0","url":"https://repo1.maven.org/maven2/"},
                    {"name":"org.slf4j:slf4j-api:1.8.0-beta4"},
                    {"name":"org.apache.logging.log4j:log4j-slf4j18-impl:2.16.0"},
                    {"name":"org.apache.logging.log4j:log4j-api:2.16.0"},
                    {"name":"org.apache.logging.log4j:log4j-core:2.16.0"},
                    {"name":"com.google.code.gson:gson:2.8.9"},
                    {"name":"com.google.guava:guava:31.0.1-jre"},
                    {"name":"com.google.guava:failureaccess:1.0.1"},
                    {"name":"org.apache.commons:commons-lang3:3.12.0"},
                    {"name":"commons-io:commons-io:2.11.0"},
                    {"name":"commons-codec:commons-codec:1.15"}
                  ]
                }
                """);
        overlay.put("mainClass", fabricProfile.path("mainClass").asText());
        return overlay;
    }

    private static void validateFabricBootstrap(JsonNode profile) {
        if (profile == null || !profile.isObject()
                || !profile.path("mainClass").isTextual()
                || profile.path("mainClass").asText().isBlank()
                || !profile.path("libraries").isArray()) {
            throw new IllegalStateException(
                    "Fabric Meta did not return a valid Babric-compatible launcher profile.");
        }
    }

    private static void validateBabricOverlay(JsonNode overlay) {
        if (overlay == null || !overlay.isObject()
                || !overlay.path("mainClass").isTextual()
                || overlay.path("mainClass").asText().isBlank()
                || !overlay.path("libraries").isArray()) {
            throw new IllegalStateException("Babric launcher overlay is invalid.");
        }
    }

    private static void requireSupportedVersion(String minecraftVersion) {
        if (!SUPPORTED_GAME_VERSION.equals(minecraftVersion)) {
            throw new IllegalArgumentException(
                    "Babric is currently supported only for Minecraft " + SUPPORTED_GAME_VERSION + ".");
        }
    }
}
