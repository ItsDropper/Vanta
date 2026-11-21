package dev.itsdropper.vanta.launcher.instance;

import dev.itsdropper.vanta.launcher.MinecraftLocator;
import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.service.MultiLaunchService;
import dev.itsdropper.vanta.launcher.service.LaunchHistoryRepository;
import dev.itsdropper.vanta.ui.LauncherSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Periodic, local full-instance snapshots. A snapshot is created only when Vanta
 * can confirm that the corresponding instance is not running. Existing backups
 * are retained until a new archive has been created and verified successfully.
 */
public final class EnvironmentGuardianService implements AutoCloseable {
    private static final long INITIAL_DELAY_MINUTES = 1;
    private static final long CHECK_INTERVAL_MINUTES = 15;

    public enum Status {
        CREATED,
        SKIPPED_RUNNING,
        SKIPPED_BUSY,
        SKIPPED_RECENT,
        FAILED
    }

    public record BackupResult(
            String instanceId,
            Status status,
            Path archive,
            String message
    ) { }

    @FunctionalInterface
    interface BackupCreator {
        void create(Instance instance, Path destination) throws IOException;
    }

    @FunctionalInterface
    interface ActivityRecorder {
        void record(Instance instance, LaunchHistoryRepository.Outcome outcome, String message) throws IOException;
    }

    private final Path backupRoot;
    private final Supplier<List<Instance>> instanceSupplier;
    private final Predicate<Instance> runningPredicate;
    private final BooleanSupplier enabled;
    private final IntSupplier intervalHours;
    private final IntSupplier retentionCount;
    private final BackupCreator backupCreator;
    private final ActivityRecorder activityRecorder;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean cycleRunning = new AtomicBoolean(false);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public EnvironmentGuardianService(MultiLaunchService launchService) {
        this(
                MinecraftLocator.getVantaDirectory().resolve("automatic-backups"),
                InstanceManager::discoverInstances,
                instance -> {
                    dev.itsdropper.vanta.launcher.service.LaunchService.LaunchState state =
                            launchService.getState(instance);
                    return state == dev.itsdropper.vanta.launcher.service.LaunchService.LaunchState.PREPARING
                            || state == dev.itsdropper.vanta.launcher.service.LaunchService.LaunchState.STARTING
                            || state == dev.itsdropper.vanta.launcher.service.LaunchService.LaunchState.RUNNING
                            || state == dev.itsdropper.vanta.launcher.service.LaunchService.LaunchState.CLOSING;
                },
                LauncherSettings::isAutomaticBackupsEnabled,
                LauncherSettings::getAutomaticBackupIntervalHours,
                LauncherSettings::getAutomaticBackupRetention,
                InstanceBackupService::createBackup,
                (instance, outcome, message) -> new LaunchHistoryRepository().recordActivity(instance, outcome, message)
        );
    }

    EnvironmentGuardianService(
            Path backupRoot,
            Supplier<List<Instance>> instanceSupplier,
            Predicate<Instance> runningPredicate,
            BooleanSupplier enabled,
            IntSupplier intervalHours,
            IntSupplier retentionCount,
            BackupCreator backupCreator
    ) {
        this(backupRoot, instanceSupplier, runningPredicate, enabled, intervalHours, retentionCount,
                backupCreator, (instance, outcome, message) ->
                        new LaunchHistoryRepository().recordActivity(instance, outcome, message));
    }

    EnvironmentGuardianService(
            Path backupRoot,
            Supplier<List<Instance>> instanceSupplier,
            Predicate<Instance> runningPredicate,
            BooleanSupplier enabled,
            IntSupplier intervalHours,
            IntSupplier retentionCount,
            BackupCreator backupCreator,
            ActivityRecorder activityRecorder
    ) {
        this.backupRoot = java.util.Objects.requireNonNull(backupRoot, "backupRoot")
                .toAbsolutePath().normalize();
        this.instanceSupplier = java.util.Objects.requireNonNull(instanceSupplier, "instanceSupplier");
        this.runningPredicate = java.util.Objects.requireNonNull(runningPredicate, "runningPredicate");
        this.enabled = java.util.Objects.requireNonNull(enabled, "enabled");
        this.intervalHours = java.util.Objects.requireNonNull(intervalHours, "intervalHours");
        this.retentionCount = java.util.Objects.requireNonNull(retentionCount, "retentionCount");
        this.backupCreator = java.util.Objects.requireNonNull(backupCreator, "backupCreator");
        this.activityRecorder = java.util.Objects.requireNonNull(activityRecorder, "activityRecorder");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "vanta-environment-guardian");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Starts a low-frequency scheduler. The enabled setting is checked on every cycle. */
    public void start() {
        if (closed.get() || !started.compareAndSet(false, true)) return;
        scheduler.scheduleWithFixedDelay(
                this::runSafely,
                INITIAL_DELAY_MINUTES,
                CHECK_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );
    }

    /**
     * Runs one due-backup pass. Package-private so filesystem behavior can be
     * exercised deterministically without starting a background scheduler.
     */
    List<BackupResult> runOnce() {
        if (closed.get() || !enabled.getAsBoolean() || !cycleRunning.compareAndSet(false, true)) {
            return List.of();
        }

        try {
            List<Instance> instances = instanceSupplier.get();
            if (instances == null || instances.isEmpty()) return List.of();

            long now = System.currentTimeMillis();
            long intervalMillis = TimeUnit.HOURS.toMillis(Math.max(1, intervalHours.getAsInt()));
            int keep = Math.max(1, Math.min(20, retentionCount.getAsInt()));
            List<BackupResult> results = new ArrayList<>();

            for (Instance instance : instances) {
                if (instance == null) continue;
                try {
                    if (runningPredicate.test(instance)) {
                        results.add(new BackupResult(instance.getId(), Status.SKIPPED_RUNNING,
                                null, "Instance is currently active in Vanta."));
                        continue;
                    }
                    if (InstanceManager.isInstallationInProgress(instance)
                            || InstanceRepairService.isRepairInProgress(instance)) {
                        results.add(new BackupResult(instance.getId(), Status.SKIPPED_BUSY,
                                null, "Instance installation or repair is in progress."));
                        continue;
                    }

                    Path instanceBackupDirectory = resolveInstanceBackupDirectory(instance);
                    List<Path> existing = listArchives(instanceBackupDirectory);
                    if (!existing.isEmpty()) {
                        long newestBackup = lastModified(existing.getLast());
                        if (now <= newestBackup || now - newestBackup < intervalMillis) {
                            results.add(new BackupResult(instance.getId(), Status.SKIPPED_RECENT,
                                    existing.getLast(), "A recent automatic backup already exists."));
                            continue;
                        }
                    }

                    String filename = now + "-" + UUID.randomUUID().toString().substring(0, 8) + ".zip";
                    Path destination = instanceBackupDirectory.resolve(filename);
                    backupCreator.create(instance, destination);

                    String message = "Full instance backup created and integrity-verified.";
                    try {
                        prune(instanceBackupDirectory, keep);
                    } catch (IOException cleanupFailure) {
                        message += " Retention cleanup could not finish: " + safeMessage(cleanupFailure);
                    }
                    results.add(new BackupResult(instance.getId(), Status.CREATED, destination, message));
                    recordBackupActivity(instance);
                } catch (Exception failure) {
                    String message = safeMessage(failure);
                    System.err.println("[Environment Guardian] Backup failed for "
                            + instance.getName() + ": " + message);
                    results.add(new BackupResult(instance.getId(), Status.FAILED, null, message));
                }
            }
            return List.copyOf(results);
        } finally {
            cycleRunning.set(false);
        }
    }

    private void recordBackupActivity(Instance instance) {
        try {
            activityRecorder.record(instance, LaunchHistoryRepository.Outcome.BACKUP_CREATED,
                    "Automatic backup created and integrity-verified.");
        } catch (IOException | RuntimeException failure) {
            // Timeline persistence is ancillary; a verified backup remains successful.
            System.err.println("[Environment Guardian] Timeline recording failed: "
                    + failure.getClass().getSimpleName());
        }
    }

    private Path resolveInstanceBackupDirectory(Instance instance) throws IOException {
        String id = instance.getId();
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw new IOException("Instance ID is not safe for an automatic-backup directory.");
        }
        if (Files.isSymbolicLink(backupRoot)) {
            throw new IOException("Automatic-backup root must not be a symbolic link.");
        }
        Files.createDirectories(backupRoot);
        Path directory = backupRoot.resolve(id).normalize();
        if (!directory.startsWith(backupRoot) || directory.equals(backupRoot)
                || Files.isSymbolicLink(directory)) {
            throw new IOException("Automatic-backup destination is not a safe directory.");
        }
        Files.createDirectories(directory);
        return directory;
    }

    private static List<Path> listArchives(Path directory) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(directory)) {
            return List.of();
        }
        try (var files = Files.list(directory)) {
            return files
                    .filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip"))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparingLong(EnvironmentGuardianService::lastModified))
                    .toList();
        }
    }

    private static void prune(Path directory, int keep) throws IOException {
        List<Path> archives = listArchives(directory);
        int removeCount = Math.max(0, archives.size() - keep);
        for (int index = 0; index < removeCount; index++) {
            Files.deleteIfExists(archives.get(index));
        }
    }

    private static long lastModified(Path path) {
        try {
            FileTime modified = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS);
            return modified.toMillis();
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private void runSafely() {
        if (closed.get()) return;
        try {
            for (BackupResult result : runOnce()) {
                if (result.status() == Status.CREATED) {
                    System.out.println("[Environment Guardian] Created backup for " + result.instanceId()
                            + ": " + result.archive());
                }
            }
        } catch (Throwable failure) {
            System.err.println("[Environment Guardian] Scheduled backup cycle failed: " + safeMessage(failure));
        }
    }

    /** Stops future cycles without interrupting an archive currently being written. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        scheduler.shutdown();
    }
}
