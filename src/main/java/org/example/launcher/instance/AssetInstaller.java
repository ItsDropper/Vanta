package org.example.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.launcher.MinecraftLocator;
import org.example.launcher.model.Instance;

import java.nio.file.Files;
import java.nio.file.Path;

public class AssetInstaller {

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    public static void install(
            Instance instance,
            JsonNode metadata
    ) throws Exception {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        if (metadata == null) {
            throw new IllegalArgumentException(
                    "Metadata cannot be null."
            );
        }

        JsonNode assetIndex =
                metadata.get("assetIndex");

        if (assetIndex == null) {
            throw new IllegalStateException(
                    "No asset index found."
            );
        }

        JsonNode urlNode =
                assetIndex.get("url");

        JsonNode idNode =
                assetIndex.get("id");

        if (urlNode == null
                || idNode == null) {

            throw new IllegalStateException(
                    "Invalid asset index metadata."
            );
        }

        String url =
                urlNode.asText();

        String id =
                idNode.asText();

        Path assets =
                MinecraftLocator
                        .getVantaDirectory()
                        .resolve("assets");

        Path indexes =
                assets.resolve("indexes");

        Path objects =
                assets.resolve("objects");

        Files.createDirectories(indexes);
        Files.createDirectories(objects);

        // ---------------------------------------------------------
        // ASSET INDEX
        // ---------------------------------------------------------

        Path indexFile =
                indexes.resolve(
                        id + ".json"
                );

        if (!Files.exists(indexFile)) {

            System.out.println(
                    "Downloading asset index: "
                            + id
            );

            AssetDownloader.downloadFile(
                    url,
                    indexFile
            );
        }

        // ---------------------------------------------------------
        // READ INDEX
        // ---------------------------------------------------------

        JsonNode index =
                MAPPER.readTree(
                        indexFile.toFile()
                );

        JsonNode assetObjects =
                index.get("objects");

        if (assetObjects == null
                || !assetObjects.isObject()) {

            throw new IllegalStateException(
                    "Asset index contains no objects."
            );
        }

        // ---------------------------------------------------------
        // DOWNLOAD OBJECTS
        // ---------------------------------------------------------

        AssetDownloader.download(
                assetObjects,
                objects
        );

        System.out.println(
                "Assets installed: "
                        + id
        );
    }
}