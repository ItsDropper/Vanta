package dev.itsdropper.vanta.launcher.environment;

import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentLockfileServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void capturesEveryRegularModFileWithStableSha256AndPortableMetadata() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("instance"));
        Path modsDirectory = Files.createDirectory(instanceDirectory.resolve("mods"));
        byte[] contents = "not-a-jar-but-still-an-installed-artifact".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(modsDirectory.resolve("unmanaged.bin"), contents);

        Instance instance = new Instance(
                "test-instance", "Test", "1.21.1", "fabric", "0.16.9", instanceDirectory
        );

        EnvironmentLockfile lockfile = new EnvironmentLockfileService().capture(instance);

        assertEquals(EnvironmentLockfile.CURRENT_SCHEMA_VERSION, lockfile.schemaVersion());
        assertEquals("test-instance", lockfile.instanceId());
        assertEquals(1, lockfile.mods().size());
        assertTrue(lockfile.environmentFiles().isEmpty());
        assertEquals("unmanaged.bin", lockfile.mods().getFirst().filename());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents)),
                lockfile.mods().getFirst().sha256());
        assertEquals(contents.length, lockfile.mods().getFirst().sizeBytes());
        assertNull(lockfile.javaMajorVersion());
        assertNull(lockfile.mods().getFirst().modId());
        assertNull(lockfile.mods().getFirst().modVersion());
        assertFalse(lockfile.generatedAt().contains(instanceDirectory.toString()));
    }

    @Test
    void writesAndReadsLockfileWithoutMachineSpecificPaths() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("portable-instance"));
        Files.createDirectory(instanceDirectory.resolve("mods"));
        Instance instance = new Instance(
                "portable-instance", "Portable", "1.20.4", "fabric", "0.15.11", instanceDirectory
        );

        EnvironmentLockfileService service = new EnvironmentLockfileService();
        assertFalse(service.hasBaseline(instance));
        Path lockfilePath = service.write(instance);
        assertTrue(service.hasBaseline(instance));
        EnvironmentLockfile loaded = service.readBaseline(instance);

        assertEquals(instance.getId(), loaded.instanceId());
        assertEquals(instance.getMinecraftVersion(), loaded.minecraftVersion());
        assertEquals(instance.getLoaderVersion(), loaded.loaderVersion());
        assertTrue(Files.isRegularFile(lockfilePath));
        assertFalse(Files.readString(lockfilePath).contains(instanceDirectory.toAbsolutePath().toString()));
    }


    @Test
    void capturesResourcePacksShadersAndNestedConfigFilesDeterministically() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("tracked-instance"));
        Files.createDirectory(instanceDirectory.resolve("mods"));
        Path resourcepacks = Files.createDirectory(instanceDirectory.resolve("resourcepacks"));
        Path shaderpacks = Files.createDirectory(instanceDirectory.resolve("shaderpacks"));
        Path config = Files.createDirectories(instanceDirectory.resolve("config/example"));
        Files.writeString(resourcepacks.resolve("pack.zip"), "resource-pack");
        Files.writeString(shaderpacks.resolve("shader.zip"), "shader-pack");
        Files.writeString(config.resolve("settings.json"), "{\\"enabled\\":true}");

        Instance instance = new Instance(
                "tracked", "Tracked", "1.21.1", "fabric", "0.16.9", instanceDirectory
        );
        EnvironmentLockfile lockfile = new EnvironmentLockfileService().capture(instance);

        assertEquals(3, lockfile.environmentFiles().size());
        assertEquals(List.of("config", "resourcepacks", "shaderpacks"),
                lockfile.environmentFiles().stream()
                        .map(EnvironmentLockfile.EnvironmentArtifact::category)
                        .toList());
        assertEquals("example/settings.json", lockfile.environmentFiles().getFirst().relativePath());
        assertEquals(64, lockfile.environmentFiles().getFirst().sha256().length());
        assertFalse(lockfile.environmentFiles().stream()
                .anyMatch(file -> file.relativePath().contains(instanceDirectory.toString())));
    }


    @Test
    void readsPreviousSchemaOneLockfilesWithoutEnvironmentFileMetadata() throws Exception {
        Path lockfilePath = temporaryDirectory.resolve("legacy-v1.json");
        Files.writeString(lockfilePath, """
                {
                  "schemaVersion": 1,
                  "generatedAt": "2026-10-30T00:00:00Z",
                  "instanceId": "legacy",
                  "minecraftVersion": "1.21.1",
                  "loader": "fabric",
                  "loaderVersion": "0.16.9",
                  "mods": []
                }
                """);

        EnvironmentLockfile loaded = new EnvironmentLockfileService().read(lockfilePath);

        assertEquals(1, loaded.schemaVersion());
        assertTrue(loaded.environmentFiles().isEmpty());
    }


    @Test
    void doesNotFollowSymbolicLinksInsideTrackedDirectories() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("symlink-instance"));
        Files.createDirectory(instanceDirectory.resolve("mods"));
        Path config = Files.createDirectory(instanceDirectory.resolve("config"));
        Path outside = Files.writeString(temporaryDirectory.resolve("outside-secret.txt"), "outside");
        try {
            Files.createSymbolicLink(config.resolve("linked.txt"), outside);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException unavailable) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "Symbolic links are unavailable in this test environment.");
        }

        Instance instance = new Instance(
                "symlink", "Symlink", "1.21.1", "fabric", "0.16.9", instanceDirectory
        );
        EnvironmentLockfile lockfile = new EnvironmentLockfileService().capture(instance);

        assertTrue(lockfile.environmentFiles().isEmpty());
    }

    @Test
    void rejectsEnvironmentArtifactPathTraversalInSavedLockfile() throws Exception {
        Path lockfilePath = temporaryDirectory.resolve("traversal.json");
        Files.writeString(lockfilePath, """
                {
                  "schemaVersion": 1,
                  "generatedAt": "2026-10-31T00:00:00Z",
                  "instanceId": "test",
                  "minecraftVersion": "1.21.1",
                  "loader": "fabric",
                  "loaderVersion": "0.16.9",
                  "mods": [],
                  "environmentFiles": [
                    {"category":"config","relativePath":"../outside.txt","sizeBytes":1,
                     "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
                  ]
                }
                """);

        assertThrows(java.io.IOException.class,
                () -> new EnvironmentLockfileService().read(lockfilePath));
    }

    @Test
    void rejectsDuplicateArtifactFilenamesWhenReading() throws Exception {
        Path lockfilePath = temporaryDirectory.resolve("invalid.json");
        Files.writeString(lockfilePath, """
                {
                  "schemaVersion": 1,
                  "generatedAt": "2026-10-31T00:00:00Z",
                  "instanceId": "test",
                  "minecraftVersion": "1.21.1",
                  "loader": "fabric",
                  "loaderVersion": "0.16.9",
                  "mods": [
                    {"filename":"same.jar","sizeBytes":1,"sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
                    {"filename":"SAME.jar","sizeBytes":1,"sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}
                  ]
                }
                """);

        assertThrows(java.io.IOException.class,
                () -> new EnvironmentLockfileService().read(lockfilePath));
    }
}
