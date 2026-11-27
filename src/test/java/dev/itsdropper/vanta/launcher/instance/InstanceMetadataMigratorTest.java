package dev.itsdropper.vanta.launcher.instance;

import dev.itsdropper.vanta.json.ObjectMapper;
import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class InstanceMetadataMigratorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void migratesLegacyMetadataIndependentlyAndPreservesAnExactBackup() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("survival"));
        Path metadata = instanceDirectory.resolve("instance.json");
        String legacy = """
                {
                  "id": "survival",
                  "name": "Survival",
                  "minecraftVersion": "1.21.11",
                  "loader": "Fabric",
                  "loaderVersion": "0.18.4",
                  "directory": { "legacyPathRepresentation": "not-a-Path-string" },
                  "icon": "GRASS_BLOCK"
                }
                """;
        Files.writeString(metadata, legacy);

        Instance migrated = InstanceMetadataMigrator.loadAndMigrate(metadata, instanceDirectory);

        assertEquals("survival", migrated.getId());
        assertEquals("Survival", migrated.getName());
        assertEquals("GRASS_BLOCK", migrated.getIcon());
        assertEquals(instanceDirectory.toAbsolutePath().normalize(), migrated.getDirectory());
        assertEquals(legacy, Files.readString(instanceDirectory.resolve(
                InstanceMetadataMigrator.BACKUP_FILE_NAME)));

        var normalized = new ObjectMapper().readTree(metadata.toFile());
        assertEquals(InstanceMetadataMigrator.CURRENT_SCHEMA_VERSION,
                normalized.get("schemaVersion").asInt());
        assertTrue(normalized.get("directory").isTextual());
        assertEquals(instanceDirectory.toAbsolutePath().normalize().toString(),
                normalized.get("directory").asText());

        String firstMigration = Files.readString(metadata);
        Instance loadedAgain = InstanceMetadataMigrator.loadAndMigrate(metadata, instanceDirectory);
        assertEquals(migrated.getId(), loadedAgain.getId());
        assertEquals(firstMigration, Files.readString(metadata));
        assertEquals(legacy, Files.readString(instanceDirectory.resolve(
                InstanceMetadataMigrator.BACKUP_FILE_NAME)));
    }

    @Test
    void invalidMetadataIsNotRewrittenOrBackedUp() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("broken"));
        Path metadata = instanceDirectory.resolve("instance.json");
        String invalid = """
                {"id":"broken","name":"Broken","minecraftVersion":"","loader":"Fabric"}
                """;
        Files.writeString(metadata, invalid);

        assertThrows(IOException.class,
                () -> InstanceMetadataMigrator.loadAndMigrate(metadata, instanceDirectory));

        assertEquals(invalid, Files.readString(metadata));
        assertFalse(Files.exists(instanceDirectory.resolve(
                InstanceMetadataMigrator.BACKUP_FILE_NAME)));
    }

    @Test
    void refusesFutureSchemaWithoutTouchingTheInstance() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("future"));
        Path metadata = instanceDirectory.resolve("instance.json");
        String future = """
                {"schemaVersion":99,"id":"future","name":"Future",
                 "minecraftVersion":"1.21.11","loader":"Fabric"}
                """;
        Files.writeString(metadata, future);

        assertThrows(IOException.class,
                () -> InstanceMetadataMigrator.loadAndMigrate(metadata, instanceDirectory));

        assertEquals(future, Files.readString(metadata));
        assertFalse(Files.exists(instanceDirectory.resolve(
                InstanceMetadataMigrator.BACKUP_FILE_NAME)));
    }
}
