package dev.itsdropper.vanta.launcher.repair;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairBackupServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void restoresProtectedMetadataWithoutTouchingUserContent() throws Exception {
        Path instance = temporaryDirectory.resolve("instance");
        Files.createDirectories(instance.resolve("minecraft"));
        Files.createDirectories(instance.resolve("mods"));
        Files.writeString(instance.resolve("instance.json"), "before-instance");
        Files.writeString(instance.resolve("minecraft/version.json"), "before-version");
        Files.writeString(instance.resolve("mods/my-mod.jar"), "user-mod");

        RepairBackupService.createSnapshot(instance);
        Files.writeString(instance.resolve("instance.json"), "after-instance");
        Files.writeString(instance.resolve("minecraft/version.json"), "after-version");

        assertTrue(RepairBackupService.restoreLatest(instance));
        assertEquals("before-instance", Files.readString(instance.resolve("instance.json")));
        assertEquals("before-version", Files.readString(instance.resolve("minecraft/version.json")));
        assertEquals("user-mod", Files.readString(instance.resolve("mods/my-mod.jar")));
    }

    @Test
    void restoreReturnsFalseWhenNoSnapshotExists() throws Exception {
        Path instance = temporaryDirectory.resolve("empty-instance");
        Files.createDirectories(instance);
        assertEquals(false, RepairBackupService.restoreLatest(instance));
    }
}
