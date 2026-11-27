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
        if (Files.isSymbolicLink(metadataFile)
                || !Files.isRegularFile(metadataFile, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Instance metadata is not a regular, non-symbolic-link file.");
        }
        if (Files.size(metadataFile) > 1024L * 1024) {
            throw new IOException("Instance metadata exceeds the 1 MiB safety limit.");
        }

        // Parse and preserve the same byte snapshot. Never copy a later version
        // of the file after parsing an earlier one.
        byte[] originalBytes = Files.readAllBytes(metadataFile);
        if (originalBytes.length > 1024 * 1024) {
            throw new IOException("Instance metadata exceeds the 1 MiB safety limit.");
        }
        JsonNode root = MAPPER.readTree(originalBytes);
        int schemaVersion = schemaVersion(root);
        Instance instance = decode(root, instanceDirectory);
        if (schemaVersion < CURRENT_SCHEMA_VERSION) {
            writeMigratedMetadata(metadataFile, instance, root, originalBytes);
        }
        return instance;
    }

    /** Decodes metadata from a backup or pack without trusting its persisted directory. */
    static Instance readMetadata(byte[] json, Path authoritativeDirectory) throws IOException {
        return decode(MAPPER.readTree(json), authoritativeDirectory);
    }

    private static Instance decode(JsonNode root, Path authoritativeDirectory) throws IOException {
        if (root == null || !root.isObject()) {
            throw new IOException("Instance metadata must be a JSON object.");
        }
        schemaVersion(root);

        String id = requiredString(root, "id");
        String name = requiredString(root, "name");
        String minecraftVersion = requiredString(root, "minecraftVersion");
        String loader = requiredString(root, "loader");
        String loaderVersion = optionalString(root, "loaderVersion");
        String icon = optionalString(root, "icon");

        // Never deserialize the persisted directory. It may have been written by
        // another mapper/platform or point outside this instance's own folder.
        Path directory = authoritativeDirectory == null
                ? null : authoritativeDirectory.toAbsolutePath().normalize();
        return new Instance(id, name, minecraftVersion, loader, loaderVersion, directory, icon);
    }

    private static int schemaVersion(JsonNode root) throws IOException {
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
        return schemaVersion;
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

    static byte[] writeCurrentMetadataBytes(Instance instance) throws IOException {
        ObjectNode normalized = (ObjectNode) MAPPER.valueToTree(instance);
        normalized.put("schemaVersion", CURRENT_SCHEMA_VERSION);
        return MAPPER.writeValueAsString(normalized).getBytes(StandardCharsets.UTF_8);
    }

    static void writeCurrentMetadata(Path target, Instance instance) throws IOException {
        Files.write(target, writeCurrentMetadataBytes(instance),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static void writeMigratedMetadata(
            Path metadataFile, Instance instance, JsonNode originalTree, byte[] originalBytes
    ) throws IOException {
        Path directory = metadataFile.toAbsolutePath().normalize().getParent();
        if (directory == null) throw new IOException("Instance metadata has no parent directory.");

        preserveOriginal(directory.resolve(BACKUP_FILE_NAME), originalBytes);

        // Retain extension fields that this launcher version does not interpret.
        // Canonical fields are overwritten with validated values and the actual
        // instance folder, so stale machine-specific paths are never trusted.
        ObjectNode normalized = (ObjectNode) originalTree.deepCopy();
        normalized.put("id", instance.getId());
        normalized.put("name", instance.getName());
        normalized.put("minecraftVersion", instance.getMinecraftVersion());
        normalized.put("loader", instance.getLoader());
        normalized.put("loaderVersion", instance.getLoaderVersion());
        normalized.put("directory", instance.getDirectory().toString());
        normalized.put("icon", instance.getIcon());
        normalized.put("schemaVersion", CURRENT_SCHEMA_VERSION);
        byte[] migratedBytes = MAPPER.writeValueAsString(normalized).getBytes(StandardCharsets.UTF_8);

        Path temporary = Files.createTempFile(directory, "instance.json.", ".tmp");
        try {
            writeAndSync(temporary, migratedBytes);
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

    /**
     * Creates the recovery copy exclusively. A process interruption can leave
     * a partial backup, but then migration refuses to proceed on the next run;
     * the original metadata remains untouched until the backup is fully synced.
     */
    private static void preserveOriginal(Path backup, byte[] originalBytes) throws IOException {
        if (Files.exists(backup, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            verifyExistingBackup(backup, originalBytes);
            return;
        }

        boolean created = false;
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(
                backup, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            created = true;
            writeAndSync(channel, originalBytes);
        } catch (java.nio.file.FileAlreadyExistsException concurrentMigration) {
            verifyExistingBackup(backup, originalBytes);
        } catch (IOException failure) {
            if (created) {
                try { Files.deleteIfExists(backup); }
                catch (IOException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            }
            throw failure;
        }
    }

    private static void verifyExistingBackup(Path backup, byte[] originalBytes) throws IOException {
        if (Files.isSymbolicLink(backup)
                || !Files.isRegularFile(backup, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.size(backup) != originalBytes.length
                || !java.util.Arrays.equals(Files.readAllBytes(backup), originalBytes)) {
            throw new IOException("The existing instance metadata backup is not an exact copy; "
                    + "the original metadata was left unchanged.");
        }
    }

    private static void writeAndSync(Path file, byte[] bytes) throws IOException {
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(
                file, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writeAndSync(channel, bytes);
        }
    }

    private static void writeAndSync(java.nio.channels.FileChannel channel, byte[] bytes) throws IOException {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) channel.write(buffer);
        channel.force(true);
    }
}
