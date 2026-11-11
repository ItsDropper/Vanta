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

        IOException failure = assertThrows(IOException.class, () ->
                ModrinthGraphInstallTransaction.execute(instance, () -> {
                    Files.writeString(originalMod, "partially replaced bytes");
                    Path newlyInstalled = mods.resolve("new-project.jar");
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
                    Files.writeString(originalMod, "partially updated");
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

    private void assertNoTransactionDirectories() throws IOException {
        try (var entries = Files.list(temporaryDirectory)) {
            assertTrue(entries.noneMatch(path ->
                    path.getFileName().toString().startsWith(".vanta-graph-transaction-")));
        }
    }
}
