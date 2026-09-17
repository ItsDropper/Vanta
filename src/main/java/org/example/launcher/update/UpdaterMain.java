package org.example.launcher.update;

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

            System.exit(1);
        }
    }

    private static void run(
            String[] args
    ) throws Exception {

        if (args.length != 3) {

            throw new IllegalArgumentException(
                    "Usage: UpdaterMain <pid> <zip> <application>"
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

        Path zip =
                Path.of(args[1])
                        .toAbsolutePath()
                        .normalize();

        Path applicationDirectory =
                Path.of(args[2])
                        .toAbsolutePath()
                        .normalize();

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

        log("---------- WAITING FOR VANTA ----------");

        waitForProcess(pid);

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

        log(
                "ZIP extraction completed."
        );

        verifyExtractedApplication(
                stagingDirectory
        );

        log(
                "Extracted application verified."
        );

        log("---------- REPLACING APPLICATION ----------");

        moveApplication(
                applicationDirectory,
                stagingDirectory,
                backupDirectory
        );

        log(
                "Application replacement completed."
        );

        log("---------- VERIFYING NEW APPLICATION ----------");

        verifyExtractedApplication(
                applicationDirectory
        );

        log(
                "New Vanta installation verified."
        );

        log("---------- CLEANING BACKUP ----------");

        try {

            deleteDirectory(
                    backupDirectory
            );

            log(
                    "Backup deleted successfully."
            );

        } catch (Exception e) {

            /*
             * The update has already succeeded.
             * Keeping the backup is safer than failing the update.
             */
            log(
                    "WARNING: Could not delete backup directory: "
                            + e
            );
        }

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

        startVanta(
                applicationDirectory
        );

        log(
                "Updated Vanta launched."
        );
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

        Path executable =
                applicationDirectory.resolve(
                        "Vanta.exe"
                );

        log(
                "Current Vanta.exe exists: "
                        + Files.isRegularFile(
                        executable
                )
        );
    }

    private static void waitForProcess(
            long pid
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

        if (process == null) {

            log(
                    "Vanta PID is already gone."
            );

            return;
        }

        long start =
                System.currentTimeMillis();

        while (process.isAlive()) {

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
                "Waiting for Windows to release Vanta files..."
        );

        long start =
                System.currentTimeMillis();

        while (true) {

            /*
             * We test whether Windows allows us to rename the
             * application directory. This is the same operation
             * used by the actual updater.
             */
            Path testDirectory =
                    applicationDirectory
                            .resolveSibling(
                                    applicationDirectory.getFileName()
                                            + ".release-test"
                            );

            try {

                Files.move(
                        applicationDirectory,
                        testDirectory
                );

                Files.move(
                        testDirectory,
                        applicationDirectory
                );

                log(
                        "Windows released the application directory."
                );

                return;

            } catch (IOException e) {

                /*
                 * If the first move succeeded but the second
                 * failed, attempt to restore the original name.
                 */
                if (Files.exists(testDirectory)
                        && !Files.exists(
                        applicationDirectory
                )) {

                    try {

                        Files.move(
                                testDirectory,
                                applicationDirectory
                        );

                    } catch (IOException restoreError) {

                        log(
                                "WARNING: Could not restore "
                                        + "release-test directory: "
                                        + restoreError
                        );
                    }
                }

                long elapsed =
                        System.currentTimeMillis()
                                - start;

                if (elapsed >=
                        FILE_RELEASE_TIMEOUT_MS) {

                    /*
                     * Do NOT throw here.
                     *
                     * The actual replacement method has its own
                     * retry mechanism. This method only prevents
                     * immediately racing Windows after process exit.
                     */
                    log(
                            "Windows has not released all Vanta "
                                    + "handles after 30 seconds."
                    );

                    log(
                            "Continuing to replacement with retries."
                    );

                    return;
                }

                log(
                        "Vanta installation is still locked. "
                                + "Retrying in "
                                + RETRY_DELAY_MS
                                + " ms."
                );

                TimeUnit.MILLISECONDS.sleep(
                        RETRY_DELAY_MS
                );
            }
        }
    }

    private static void verifyExtractedApplication(
            Path applicationDirectory
    ) throws IOException {

        if (!Files.isDirectory(
                applicationDirectory
        )) {

            throw new IOException(
                    "Application directory is missing: "
                            + applicationDirectory
            );
        }

        Path executable =
                applicationDirectory.resolve(
                        "Vanta.exe"
                );

        Path appDirectory =
                applicationDirectory.resolve(
                        "app"
                );

        Path runtimeDirectory =
                applicationDirectory.resolve(
                        "runtime"
                );

        Path javaExecutable =
                runtimeDirectory
                        .resolve("bin")
                        .resolve("java.exe");

        log(
                "Checking application: "
                        + applicationDirectory
        );

        log(
                "Vanta.exe: "
                        + Files.isRegularFile(
                        executable
                )
        );

        log(
                "app/: "
                        + Files.isDirectory(
                        appDirectory
                )
        );

        log(
                "runtime/: "
                        + Files.isDirectory(
                        runtimeDirectory
                )
        );

        log(
                "runtime/bin/java.exe: "
                        + Files.isRegularFile(
                        javaExecutable
                )
        );

        if (!Files.isRegularFile(
                executable
        )) {

            throw new IOException(
                    "Application does not contain Vanta.exe."
            );
        }

        if (!Files.isDirectory(
                appDirectory
        )) {

            throw new IOException(
                    "Application does not contain app directory."
            );
        }

        if (!Files.isDirectory(
                runtimeDirectory
        )) {

            throw new IOException(
                    "Application does not contain runtime directory."
            );
        }

        if (!Files.isRegularFile(
                javaExecutable
        )) {

            throw new IOException(
                    "Application does not contain bundled Java."
            );
        }
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

    private static void startVanta(
            Path applicationDirectory
    ) throws IOException {

        Path executable =
                applicationDirectory.resolve(
                                "Vanta.exe"
                        )
                        .toAbsolutePath()
                        .normalize();

        if (!Files.isRegularFile(
                executable
        )) {

            throw new IOException(
                    "Updated Vanta.exe was not found."
            );
        }

        log(
                "Starting: "
                        + executable
        );

        Process process =
                new ProcessBuilder(
                        executable.toString()
                )
                        .directory(
                                applicationDirectory.toFile()
                        )
                        .start();

        log(
                "New Vanta PID: "
                        + process.pid()
        );

        log(
                "New Vanta alive: "
                        + process.isAlive()
        );
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

            while ((entry =
                    zipInput.getNextEntry()) != null) {

                entryCount++;

                Path output =
                        normalizedDestination
                                .resolve(
                                        entry.getName()
                                )
                                .normalize();

                log(
                        "ZIP entry #"
                                + entryCount
                                + ": "
                                + entry.getName()
                );

                if (!output.startsWith(
                        normalizedDestination
                )) {

                    throw new IOException(
                            "Unsafe ZIP entry: "
                                    + entry.getName()
                    );
                }

                if (entry.isDirectory()) {

                    Files.createDirectories(
                            output
                    );

                } else {

                    Path entryParent =
                            output.getParent();

                    if (entryParent != null) {

                        Files.createDirectories(
                                entryParent
                        );
                    }

                    Files.copy(
                            zipInput,
                            output,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }

                zipInput.closeEntry();
            }

            log(
                    "Total ZIP entries extracted: "
                            + entryCount
            );
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

