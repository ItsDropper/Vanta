package org.example.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.example.launcher.MinecraftLocator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class FabricInstaller {

    private static final String FABRIC_PROFILE_URL =
            "https://meta.fabricmc.net/v2/versions/loader/";

    private static final String FABRIC_META_URL =
            "https://meta.fabricmc.net/v2/versions/loader/";

    // =============================================================
    // PROFILE
    // =============================================================

    public static JsonNode downloadProfile(
            String minecraftVersion,
            String loaderVersion
    ) throws Exception {

        requireVersion(
                minecraftVersion,
                "Minecraft version"
        );

        requireVersion(
                loaderVersion,
                "Fabric Loader version"
        );

        String url =
                FABRIC_PROFILE_URL
                        + minecraftVersion
                        + "/"
                        + loaderVersion
                        + "/profile/json";

        JsonNode profile =
                DownloadUtil.downloadJson(url);

        validateProfile(profile);

        return profile;
    }

    // =============================================================
    // FIND LATEST LOADER
    // =============================================================

    public static String findLatestLoaderVersion(
            String minecraftVersion
    ) throws Exception {

        requireVersion(
                minecraftVersion,
                "Minecraft version"
        );

        String url =
                FABRIC_META_URL
                        + minecraftVersion;

        JsonNode versions =
                DownloadUtil.downloadJson(url);

        if (versions == null
                || !versions.isArray()
                || versions.isEmpty()) {

            throw new IllegalStateException(
                    "No Fabric Loader versions found for Minecraft "
                            + minecraftVersion
            );
        }

        /*
         * Fabric Meta normally returns versions newest-first,
         * but do not blindly assume the first entry is stable.
         *
         * Prefer the newest stable loader. If the API somehow
         * provides no stable entry, fall back to the first valid
         * loader version rather than failing unnecessarily.
         */
        JsonNode fallback = null;

        for (JsonNode version : versions) {

            if (version == null
                    || !version.isObject()) {
                continue;
            }

            JsonNode loader =
                    version.get("loader");

            if (loader == null
                    || !loader.isObject()) {
                continue;
            }

            JsonNode loaderVersionNode =
                    loader.get("version");

            if (loaderVersionNode == null
                    || !loaderVersionNode.isTextual()
                    || loaderVersionNode.asText().isBlank()) {
                continue;
            }

            if (fallback == null) {
                fallback = loader;
            }

            JsonNode stable =
                    version.get("stable");

            if (stable != null
                    && stable.isBoolean()
                    && stable.asBoolean()) {

                return loaderVersionNode.asText();
            }
        }

        if (fallback != null) {

            String version =
                    fallback
                            .get("version")
                            .asText();

            if (!version.isBlank()) {
                return version;
            }
        }

        throw new IllegalStateException(
                "Fabric Loader metadata contained no valid loader "
                        + "versions for Minecraft "
                        + minecraftVersion
        );
    }

    // =============================================================
    // LIBRARIES
    // =============================================================

    public static void installLibraries(
            JsonNode fabricProfile
    ) throws Exception {

        validateProfile(
                fabricProfile
        );

        JsonNode libraries =
                fabricProfile.get("libraries");

        if (libraries == null
                || libraries.isNull()) {

            return;
        }

        if (!libraries.isArray()) {

            throw new IllegalStateException(
                    "Fabric library metadata is not an array."
            );
        }

        Path librariesDirectory =
                MinecraftLocator
                        .getLibrariesDirectory();

        for (JsonNode library : libraries) {

            if (library == null
                    || !library.isObject()) {

                throw new IllegalStateException(
                        "Invalid Fabric library entry."
                );
            }

            JsonNode urlNode =
                    library.get("url");

            JsonNode nameNode =
                    library.get("name");

            if (urlNode == null
                    || !urlNode.isTextual()
                    || urlNode.asText().isBlank()) {

                throw new IllegalStateException(
                        "Fabric library is missing a repository URL."
                );
            }

            if (nameNode == null
                    || !nameNode.isTextual()
                    || nameNode.asText().isBlank()) {

                throw new IllegalStateException(
                        "Fabric library is missing its Maven name."
                );
            }

            String name =
                    nameNode.asText().trim();

            MavenCoordinate coordinate =
                    parseCoordinate(name);

            Path target =
                    librariesDirectory
                            .resolve(
                                    coordinate.groupPath
                            )
                            .resolve(
                                    coordinate.artifact
                            )
                            .resolve(
                                    coordinate.version
                            )
                            .resolve(
                                    coordinate.fileName()
                            )
                            .normalize();

            /*
             * Make sure the resolved path cannot escape the
             * shared Vanta libraries directory.
             */
            if (!target.startsWith(
                    librariesDirectory.normalize()
            )) {

                throw new IllegalStateException(
                        "Unsafe Fabric library path: "
                                + name
                );
            }

            if (Files.isRegularFile(target)) {
                continue;
            }

            if (Files.exists(target)) {

                throw new IllegalStateException(
                        "Fabric library target is not a regular file: "
                                + target
                );
            }

            Files.createDirectories(
                    target.getParent()
            );

            String baseUrl =
                    urlNode
                            .asText()
                            .trim();

            if (baseUrl.isBlank()) {

                throw new IllegalStateException(
                        "Fabric library repository URL is empty."
                );
            }

            if (!baseUrl.endsWith("/")) {
                baseUrl += "/";
            }

            String relativePath =
                    coordinate.groupPath
                            + "/"
                            + coordinate.artifact
                            + "/"
                            + coordinate.version
                            + "/"
                            + coordinate.fileName();

            String downloadUrl =
                    baseUrl
                            + relativePath;

            System.out.println(
                    "Downloading Fabric library: "
                            + name
            );

            DownloadUtil.downloadFile(
                    downloadUrl,
                    target
            );

            if (!Files.isRegularFile(target)
                    || Files.size(target) == 0) {

                throw new IllegalStateException(
                        "Fabric library download produced an "
                                + "invalid file: "
                                + target
                );
            }
        }
    }

    // =============================================================
    // MERGE
    // =============================================================

    public static JsonNode mergeProfile(
            JsonNode vanilla,
            JsonNode fabric
    ) {

        if (vanilla == null
                || !vanilla.isObject()) {

            throw new IllegalArgumentException(
                    "Vanilla version metadata must be an object."
            );
        }

        validateProfile(
                fabric
        );

        ObjectNode merged =
                (ObjectNode) vanilla.deepCopy();

        // ---------------------------------------------------------
        // MAIN CLASS
        // ---------------------------------------------------------

        JsonNode mainClass =
                fabric.get("mainClass");

        if (mainClass != null
                && mainClass.isTextual()
                && !mainClass.asText().isBlank()) {

            merged.set(
                    "mainClass",
                    mainClass.deepCopy()
            );
        }

        // ---------------------------------------------------------
        // INHERITS
        // ---------------------------------------------------------

        /*
         * The merged profile is standalone, so it must not depend
         * on the original vanilla profile through inheritsFrom.
         */
        merged.remove(
                "inheritsFrom"
        );

        // ---------------------------------------------------------
        // LIBRARIES
        // ---------------------------------------------------------

        mergeLibraries(
                merged,
                fabric
        );

        // ---------------------------------------------------------
        // VALIDATION
        // ---------------------------------------------------------

        validateMergedProfile(
                merged
        );

        return merged;
    }

    private static void mergeLibraries(
            ObjectNode merged,
            JsonNode fabric
    ) {

        Map<String, JsonNode> libraries =
                new LinkedHashMap<>();

        JsonNode vanillaLibraries =
                merged.get("libraries");

        if (vanillaLibraries != null
                && vanillaLibraries.isArray()) {

            for (JsonNode library :
                    vanillaLibraries) {

                String key =
                        libraryKey(library);

                libraries.put(
                        key,
                        library
                );
            }
        }

        JsonNode fabricLibraries =
                fabric.get("libraries");

        if (fabricLibraries != null
                && fabricLibraries.isArray()) {

            /*
             * Fabric entries are added last so a Fabric library
             * with the same Maven coordinate replaces the vanilla
             * definition instead of producing duplicates.
             */
            for (JsonNode library :
                    fabricLibraries) {

                String key =
                        libraryKey(library);

                libraries.put(
                        key,
                        library.deepCopy()
                );
            }
        }

        ArrayNode mergedLibraries =
                JsonNodeFactory.instance.arrayNode();

        for (JsonNode library :
                libraries.values()) {

            mergedLibraries.add(
                    library.deepCopy()
            );
        }

        merged.set(
                "libraries",
                mergedLibraries
        );
    }

    // =============================================================
    // VALIDATION
    // =============================================================

    private static void validateProfile(
            JsonNode profile
    ) {

        if (profile == null
                || !profile.isObject()) {

            throw new IllegalStateException(
                    "Fabric profile metadata is invalid."
            );
        }

        JsonNode mainClass =
                profile.get("mainClass");

        if (mainClass == null
                || !mainClass.isTextual()
                || mainClass.asText().isBlank()) {

            throw new IllegalStateException(
                    "Fabric profile is missing mainClass."
            );
        }

        JsonNode libraries =
                profile.get("libraries");

        if (libraries != null
                && !libraries.isArray()) {

            throw new IllegalStateException(
                    "Fabric profile libraries are invalid."
            );
        }
    }

    private static void validateMergedProfile(
            ObjectNode profile
    ) {

        JsonNode mainClass =
                profile.get("mainClass");

        if (mainClass == null
                || !mainClass.isTextual()
                || mainClass.asText().isBlank()) {

            throw new IllegalStateException(
                    "Merged Minecraft profile is missing mainClass."
            );
        }

        JsonNode libraries =
                profile.get("libraries");

        if (libraries == null
                || !libraries.isArray()) {

            throw new IllegalStateException(
                    "Merged Minecraft profile is missing libraries."
            );
        }
    }

    private static String libraryKey(
            JsonNode library
    ) {

        if (library == null
                || !library.isObject()) {

            throw new IllegalStateException(
                    "Invalid library entry in version metadata."
            );
        }

        JsonNode name =
                library.get("name");

        if (name == null
                || !name.isTextual()
                || name.asText().isBlank()) {

            /*
             * Some metadata could theoretically lack a Maven
             * name. Give it a deterministic key rather than
             * silently collapsing unrelated entries.
             */
            return library.toString();
        }

        return name.asText().trim();
    }

    // =============================================================
    // MAVEN COORDINATES
    // =============================================================

    private static MavenCoordinate parseCoordinate(
            String name
    ) {

        String[] parts =
                name.split(":");

        if (parts.length < 3
                || parts.length > 4) {

            throw new IllegalStateException(
                    "Invalid Fabric Maven coordinate: "
                            + name
            );
        }

        String group =
                parts[0].trim();

        String artifact =
                parts[1].trim();

        String version =
                parts[2].trim();

        String classifier =
                parts.length == 4
                        ? parts[3].trim()
                        : null;

        if (group.isBlank()
                || artifact.isBlank()
                || version.isBlank()) {

            throw new IllegalStateException(
                    "Invalid Fabric Maven coordinate: "
                            + name
            );
        }

        String groupPath =
                group.replace('.', '/');

        if (groupPath.startsWith("/")
                || groupPath.contains("..")
                || artifact.contains("/")
                || artifact.contains("\\")
                || artifact.contains("..")
                || version.contains("/")
                || version.contains("\\")
                || version.contains("..")
                || (classifier != null
                && (classifier.contains("/")
                || classifier.contains("\\")
                || classifier.contains("..")))) {

            throw new IllegalStateException(
                    "Unsafe Fabric Maven coordinate: "
                            + name
            );
        }

        return new MavenCoordinate(
                groupPath,
                artifact,
                version,
                classifier
        );
    }

    private static void requireVersion(
            String value,
            String description
    ) {

        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    description
                            + " cannot be empty."
            );
        }

        String trimmed =
                value.trim();

        if (trimmed.contains("/")
                || trimmed.contains("\\")
                || trimmed.contains("..")) {

            throw new IllegalArgumentException(
                    "Invalid "
                            + description
                            + ": "
                            + value
            );
        }
    }

    // =============================================================
    // MAVEN COORDINATE
    // =============================================================

    private static final class MavenCoordinate {

        private final String groupPath;
        private final String artifact;
        private final String version;
        private final String classifier;

        private MavenCoordinate(
                String groupPath,
                String artifact,
                String version,
                String classifier
        ) {

            this.groupPath = groupPath;
            this.artifact = artifact;
            this.version = version;
            this.classifier = classifier;
        }

        private String fileName() {

            StringBuilder file =
                    new StringBuilder();

            file.append(artifact)
                    .append("-")
                    .append(version);

            if (classifier != null
                    && !classifier.isBlank()) {

                file.append("-")
                        .append(classifier);
            }

            file.append(".jar");

            return file.toString();
        }
    }
}