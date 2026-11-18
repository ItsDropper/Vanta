package dev.itsdropper.vanta.launcher.update;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Durable self-update journal. Only the replaceable application payload is
 * swapped; the native launcher and bundled runtime remain in place as a
 * recovery bootstrap throughout the transaction.
 */
public final class UpdateTransaction {
    private static final String JOURNAL_SUFFIX = ".update-transaction";
    private static final String HEALTH_ARGUMENT = "--vanta-update-health";

    private final Path applicationRoot;
    private final Path stagingRoot;
    private final Path activePayload;
    private final Path stagedPayload;
    private final Path backupPayload;
    private final Path journal;
    private final Path healthMarker;

    private UpdateTransaction(Path applicationRoot, Path stagingRoot, Path activePayload,
                              Path stagedPayload, Path backupPayload, Path journal, Path healthMarker) {
        this.applicationRoot = applicationRoot;
        this.stagingRoot = stagingRoot;
        this.activePayload = activePayload;
        this.stagedPayload = stagedPayload;
        this.backupPayload = backupPayload;
        this.journal = journal;
        this.healthMarker = healthMarker;
    }

    public static UpdateTransaction begin(Path applicationRoot, Path stagingRoot,
                                          Path activePayload, Path stagedPayload,
                                          Path backupPayload, Path healthMarker) throws IOException {
        Path app = applicationRoot.toAbsolutePath().normalize();
        Path stage = stagingRoot.toAbsolutePath().normalize();
        Path active = activePayload.toAbsolutePath().normalize();
        Path staged = stagedPayload.toAbsolutePath().normalize();
        Path backup = backupPayload.toAbsolutePath().normalize();
        Path marker = healthMarker.toAbsolutePath().normalize();
        Path parent = app.getParent();
        Path expectedActive = ApplicationLocator.getApplicationPayloadDirectoryForRecovery(app);
        Path expectedStaged = stage.resolve(ApplicationLocator.getApplicationPayloadRelativePath(app)).normalize();

        if (parent == null || !parent.equals(stage.getParent())
                || !active.equals(expectedActive) || !staged.equals(expectedStaged)
                || !backup.equals(active.resolveSibling(active.getFileName() + ".backup"))
                || !staged.startsWith(stage) || Files.exists(stage) || Files.exists(backup)
                || !Files.isDirectory(active) || !ApplicationLocator.isSupportedInstallation(app)
                || !isManagedHealthMarker(marker, true)) {
            throw new IOException("Update transaction paths are not in a clean, supported state.");
        }

        UpdateTransaction transaction = new UpdateTransaction(app, stage, active, staged, backup,
                parent.resolve(app.getFileName() + JOURNAL_SUFFIX), marker);
        if (Files.exists(transaction.journal)) {
            throw new IOException("An unfinished update transaction already exists.");
        }
        transaction.writeState("PREPARED");
        return transaction;
    }

    public void markStaged() throws IOException { writeState("STAGED"); }
    public void markSwapped() throws IOException { writeState("SWAPPED"); }

    public void commit() throws IOException {
        if (!Files.isRegularFile(healthMarker)) {
            throw new IOException("Cannot commit before Vanta reports healthy startup.");
        }
        writeState("HEALTHY");
        deleteTree(backupPayload);
        deleteTree(stagingRoot);
        Files.deleteIfExists(healthMarker);
        Files.deleteIfExists(journal);
    }

    /** Exclusive cross-process lock for one installation's update/recovery work. */
    public static InstallationLock acquireInstallationLock(Path applicationRoot) throws IOException {
        Path app = applicationRoot.toAbsolutePath().normalize();
        Path parent = app.getParent();
        if (parent == null) throw new IOException("Application installation has no parent directory.");
        Path lockPath = parent.resolve(app.getFileName() + ".update.lock");
        FileChannel channel = FileChannel.open(lockPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException alreadyLocked) {
                lock = null;
            }
            if (lock == null) throw new IOException("Another Vanta update or recovery is already active.");
            return new InstallationLock(channel, lock);
        } catch (IOException | RuntimeException failure) {
            channel.close();
            throw failure;
        }
    }

    public static final class InstallationLock implements AutoCloseable {
        private final FileChannel channel;
        private final FileLock lock;

        private InstallationLock(FileChannel channel, FileLock lock) {
            this.channel = channel;
            this.lock = lock;
        }

        @Override
        public void close() throws IOException {
            try {
                if (lock.isValid()) lock.release();
            } finally {
                channel.close();
            }
        }
    }

    public static boolean isHealthInvocation(String[] args) {
        return healthMarkerArgument(args) != null;
    }

    public static void acknowledgeHealthyStartup(String[] args) throws IOException {
        String markerArgument = healthMarkerArgument(args);
        if (markerArgument == null) return;
        Path marker = Path.of(markerArgument).toAbsolutePath().normalize();
        if (!isManagedHealthMarker(marker, true)) {
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

    /** Recover an unfinished payload swap before JavaFX or application services start. */
    public static void recoverBeforeLaunch(Path applicationRoot) throws IOException {
        if (applicationRoot == null) return;
        Path app = applicationRoot.toAbsolutePath().normalize();
        Path parent = app.getParent();
        if (parent == null) return;
        Path journal = parent.resolve(app.getFileName() + JOURNAL_SUFFIX);
        if (!Files.isRegularFile(journal)) return;

        try (InstallationLock ignored = acquireInstallationLock(app)) {
            recoverJournal(app, journal);
        }
    }

    private static void recoverJournal(Path app, Path journal) throws IOException {
        Path parent = app.getParent();
        if (parent == null) throw new IOException("Installation root has no parent directory.");
        Properties properties = new Properties();
        try (var input = Files.newInputStream(journal)) { properties.load(input); }

        Path stage = sibling(parent, required(properties, "stagingRoot"));
        Path active = Path.of(required(properties, "activePayload")).toAbsolutePath().normalize();
        Path staged = Path.of(required(properties, "stagedPayload")).toAbsolutePath().normalize();
        Path backup = Path.of(required(properties, "backupPayload")).toAbsolutePath().normalize();
        Path marker = Path.of(required(properties, "healthMarker")).toAbsolutePath().normalize();
        String state = required(properties, "state");
        Path expectedActive;
        Path expectedStaged;
        try {
            expectedActive = ApplicationLocator.getApplicationPayloadDirectoryForRecovery(app);
            expectedStaged = stage.resolve(ApplicationLocator.getApplicationPayloadRelativePath(app)).normalize();
        } catch (RuntimeException invalidLayout) {
            throw new IOException("Cannot identify the stable Vanta bootstrap for update recovery.", invalidLayout);
        }

        if (!Path.of(required(properties, "applicationRoot")).toAbsolutePath().normalize().equals(app)
                || !("PREPARED".equals(state) || "STAGED".equals(state)
                || "SWAPPED".equals(state) || "HEALTHY".equals(state))
                || !active.equals(expectedActive) || !staged.equals(expectedStaged)
                || !staged.startsWith(stage)
                || !backup.equals(active.resolveSibling(active.getFileName() + ".backup"))
                || !isManagedHealthMarker(marker, false)) {
            throw new IOException("Update journal contains unsafe paths or state; preserving it for diagnosis.");
        }

        boolean installedPayloadValid = ApplicationLocator.isSupportedInstallation(app);
        if (Files.isRegularFile(marker) && installedPayloadValid) {
            cleanupCommitted(stage, backup, marker, journal);
            return;
        }

        if (Files.exists(backup)) {
            deleteTree(active);
            moveDirectory(backup, active);
        } else if (!Files.isDirectory(active) || !ApplicationLocator.isSupportedInstallation(app)) {
            throw new IOException("Interrupted payload update has no verified previous payload to restore.");
        }

        try {
            deleteTree(stage);
            Files.deleteIfExists(marker);
            Files.deleteIfExists(journal);
        } catch (IOException cleanupFailure) {
            System.err.println("[Vanta Update Recovery] Deferred rollback cleanup: " + cleanupFailure.getMessage());
        }
    }

    private static void cleanupCommitted(Path stage, Path backup, Path marker, Path journal) {
        try {
            deleteTree(backup);
            deleteTree(stage);
            Files.deleteIfExists(marker);
            Files.deleteIfExists(journal);
        } catch (IOException cleanupFailure) {
            System.err.println("[Vanta Update Recovery] Deferred post-commit cleanup: "
                    + cleanupFailure.getMessage());
        }
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
        properties.setProperty("applicationRoot", applicationRoot.toString());
        properties.setProperty("stagingRoot", stagingRoot.toString());
        properties.setProperty("activePayload", activePayload.toString());
        properties.setProperty("stagedPayload", stagedPayload.toString());
        properties.setProperty("backupPayload", backupPayload.toString());
        properties.setProperty("healthMarker", healthMarker.toString());

        Path temporary = journal.resolveSibling(journal.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            properties.store(Channels.newOutputStream(channel), "Vanta payload update transaction");
            channel.force(true);
        }
        try {
            Files.move(temporary, journal, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, journal, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isManagedHealthMarker(Path marker, boolean requireExistingWorkspace) {
        if (marker == null || marker.getFileName() == null
                || !"vanta-update-health".equals(marker.getFileName().toString())) return false;
        Path workspace = marker.getParent();
        if (workspace == null || workspace.getFileName() == null
                || !workspace.getFileName().toString().startsWith("session-")
                || Files.isSymbolicLink(workspace)) return false;
        Path expectedRoot = Path.of(System.getProperty("java.io.tmpdir"), "Vanta-updater")
                .toAbsolutePath().normalize();
        if (!expectedRoot.equals(workspace.getParent())) return false;
        return !requireExistingWorkspace || UpdaterWorkspaceCleanup.isManagedWorkspace(workspace);
    }

    private static Path sibling(Path parent, String value) throws IOException {
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!parent.equals(path.getParent())) {
            throw new IOException("Update journal references a path outside the installation parent.");
        }
        return path;
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Update journal is missing " + key + ".");
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
