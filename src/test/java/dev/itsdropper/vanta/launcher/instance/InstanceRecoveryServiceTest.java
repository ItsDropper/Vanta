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
        Files.createDirectories(instanceDirectory.resolve("loader-profiles"));
        Files.createDirectories(instanceDirectory.resolve("saves/world"));
        Files.writeString(instanceDirectory.resolve("mods/example.jar"), "mod-v1");
        Files.writeString(instanceDirectory.resolve("config/example.json"), "{}");
        Files.writeString(instanceDirectory.resolve("loader-profiles/custom.json"), "{\\"libraries\\":[]}");

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
            assertNotNull(zip.getEntry("loader-profiles/custom.json"));
            assertNull(zip.getEntry("saves/world/level.dat"));
        }
        assertEquals(1, InstanceRecoveryService.listRecoveryPoints(instance, temporaryDirectory).size());
    }

    @Test
    void restoreReplacesOnlyManagedDirectoriesAndPreservesWorlds() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("instance"));
        Instance instance = instance(instanceDirectory);
        for (String name : List.of("mods", "config", "resourcepacks", "shaderpacks", "loader-profiles", "saves/world")) {
            Files.createDirectories(instanceDirectory.resolve(name));
        }
        Files.writeString(instanceDirectory.resolve("mods/old.jar"), "old-mod");
        Files.writeString(instanceDirectory.resolve("config/old.json"), "old-config");
        Files.writeString(instanceDirectory.resolve("loader-profiles/old.json"), "{\\"libraries\\":[]}");

        Files.writeString(instanceDirectory.resolve("saves/world/level.dat"), "world-data");

        InstanceRecoveryService.RecoveryPoint point =
                InstanceRecoveryService.createRecoveryPoint(instance, "Known good state", temporaryDirectory);

        Files.writeString(instanceDirectory.resolve("mods/new.jar"), "new-mod");
        Files.writeString(instanceDirectory.resolve("config/new.json"), "new-config");
        Files.writeString(instanceDirectory.resolve("loader-profiles/new.json"), "{\\"libraries\\":[]}");

        Files.writeString(instanceDirectory.resolve("saves/world/level.dat"), "updated-world-data");

        InstanceRecoveryService.restoreRecoveryPoint(instance, point.id(), temporaryDirectory);

        assertTrue(Files.exists(instanceDirectory.resolve("mods/old.jar")));
        assertFalse(Files.exists(instanceDirectory.resolve("mods/new.jar")));
        assertTrue(Files.exists(instanceDirectory.resolve("config/old.json")));
        assertFalse(Files.exists(instanceDirectory.resolve("config/new.json")));
        assertTrue(Files.exists(instanceDirectory.resolve("loader-profiles/old.json")));
        assertFalse(Files.exists(instanceDirectory.resolve("loader-profiles/new.json")));
        assertEquals("updated-world-data",
                Files.readString(instanceDirectory.resolve("saves/world/level.dat")));

        List<InstanceRecoveryService.RecoveryPoint> points =
                InstanceRecoveryService.listRecoveryPoints(instance, temporaryDirectory);
        assertEquals(2, points.size(), "Restore should preserve the pre-restore state as a new point");
        InstanceRecoveryService.RecoveryPoint beforeRestore = points.stream()
                .filter(candidate -> candidate.reason().equals("Automatic snapshot before recovery restore"))
                .findFirst().orElseThrow();

        InstanceRecoveryService.restoreRecoveryPoint(instance, beforeRestore.id(), temporaryDirectory);
        assertTrue(Files.exists(instanceDirectory.resolve("mods/new.jar")),
                "The automatic pre-restore point should make the restore reversible");

        try (var children = Files.list(instanceDirectory)) {
            assertTrue(children.noneMatch(path -> path.getFileName().toString().startsWith(".vanta-recovery-")));
        }
    }

    @Test
    void interruptedRestoreIsRolledBackFromJournal() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("instance"));
        Instance instance = instance(instanceDirectory);
        Path mods = Files.createDirectories(instanceDirectory.resolve("mods"));
        Path config = Files.createDirectories(instanceDirectory.resolve("config"));
        Path resourcepacks = Files.createDirectories(instanceDirectory.resolve("resourcepacks"));
        Files.writeString(mods.resolve("new.jar"), "partially-published");
        Files.writeString(config.resolve("old.json"), "old-config");
        Files.writeString(resourcepacks.resolve("old.zip"), "old-resource-pack");

        Path transaction = Files.createDirectory(instanceDirectory.resolve(".vanta-recovery-crash-test"));
        Path rollback = Files.createDirectory(transaction.resolve("rollback"));
        Files.createDirectories(transaction.resolve("staged"));
        Files.createDirectories(rollback.resolve("mods"));
        Files.createDirectories(rollback.resolve("config"));
        Files.writeString(rollback.resolve("mods/old.jar"), "old-mod");
        Files.writeString(rollback.resolve("config/old.json"), "old-config");
        Files.writeString(transaction.resolve("restore-journal.txt"),
                "VANTA-RECOVERY-TRANSACTION-V1\npresent=mods,config,resourcepacks\n");

        int recovered = InstanceRecoveryService.recoverInterruptedRestores(instance);

        assertEquals(1, recovered);
        assertEquals("old-mod", Files.readString(instanceDirectory.resolve("mods/old.jar")));
        assertEquals("old-config", Files.readString(instanceDirectory.resolve("config/old.json")));
        assertEquals("old-resource-pack",
                Files.readString(instanceDirectory.resolve("resourcepacks/old.zip")));
        assertFalse(Files.exists(instanceDirectory.resolve("shaderpacks")),
                "Directories absent before restore must not be left behind after rollback");
        assertFalse(Files.exists(transaction));
    }

    @Test
    void committedRestoreIsNotRolledBackDuringCleanupRecovery() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("instance"));
        Instance instance = instance(instanceDirectory);
        Path mods = Files.createDirectories(instanceDirectory.resolve("mods"));
        Files.writeString(mods.resolve("new.jar"), "committed-state");

        Path transaction = Files.createDirectory(instanceDirectory.resolve(".vanta-recovery-committed-test"));
        Files.createDirectories(transaction.resolve("staged"));
        Files.createDirectories(transaction.resolve("rollback"));
        Files.writeString(transaction.resolve("restore-journal.txt"),
                "VANTA-RECOVERY-TRANSACTION-V1\npresent=mods\n");
        Files.writeString(transaction.resolve("restore-committed"), "committed");

        int recovered = InstanceRecoveryService.recoverInterruptedRestores(instance);

        assertEquals(0, recovered);
        assertEquals("committed-state", Files.readString(instanceDirectory.resolve("mods/new.jar")));
        assertFalse(Files.exists(transaction));
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
