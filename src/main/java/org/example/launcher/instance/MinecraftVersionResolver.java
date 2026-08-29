package org.example.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.example.launcher.model.Instance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MinecraftVersionResolver {

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    private static final String VERSION_MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    // =============================================================
    // METADATA
    // =============================================================

    public static JsonNode downloadMetadata(
            String minecraftVersion
    ) throws Exception {

        JsonNode version =
                findVersion(
                        minecraftVersion
                );

        if (version == null) {
            throw new IllegalArgumentException(
                    "Minecraft version not found: "
                            + minecraftVersion
            );
        }

        JsonNode url =
                version.get("url");

        if (url == null) {
            throw new IllegalStateException(
                    "Minecraft version metadata URL is missing."
            );
        }

        return DownloadUtil.downloadJson(
                url.asText()
        );
    }

    // =============================================================
    // VERSION
    // =============================================================

    private static JsonNode findVersion(
            String minecraftVersion
    ) throws Exception {

        JsonNode manifest =
                DownloadUtil.downloadJson(
                        VERSION_MANIFEST_URL
                );

        JsonNode versions =
                manifest.get("versions");

        if (versions == null
                || !versions.isArray()) {

            throw new IllegalStateException(
                    "Invalid Minecraft version manifest."
            );
        }

        for (JsonNode version : versions) {

            JsonNode id =
                    version.get("id");

            if (id != null
                    && minecraftVersion.equals(
                    id.asText()
            )) {

                return version;
            }
        }

        return null;
    }

    // =============================================================
    // SAVE METADATA
    // =============================================================

    public static void saveMetadata(
            Instance instance,
            JsonNode metadata
    ) throws IOException {

        Path minecraftDirectory =
                instance
                        .getDirectory()
                        .resolve("minecraft");

        Files.createDirectories(
                minecraftDirectory
        );

        Path versionFile =
                minecraftDirectory
                        .resolve("version.json");

        MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(
                        versionFile.toFile(),
                        metadata
                );
    }
}