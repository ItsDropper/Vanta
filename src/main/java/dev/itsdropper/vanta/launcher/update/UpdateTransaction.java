package dev.itsdropper.vanta.launcher.update;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Durable journal for a self-update. The journal and all installation
 * directories are siblings so directory moves stay on the same filesystem.
 */
public final class UpdateTransaction {
    private static final String JOURNAL_SUFFIX = ".update-transaction";
    private static final String HEALTH_ARGUMENT = "--vanta-update-health";

    private final Path application;
    private final Path staging;
    private final Path backup;
    private final Path journal;
    private final Path healthMarker;

    private UpdateTransaction(Path application, Path staging, Path backup,
                              Path journal, Path healthMarker) {
        this.application = application;
        this.staging = staging;
        this.backup = backup;
        this.journal = journal;
        this.healthMarker = healthMarker;
    }

    public static UpdateTransaction begin(Path application, Path staging, Path backup,
                                          Path healthMarker) throws IOException {
        Path app = application.toAbsolutePath().normalize();
        Path stage = staging.toAbsolutePath().normalize();
        Path old = backup.toAbsolutePath().normalize();
        Path marker = healthMarker.toAbsolutePath().normalize();
        Path parent = app.getParent();
        if (parent == null || !parent.equals(stage.getParent()) || !parent.equals(old.getParent())) {
            throw new IOException("Update transaction paths must share the installation parent.");
        }
        if (app.equals(stage) || app.equals(old) || stage.equals(old)
                || !Files.isDirectory(stage) || Files.exists(old)) {
            throw new IOException("Update transaction paths are not in a clean state.");
        }
        UpdateTransaction transaction = new UpdateTransaction(
                app, stage, old, parent.resolve(app.getFileName() + JOURNAL_SUFFIX), marker);
        transaction.writeState("PREPARED");
        return transaction;
    }

    public void markSwapped() throws IOException {
        writeState("SWAPPED");
    }

    public void commit() throws IOException {
        if (!Files.isRegularFile(healthMarker)) {
            throw new IOException("Cannot commit an update before the new launcher reports healthy startup.");
        }
        writeState("HEALTHY");
        deleteTree(backup);
        deleteTree(staging);
        Files.deleteIfExists(journal);
        Files.deleteIfExists(healthMarker);
    }

    public static boolean isHealthInvocation(String[] args) {
        return healthMarkerArgument(args) != null;
    }

    public static void acknowledgeHealthyStartup(String[] args) throws IOException {
        String markerArgument = healthMarkerArgument(args);
        if (markerArgument == null) return;
        Path marker = Path.of(markerArgument).toAbsolutePath().normalize();
        Path parent = marker.getParent();
        if (parent == null || !UpdaterWorkspaceCleanup.isManagedWorkspace(parent)
                || !"vanta-update-health".equals(marker.getFileName().toString())) {
            throw new IOException("Invalid update health marker path.");
        }
        Files.writeString(marker, "healthy" + System.lineSeparator(),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static String healthMarkerArgument(String[] args) {
        if (args == null) return null;
        for (int i = 0; i + 1 < args.length; i++) {
            if (HEALTH_ARGUMENT.equals(args[i])) return args[i + 1];
        }
        return null;
    }

    /**
     * Called on ordinary launches only. An update-specific launch is allowed
     * to finish startup and publish its health acknowledgement first.
     */
    public static void recoverBeforeLaunch(Path application) throws IOException {
        if (application == null || !ApplicationLocator.isSupportedInstallation(application)) return;
        Path app = application.toAbsolutePath().normalize();
        Path parent = app.getParent();
        if (parent == null) return;
        Path journal = parent.resolve(app.getFileName() + JOURNAL_SUFFIX);
        if (!Files.isRegularFile(journal)) return;

        Properties properties = new Properties();
        try (var input = Files.newInputStream(journal)) {
            properties.load(input);
        }

        Path stage = sibling(app, properties.getProperty("staging"));
        Path backup = sibling(app, properties.getProperty("backup"));
        Path marker = Path.of(required(properties, "healthMarker")).toAbsolutePath().normalize();
        if (stage.equals(app) || backup.equals(app) || stage.equals(backup)
                || !marker.getFileName().toString().equals("vanta-update-health")) {
            throw new IOException("Update transaction journal contains unsafe paths; preserving it for diagnosis.");
        }

        if (Files.isRegularFile(marker) && ApplicationLocator.isSupportedInstallation(app)) {
            deleteTree(backup);
            deleteTree(stage);
            Files.deleteIfExists(marker);
            Files.deleteIfExists(journal);
            return;
        }

        if (Files.exists(backup)) {
            deleteTree(app);
            moveDirectory(backup, app);
        } else if (!ApplicationLocator.isSupportedInstallation(app)) {
            throw new IOException("Interrupted update has no verified previous installation to restore.");
        }
        deleteTree(stage);
        Files.deleteIfExists(marker);
        Files.deleteIfExists(journal);
    }

    public static boolean awaitHealthyStartup(Path marker, Process process, long timeout,
                                               TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (Files.isRegularFile(marker)) return true;
            if (process != null && !process.isAlive()) return false;
            TimeUnit.MILLISECONDS.sleep(100);
        }
        return Files.isRegularFile(marker);
    }

    private void writeState(String state) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("state", state);
        properties.setProperty("application", application.toString());
        properties.setProperty("staging", staging.toString());
        properties.setProperty("backup", backup.toString());
        properties.setProperty("healthMarker", healthMarker.toString());
        Path temporary = journal.resolveSibling(journal.getFileName() + ".tmp");
        try (var output = Files.newOutputStream(temporary,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            properties.store(output, "Vanta self-update transaction");
        }
        try {
            Files.move(temporary, journal, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, journal, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path sibling(Path app, String value) throws IOException {
        if (value == null) throw new IOException("Update transaction journal is incomplete.");
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!app.getParent().equals(path.getParent())) {
            throw new IOException("Update transaction journal references a path outside the installation parent.");
        }
        return path;
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Update transaction journal is missing " + key + ".");
        return value;
    }

    private static void moveDirectory(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(source, destination);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
