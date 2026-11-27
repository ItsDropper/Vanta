package dev.itsdropper.vanta.launcher.instance;

import dev.itsdropper.vanta.json.ObjectMapper;
import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
                  "directory": "C:/Users/Friend/AppData/Roaming/Vanta/instances/survival",
                  "icon": "GRASS_BLOCK",
                  "legacyExtension": {"enabled": true, "label": "preserve me"}
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
        assertTrue(normalized.path("legacyExtension").path("enabled").asBoolean());
        assertEquals("preserve me", normalized.path("legacyExtension").path("label").asText());

        String firstMigration = Files.readString(metadata);
        Instance loadedAgain = InstanceMetadataMigrator.loadAndMigrate(metadata, instanceDirectory);
        assertEquals(migrated.getId(), loadedAgain.getId());
        assertEquals(firstMigration, Files.readString(metadata));
        assertEquals(legacy, Files.readString(instanceDirectory.resolve(
                InstanceMetadataMigrator.BACKUP_FILE_NAME)));
    }

    @Test
    void ignoresLegacyDirectoryObjectsAndUsesTheActualInstanceFolder() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("legacy-object-path"));
        Path metadata = instanceDirectory.resolve("instance.json");
        Files.writeString(metadata, """
                {"id":"legacy-object-path","name":"Legacy","minecraftVersion":"1.20.1",
                 "loader":"Fabric","directory":{"root":"C:/old-machine","names":["Vanta","instances"]}}
                """);

        Instance instance = InstanceMetadataMigrator.loadAndMigrate(metadata, instanceDirectory);

        assertEquals(instanceDirectory.toAbsolutePath().normalize(), instance.getDirectory());
        var normalized = new ObjectMapper().readTree(metadata.toFile());
        assertEquals(instanceDirectory.toAbsolutePath().normalize().toString(),
                normalized.path("directory").asText());
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

    @Test
    void refusesToMigrateWhenAnExistingBackupDoesNotMatchTheOriginal() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("backup-conflict"));
        Path metadata = instanceDirectory.resolve("instance.json");
        Path backup = instanceDirectory.resolve(InstanceMetadataMigrator.BACKUP_FILE_NAME);
        String original = """
                {"id":"backup-conflict","name":"Backup conflict",
                 "minecraftVersion":"1.21.11","loader":"Fabric"}
                """;
        Files.writeString(metadata, original);
        Files.writeString(backup, "not the original metadata");

        assertThrows(IOException.class,
                () -> InstanceMetadataMigrator.loadAndMigrate(metadata, instanceDirectory));

        assertEquals(original, Files.readString(metadata));
        assertEquals("not the original metadata", Files.readString(backup));
    }

    @Test
    void refusesToMigrateWhenBackupPathIsNotAFile() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("backup-directory"));
        Path metadata = instanceDirectory.resolve("instance.json");
        Path backup = Files.createDirectory(
                instanceDirectory.resolve(InstanceMetadataMigrator.BACKUP_FILE_NAME));
        String original = """
                {"id":"backup-directory","name":"Backup directory",
                 "minecraftVersion":"1.21.11","loader":"Fabric"}
                """;
        Files.writeString(metadata, original);

        assertThrows(IOException.class,
                () -> InstanceMetadataMigrator.loadAndMigrate(metadata, instanceDirectory));

        assertEquals(original, Files.readString(metadata));
        assertTrue(Files.isDirectory(backup));
    }

    @Test
    void concurrentDiscoveryOnlyCreatesOneExactBackup() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("concurrent"));
        Path metadata = instanceDirectory.resolve("instance.json");
        String original = """
                {"id":"concurrent","name":"Concurrent","minecraftVersion":"1.21.11","loader":"Fabric"}
                """;
        Files.writeString(metadata, original);

        List<CompletableFuture<Instance>> loads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            loads.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return InstanceMetadataMigrator.loadAndMigrate(metadata, instanceDirectory);
                } catch (IOException failure) {
                    throw new java.util.concurrent.CompletionException(failure);
                }
            }));
        }

        for (CompletableFuture<Instance> load : loads) {
            assertEquals("concurrent", load.join().getId());
        }
        assertEquals(original, Files.readString(instanceDirectory.resolve(
                InstanceMetadataMigrator.BACKUP_FILE_NAME)));
        assertEquals(InstanceMetadataMigrator.CURRENT_SCHEMA_VERSION,
                new ObjectMapper().readTree(metadata.toFile()).path("schemaVersion").asInt());
    }

    @Test
    void saveDoesNotOverwriteInvalidExistingMetadata() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("save-invalid"));
        Path metadata = instanceDirectory.resolve("instance.json");
        String invalid = "{not valid JSON";
        Files.writeString(metadata, invalid);
        Instance replacement = new Instance("save-invalid", "Replacement", "1.21.11",
                "Fabric", "0.18.4", instanceDirectory);

        assertThrows(IOException.class, () -> InstanceManager.saveInstance(replacement));

        assertEquals(invalid, Files.readString(metadata));
        assertFalse(Files.exists(instanceDirectory.resolve(
                InstanceMetadataMigrator.BACKUP_FILE_NAME)));
    }

    @Test
    void rejectsMetadataLargerThanTheSafetyLimitWithoutRewritingIt() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("oversized"));
        Path metadata = instanceDirectory.resolve("instance.json");
        String oversized = " ".repeat(1024 * 1024 + 1);
        Files.writeString(metadata, oversized);

        assertThrows(IOException.class,
                () -> InstanceMetadataMigrator.loadAndMigrate(metadata, instanceDirectory));

        assertEquals(oversized.length(), Files.size(metadata));
        assertFalse(Files.exists(instanceDirectory.resolve(
                InstanceMetadataMigrator.BACKUP_FILE_NAME)));
    }
}
