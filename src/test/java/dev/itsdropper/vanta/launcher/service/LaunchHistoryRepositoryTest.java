package dev.itsdropper.vanta.launcher.service;

import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.repair.FailureCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class LaunchHistoryRepositoryTest {
    @TempDir Path temporaryDirectory;

    private Instance instance(String id, String name) {
        return new Instance(id, name, "1.21.11", "Fabric", "0.19.5",
                temporaryDirectory.resolve(id));
    }

    @Test
    void persistsLifecycleAndDiagnosticEvidenceAcrossRepositoryInstances() throws Exception {
        Path store = temporaryDirectory.resolve("history.json");
        LaunchHistoryRepository repository = new LaunchHistoryRepository(store);
        String id = repository.begin(instance("fabric-main", "Main Fabric"));
        repository.transition(id, LaunchHistoryRepository.Outcome.STARTING, "Creating process");
        repository.transition(id, LaunchHistoryRepository.Outcome.RUNNING, "Process started");
        repository.finish(id, LaunchHistoryRepository.Outcome.FAILED, "Minecraft crashed",
                "java.lang.IllegalStateException: failed to start mod Example\n"
                        + "access_token=secret-value\n"
                        + "Connecting to private.example.net:25565\n"
                        + "at C:\\Users\\Alice\\AppData\\Vanta\\latest.log",
                1);

        LaunchHistoryRepository.LaunchRecord record =
                new LaunchHistoryRepository(store).find(id);
        assertNotNull(record);
        assertEquals(LaunchHistoryRepository.Outcome.FAILED, record.outcome());
        assertEquals(1, record.exitCode());
        assertEquals(4, record.events().size());
        assertEquals(FailureCategory.MOD_CRASH, record.failureCategory());
        assertFalse(record.sanitizedLog().contains("secret-value"));
        assertFalse(record.sanitizedLog().contains("private.example.net"));
        assertFalse(record.sanitizedLog().contains("Alice"));
        assertEquals(64, record.fingerprint().length());
    }

    @Test
    void recordsSuccessfulAndUserStoppedRunsAsDifferentOutcomes() throws Exception {
        LaunchHistoryRepository repository = new LaunchHistoryRepository(
                temporaryDirectory.resolve("history.json"));
        String successful = repository.begin(instance("one", "One"));
        repository.transition(successful, LaunchHistoryRepository.Outcome.RUNNING, "Started");
        repository.finish(successful, LaunchHistoryRepository.Outcome.SUCCEEDED,
                "Exited normally", "Minecraft shutdown complete", 0);

        String stopped = repository.begin(instance("two", "Two"));
        repository.transition(stopped, LaunchHistoryRepository.Outcome.RUNNING, "Started");
        repository.transition(stopped, LaunchHistoryRepository.Outcome.STOPPING, "User requested shutdown");
        repository.finish(stopped, LaunchHistoryRepository.Outcome.STOPPED,
                "Closed by user", "Minecraft shutdown complete", 0);

        List<LaunchHistoryRepository.LaunchRecord> records = repository.list();
        var stoppedRecord = records.stream().filter(record -> record.instanceId().equals("two"))
                .findFirst().orElseThrow();
        var successfulRecord = records.stream().filter(record -> record.instanceId().equals("one"))
                .findFirst().orElseThrow();
        assertEquals(LaunchHistoryRepository.Outcome.STOPPED, stoppedRecord.outcome());
        assertEquals(LaunchHistoryRepository.Outcome.SUCCEEDED, successfulRecord.outcome());
        assertTrue(stoppedRecord.terminal());
        assertTrue(successfulRecord.terminal());
    }

    @Test
    void rejectsIllegalTransitionsAndMutationAfterCompletion() throws Exception {
        LaunchHistoryRepository repository = new LaunchHistoryRepository(
                temporaryDirectory.resolve("history.json"));
        String id = repository.begin(instance("one", "One"));
        assertThrows(IllegalArgumentException.class, () ->
                repository.transition(id, LaunchHistoryRepository.Outcome.FAILED, "No"));
        assertThrows(IllegalArgumentException.class, () ->
                repository.transition(id, LaunchHistoryRepository.Outcome.MOD_INSTALLED, "Not a launch state"));
        repository.finish(id, LaunchHistoryRepository.Outcome.SUCCEEDED, "Done", "", 0);
        assertThrows(IllegalStateException.class, () ->
                repository.transition(id, LaunchHistoryRepository.Outcome.RUNNING, "Late event"));
        assertThrows(IllegalStateException.class, () ->
                repository.finish(id, LaunchHistoryRepository.Outcome.FAILED, "Changed", "new", 1));
    }

    @Test
    void concurrentWritersDoNotLoseIndependentLaunches() throws Exception {
        Path store = temporaryDirectory.resolve("concurrent.json");
        var executor = Executors.newFixedThreadPool(6);
        try {
            List<java.util.concurrent.Future<?>> writes = new ArrayList<>();
            for (int i = 0; i < 36; i++) {
                int index = i;
                writes.add(executor.submit(() -> {
                    try {
                        LaunchHistoryRepository repository = new LaunchHistoryRepository(store);
                        String id = repository.begin(instance("instance-" + index, "Instance " + index));
                        repository.finish(id, LaunchHistoryRepository.Outcome.SUCCEEDED, "Exited", "", 0);
                    } catch (IOException failure) {
                        throw new java.util.concurrent.CompletionException(failure);
                    }
                }));
            }
            for (var write : writes) write.get();
            assertEquals(36, new LaunchHistoryRepository(store).list().size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rotatesOldCompletedRecordsAtTheConfiguredLimit() throws Exception {
        LaunchHistoryRepository repository = new LaunchHistoryRepository(
                temporaryDirectory.resolve("bounded.json"));
        for (int i = 0; i < 205; i++) {
            String id = repository.begin(instance("instance-" + i, "Instance " + i));
            repository.finish(id, LaunchHistoryRepository.Outcome.SUCCEEDED, "Exited", "", 0);
        }
        List<LaunchHistoryRepository.LaunchRecord> records = repository.list();
        assertEquals(200, records.size());
        assertFalse(records.stream().anyMatch(record -> record.instanceId().equals("instance-0")));
        assertTrue(records.stream().anyMatch(record -> record.instanceId().equals("instance-204")));
    }

    @Test
    void refusesCorruptStoresWithoutSilentlyOverwritingThem() throws Exception {
        Path store = temporaryDirectory.resolve("corrupt.json");
        Files.writeString(store, "{not-json");
        LaunchHistoryRepository repository = new LaunchHistoryRepository(store);
        assertThrows(IOException.class, repository::list);
        assertEquals("{not-json", Files.readString(store));
    }

    @Test
    void recordsInstanceOperationsAlongsideLaunchesInOneTimeline() throws Exception {
        LaunchHistoryRepository repository = new LaunchHistoryRepository(
                temporaryDirectory.resolve("activity.json"));
        Instance instance = instance("main", "Main");
        String launchId = repository.begin(instance);
        repository.finish(launchId, LaunchHistoryRepository.Outcome.SUCCEEDED,
                "Exited normally", "", 0);
        var activity = repository.recordActivity(instance,
                LaunchHistoryRepository.Outcome.BACKUP_CREATED,
                "Created a verified backup");
        var loaderChange = repository.recordActivity(instance,
                LaunchHistoryRepository.Outcome.LOADER_CHANGED,
                "Changed loader metadata");
        List<LaunchHistoryRepository.LaunchRecord> timeline = repository.list();
        assertEquals(3, timeline.size());
        var loaderEvent = timeline.stream().filter(record -> record.id().equals(loaderChange.id()))
                .findFirst().orElseThrow();
        var backupEvent = timeline.stream().filter(record -> record.id().equals(activity.id()))
                .findFirst().orElseThrow();
        assertEquals(LaunchHistoryRepository.Outcome.LOADER_CHANGED, loaderEvent.outcome());
        assertEquals(LaunchHistoryRepository.Outcome.BACKUP_CREATED, backupEvent.outcome());
        assertEquals("Created a verified backup", backupEvent.events().getFirst().message());
        assertTrue(backupEvent.terminal());
        assertThrows(IllegalStateException.class, () ->
                repository.transition(activity.id(), LaunchHistoryRepository.Outcome.RUNNING, "Invalid"));
    }

    @Test
    void preservesUuidShapedStableInstanceIds() throws Exception {
        String instanceId = "123e4567-e89b-12d3-a456-426614174000";
        LaunchHistoryRepository repository = new LaunchHistoryRepository(
                temporaryDirectory.resolve("uuid-id.json"));
        String launchId = repository.begin(instance(instanceId, "UUID instance"));
        assertEquals(instanceId, repository.find(launchId).instanceId());
    }

    @Test
    void truncatesDiagnosticEvidenceInsideTheConfiguredRecordLimit() throws Exception {
        LaunchHistoryRepository repository = new LaunchHistoryRepository(
                temporaryDirectory.resolve("bounded-evidence.json"));
        String id = repository.begin(instance("bounded", "Bounded"));
        String largeLog = "diagnostic-line\n".repeat(8_000);
        var record = repository.finish(id, LaunchHistoryRepository.Outcome.FAILED,
                "Process failed", largeLog, 1);
        assertTrue(record.sanitizedLog().length() <= 48 * 1024);
        assertTrue(record.sanitizedLog().endsWith("[TRUNCATED: launch-history evidence limit]"));
        assertEquals(record.id(), repository.find(id).id());
    }
}
