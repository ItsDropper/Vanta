package dev.itsdropper.vanta.launcher.bundle;

import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VantaBundleManifestSnapshotStoreTest {
    @TempDir
    Path temporaryDirectory;

    private final VantaBundleManifestSnapshotStore store = new VantaBundleManifestSnapshotStore();

    @Test
    void savesAndReloadsTheValidatedManifestSnapshot() throws Exception {
        Instance instance = instance();
        VantaBundleManifest manifest = manifest();

        store.save(instance, manifest);

        assertEquals(manifest, store.load(instance));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve(
                VantaBundleManifestSnapshotStore.FILE_NAME)));
        try (var files = Files.list(temporaryDirectory)) {
            assertEquals(1, files.count(), "Only the committed snapshot should remain.");
        }
    }

    @Test
    void rejectsInvalidManifestBeforeWritingSnapshot() {
        VantaBundleManifest invalid = new VantaBundleManifest(1, "bad id", "1.0.0",
                List.of("1.21.11"), "fabric",
                List.of(new VantaBundleManifest.Bundle("core", "Core", "Core mods",
                        true, true, List.of())));

        assertThrows(IOException.class, () -> store.save(instance(), invalid));
        assertFalse(Files.exists(temporaryDirectory.resolve(
                VantaBundleManifestSnapshotStore.FILE_NAME)));
    }

    @Test
    void rejectsMalformedSnapshotWhenLoading() throws Exception {
        Files.writeString(temporaryDirectory.resolve(VantaBundleManifestSnapshotStore.FILE_NAME),
                "{not-json");

        assertThrows(IOException.class, () -> store.load(instance()));
    }

    @Test
    void missingSnapshotReturnsNull() throws Exception {
        assertNull(store.load(instance()));
    }

    private VantaBundleManifest manifest() {
        return new VantaBundleManifest(1, "vanta-client", "1.2.0",
                List.of("1.21.11"), "fabric",
                List.of(new VantaBundleManifest.Bundle("core", "Core", "Core mods",
                        true, true, List.of(new VantaBundleManifest.Mod(
                                "fabric-api", null, true, true)))));
    }

    private Instance instance() {
        return new Instance("test", "Test", "1.21.11", "fabric", "0.16.0", temporaryDirectory);
    }
}
