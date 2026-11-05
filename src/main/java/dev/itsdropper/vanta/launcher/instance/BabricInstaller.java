package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
    // Matches the Fabric Loader component pinned by babric/prism-instance.
    // Babric supplies its intermediary separately, so the generic compatibility
    // endpoint is not a reliable way to discover this legacy loader version.
    public static final String SUPPORTED_LOADER_VERSION = "0.19.5";
    private static final String FABRIC_PROFILE_REFERENCE_GAME_VERSION = "1.21.11";
    private static final String BABRIC_PRISM_REVISION = "ebff13db466cb4ccb3ff8bb5e2658ff30bfc913d";
    private static final String BABRIC_ARGUMENTS =
            "${auth_player_name} ${auth_session} --gameDir ${game_directory} --assetsDir ${game_assets}";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BabricInstaller() {
    }

    public static String findLatestLoaderVersion(String minecraftVersion) throws Exception {
        requireSupportedVersion(minecraftVersion);
        // Babric's upstream Prism profile pins this Fabric Loader component.
        // Do not query Fabric Meta's generic game-version list here: its
        // intermediary compatibility check can reject Babric's custom
        // b1.7.3 mappings even though the official Babric profile is valid.
        return SUPPORTED_LOADER_VERSION;
    }

    public static JsonNode downloadProfile(String minecraftVersion, String loaderVersion) throws Exception {
        requireSupportedVersion(minecraftVersion);
        if (loaderVersion == null || loaderVersion.isBlank()) {
            throw new IllegalArgumentException("Babric's Fabric Loader version cannot be empty.");
        }
        JsonNode fabricProfile = downloadFabricProfile(minecraftVersion, loaderVersion);
        return downloadOverlay(fabricProfile);
    }

    public static JsonNode downloadFabricProfile(String minecraftVersion, String loaderVersion)
            throws Exception {
        requireSupportedVersion(minecraftVersion);
        JsonNode downloaded = FabricInstaller.downloadProfile(
                FABRIC_PROFILE_REFERENCE_GAME_VERSION, loaderVersion);
        validateFabricBootstrap(downloaded);

        // Fabric Meta's generic profile may include Fabric's own intermediary
        // mapping artifact. Babric ships its own b1.7.3 intermediary and the
        // Prism instance patch replaces that dependency with this mapping.
        ObjectNode profile = (ObjectNode) downloaded.deepCopy();
        ArrayNode libraries = (ArrayNode) profile.get("libraries");
        for (int i = libraries.size() - 1; i >= 0; i--) {
            String name = libraries.get(i).path("name").asText("");
            if (name.startsWith("net.fabricmc:intermediary:")) {
                libraries.remove(i);
            }
        }
        return profile;
    }

    public static JsonNode downloadOverlay(JsonNode fabricProfile) throws Exception {
        ObjectNode overlay = (ObjectNode) buildOverlay(fabricProfile);
        JsonNode lwjglPatch = DownloadUtil.downloadJson(
                "https://raw.githubusercontent.com/babric/prism-instance/ebff13db466cb4ccb3ff8bb5e2658ff30bfc913d/patches/org.lwjgl.json");
        JsonNode patchLibraries = lwjglPatch == null ? null : lwjglPatch.get("libraries");
        if (patchLibraries == null || !patchLibraries.isArray()) {
            throw new IllegalStateException("Official Babric LWJGL patch has no library list.");
        }
        ArrayNode libraries = (ArrayNode) overlay.get("libraries");
        for (JsonNode library : patchLibraries) {
            if (library == null || !library.isObject()
                    || !library.path("name").isTextual()) {
                throw new IllegalStateException("Official Babric LWJGL patch contains an invalid library.");
            }
            libraries.add(library.deepCopy());
        }
        return overlay;
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
                    {"name":"org.slf4j:slf4j-api:1.8.0-beta4","url":"https://repo1.maven.org/maven2/"},
                    {"name":"org.apache.logging.log4j:log4j-slf4j18-impl:2.16.0","url":"https://repo1.maven.org/maven2/"},
                    {"name":"org.apache.logging.log4j:log4j-api:2.16.0","url":"https://repo1.maven.org/maven2/"},
                    {"name":"org.apache.logging.log4j:log4j-core:2.16.0","url":"https://repo1.maven.org/maven2/"},
                    {"name":"com.google.code.gson:gson:2.8.9","url":"https://repo1.maven.org/maven2/"},
                    {"name":"com.google.guava:guava:31.0.1-jre","url":"https://repo1.maven.org/maven2/"},
                    {"name":"com.google.guava:failureaccess:1.0.1","url":"https://repo1.maven.org/maven2/"},
                    {"name":"org.apache.commons:commons-lang3:3.12.0","url":"https://repo1.maven.org/maven2/"},
                    {"name":"commons-io:commons-io:2.11.0","url":"https://repo1.maven.org/maven2/"},
                    {"name":"commons-codec:commons-codec:1.15","url":"https://repo1.maven.org/maven2/"}
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
