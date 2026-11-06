package dev.itsdropper.vanta.launcher.instance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class InstanceRepairServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void restoresOriginalPatchedClientAndVersionMetadataAfterFailedRepair() throws Exception {
        Path instance = temporaryDirectory.resolve("instance");
        Path client = instance.resolve("minecraft/client.jar");
        Path version = instance.resolve("minecraft/version.json");
        Path rollback = temporaryDirectory.resolve("rollback");
        Path backupClient = rollback.resolve("client.jar");
        Path backupVersion = rollback.resolve("version.json");

        Files.createDirectories(client.getParent());
        Files.createDirectories(rollback);
        Files.writeString(client, "previous-patched-client");
        Files.writeString(version, "previous-stein-metadata");

        InstanceRepairService.copyFileIfPresent(client, backupClient);
        InstanceRepairService.copyFileIfPresent(version, backupVersion);

        Files.writeString(client, "replacement-vanilla-client");
        Files.writeString(version, "replacement-vanilla-metadata");

        Exception failure = new Exception("simulated Stein repair failure");
        InstanceRepairService.restoreFile(backupClient, client, failure);
        InstanceRepairService.restoreFile(backupVersion, version, failure);

        assertEquals("previous-patched-client", Files.readString(client));
        assertEquals("previous-stein-metadata", Files.readString(version));
        assertTrue(failure.getSuppressed().length == 0);
    }

    @Test
    void restoresRuntimeAndNativeDirectoryTreesWithoutLeavingNewFiles() throws Exception {
        Path instance = temporaryDirectory.resolve("instance");
        Path runtime = instance.resolve("stein/runtime");
        Path natives = instance.resolve("natives");
        Path rollback = temporaryDirectory.resolve("rollback");
        Path backupRuntime = rollback.resolve("runtime");
        Path backupNatives = rollback.resolve("natives");

        Files.createDirectories(runtime.resolve("bin"));
        Files.createDirectories(natives);
        Files.writeString(runtime.resolve("bin/java"), "previous-runtime");
        Files.writeString(natives.resolve("lwjgl.dll"), "previous-native");

        InstanceRepairService.copyTreeIfPresent(runtime, backupRuntime);
        InstanceRepairService.copyTreeIfPresent(natives, backupNatives);

        Files.writeString(runtime.resolve("bin/java"), "replacement-runtime");
        Files.writeString(runtime.resolve("bin/new-file"), "new");
        Files.delete(natives.resolve("lwjgl.dll"));
        Files.writeString(natives.resolve("replacement.dll"), "new-native");

        Exception failure = new Exception("simulated Stein repair failure");
        InstanceRepairService.restoreTree(backupRuntime, runtime, failure);
        InstanceRepairService.restoreTree(backupNatives, natives, failure);

        assertEquals("previous-runtime", Files.readString(runtime.resolve("bin/java")));
        assertFalse(Files.exists(runtime.resolve("bin/new-file")));
        assertEquals("previous-native", Files.readString(natives.resolve("lwjgl.dll")));
        assertFalse(Files.exists(natives.resolve("replacement.dll")));
        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    void removesFilesAndDirectoriesThatDidNotExistBeforeRepair() throws Exception {
        Path newClient = temporaryDirectory.resolve("minecraft/client.jar");
        Path newRuntime = temporaryDirectory.resolve("stein/runtime");
        Files.createDirectories(newClient.getParent());
        Files.createDirectories(newRuntime);
        Files.writeString(newClient, "new-client");
        Files.writeString(newRuntime.resolve("java"), "new-runtime");

        Exception failure = new Exception("simulated failure");
        InstanceRepairService.restoreFile(temporaryDirectory.resolve("missing-client-backup"),
                newClient, failure);
        InstanceRepairService.restoreTree(temporaryDirectory.resolve("missing-runtime-backup"),
                newRuntime, failure);

        assertFalse(Files.exists(newClient));
        assertFalse(Files.exists(newRuntime));
        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    void identifiesMissingSteinRuntimeAsRepairable() {
        assertTrue(InstanceRepairService.isRepairableInstallationFailure(
                new IllegalStateException("Stein's Java 25 runtime is missing or invalid.")));
    }
}
