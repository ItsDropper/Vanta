package dev.itsdropper.vanta.launcher.service;

import dev.itsdropper.vanta.launcher.instance.InstalledModManager;
import dev.itsdropper.vanta.launcher.instance.InstalledModRecord;
import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ModrinthGraphInstallTransactionTest {
    @TempDir
    Path temporaryDirectory;

    private Instance instance() {
        return new Instance("test", "Test", "1.21.1", "fabric", "0.16.0", temporaryDirectory);
    }

    @Test
    void failureRestoresEveryTrackedArtifactAndRegistryWithoutTouchingUntrackedFiles() throws Exception {
        Instance instance = instance();
        Path mods = temporaryDirectory.resolve("mods");
        Files.createDirectories(mods);
        Path originalMod = mods.resolve("existing.jar");
        Path untrackedMod = mods.resolve("manual-user-mod.jar");
        Files.writeString(originalMod, "original bytes");
        Files.writeString(untrackedMod, "user-owned bytes");

        List<InstalledModRecord> originalRegistry = List.of(
                new InstalledModRecord("existing-project", "old-version", "existing", "1.0", "existing.jar"));
        InstalledModManager.save(instance, originalRegistry);
        String originalRegistryBytes = Files.readString(temporaryDirectory.resolve("vanta-mods.json"));

        IOException failure = assertThrows(IOException.class, () ->
                ModrinthGraphInstallTransaction.execute(instance, () -> {
                    ModrinthGraphInstallTransaction.registerManagedPath(instance, originalMod);
                    Files.writeString(originalMod, "partially replaced bytes");
                    Path newlyInstalled = mods.resolve("new-project.jar");
                    ModrinthGraphInstallTransaction.registerManagedPath(instance, newlyInstalled);
                    Files.writeString(newlyInstalled, "new mod bytes");
                    InstalledModManager.add(instance, new InstalledModRecord(
                            "new-project", "new-version", "new-project", "1.0", "new-project.jar"));
                    throw new IOException("simulated later dependency failure");
                }));

        assertEquals("simulated later dependency failure", failure.getMessage());
        assertEquals("original bytes", Files.readString(originalMod));
        assertFalse(Files.exists(mods.resolve("new-project.jar")));
        assertEquals("user-owned bytes", Files.readString(untrackedMod));
        List<InstalledModRecord> restored = InstalledModManager.load(instance);
        assertEquals(1, restored.size());
        assertEquals("existing-project", restored.get(0).getProjectId());
        assertEquals("old-version", restored.get(0).getVersionId());
        assertEquals(originalRegistryBytes,
                Files.readString(temporaryDirectory.resolve("vanta-mods.json")),
                "Rollback must restore the registry byte-for-byte.");
        assertNoTransactionDirectories();
    }

    @Test
    void successfulOperationKeepsAllChangesAndCleansItsSnapshot() throws Exception {
        Instance instance = instance();
        Path mods = temporaryDirectory.resolve("mods");
        Files.createDirectories(mods);
        InstalledModManager.save(instance, List.of());

        Path installed = ModrinthGraphInstallTransaction.execute(instance, () -> {
            Path file = mods.resolve("installed.jar");
            ModrinthGraphInstallTransaction.registerManagedPath(instance, file);
            Files.writeString(file, "verified mod bytes");
            InstalledModManager.add(instance, new InstalledModRecord(
                    "project", "version", "mod", "1.0", "installed.jar"));
            return file;
        });

        assertEquals("verified mod bytes", Files.readString(installed));
        assertEquals("project", InstalledModManager.load(instance).get(0).getProjectId());
        assertNoTransactionDirectories();
    }

    @Test
    void rollbackRestoresAbsenceOfRegistryWhenInstanceWasPreviouslyUntracked() throws Exception {
        Instance instance = instance();
        Path mods = temporaryDirectory.resolve("mods");
        Files.createDirectories(mods);
        Path registry = temporaryDirectory.resolve("vanta-mods.json");
        assertFalse(Files.exists(registry));

        assertThrows(IOException.class, () ->
                ModrinthGraphInstallTransaction.execute(instance, () -> {
                    ModrinthGraphInstallTransaction.registerManagedPath(instance, mods.resolve("new.jar"));
                    Files.writeString(mods.resolve("new.jar"), "new artifact");
                    InstalledModManager.add(instance, new InstalledModRecord(
                            "new", "new-version", "new", "1.0", "new.jar"));
                    throw new IOException("simulated install failure");
                }));

        assertFalse(Files.exists(registry), "Rollback must not create a registry that did not exist before.");
        assertFalse(Files.exists(mods.resolve("new.jar")));
        assertNoTransactionDirectories();
    }

    @Test
    void interruptionRollsBackEarlierGraphChangesAndPropagatesInterruption() throws Exception {
        Instance instance = instance();
        Path mods = temporaryDirectory.resolve("mods");
        Files.createDirectories(mods);
        Path originalMod = mods.resolve("existing.jar");
        Files.writeString(originalMod, "before interruption");
        InstalledModManager.save(instance, List.of(
                new InstalledModRecord("existing", "old", "existing", "1.0", "existing.jar")));

        InterruptedException failure = assertThrows(InterruptedException.class, () ->
                ModrinthGraphInstallTransaction.execute(instance, () -> {
                    ModrinthGraphInstallTransaction.registerManagedPath(instance, originalMod);
                    Files.writeString(originalMod, "partially updated");
                    ModrinthGraphInstallTransaction.registerManagedPath(instance, mods.resolve("new.jar"));
                    Files.writeString(mods.resolve("new.jar"), "new artifact");
                    InstalledModManager.add(instance, new InstalledModRecord(
                            "new", "new-version", "new", "1.0", "new.jar"));
                    throw new InterruptedException("cancelled by caller");
                }));

        assertEquals("cancelled by caller", failure.getMessage());
        assertEquals("before interruption", Files.readString(originalMod));
        assertFalse(Files.exists(mods.resolve("new.jar")));
        assertEquals("existing", InstalledModManager.load(instance).get(0).getProjectId());
        assertNoTransactionDirectories();
    }

    @Test
    void rejectsRegistryPathTraversalBeforeRunningOperation() throws Exception {
        Instance instance = instance();
        InstalledModManager.save(instance, List.of(
                new InstalledModRecord("unsafe", "version", "unsafe", "1.0", "../outside.jar")));

        Path outside = temporaryDirectory.resolve("outside.jar");
        assertFalse(Files.exists(outside));
        assertThrows(IOException.class, () ->
                ModrinthGraphInstallTransaction.execute(instance, () -> {
                    fail("The operation must not run when the registry contains an unsafe path.");
                    return null;
                }));
        assertFalse(Files.exists(outside));
    }


    @Test
    void nextInstallRecoversAnInterruptedPublishBeforeRegistryCommit() throws Exception {
        Instance instance = instance();
        Path mods = temporaryDirectory.resolve("mods");
        Files.createDirectories(mods);
        Path original = mods.resolve("existing.jar");
        Path newArtifact = mods.resolve("new-project.jar");
        Path staged = mods.resolve(".vanta-stage-crashed.jar");
        Files.writeString(original, "partially replaced bytes");
        Files.writeString(newArtifact, "published before crash");
        Files.writeString(staged, "incomplete download");

        List<InstalledModRecord> originalRecords = List.of(
                new InstalledModRecord("existing", "old-version", "existing", "1.0", "existing.jar"));
        InstalledModManager.save(instance, originalRecords);
        String originalRegistry = Files.readString(temporaryDirectory.resolve("vanta-mods.json"));

        Path journalDirectory = Files.createDirectory(
                temporaryDirectory.resolve(".vanta-graph-transaction-simulated-crash"));
        Files.writeString(journalDirectory.resolve("OWNER"),
                "VANTA_MODRINTH_GRAPH_TRANSACTION_V1");
        Files.writeString(journalDirectory.resolve("tracked-0"), "original bytes");
        Files.writeString(journalDirectory.resolve("registry-backup.json"), originalRegistry);
        Properties journal = new Properties();
        journal.setProperty("registryExisted", "true");
        journal.setProperty("registryBackup", "registry-backup.json");
        journal.setProperty("backupCount", "1");
        journal.setProperty("backup.0.filename", "existing.jar");
        journal.setProperty("backup.0.file", "tracked-0");
        journal.setProperty("managedCount", "2");
        journal.setProperty("managed.0", "new-project.jar");
        journal.setProperty("managed.1", ".vanta-stage-crashed.jar");
        try (var output = Files.newOutputStream(journalDirectory.resolve("journal.properties"))) {
            journal.store(output, "simulated process interruption");
        }

        // Simulate interruption during a registry write. The durable intent
        // journal is sufficient to restore without parsing this damaged file.
        Files.writeString(temporaryDirectory.resolve("vanta-mods.json"), "{ incomplete registry");

        ModrinthGraphInstallTransaction.execute(instance, () -> List.of());

        assertEquals("original bytes", Files.readString(original));
        assertFalse(Files.exists(newArtifact));
        assertFalse(Files.exists(staged));
        assertEquals(originalRegistry, Files.readString(temporaryDirectory.resolve("vanta-mods.json")));
        assertEquals(1, InstalledModManager.load(instance).size());
        assertNoTransactionDirectories();
    }

    @Test
    void recoveryRefusesAnIncompletePreparedJournalInsteadOfDeletingUserData() throws Exception {
        Instance instance = instance();
        Path journalDirectory = Files.createDirectory(
                temporaryDirectory.resolve(".vanta-graph-transaction-corrupt"));
        Files.writeString(journalDirectory.resolve("OWNER"), "VANTA_MODRINTH_GRAPH_TRANSACTION_V1");
        Files.writeString(journalDirectory.resolve("journal.properties"), "registryExisted=true\\n");
        Path important = temporaryDirectory.resolve("mods");
        Files.createDirectories(important);
        Files.writeString(important.resolve("keep.jar"), "user data");

        assertThrows(IOException.class, () ->
                ModrinthGraphInstallTransaction.execute(instance, () -> List.of()));

        assertEquals("user data", Files.readString(important.resolve("keep.jar")));
        assertTrue(Files.exists(journalDirectory), "An unreadable prepared journal must remain for manual recovery.");
    }


    @Test
    void recoveryRetainsJournalWhenAnOriginalBackupIsMissing() throws Exception {
        Instance instance = instance();
        Path mods = temporaryDirectory.resolve("mods");
        Files.createDirectories(mods);
        Path original = mods.resolve("existing.jar");
        Files.writeString(original, "partially replaced bytes");
        InstalledModManager.save(instance, List.of(
                new InstalledModRecord("existing", "old", "existing", "1.0", "existing.jar")));
        String registry = Files.readString(temporaryDirectory.resolve("vanta-mods.json"));

        Path journalDirectory = Files.createDirectory(
                temporaryDirectory.resolve(".vanta-graph-transaction-missing-backup"));
        Files.writeString(journalDirectory.resolve("OWNER"), "VANTA_MODRINTH_GRAPH_TRANSACTION_V1");
        Files.writeString(journalDirectory.resolve("registry-backup.json"), registry);
        Properties journal = new Properties();
        journal.setProperty("registryExisted", "true");
        journal.setProperty("registryBackup", "registry-backup.json");
        journal.setProperty("backupCount", "1");
        journal.setProperty("backup.0.filename", "existing.jar");
        journal.setProperty("backup.0.file", "tracked-0");
        journal.setProperty("managedCount", "1");
        journal.setProperty("managed.0", "existing.jar");
        try (var output = Files.newOutputStream(journalDirectory.resolve("journal.properties"))) {
            journal.store(output, "simulated incomplete backup");
        }

        assertThrows(IOException.class, () ->
                ModrinthGraphInstallTransaction.execute(instance, () -> List.of()));

        assertTrue(Files.exists(journalDirectory),
                "Recovery must preserve the journal when it cannot restore an original artifact.");
        assertEquals("partially replaced bytes", Files.readString(original));
    }


    @Test
    void serializesConcurrentTransactionsForTheSameInstance() throws Exception {
        Instance instance = instance();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch secondAttempting = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();

        try {
            Future<?> first = workers.submit(() ->
                    ModrinthGraphInstallTransaction.execute(instance, () -> {
                        int now = active.incrementAndGet();
                        maximumActive.accumulateAndGet(now, Math::max);
                        firstEntered.countDown();
                        try {
                            if (!releaseFirst.await(5, TimeUnit.SECONDS)) {
                                throw new IOException("Timed out waiting to release first transaction.");
                            }
                            return null;
                        } finally {
                            active.decrementAndGet();
                        }
                    }));

            assertTrue(firstEntered.await(5, TimeUnit.SECONDS), "First transaction did not start.");
            Future<?> second = workers.submit(() -> {
                secondAttempting.countDown();
                return ModrinthGraphInstallTransaction.execute(instance, () -> {
                    int now = active.incrementAndGet();
                    maximumActive.accumulateAndGet(now, Math::max);
                    try {
                        return null;
                    } finally {
                        active.decrementAndGet();
                    }
                });
            });

            assertTrue(secondAttempting.await(5, TimeUnit.SECONDS), "Second transaction did not start.");
            assertEquals(1, active.get(), "Second transaction must wait while the first owns the instance lock.");
            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertEquals(1, maximumActive.get(), "Transactions for one instance must never overlap.");
            assertNoTransactionDirectories();
        } finally {
            releaseFirst.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }


    @Test
    void nestedInstallOperationsJoinTheOuterTransactionAndRollbackTogether() throws Exception {
        Instance instance = instance();
        Path mods = temporaryDirectory.resolve("mods");
        Files.createDirectories(mods);
        Path nestedArtifact = mods.resolve("nested.jar");

        assertThrows(IOException.class, () ->
                ModrinthGraphInstallTransaction.execute(instance, () -> {
                    ModrinthGraphInstallTransaction.execute(instance, () -> {
                        ModrinthGraphInstallTransaction.registerManagedPath(instance, nestedArtifact);
                        Files.writeString(nestedArtifact, "nested install");
                        return null;
                    });
                    throw new IOException("outer repair failed after nested install");
                }));

        assertFalse(Files.exists(nestedArtifact),
                "A nested install must be rolled back with its outer repair transaction.");
        assertNoTransactionDirectories();
    }

    private void assertNoTransactionDirectories() throws IOException {
        try (var entries = Files.list(temporaryDirectory)) {
            assertTrue(entries.noneMatch(path ->
                    path.getFileName().toString().startsWith(".vanta-graph-transaction-")));
        }
    }
}
