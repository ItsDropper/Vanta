package dev.itsdropper.vanta.launcher.bundle;

import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VantaBundleStateStoreTest {
    @TempDir
    Path temporaryDirectory;

    private final VantaBundleStateStore store = new VantaBundleStateStore();

    @Test
    void persistsSelectionsAndManagedOwnershipPerInstance() throws Exception {
        Instance instance = instance();
        VantaBundleState state = new VantaBundleState(1, "vanta-client", "1.2.0",
                List.of("core", "performance"), List.of("optional-project"),
                List.of("vanta-core-project", "sodium-project"), "1.3.0");

        store.save(instance, state);

        assertEquals(state, store.load(instance));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve(VantaBundleStateStore.FILE_NAME)));
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(1, files.count(), "Only the committed state file should remain.");
        }
    }

    @Test
    void reconciliationRemovesMissingManagedProjectsWithoutClaimingManualMods() throws Exception {
        Instance instance = instance();
        VantaBundleState initial = new VantaBundleState(1, "vanta-client", "1.2.0",
                List.of("core"), List.of(), List.of("managed-present", "managed-removed"), null);
        store.save(instance, initial);

        VantaBundleState reconciled = store.reconcileManagedProjects(
                instance, java.util.Set.of("managed-present", "manually-installed"));

        assertNotNull(reconciled);
        assertEquals(List.of("managed-present"), reconciled.installedManagedProjectIds());
        assertFalse(reconciled.installedManagedProjectIds().contains("manually-installed"));
        assertEquals(reconciled, store.load(instance));
    }

    @Test
    void reconciliationLeavesMissingProfileStateAbsent() throws Exception {
        assertNull(store.reconcileManagedProjects(instance(), java.util.Set.of("manual-project")));
    }

    @Test
    void missingStateMeansInstanceHasNoVantaProfile() throws Exception {
        assertNull(store.load(instance()));
    }

    @Test
    void rejectsUnsupportedStateSchema() throws Exception {
        Path state = temporaryDirectory.resolve(VantaBundleStateStore.FILE_NAME);
        Files.writeString(state, """
                {"schemaVersion": 99, "profileId": "vanta-client", "profileVersion": "1.0.0"}
                """);

        assertThrows(IOException.class, () -> store.load(instance()));
    }

    @Test
    void rejectsDuplicateManagedProjectIds() {
        VantaBundleState invalid = new VantaBundleState(1, "vanta-client", "1.0.0",
                List.of("core"), List.of(), List.of("same-project", "same-project"), null);

        assertThrows(IOException.class, () -> store.save(instance(), invalid));
    }

    @Test
    void rejectsUnknownStateFields() throws Exception {
        Path state = temporaryDirectory.resolve(VantaBundleStateStore.FILE_NAME);
        Files.writeString(state, """
                {"schemaVersion": 1, "profileId": "vanta-client", "profileVersion": "1.0.0",
                 "selectedBundleIds": [], "selectedModProjectIds": [],
                 "installedManagedProjectIds": [], "unexpected": true}
                """);

        assertThrows(IOException.class, () -> store.load(instance()));
    }

    private Instance instance() {
        return new Instance("test", "Test", "1.21.11", "fabric", "0.16.0", temporaryDirectory);
    }
}
