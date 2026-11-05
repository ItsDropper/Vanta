package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

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
        normalizeLegacyNativeLibraries(profile);
        return profile;
    }

    public static void installLibraries(JsonNode profile) throws Exception {
        validateProfile(profile);
        FabricInstaller.installLibraries(profile);
    }

    public static JsonNode mergeProfile(JsonNode vanilla, JsonNode profile) {
        validateProfile(profile);
        ObjectNode merged =
                (ObjectNode) FabricInstaller.mergeProfile(vanilla, profile);

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

    /**
     * Older Legacy Fabric profiles describe native libraries using the
     * pre-Mojang "natives" map and a repository URL, but omit
     * downloads.classifiers. Convert that metadata into the modern shape
     * consumed by LibraryResolver and MinecraftFileInstaller. In particular,
     * lwjgl-platform is published as OS-classified JARs; its unclassified
     * Maven JAR does not exist and must never be requested.
     */
    private static void normalizeLegacyNativeLibraries(JsonNode profile) {
        JsonNode libraries = profile.get("libraries");
        if (libraries == null || !libraries.isArray()) {
            return;
        }

        for (JsonNode node : libraries) {
            if (!(node instanceof ObjectNode library)) {
                continue;
            }

            JsonNode natives = library.get("natives");
            if (natives == null || !natives.isObject() || natives.isEmpty()) {
                continue;
            }

            JsonNode existingDownloads = library.get("downloads");
            if (existingDownloads != null
                    && existingDownloads.isObject()
                    && existingDownloads.path("classifiers").isObject()
                    && !existingDownloads.path("classifiers").isEmpty()) {
                continue;
            }

            String[] coordinate = library.path("name").asText("").split(":", -1);
            if (coordinate.length < 3 || coordinate.length > 4
                    || coordinate[0].isBlank()
                    || coordinate[1].isBlank()
                    || coordinate[2].isBlank()) {
                throw new IllegalStateException(
                        "Invalid Legacy Fabric native library coordinate: "
                                + library.path("name").asText("<missing>"));
            }

            String groupPath = coordinate[0].replace('.', '/');
            String artifact = coordinate[1];
            String version = coordinate[2];
            validateMavenPathPart(groupPath, "group", library);
            validateMavenPathPart(artifact, "artifact", library);
            validateMavenPathPart(version, "version", library);

            String repository = legacyRepositoryUrl(library.path("url").asText(""));
            ObjectNode downloads = existingDownloads instanceof ObjectNode object
                    ? object
                    : library.putObject("downloads");
            ObjectNode classifiers = downloads.putObject("classifiers");

            Iterator<Map.Entry<String, JsonNode>> entries = natives.fields();
            while (entries.hasNext()) {
                Map.Entry<String, JsonNode> entry = entries.next();
                String nativePattern = entry.getValue().asText("");
                if (nativePattern.isBlank()) {
                    continue;
                }

                if (nativePattern.contains("${arch}")) {
                    addNativeClassifier(classifiers, repository, groupPath, artifact,
                            version, nativePattern.replace("${arch}", "32"));
                    addNativeClassifier(classifiers, repository, groupPath, artifact,
                            version, nativePattern.replace("${arch}", "64"));
                } else {
                    addNativeClassifier(classifiers, repository, groupPath, artifact,
                            version, nativePattern);
                }
            }

            if (classifiers.isEmpty()) {
                throw new IllegalStateException(
                        "Legacy Fabric native library has no usable classifiers: "
                                + library.path("name").asText());
            }
        }
    }

    private static void addNativeClassifier(
            ObjectNode classifiers,
            String repository,
            String groupPath,
            String artifact,
            String version,
            String classifier
    ) {
        validateMavenPathPart(classifier, "native classifier", null);
        String fileName = artifact + "-" + version + "-" + classifier + ".jar";
        String relativePath = groupPath + "/" + artifact + "/" + version + "/" + fileName;
        ObjectNode metadata = classifiers.putObject(classifier);
        metadata.put("path", relativePath);
        metadata.put("url", repository + relativePath);
    }

    private static String legacyRepositoryUrl(String value) {
        String baseUrl = value == null || value.isBlank()
                ? "https://libraries.minecraft.net/"
                : value.trim();
        try {
            URI repository = URI.create(baseUrl);
            if ("maven.legacyfabric.net".equalsIgnoreCase(repository.getHost())) {
                String pathPrefix = repository.getPath();
                if (pathPrefix == null || pathPrefix.isBlank() || "/".equals(pathPrefix)) {
                    baseUrl = "https://repo.legacyfabric.net/legacyfabric/";
                } else {
                    baseUrl = "https://repo.legacyfabric.net/legacyfabric/"
                            + pathPrefix.replaceFirst("^/+", "");
                }
            }
        } catch (IllegalArgumentException invalidRepositoryUrl) {
            throw new IllegalStateException(
                    "Invalid Legacy Fabric library repository URL: " + value,
                    invalidRepositoryUrl
            );
        }
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    private static void validateMavenPathPart(String value, String label, JsonNode library) {
        if (value == null || value.isBlank()
                || value.equals(".") || value.equals("..")
                || value.contains("/") || value.contains("\\")
                || value.indexOf('\0') >= 0) {
            String name = library == null ? "" : " in " + library.path("name").asText();
            throw new IllegalStateException("Unsafe Legacy Fabric " + label + name + ".");
        }
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