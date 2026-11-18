package dev.itsdropper.vanta.launcher.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class UpdaterMain {

    private static final Path LOG =
            Path.of(
                    System.getProperty("java.io.tmpdir"),
                    "Vanta-updater.log"
            );

    private static final long PROCESS_WAIT_TIMEOUT_MS =
            60_000;

    private static final long FILE_RELEASE_TIMEOUT_MS =
            30_000;

    private static final long RETRY_DELAY_MS =
            500;

    private static final int MAX_ARCHIVE_ENTRIES = 100_000;
    private static final long MAX_EXTRACTED_BYTES = 2L * 1024 * 1024 * 1024;
    private static final long MAX_ENTRY_BYTES = 1024L * 1024 * 1024;

    private static volatile boolean replacementLauncherStarted;

    private UpdaterMain() {
    }

    public static void main(String[] args) {

        log("========================================");
        log("VANTA UPDATER STARTED");
        log("========================================");

        log(
                "Arguments received: "
                        + args.length
        );

        for (int i = 0; i < args.length; i++) {

            log(
                    "args[" + i + "] = "
                            + args[i]
            );
        }

        try {

            run(args);

            log("========================================");
            log("VANTA UPDATE COMPLETED SUCCESSFULLY");
            log("========================================");

        } catch (Exception e) {

            log("========================================");
            log("VANTA UPDATE FAILED");
            log("========================================");

            log(
                    "Exception class: "
                            + e.getClass().getName()
            );

            log(
                    "Exception message: "
                            + e.getMessage()
            );

            StringWriter stackTrace =
                    new StringWriter();

            e.printStackTrace(
                    new PrintWriter(stackTrace)
            );

            log(
                    stackTrace.toString()
            );

            System.err.println(
                    "Vanta update failed: "
                            + e.getMessage()
            );

            e.printStackTrace();

            if (!replacementLauncherStarted) {
                recoverAfterFailedHandoff(args, e);
            }
            System.exit(1);
        }
    }

    private static void run(
            String[] args
    ) throws Exception {

        if (args.length != 5) {
            throw new IllegalArgumentException(
                    "Usage: UpdaterMain <pid> <process-start-time-ms> <zip> <application> <ready-file>"
            );
        }

        long pid;

        try {

            pid =
                    Long.parseLong(
                            args[0]
                    );

        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                    "Invalid Vanta process ID: "
                            + args[0],
                    e
            );
        }

        long processStartTime;
        try {
            processStartTime = Long.parseLong(args[1]);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid original Vanta process start time.", failure);
        }

        Path zip =
                Path.of(args[2])
                        .toAbsolutePath()
                        .normalize();

        Path applicationDirectory =
                Path.of(args[3])
                        .toAbsolutePath()
                        .normalize();

        Path helperApplication = ApplicationLocator.getApplicationDirectory();
        Path readyFile = validateReadinessFile(helperApplication, Path.of(args[4]));
        Path helperWorkspace = readyFile.getParent();

        log("---------- ENVIRONMENT ----------");

        log(
                "Java version: "
                        + System.getProperty("java.version")
        );

        log(
                "Java home: "
                        + System.getProperty("java.home")
        );

        log(
                "OS: "
                        + System.getProperty("os.name")
                        + " "
                        + System.getProperty("os.version")
        );

        log(
                "OS architecture: "
                        + System.getProperty("os.arch")
        );

        log(
                "Working directory: "
                        + System.getProperty("user.dir")
        );

        log(
                "TEMP: "
                        + System.getProperty("java.io.tmpdir")
        );

        log("---------- INPUT ----------");

        log(
                "Vanta PID: "
                        + pid
        );

        log(
                "Update ZIP: "
                        + zip
        );

        log(
                "Application directory: "
                        + applicationDirectory
        );

        verifyInput(
                zip,
                applicationDirectory
        );

        Files.writeString(
                readyFile,
                "ready" + System.lineSeparator(),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        );
        log("Updater readiness handshake published: " + readyFile);

        log("---------- WAITING FOR VANTA ----------");

        waitForProcess(pid, processStartTime);

        log(
                "Vanta process has terminated."
        );

        /*
         * Windows can take a short amount of time to release
         * file handles after the JVM exits. Give it time before
         * attempting to replace the application directory.
         */
        waitForApplicationRelease(
                applicationDirectory
        );

        log("---------- RECHECKING FILES ----------");

        verifyInput(
                zip,
                applicationDirectory
        );

        Path parent =
                applicationDirectory.getParent();

        if (parent == null) {

            throw new IOException(
                    "Application directory has no parent."
            );
        }

        Path stagingDirectory =
                parent.resolve(
                        applicationDirectory.getFileName()
                                + ".update"
                );

        Path backupDirectory =
                parent.resolve(
                        applicationDirectory.getFileName()
                                + ".backup"
                );

        log(
                "Staging directory: "
                        + stagingDirectory
        );

        log(
                "Backup directory: "
                        + backupDirectory
        );

        log("---------- CLEANING OLD STAGING ----------");

        deleteDirectory(
                stagingDirectory
        );

        deleteDirectory(
                backupDirectory
        );

        log("---------- EXTRACTING UPDATE ----------");

        Files.createDirectories(
                stagingDirectory
        );

        extractZip(
                zip,
                stagingDirectory
        );

        restoreExecutablePermissions(stagingDirectory);

        log(
                "ZIP extraction completed and native executable permissions restored."
        );

        verifyExtractedApplication(
                stagingDirectory
        );

        log("Extracted application verified.");

        Path healthMarker = helperWorkspace.resolve("vanta-update-health");
        UpdateTransaction transaction = UpdateTransaction.begin(
                applicationDirectory, stagingDirectory, backupDirectory, healthMarker);

        log("---------- REPLACING APPLICATION ----------");

        moveApplication(
                applicationDirectory,
                stagingDirectory,
                backupDirectory
        );

        transaction.markSwapped();

        log("Application replacement completed.");

        log("---------- VERIFYING NEW APPLICATION ----------");

        verifyExtractedApplication(
                applicationDirectory
        );

        log(
                "New Vanta installation verified."
        );

        log("---------- CLEANING UPDATE ZIP ----------");

        try {

            Files.deleteIfExists(
                    zip
            );

            log(
                    "Update ZIP deleted."
            );

        } catch (Exception e) {

            log(
                    "WARNING: Could not delete update ZIP: "
                            + e
            );
        }

        log("---------- STARTING UPDATED VANTA ----------");

        Process replacement = startVanta(applicationDirectory, helperWorkspace, healthMarker);
        if (!UpdateTransaction.awaitHealthyStartup(healthMarker, replacement, 90, TimeUnit.SECONDS)) {
            if (replacement.isAlive()) {
                replacement.descendants().forEach(ProcessHandle::destroyForcibly);
                replacement.destroyForcibly();
                replacement.waitFor(10, TimeUnit.SECONDS);
            }
            throw new IOException("Updated Vanta did not complete startup health checks within 90 seconds. "
                    + "The previous installation will be restored.");
        }

        log("Updated Vanta passed its startup-health handshake.");
        replacementLauncherStarted = true;
        transaction.commit();
        log("Update transaction committed; previous installation can now be removed.");
    }

    static Path validateReadinessFile(Path helperApplication, Path readyFile) throws IOException {
        if (helperApplication == null || readyFile == null) {
            throw new IllegalArgumentException("Helper application and readiness file are required.");
        }
        Path helperRoot = helperApplication.toAbsolutePath().normalize();
        Path workspace = helperRoot.getParent();
        Path normalizedReadyFile = readyFile.toAbsolutePath().normalize();
        if (workspace == null
                || !workspace.equals(normalizedReadyFile.getParent())
                || !"updater-ready".equals(normalizedReadyFile.getFileName().toString())) {
            throw new IOException("Updater readiness file must be inside its private helper workspace.");
        }
        return normalizedReadyFile;
    }

    private static void verifyInput(
            Path zip,
            Path applicationDirectory
    ) throws IOException {

        log(
                "ZIP exists: "
                        + Files.exists(zip)
        );

        log(
                "ZIP is regular file: "
                        + Files.isRegularFile(zip)
        );

        if (!Files.isRegularFile(zip)) {

            throw new IOException(
                    "Update ZIP does not exist: "
                            + zip
            );
        }

        log(
                "ZIP size: "
                        + Files.size(zip)
                        + " bytes"
        );

        log(
                "Application exists: "
                        + Files.exists(
                        applicationDirectory
                )
        );

        log(
                "Application is directory: "
                        + Files.isDirectory(
                        applicationDirectory
                )
        );

        if (!Files.isDirectory(
                applicationDirectory
        )) {

            throw new IOException(
                    "Application directory does not exist: "
                            + applicationDirectory
            );
        }

        if (!ApplicationLocator.isSupportedInstallation(applicationDirectory)) {
            throw new IOException("Current Vanta installation is not a supported application image: "
                    + applicationDirectory);
        }
        log("Current Vanta launcher: "
                + ApplicationLocator.getLauncherExecutable(applicationDirectory));
    }

    private static void waitForProcess(
            long pid,
            long expectedStartTime
    ) throws Exception {

        if (pid <= 0) {

            throw new IllegalArgumentException(
                    "Invalid process ID: "
                            + pid
            );
        }

        ProcessHandle process =
                ProcessHandle.of(pid)
                        .orElse(null);

        if (process == null || !matchesProcessStartTime(process, expectedStartTime)) {

            log("Original Vanta process is already gone or its PID has been reused.");
            return;
        }

        long start =
                System.currentTimeMillis();

        while (process.isAlive() && matchesProcessStartTime(process, expectedStartTime)) {

            long elapsed =
                    System.currentTimeMillis()
                            - start;

            if (elapsed >=
                    PROCESS_WAIT_TIMEOUT_MS) {

                throw new IOException(
                        "Timed out waiting for Vanta to close."
                );
            }

            log(
                    "Vanta still running. "
                            + "Elapsed: "
                            + elapsed
                            + " ms"
            );

            TimeUnit.MILLISECONDS.sleep(
                    250
            );
        }
    }

    private static void waitForApplicationRelease(
            Path applicationDirectory
    ) throws InterruptedException {

        log(
                "Waiting briefly for the operating system to settle application file handles..."
        );

        /*
         * The updater is now running completely outside
         * VantaLauncher, including its Java runtime.
         *
         * Vanta itself has already terminated before this method
         * is called. There is therefore no reason to repeatedly
         * rename the entire application directory just to test
         * whether Windows has released it.
         *
         * A short settling period is enough. The real replacement
         * operation below still has its own retry/rollback logic.
         */

        TimeUnit.MILLISECONDS.sleep(
                500
        );

        log(
                "Finished waiting for application file handles to settle."
        );
    }

    static void restoreExecutablePermissions(Path applicationDirectory)
            throws IOException {
        if (dev.itsdropper.vanta.launcher.Platform.operatingSystem()
                == dev.itsdropper.vanta.launcher.Platform.OperatingSystem.WINDOWS) {
            return;
        }

        Path launcher = ApplicationLocator.getLauncherExecutable(applicationDirectory);
        if (!launcher.toFile().setExecutable(true, false) && !Files.isExecutable(launcher)) {
            throw new IOException("Could not mark the Vanta launcher executable: " + launcher);
        }

        Path runtime = ApplicationLocator.getRuntimeDirectory(applicationDirectory);
        Path binaryDirectory = runtime.resolve("bin");
        if (Files.isDirectory(binaryDirectory)) {
            try (var paths = Files.list(binaryDirectory)) {
                for (Path executable : paths.filter(Files::isRegularFile).toList()) {
                    if (!executable.toFile().setExecutable(true, false)
                            && !Files.isExecutable(executable)) {
                        throw new IOException("Could not restore executable permission: " + executable);
                    }
                }
            }
        }

        Path spawnHelper = runtime.resolve("lib/jspawnhelper");
        if (Files.isRegularFile(spawnHelper)
                && !spawnHelper.toFile().setExecutable(true, false)
                && !Files.isExecutable(spawnHelper)) {
            throw new IOException("Could not restore Java process helper permissions: " + spawnHelper);
        }
    }

    private static void verifyExtractedApplication(
            Path applicationDirectory
    ) throws IOException {
        if (!ApplicationLocator.isSupportedInstallation(applicationDirectory)) {
            throw new IOException("Extracted Vanta package is not a supported Windows, Linux, or macOS application image: "
                    + applicationDirectory);
        }
        Path launcher = ApplicationLocator.getLauncherExecutable(applicationDirectory);
        Path runtime = ApplicationLocator.getRuntimeDirectory(applicationDirectory);
        Path payload = ApplicationLocator.getApplicationPayloadDirectory(applicationDirectory);
        if (!Files.isExecutable(launcher)) {
            throw new IOException("Vanta launcher is not executable: " + launcher);
        }
        if (!Files.isRegularFile(runtime.resolve("lib/modules"))) {
            throw new IOException("Bundled Java runtime modules are missing: " + runtime);
        }
        if (!Files.isRegularFile(payload.resolve("Vanta.jar"))
                && !Files.isRegularFile(payload.resolve("Vanta.cfg"))) {
            throw new IOException("Application payload is missing its jpackage launcher metadata.");
        }

        log("Verified application image: " + applicationDirectory);
        log("Launcher: " + launcher);
        log("Runtime: " + runtime);
        log("Payload: " + payload);
    }

    private static void moveApplication(
            Path applicationDirectory,
            Path stagingDirectory,
            Path backupDirectory
    ) throws IOException {

        long start =
                System.currentTimeMillis();

        IOException lastError =
                null;

        while (true) {

            try {

                log(
                        "Attempting application replacement."
                );

                log(
                        "Moving current Vanta to backup."
                );

                moveDirectory(
                        applicationDirectory,
                        backupDirectory
                );

                log(
                        "Current Vanta moved to: "
                                + backupDirectory
                );

                log(
                        "Moving new Vanta into application location."
                );

                moveDirectory(
                        stagingDirectory,
                        applicationDirectory
                );

                log(
                        "New Vanta moved into place."
                );

                return;

            } catch (IOException error) {

                lastError = error;

                log(
                        "Application replacement attempt failed: "
                                + error
                );

                /*
                 * If the old application was moved successfully
                 * but the new one could not be moved into place,
                 * restore the old installation.
                 */
                if (Files.exists(backupDirectory)
                        && !Files.exists(
                        applicationDirectory
                )) {

                    try {

                        log(
                                "Attempting rollback."
                        );

                        moveDirectory(
                                backupDirectory,
                                applicationDirectory
                        );

                        log(
                                "ROLLBACK SUCCESSFUL."
                        );

                    } catch (IOException rollbackError) {

                        error.addSuppressed(
                                rollbackError
                        );

                        log(
                                "ROLLBACK FAILED: "
                                        + rollbackError
                        );
                    }
                }

                long elapsed =
                        System.currentTimeMillis()
                                - start;

                if (elapsed >=
                        FILE_RELEASE_TIMEOUT_MS) {

                    log(
                            "Application replacement failed "
                                    + "after 30 seconds of retries."
                    );

                    throw lastError;
                }

                log(
                        "Retrying application replacement in "
                                + RETRY_DELAY_MS
                                + " ms."
                );

                try {

                    TimeUnit.MILLISECONDS.sleep(
                            RETRY_DELAY_MS
                    );

                } catch (InterruptedException interrupted) {

                    Thread.currentThread().interrupt();

                    IOException interruptedError =
                            new IOException(
                                    "Updater interrupted while "
                                            + "waiting to retry "
                                            + "application replacement.",
                                    interrupted
                            );

                    if (lastError != null) {

                        interruptedError.addSuppressed(
                                lastError
                        );
                    }

                    throw interruptedError;
                }
            }
        }
    }

    private static void moveDirectory(
            Path source,
            Path destination
    ) throws IOException {

        log(
                "Moving:"
        );

        log(
                "  FROM: "
                        + source
        );

        log(
                "  TO:   "
                        + destination
        );

        try {

            Files.move(
                    source,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE
            );

            log(
                    "Atomic move succeeded."
            );

        } catch (IOException atomicError) {

            log(
                    "Atomic move failed: "
                            + atomicError
            );

            log(
                    "Trying normal move."
            );

            Files.move(
                    source,
                    destination
            );

            log(
                    "Normal move succeeded."
            );
        }
    }

    private static boolean isOriginalProcessAlive(long pid, long expectedStartTime) {
        return ProcessHandle.of(pid)
                .filter(process -> matchesProcessStartTime(process, expectedStartTime))
                .isPresent();
    }

    static boolean matchesProcessStartTime(ProcessHandle process, long expectedStartTime) {
        if (expectedStartTime <= 0) {
            return process.isAlive();
        }
        return process.info().startInstant()
                .map(start -> start.toEpochMilli() == expectedStartTime)
                .orElse(process.isAlive());
    }

    private static Process startVanta(
            Path applicationDirectory,
            Path helperWorkspace,
            Path healthMarker
    ) throws IOException {
        Path executable = ApplicationLocator.getLauncherExecutable(applicationDirectory)
                .toAbsolutePath().normalize();
        if (!Files.isRegularFile(executable) || !Files.isExecutable(executable)) {
            throw new IOException("Updated Vanta launcher is missing or not executable: " + executable);
        }

        List<String> command = new java.util.ArrayList<>();
        command.add(executable.toString());
        if (UpdaterWorkspaceCleanup.isManagedWorkspace(helperWorkspace)) {
            command.add("--vanta-cleanup-updater");
            command.add(helperWorkspace.toAbsolutePath().normalize().toString());
            command.add(Long.toString(ProcessHandle.current().pid()));
            command.add(Long.toString(ProcessHandle.current().info().startInstant()
                    .map(java.time.Instant::toEpochMilli).orElse(-1L)));
        }
        if (healthMarker != null) {
            command.add("--vanta-update-health");
            command.add(healthMarker.toAbsolutePath().normalize().toString());
        }

        log("Starting Vanta and awaiting startup-health acknowledgement: " + executable);
        Process process = new ProcessBuilder(command)
                .directory(applicationDirectory.toFile())
                .start();
        log("New Vanta PID: " + process.pid());
        if (!process.isAlive()) {
            throw new IOException("Vanta exited immediately after the updater launched it.");
        }
        return process;
    }

    private static void recoverAfterFailedHandoff(String[] args, Exception originalFailure) {
        if (args == null || args.length != 5) return;
        try {
            long originalPid = Long.parseLong(args[0]);
            long originalStartTime = Long.parseLong(args[1]);
            Path application = Path.of(args[3]).toAbsolutePath().normalize();
            Path readyFile = Path.of(args[4]).toAbsolutePath().normalize();
            Path workspace = readyFile.getParent();
            if (workspace == null || !UpdaterWorkspaceCleanup.isManagedWorkspace(workspace)
                    || !Files.isRegularFile(readyFile)) return;

            if (isOriginalProcessAlive(originalPid, originalStartTime)) {
                log("Original Vanta is still alive; not starting a duplicate instance.");
                return;
            }

            // The durable journal decides whether to restore the backup or
            // retain the already-healthy image. This is also the same path
            // used on the next ordinary launcher start after a power loss.
            UpdateTransaction.recoverBeforeLaunch(application);
            if (!ApplicationLocator.isSupportedInstallation(application)) {
                Path backup = application.resolveSibling(application.getFileName() + ".backup");
                if (!Files.isDirectory(backup)) {
                    log("No verified previous Vanta installation is available for recovery.");
                    return;
                }
                if (Files.exists(application)) deleteDirectory(application);
                moveDirectory(backup, application);
            }
            verifyExtractedApplication(application);
            startVanta(application, workspace, null);
            log("Recovered Vanta installation relaunched after updater failure.");
        } catch (Exception recoveryFailure) {
            originalFailure.addSuppressed(recoveryFailure);
            log("Automatic update recovery failed: " + recoveryFailure);
        }
    }

    private static void extractZip(
            Path zip,
            Path destination
    ) throws IOException {

        Path normalizedDestination =
                destination
                        .toAbsolutePath()
                        .normalize();

        try (
                InputStream input =
                        Files.newInputStream(zip);

                ZipInputStream zipInput =
                        new ZipInputStream(input)
        ) {

            ZipEntry entry;

            int entryCount = 0;
            long totalExtractedBytes = 0;
            byte[] buffer = new byte[64 * 1024];

            while ((entry = zipInput.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ARCHIVE_ENTRIES) {
                    throw new IOException("Update archive contains too many entries.");
                }
                if (entry.getName() == null || entry.getName().isBlank()
                        || entry.getName().indexOf('\0') >= 0) {
                    throw new IOException("Update archive contains an invalid entry name.");
                }

                Path output = normalizedDestination.resolve(entry.getName()).normalize();
                log("ZIP entry #" + entryCount + ": " + entry.getName());
                if (!output.startsWith(normalizedDestination) || output.equals(normalizedDestination)) {
                    throw new IOException("Unsafe ZIP entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Path entryParent = output.getParent();
                    if (entryParent != null) Files.createDirectories(entryParent);

                    long entryBytes = 0;
                    try (var fileOutput = Files.newOutputStream(output)) {
                        int read;
                        while ((read = zipInput.read(buffer)) != -1) {
                            entryBytes += read;
                            totalExtractedBytes += read;
                            if (entryBytes > MAX_ENTRY_BYTES || totalExtractedBytes > MAX_EXTRACTED_BYTES) {
                                throw new IOException("Update archive exceeds the extraction size limit.");
                            }
                            fileOutput.write(buffer, 0, read);
                        }
                    }
                }
                zipInput.closeEntry();
            }

            if (entryCount == 0) throw new IOException("Update archive was empty.");
            log("Total ZIP entries extracted: " + entryCount
                    + ", uncompressed bytes: " + totalExtractedBytes);
        }
    }

    private static void deleteDirectory(
            Path directory
    ) throws IOException {

        if (!Files.exists(directory)) {

            log(
                    "Nothing to delete: "
                            + directory
            );

            return;
        }

        log(
                "Deleting directory: "
                        + directory
        );

        try (var paths =
                     Files.walk(directory)) {

            paths
                    .sorted(
                            Comparator.reverseOrder()
                    )
                    .forEach(path -> {

                        try {

                            Files.deleteIfExists(
                                    path
                            );

                        } catch (IOException e) {

                            throw new DeleteDirectoryException(
                                    e
                            );
                        }
                    });

        } catch (DeleteDirectoryException e) {

            throw e.getCause();
        }
    }

    private static synchronized void log(
            String message
    ) {

        String line =
                "["
                        + LocalDateTime.now()
                        + "] "
                        + message
                        + System.lineSeparator();

        try {

            Files.writeString(
                    LOG,
                    line,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

        } catch (Exception ignored) {
            // Logging must never prevent the updater from running.
        }
    }

    private static final class DeleteDirectoryException
            extends RuntimeException {

        private DeleteDirectoryException(
                IOException cause
        ) {
            super(cause);
        }

        @Override
        public synchronized IOException getCause() {
            return (IOException) super.getCause();
        }
    }
}

