package dev.itsdropper.vanta.launcher.instance;

import dev.itsdropper.vanta.json.JsonNode;
import dev.itsdropper.vanta.json.ObjectMapper;
import dev.itsdropper.vanta.json.ObjectNode;
import dev.itsdropper.vanta.json.SerializationFeature;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Reads instance metadata independently of Gson's reflective model binding and
 * upgrades legacy files one instance at a time. The original file is retained
 * before any replacement is attempted.
 */
final class InstanceMetadataMigrator {
    static final int CURRENT_SCHEMA_VERSION = 1;
    static final String BACKUP_FILE_NAME = "instance.json.pre-migration.bak";

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private InstanceMetadataMigrator() {}

    static Instance loadAndMigrate(Path metadataFile, Path instanceDirectory) throws IOException {
        JsonNode root = MAPPER.readTree(metadataFile.toFile());
        if (root == null || !root.isObject()) {
            throw new IOException("Instance metadata must be a JSON object.");
        }

        JsonNode versionNode = root.get("schemaVersion");
        int schemaVersion = 0;
        if (versionNode != null && !versionNode.isNull()) {
            if (!versionNode.isIntegralNumber() || !versionNode.canConvertToInt()) {
                throw new IOException("Instance metadata has an invalid schemaVersion.");
            }
            schemaVersion = versionNode.asInt();
        }
        if (schemaVersion < 0 || schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new IOException("Unsupported instance metadata schema version: " + schemaVersion);
        }

        String id = requiredString(root, "id");
        String name = requiredString(root, "name");
        String minecraftVersion = requiredString(root, "minecraftVersion");
        String loader = requiredString(root, "loader");
        String loaderVersion = optionalString(root, "loaderVersion");
        String icon = optionalString(root, "icon");

        // Never deserialize the persisted directory. It may have been written by
        // another mapper/platform or point outside this instance's own folder.
        Path authoritativeDirectory = instanceDirectory.toAbsolutePath().normalize();
        Instance instance = new Instance(
                id, name, minecraftVersion, loader, loaderVersion,
                authoritativeDirectory, icon);

        if (schemaVersion < CURRENT_SCHEMA_VERSION) {
            writeMigratedMetadata(metadataFile, instance);
        }
        return instance;
    }

    private static String requiredString(JsonNode root, String property) throws IOException {
        JsonNode value = root.get(property);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IOException("Instance metadata is missing a valid '" + property + "' value.");
        }
        return value.asText().trim();
    }

    private static String optionalString(JsonNode root, String property) throws IOException {
        JsonNode value = root.get(property);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) {
            throw new IOException("Instance metadata property '" + property + "' must be a string or null.");
        }
        return value.asText();
    }

    static void writeCurrentMetadata(Path target, Instance instance) throws IOException {
        ObjectNode normalized = (ObjectNode) MAPPER.valueToTree(instance);
        normalized.put("schemaVersion", CURRENT_SCHEMA_VERSION);
        byte[] bytes = MAPPER.writeValueAsString(normalized).getBytes(StandardCharsets.UTF_8);
        Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private static void writeMigratedMetadata(Path metadataFile, Instance instance) throws IOException {
        Path directory = metadataFile.toAbsolutePath().normalize().getParent();
        if (directory == null) throw new IOException("Instance metadata has no parent directory.");

        Path backup = directory.resolve(BACKUP_FILE_NAME);
        if (!Files.exists(backup)) {
            // CREATE_NEW semantics prevent replacing an earlier recovery copy.
            Files.copy(metadataFile, backup);
        }

        Path temporary = directory.resolve("instance.json." + UUID.randomUUID() + ".tmp");
        try {
            Files.createFile(temporary);
            writeCurrentMetadata(temporary, instance);
            try {
                Files.move(temporary, metadataFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, metadataFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
