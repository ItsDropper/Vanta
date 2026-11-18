package dev.itsdropper.vanta.launcher.update;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class UpdateTransactionTest {
    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void removeUpdaterWorkspace() throws Exception {
        Path workspace = Path.of(System.getProperty("java.io.tmpdir"), "Vanta-updater")
                .resolve("session-" + temporaryDirectory.getFileName());
        if (Files.exists(workspace)) {
            try (var paths = Files.walk(workspace)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @Test
    void interruptionAfterBothPayloadMovesRestoresPreviousPayload() throws Exception {
        Fixture f = new Fixture();
        UpdateTransaction transaction = f.begin();
        Files.move(f.activePayload, f.backupPayload);
        Files.move(f.stagedPayload, f.activePayload);
        transaction.markSwapped();

        UpdateTransaction.recoverBeforeLaunch(f.application);

        assertEquals("old", Files.readString(f.activePayload.resolve("version.txt")));
        assertTrue(Files.exists(f.application.resolve("bin/Vanta")));
        assertTrue(Files.exists(f.application.resolve("lib/runtime/lib/modules")));
        assertFalse(Files.exists(f.backupPayload));
        assertFalse(Files.exists(f.staging));
        assertFalse(Files.exists(f.journal()));
    }

    @Test
    void interruptionBetweenPayloadMovesRestoresFromBackupUsingStableBootstrap() throws Exception {
        Fixture f = new Fixture();
        f.begin();
        Files.move(f.activePayload, f.backupPayload);

        assertTrue(Files.exists(f.application.resolve("bin/Vanta")));
        assertTrue(Files.exists(f.application.resolve("lib/runtime/lib/modules")));
        assertFalse(Files.exists(f.activePayload));

        UpdateTransaction.recoverBeforeLaunch(f.application);

        assertEquals("old", Files.readString(f.activePayload.resolve("version.txt")));
        assertFalse(Files.exists(f.backupPayload));
        assertFalse(Files.exists(f.journal()));
    }

    @Test
    void healthyMarkerCommitsNewPayloadAfterUpdaterCrash() throws Exception {
        Fixture f = new Fixture();
        f.begin();
        Files.move(f.activePayload, f.backupPayload);
        Files.move(f.stagedPayload, f.activePayload);
        Files.writeString(f.healthMarker, "healthy");

        UpdateTransaction.recoverBeforeLaunch(f.application);

        assertEquals("new", Files.readString(f.activePayload.resolve("version.txt")));
        assertFalse(Files.exists(f.backupPayload));
        assertFalse(Files.exists(f.staging));
        assertFalse(Files.exists(f.journal()));
    }

    @Test
    void interruptionBeforeSwapKeepsCurrentPayloadAndDiscardsStage() throws Exception {
        Fixture f = new Fixture();
        f.begin();

        UpdateTransaction.recoverBeforeLaunch(f.application);

        assertEquals("old", Files.readString(f.activePayload.resolve("version.txt")));
        assertFalse(Files.exists(f.staging));
        assertFalse(Files.exists(f.backupPayload));
        assertFalse(Files.exists(f.journal()));
    }

    private final class Fixture {
        private final Path application = temporaryDirectory.resolve("Vanta");
        private final Path staging = temporaryDirectory.resolve("Vanta.update");
        private final Path activePayload = application.resolve("lib/app");
        private final Path stagedPayload = staging.resolve("lib/app");
        private final Path backupPayload = application.resolve("lib/app.backup");
        private final Path workspace = Path.of(System.getProperty("java.io.tmpdir"), "Vanta-updater")
                .resolve("session-" + temporaryDirectory.getFileName());
        private final Path healthMarker = workspace.resolve("vanta-update-health");

        private Fixture() throws Exception {
            createImage(application, "old");
            Files.createDirectories(workspace);
        }

        private UpdateTransaction begin() throws Exception {
            UpdateTransaction transaction = UpdateTransaction.begin(
                    application, staging, activePayload, stagedPayload, backupPayload, healthMarker);
            createImage(staging, "new");
            transaction.markStaged();
            return transaction;
        }

        private Path journal() {
            return temporaryDirectory.resolve("Vanta.update-transaction");
        }
    }

    private static void createImage(Path root, String version) throws Exception {
        Files.createDirectories(root.resolve("bin"));
        Files.createDirectories(root.resolve("lib/runtime/lib"));
        Files.createDirectories(root.resolve("lib/app"));
        Path launcher = root.resolve("bin/Vanta");
        Files.writeString(launcher, "native-launcher");
        launcher.toFile().setExecutable(true);
        Files.writeString(root.resolve("lib/runtime/lib/modules"), "runtime-modules");
        Files.writeString(root.resolve("lib/app/Vanta.cfg"), "main-class=Vanta");
        Files.writeString(root.resolve("lib/app/version.txt"), version);
    }
}
