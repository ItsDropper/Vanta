package dev.itsdropper.vanta.launcher.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
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
    void interruptedSwapRestoresPreviousInstallation() throws Exception {
        Fixture fixture = new Fixture();
        UpdateTransaction transaction = fixture.begin();

        Files.move(fixture.application, fixture.backup);
        Files.move(fixture.staging, fixture.application);
        transaction.markSwapped();

        UpdateTransaction.recoverBeforeLaunch(fixture.application);

        assertEquals("old", Files.readString(fixture.application.resolve("version.txt")));
        assertFalse(Files.exists(fixture.backup));
        assertFalse(Files.exists(fixture.staging));
        assertFalse(Files.exists(fixture.journal()));
    }

    @Test
    void crashBetweenDirectoryMovesCanRestoreWhenApplicationPathIsMissing() throws Exception {
        Fixture fixture = new Fixture();
        fixture.begin();

        Files.move(fixture.application, fixture.backup);

        UpdateTransaction.recoverBeforeLaunch(fixture.application);

        assertEquals("old", Files.readString(fixture.application.resolve("version.txt")));
        assertFalse(Files.exists(fixture.backup));
        assertFalse(Files.exists(fixture.journal()));
    }

    @Test
    void healthyMarkerCommitsNewInstallationAfterUpdaterCrash() throws Exception {
        Fixture fixture = new Fixture();
        fixture.begin();

        Files.move(fixture.application, fixture.backup);
        Files.move(fixture.staging, fixture.application);
        Files.writeString(fixture.healthMarker, "healthy");

        UpdateTransaction.recoverBeforeLaunch(fixture.application);

        assertEquals("new", Files.readString(fixture.application.resolve("version.txt")));
        assertFalse(Files.exists(fixture.backup));
        assertFalse(Files.exists(fixture.journal()));
    }

    @Test
    void interruptionBeforeSwapKeepsCurrentInstallationAndDiscardsStage() throws Exception {
        Fixture fixture = new Fixture();
        fixture.begin();

        UpdateTransaction.recoverBeforeLaunch(fixture.application);

        assertEquals("old", Files.readString(fixture.application.resolve("version.txt")));
        assertFalse(Files.exists(fixture.staging));
        assertFalse(Files.exists(fixture.journal()));
    }

    private final class Fixture {
        private final Path application = temporaryDirectory.resolve("Vanta");
        private final Path staging = temporaryDirectory.resolve("Vanta.update");
        private final Path backup = temporaryDirectory.resolve("Vanta.backup");
        private final Path workspace = Path.of(System.getProperty("java.io.tmpdir"), "Vanta-updater")
                .resolve("session-" + temporaryDirectory.getFileName());
        private final Path healthMarker = workspace.resolve("vanta-update-health");

        private Fixture() throws Exception {
            createImage(application, "old");
            createImage(staging, "new");
            Files.createDirectories(workspace);
        }

        private UpdateTransaction begin() throws Exception {
            return UpdateTransaction.begin(application, staging, backup, healthMarker);
        }

        private Path journal() {
            return temporaryDirectory.resolve("Vanta.update-transaction");
        }
    }

    private static void createImage(Path root, String version) throws Exception {
        Files.createDirectories(root.resolve("bin"));
        Files.createDirectories(root.resolve("lib/runtime/lib"));
        Files.createDirectories(root.resolve("lib/app"));
        Files.writeString(root.resolve("bin/Vanta"), "launcher");
        root.resolve("bin/Vanta").toFile().setExecutable(true);
        Files.writeString(root.resolve("lib/runtime/lib/modules"), "modules");
        Files.writeString(root.resolve("lib/app/Vanta.cfg"), "main-class=Vanta");
        Files.writeString(root.resolve("version.txt"), version);
    }
}
