package org.example.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.example.launcher.MinecraftLocator;

import java.nio.file.Files;
import java.nio.file.Path;

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

        String url =
                FABRIC_PROFILE_URL
                        + minecraftVersion
                        + "/"
                        + loaderVersion
                        + "/profile/json";

        return DownloadUtil.downloadJson(
                url
        );
    }

    // =============================================================
    // FIND LATEST LOADER
    // =============================================================

    public static String findLatestLoaderVersion(
            String minecraftVersion
    ) throws Exception {

        String url =
                FABRIC_META_URL
                        + minecraftVersion;

        JsonNode versions =
                DownloadUtil.downloadJson(
                        url
                );

        if (versions == null
                || !versions.isArray()
                || versions.isEmpty()) {

            throw new IllegalStateException(
                    "No Fabric Loader versions found for Minecraft "
                            + minecraftVersion
            );
        }

        JsonNode latest =
                versions.get(0);

        JsonNode loader =
                latest.get("loader");

        if (loader == null
                || loader.get("version") == null) {

            throw new IllegalStateException(
                    "Invalid Fabric Loader metadata."
            );
        }

        return loader
                .get("version")
                .asText();
    }

    // =============================================================
    // LIBRARIES
    // =============================================================

    public static void installLibraries(
            JsonNode fabricProfile
    ) throws Exception {

        JsonNode libraries =
                fabricProfile.get("libraries");

        if (libraries == null
                || !libraries.isArray()) {

            return;
        }

        Path librariesDirectory =
                MinecraftLocator
                        .getLibrariesDirectory();

        for (JsonNode library : libraries) {

            JsonNode urlNode =
                    library.get("url");

            JsonNode nameNode =
                    library.get("name");

            if (urlNode == null
                    || nameNode == null) {

                continue;
            }

            String name =
                    nameNode.asText();

            String[] parts =
                    name.split(":");

            if (parts.length < 3) {
                continue;
            }

            String group =
                    parts[0]
                            .replace('.', '/');

            String artifact =
                    parts[1];

            String version =
                    parts[2];

            Path target =
                    librariesDirectory
                            .resolve(group)
                            .resolve(artifact)
                            .resolve(version)
                            .resolve(
                                    artifact
                                            + "-"
                                            + version
                                            + ".jar"
                            );

            if (Files.exists(target)) {
                continue;
            }

            Files.createDirectories(
                    target.getParent()
            );

            String baseUrl =
                    urlNode.asText();

            if (!baseUrl.endsWith("/")) {
                baseUrl += "/";
            }

            String relativePath =
                    group
                            + "/"
                            + artifact
                            + "/"
                            + version
                            + "/"
                            + artifact
                            + "-"
                            + version
                            + ".jar";

            DownloadUtil.downloadFile(
                    baseUrl + relativePath,
                    target
            );
        }
    }

    // =============================================================
    // MERGE
    // =============================================================

    public static JsonNode mergeProfile(
            JsonNode vanilla,
            JsonNode fabric
    ) {

        ObjectNode merged =
                (ObjectNode) vanilla.deepCopy();

        // ---------------------------------------------------------
        // MAIN CLASS
        // ---------------------------------------------------------

        JsonNode mainClass =
                fabric.get("mainClass");

        if (mainClass != null) {

            merged.set(
                    "mainClass",
                    mainClass.deepCopy()
            );
        }

        // ---------------------------------------------------------
        // INHERITS
        // ---------------------------------------------------------

        merged.remove(
                "inheritsFrom"
        );

        // ---------------------------------------------------------
        // LIBRARIES
        // ---------------------------------------------------------

        JsonNode vanillaLibraries =
                merged.get("libraries");

        JsonNode fabricLibraries =
                fabric.get("libraries");

        if (fabricLibraries != null
                && fabricLibraries.isArray()) {

            if (vanillaLibraries == null
                    || !vanillaLibraries.isArray()) {

                merged.set(
                        "libraries",
                        fabricLibraries.deepCopy()
                );

            } else {

                for (JsonNode library :
                        fabricLibraries) {

                    ((ArrayNode)
                            vanillaLibraries)
                            .add(
                                    library.deepCopy()
                            );
                }
            }
        }

        return merged;
    }
}

