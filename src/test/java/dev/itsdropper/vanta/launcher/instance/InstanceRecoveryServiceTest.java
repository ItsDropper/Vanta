package dev.itsdropper.vanta.launcher.instance;

import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class InstanceRecoveryServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void recoveryPointIncludesManagedFoldersButExcludesWorlds() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("instance"));
        Instance instance = instance(instanceDirectory);
        Files.createDirectories(instanceDirectory.resolve("mods"));
        Files.createDirectories(instanceDirectory.resolve("config"));
        Files.createDirectories(instanceDirectory.resolve("resourcepacks"));
        Files.createDirectories(instanceDirectory.resolve("shaderpacks"));
        Files.createDirectories(instanceDirectory.resolve("saves/world"));
        Files.writeString(instanceDirectory.resolve("mods/example.jar"), "mod-v1");
        Files.writeString(instanceDirectory.resolve("config/example.json"), "{}");
        Files.writeString(instanceDirectory.resolve("saves/world/level.dat"), "world-data");

        InstanceRecoveryService.RecoveryPoint point =
                InstanceRecoveryService.createRecoveryPoint(instance, "Before loader repair", temporaryDirectory);

        Path archive = temporaryDirectory.resolve("recovery-points").resolve(instance.getId())
                .resolve(point.id() + ".zip");
        assertTrue(Files.isRegularFile(archive));
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            assertNotNull(zip.getEntry("mods/example.jar"));
            assertNotNull(zip.getEntry("config/example.json"));
            assertNotNull(zip.getEntry("resourcepacks/"));
            assertNotNull(zip.getEntry("shaderpacks/"));
            assertNull(zip.getEntry("saves/world/level.dat"));
        }
        assertEquals(1, InstanceRecoveryService.listRecoveryPoints(instance, temporaryDirectory).size());
    }

    @Test
    void restoreReplacesOnlyManagedDirectoriesAndPreservesWorlds() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("instance"));
        Instance instance = instance(instanceDirectory);
        for (String name : List.of("mods", "config", "resourcepacks", "shaderpacks", "saves/world")) {
            Files.createDirectories(instanceDirectory.resolve(name));
        }
        Files.writeString(instanceDirectory.resolve("mods/old.jar"), "old-mod");
        Files.writeString(instanceDirectory.resolve("config/old.json"), "old-config");
        Files.writeString(instanceDirectory.resolve("saves/world/level.dat"), "world-data");

        InstanceRecoveryService.RecoveryPoint point =
                InstanceRecoveryService.createRecoveryPoint(instance, "Known good state", temporaryDirectory);

        Files.writeString(instanceDirectory.resolve("mods/new.jar"), "new-mod");
        Files.writeString(instanceDirectory.resolve("config/new.json"), "new-config");
        Files.writeString(instanceDirectory.resolve("saves/world/level.dat"), "updated-world-data");

        InstanceRecoveryService.restoreRecoveryPoint(instance, point.id(), temporaryDirectory);

        assertTrue(Files.exists(instanceDirectory.resolve("mods/old.jar")));
        assertFalse(Files.exists(instanceDirectory.resolve("mods/new.jar")));
        assertTrue(Files.exists(instanceDirectory.resolve("config/old.json")));
        assertFalse(Files.exists(instanceDirectory.resolve("config/new.json")));
        assertEquals("updated-world-data",
                Files.readString(instanceDirectory.resolve("saves/world/level.dat")));
        try (var children = Files.list(instanceDirectory)) {
            assertTrue(children.noneMatch(path -> path.getFileName().toString().startsWith(".vanta-recovery-")));
        }
    }

    @Test
    void keepsOnlyFiveMostRecentRecoveryPoints() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("instance"));
        Instance instance = instance(instanceDirectory);
        Files.createDirectories(instanceDirectory.resolve("mods"));
        Files.writeString(instanceDirectory.resolve("mods/example.jar"), "mod");

        for (int i = 0; i < 6; i++) {
            Files.writeString(instanceDirectory.resolve("mods/example.jar"), "mod-" + i);
            InstanceRecoveryService.createRecoveryPoint(instance, "Point " + i, temporaryDirectory);
        }

        assertEquals(5, InstanceRecoveryService.listRecoveryPoints(instance, temporaryDirectory).size());
    }

    private static Instance instance(Path directory) {
        return new Instance("recovery-test", "Recovery Test", "1.21.1",
                "fabric", "0.16.0", directory);
    }
}
