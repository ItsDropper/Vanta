package dev.itsdropper.vanta.launcher.instance;

import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InstalledModManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void savesAndReloadsRegistryWithoutLeavingTemporaryFiles() throws Exception {
        Instance instance = new Instance("test", "Test", "1.21.1",
                "fabric", "0.16.0", temporaryDirectory);
        List<InstalledModRecord> records = List.of(
                new InstalledModRecord("project-a", "version-a", "mod-a", "1.0.0", "mod-a.jar"),
                new InstalledModRecord("project-b", "version-b", "mod-b", "2.0.0", "mod-b.jar")
        );

        InstalledModManager.save(instance, records);

        List<InstalledModRecord> loaded = InstalledModManager.load(instance);
        assertEquals(2, loaded.size());
        assertEquals("project-a", loaded.get(0).getProjectId());
        assertEquals("project-b", loaded.get(1).getProjectId());

        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(1, files.count(), "Only the committed registry file should remain.");
        }
    }

    @Test
    void renameByFilenameKeepsEnabledStateInRegistry() throws Exception {
        Instance instance = new Instance("test", "Test", "1.21.1",
                "fabric", "0.16.0", temporaryDirectory);
        InstalledModManager.save(instance, List.of(
                new InstalledModRecord("project-a", "version-a", "mod-a", "1.0.0", "mod-a.jar")
        ));

        assertEquals(1, InstalledModManager.renameByFilename(
                instance, "mod-a.jar", "mod-a.jar.disabled"));
        assertEquals("mod-a.jar.disabled",
                InstalledModManager.load(instance).get(0).getFilename());
        assertEquals(0, InstalledModManager.renameByFilename(
                instance, "unknown.jar", "unknown.jar.disabled"));
    }

    @Test
    void removeByFilenameReturnsRecordsForRollbackAndPersistsRemoval() throws Exception {
        Instance instance = new Instance("test", "Test", "1.21.1",
                "fabric", "0.16.0", temporaryDirectory);
        InstalledModRecord record = new InstalledModRecord(
                "project-a", "version-a", "mod-a", "1.0.0", "mod-a.jar");
        InstalledModManager.save(instance, List.of(record));

        List<InstalledModRecord> removed =
                InstalledModManager.removeByFilename(instance, "mod-a.jar");

        assertEquals(1, removed.size());
        assertEquals("project-a", removed.get(0).getProjectId());
        assertTrue(InstalledModManager.load(instance).isEmpty());

        InstalledModManager.add(instance, removed.get(0));
        assertEquals("mod-a.jar", InstalledModManager.load(instance).get(0).getFilename());
    }

    @Test
    void failedRegistrySerializationDoesNotReplaceExistingRegistry() throws Exception {
        Instance instance = new Instance("test", "Test", "1.21.1",
                "fabric", "0.16.0", temporaryDirectory);
        InstalledModManager.save(instance, List.of(
                new InstalledModRecord("project-a", "version-a", "mod-a", "1.0.0", "mod-a.jar")
        ));

        Path registry = temporaryDirectory.resolve("vanta-mods.json");
        String original = Files.readString(registry);

        assertThrows(Exception.class, () -> InstalledModManager.save(instance, null));
        assertEquals(original, Files.readString(registry));
    }
}
