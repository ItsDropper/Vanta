package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.MinecraftLocator;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class AssetInstaller {

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    public static void install(
            Instance instance,
            JsonNode metadata
    ) throws Exception {
        install(metadata);
    }

    public static void install(
            JsonNode metadata
    ) throws Exception {

        if (metadata == null) {
            throw new IllegalArgumentException(
                    "Metadata cannot be null."
            );
        }

        JsonNode assetIndex =
                metadata.get("assetIndex");

        if (assetIndex == null) {
            String type = metadata.path("type").asText();
            if ("old_alpha".equalsIgnoreCase(type)
                    || "old_beta".equalsIgnoreCase(type)) {
                Path legacyAssets = MinecraftLocator.getVantaDirectory()
                        .resolve("assets")
                        .resolve("virtual")
                        .resolve("legacy");
                Files.createDirectories(legacyAssets);
                return;
            }
            throw new IllegalStateException("No asset index found.");
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

        /*
         * Pre-1.7 clients read assets from a directory tree rather than
         * the content-addressed object store. Materialize the legacy view
         * while keeping the shared object store as the download source.
         */
        if ("legacy".equalsIgnoreCase(id)
                || "pre-1.6".equalsIgnoreCase(id)
                || index.path("virtual").asBoolean(false)) {
            String virtualName = "pre-1.6".equalsIgnoreCase(id) ? "pre-1.6" : "legacy";
            Path virtualDirectory = assets.resolve("virtual").resolve(virtualName).normalize();
            if (!virtualDirectory.startsWith(assets.normalize())) {
                throw new IllegalStateException("Unsafe legacy asset directory.");
            }
            Files.createDirectories(virtualDirectory);

            java.util.Iterator<java.util.Map.Entry<String, JsonNode>> entries =
                    assetObjects.fields();
            while (entries.hasNext()) {
                java.util.Map.Entry<String, JsonNode> entry = entries.next();
                String assetPath = entry.getKey();
                JsonNode hashNode = entry.getValue().get("hash");
                if (hashNode == null || !hashNode.isTextual()
                        || !hashNode.asText().matches("[a-fA-F0-9]{40}")) {
                    throw new IllegalStateException("Invalid legacy asset hash for " + assetPath);
                }

                Path relative = Path.of(assetPath).normalize();
                if (relative.isAbsolute() || relative.startsWith("..")) {
                    throw new IllegalStateException("Unsafe legacy asset path: " + assetPath);
                }

                String hash = hashNode.asText().toLowerCase(java.util.Locale.ROOT);
                Path source = objects.resolve(hash.substring(0, 2)).resolve(hash);
                Path target = virtualDirectory.resolve(relative).normalize();
                if (!target.startsWith(virtualDirectory)) {
                    throw new IllegalStateException("Legacy asset path escapes its directory: " + assetPath);
                }
                if (!Files.isRegularFile(source)) {
                    throw new IllegalStateException("Legacy asset object is missing: " + source);
                }
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        System.out.println(
                "Assets installed: "
                        + id
        );
    }
}