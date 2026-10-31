package dev.itsdropper.vanta.launcher.environment;

import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

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
