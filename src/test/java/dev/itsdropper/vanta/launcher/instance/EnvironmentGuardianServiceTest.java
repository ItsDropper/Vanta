package dev.itsdropper.vanta.launcher.instance;

import dev.itsdropper.vanta.json.ObjectMapper;
import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.service.LaunchHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentGuardianServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsVerifiedBackupsForStoppedInstancesAndSkipsRunningOnes() throws Exception {
        Instance stopped = createInstance("stopped");
        Instance running = createInstance("running");
        Path backupRoot = temporaryDirectory.resolve("automatic-backups");

        java.util.List<LaunchHistoryRepository.Outcome> recorded = new java.util.ArrayList<>();
        EnvironmentGuardianService service = new EnvironmentGuardianService(
                backupRoot, () -> List.of(stopped, running), instance -> instance.getId().equals("running"),
                () -> true, () -> 24, () -> 3, InstanceBackupService::createBackup,
                (instance, outcome, message) -> recorded.add(outcome));

        List<EnvironmentGuardianService.BackupResult> results = service.runOnce();

        assertEquals(2, results.size());
        EnvironmentGuardianService.BackupResult created = results.stream()
                .filter(result -> result.instanceId().equals("stopped")).findFirst().orElseThrow();
        EnvironmentGuardianService.BackupResult skipped = results.stream()
                .filter(result -> result.instanceId().equals("running")).findFirst().orElseThrow();
        assertEquals(EnvironmentGuardianService.Status.CREATED, created.status());
        assertTrue(Files.isRegularFile(created.archive()));
        assertTrue(InstanceBackupService.verifyBackup(created.archive()).entryCount() >= 2);
        assertEquals(EnvironmentGuardianService.Status.SKIPPED_RUNNING, skipped.status());
        assertEquals(List.of(LaunchHistoryRepository.Outcome.BACKUP_CREATED), recorded);
        assertFalse(Files.exists(backupRoot.resolve("running")));
    }

    @Test
    void skipsInstancesWithARecentAutomaticBackup() throws Exception {
        Instance instance = createInstance("recent");
        Path directory = Files.createDirectories(temporaryDirectory.resolve("automatic-backups/recent"));
        Path existing = Files.writeString(directory.resolve("recent.zip"), "existing archive");
        Files.setLastModifiedTime(existing, FileTime.fromMillis(System.currentTimeMillis()));

        EnvironmentGuardianService service = service(
                temporaryDirectory.resolve("automatic-backups"),
                List.of(instance),
                ignored -> false,
                24,
                3,
                InstanceBackupService::createBackup
        );

        List<EnvironmentGuardianService.BackupResult> results = service.runOnce();

        assertEquals(EnvironmentGuardianService.Status.SKIPPED_RECENT, results.getFirst().status());
        assertEquals(existing, results.getFirst().archive());
        long archiveCount;
        try (var stream = Files.list(directory)) {
            archiveCount = stream.count();
        }
        assertEquals(1, archiveCount);
    }

    @Test
    void prunesOldestArchivesOnlyAfterTheNewBackupHasBeenCreated() throws Exception {
        Instance instance = createInstance("retained");
        Path directory = Files.createDirectories(temporaryDirectory.resolve("automatic-backups/retained"));
        long oldTime = System.currentTimeMillis() - Duration.ofDays(3).toMillis();
        Path first = Files.writeString(directory.resolve("first.zip"), "old one");
        Path second = Files.writeString(directory.resolve("second.zip"), "old two");
        Files.setLastModifiedTime(first, FileTime.fromMillis(oldTime));
        Files.setLastModifiedTime(second, FileTime.fromMillis(oldTime + 1000));

        EnvironmentGuardianService service = service(
                temporaryDirectory.resolve("automatic-backups"),
                List.of(instance),
                ignored -> false,
                6,
                2,
                InstanceBackupService::createBackup
        );

        List<EnvironmentGuardianService.BackupResult> results = service.runOnce();

        assertEquals(EnvironmentGuardianService.Status.CREATED, results.getFirst().status());
        List<Path> remaining;
        try (var stream = Files.list(directory)) {
            remaining = stream.filter(path -> path.toString().endsWith(".zip")).toList();
        }
        assertEquals(2, remaining.size());
        assertFalse(Files.exists(first));
        assertTrue(remaining.contains(results.getFirst().archive()));
    }

    @Test
    void preservesExistingArchivesWhenANewBackupFails() throws Exception {
        Instance instance = createInstance("failure");
        Path directory = Files.createDirectories(temporaryDirectory.resolve("automatic-backups/failure"));
        Path existing = Files.writeString(directory.resolve("previous.zip"), "previous archive");
        Files.setLastModifiedTime(existing,
                FileTime.fromMillis(System.currentTimeMillis() - Duration.ofDays(2).toMillis()));

        EnvironmentGuardianService service = service(
                temporaryDirectory.resolve("automatic-backups"),
                List.of(instance),
                ignored -> false,
                6,
                1,
                (ignored, destination) -> { throw new IOException("simulated disk failure"); }
        );

        List<EnvironmentGuardianService.BackupResult> results = service.runOnce();

        assertEquals(EnvironmentGuardianService.Status.FAILED, results.getFirst().status());
        assertTrue(Files.exists(existing));
        long archiveCount;
        try (var stream = Files.list(directory)) {
            archiveCount = stream.count();
        }
        assertEquals(1, archiveCount);
    }

    @Test
    void skipsInstancesThatAreBeingInstalledOrRepaired() throws Exception {
        Instance installing = createInstance("installing");
        Instance repairing = createInstance("repairing");
        Files.writeString(installing.getDirectory().resolve(".installing"), "active");
        assertTrue(InstanceRepairService.beginRepair(repairing));
        try {
            EnvironmentGuardianService service = service(
                    temporaryDirectory.resolve("busy-backups"),
                    List.of(installing, repairing),
                    ignored -> false,
                    24,
                    3,
                    InstanceBackupService::createBackup
            );

            List<EnvironmentGuardianService.BackupResult> results = service.runOnce();

            assertEquals(2, results.size());
            assertTrue(results.stream().allMatch(result ->
                    result.status() == EnvironmentGuardianService.Status.SKIPPED_BUSY));
            assertFalse(Files.exists(temporaryDirectory.resolve("busy-backups")));
        } finally {
            InstanceRepairService.endRepair(repairing);
        }
    }

    @Test
    void doesNothingWhenAutomaticBackupsAreDisabled() throws Exception {
        Instance instance = createInstance("disabled");
        Path backupRoot = temporaryDirectory.resolve("disabled-backups");
        EnvironmentGuardianService service = new EnvironmentGuardianService(
                backupRoot,
                () -> List.of(instance),
                ignored -> false,
                () -> false,
                () -> 24,
                () -> 3,
                InstanceBackupService::createBackup
        );

        assertTrue(service.runOnce().isEmpty());
        assertFalse(Files.exists(backupRoot));
    }

    @Test
    void rejectsUnsafeInstanceIdsWithoutEscapingTheBackupRoot() throws Exception {
        Instance instance = createInstance("../outside");
        Path backupRoot = temporaryDirectory.resolve("automatic-backups");
        EnvironmentGuardianService service = service(
                backupRoot, List.of(instance), ignored -> false, 24, 3,
                InstanceBackupService::createBackup
        );

        List<EnvironmentGuardianService.BackupResult> results = service.runOnce();

        assertEquals(EnvironmentGuardianService.Status.FAILED, results.getFirst().status());
        assertFalse(Files.exists(temporaryDirectory.resolve("outside")));
    }

    private Instance createInstance(String id) throws IOException {
        String folder = id.replaceAll("[^A-Za-z0-9._-]", "_");
        Path directory = Files.createDirectory(temporaryDirectory.resolve(folder));
        Instance instance = new Instance(id, "Test " + folder, "1.21.11",
                "Fabric", "0.16.0", directory);
        new ObjectMapper().writeValue(directory.resolve("instance.json").toFile(), instance);
        Files.writeString(directory.resolve("options.txt"), "guiScale:2");
        Files.createDirectories(directory.resolve("mods"));
        Files.writeString(directory.resolve("mods/example.jar"), "mod bytes");
        return instance;
    }

    private static EnvironmentGuardianService service(
            Path backupRoot,
            List<Instance> instances,
            java.util.function.Predicate<Instance> running,
            int intervalHours,
            int retention,
            EnvironmentGuardianService.BackupCreator creator
    ) {
        return new EnvironmentGuardianService(
                backupRoot,
                () -> instances,
                running,
                () -> true,
                () -> intervalHours,
                () -> retention,
                creator
        );
    }
}
