package org.example.launcher.update;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

public final class UpdaterLauncher {

    private static final Path DEBUG_LOG =
            Path.of(
                    System.getProperty("java.io.tmpdir"),
                    "Vanta-updater-launcher.log"
            );

    private static final Path TEMP_UPDATER_ROOT =
            Path.of(
                    System.getProperty("java.io.tmpdir"),
                    "Vanta-updater"
            );

    private UpdaterLauncher() {
    }

    public static Path getUpdaterDirectory() {

        Path directory =
                ApplicationLocator
                        .getApplicationDirectory()
                        .resolve("updater");

        log(
                "getUpdaterDirectory() -> "
                        + directory
        );

        return directory;
    }

    public static Path getUpdaterJar() {

        Path jar =
                getUpdaterDirectory()
                        .resolve("VantaUpdater.jar");

        log(
                "getUpdaterJar() -> "
                        + jar
        );

        return jar;
    }

    public static void ensureDirectory()
            throws Exception {

        log("========================================");
        log("ensureDirectory() CALLED");
        log("========================================");

        Path directory =
                getUpdaterDirectory();

        Files.createDirectories(
                directory
        );

        log(
                "Updater directory: "
                        + directory
        );

        log(
                "Exists: "
                        + Files.exists(directory)
        );

        log(
                "Is directory: "
                        + Files.isDirectory(directory)
        );
    }

    public static void start(
            long processId,
            Path updateZip,
            Path applicationDirectory
    ) throws Exception {

        log("========================================");
        log("UpdaterLauncher.start() CALLED");
        log("========================================");

        log(
                "Vanta PID: "
                        + processId
        );

        log(
                "Current JVM: "
                        + ProcessHandle.current()
                        .info()
                        .command()
                        .orElse("<unknown>")
        );

        log(
                "Java version: "
                        + System.getProperty("java.version")
        );

        log(
                "Java home: "
                        + System.getProperty("java.home")
        );

        log(
                "Working directory: "
                        + System.getProperty("user.dir")
        );

        log(
                "TEMP: "
                        + System.getProperty("java.io.tmpdir")
        );

        Path updaterJar =
                getUpdaterJar();

        Path absoluteZip =
                updateZip
                        .toAbsolutePath()
                        .normalize();

        Path absoluteApplication =
                applicationDirectory
                        .toAbsolutePath()
                        .normalize();

        log("---------- PATHS ----------");

        log(
                "Updater JAR: "
                        + updaterJar
        );

        log(
                "Update ZIP: "
                        + absoluteZip
        );

        log(
                "Application directory: "
                        + absoluteApplication
        );

        log("---------- VALIDATION ----------");

        if (!Files.isRegularFile(updaterJar)) {

            log(
                    "FAIL: updater JAR does not exist."
            );

            throw new IllegalStateException(
                    "Vanta updater JAR was not found: "
                            + updaterJar
            );
        }

        if (!Files.isRegularFile(absoluteZip)) {

            log(
                    "FAIL: update ZIP does not exist."
            );

            throw new IllegalArgumentException(
                    "Update ZIP was not found: "
                            + absoluteZip
            );
        }

        if (!Files.isDirectory(absoluteApplication)) {

            log(
                    "FAIL: application directory does not exist."
            );

            throw new IllegalArgumentException(
                    "Vanta application directory was not found: "
                            + absoluteApplication
            );
        }

        log(
                "Updater JAR size: "
                        + Files.size(updaterJar)
                        + " bytes"
        );

        log(
                "Update ZIP size: "
                        + Files.size(absoluteZip)
                        + " bytes"
        );

        /*
         * IMPORTANT:
         *
         * The updater must NOT run from inside VantaLauncher.
         *
         * Vanta is currently running from the bundled runtime
         * inside VantaLauncher. If we launch the updater using
         * that same runtime, Windows can keep the runtime files
         * locked and prevent the entire VantaLauncher directory
         * from being renamed.
         *
         * Therefore we create a completely external temporary
         * updater environment containing:
         *
         *   %TEMP%\Vanta-updater\<id>\
         *       updater\
         *           VantaUpdater.jar
         *       runtime\
         *           ...
         *
         * The updater process then runs entirely from there.
         */

        Path tempUpdaterDirectory =
                createTemporaryUpdaterDirectory();

        Path tempUpdaterJar =
                tempUpdaterDirectory
                        .resolve("VantaUpdater.jar");

        Path tempRuntimeDirectory =
                tempUpdaterDirectory
                        .resolve("runtime");

        Path tempJavaExecutable =
                tempRuntimeDirectory
                        .resolve("bin")
                        .resolve("java.exe");

        log("---------- PREPARING EXTERNAL UPDATER ----------");

        log(
                "External updater directory: "
                        + tempUpdaterDirectory
        );

        log(
                "External updater JAR: "
                        + tempUpdaterJar
        );

        log(
                "External runtime directory: "
                        + tempRuntimeDirectory
        );

        log(
                "External Java: "
                        + tempJavaExecutable
        );

        copyUpdaterJar(
                updaterJar,
                tempUpdaterJar
        );

        copyBundledRuntime(
                absoluteApplication,
                tempRuntimeDirectory
        );

        if (!Files.isRegularFile(
                tempJavaExecutable
        )) {

            throw new IllegalStateException(
                    "External updater Java was not created: "
                            + tempJavaExecutable
            );
        }

        log(
                "External updater environment prepared successfully."
        );

        log(
                "External Java exists: "
                        + Files.isRegularFile(
                        tempJavaExecutable
                )
        );

        log(
                "External updater JAR exists: "
                        + Files.isRegularFile(
                        tempUpdaterJar
                )
        );

        log("---------- UPDATER COMMAND ----------");

        List<String> command =
                List.of(
                        tempJavaExecutable.toString(),
                        "-jar",
                        tempUpdaterJar.toString(),
                        Long.toString(processId),
                        absoluteZip.toString(),
                        absoluteApplication.toString()
                );

        for (int i = 0;
             i < command.size();
             i++) {

            log(
                    "[" + i + "] "
                            + command.get(i)
            );
        }

        log(
                "Starting updater using the external temporary runtime."
        );

        log(
                "The updater process will NOT use files inside VantaLauncher."
        );

        log(
                "The updater will wait for Vanta PID "
                        + processId
                        + " to terminate."
        );

        try {

            Process process =
                    new ProcessBuilder(command)
                            .directory(
                                    tempUpdaterDirectory.toFile()
                            )
                            .redirectErrorStream(true)
                            .start();

            log(
                    "SUCCESS: updater process started."
            );

            log(
                    "Updater process PID: "
                            + process.pid()
            );

            log(
                    "Updater process alive: "
                            + process.isAlive()
            );

            log(
                    "Updater command: "
                            + process.info()
                            .command()
                            .orElse("<unknown>")
            );

            log(
                    "Updater command line: "
                            + process.info()
                            .commandLine()
                            .orElse("<unknown>")
            );

            /*
             * Read updater output in the background.
             * This prevents the updater from blocking if its
             * output buffer becomes full.
             */
            Thread outputThread =
                    new Thread(() -> {

                        try {

                            process.getInputStream()
                                    .transferTo(
                                            Files.newOutputStream(
                                                    getUpdaterOutputLog(),
                                                    StandardOpenOption.CREATE,
                                                    StandardOpenOption.APPEND
                                            )
                                    );

                        } catch (Exception e) {

                            log(
                                    "Failed to capture updater output: "
                                            + e
                            );
                        }

                    }, "Vanta-Updater-Output");

            outputThread.setDaemon(true);
            outputThread.start();

            log(
                    "Updater output capture thread started."
            );

        } catch (Exception e) {

            log(
                    "========================================"
            );

            log(
                    "FAILED TO START UPDATER"
            );

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

            log(
                    "========================================"
            );

            throw e;
        }

        log(
                "UpdaterLauncher.start() FINISHED."
        );

        log(
                "Vanta can now shut down."
        );

        log("========================================");
    }

    private static Path createTemporaryUpdaterDirectory()
            throws IOException {

        Files.createDirectories(
                TEMP_UPDATER_ROOT
        );

        Path directory =
                Files.createTempDirectory(
                        TEMP_UPDATER_ROOT,
                        "session-"
                );

        log(
                "Created temporary updater directory: "
                        + directory
        );

        return directory;
    }

    private static void copyUpdaterJar(
            Path source,
            Path destination
    ) throws IOException {

        log(
                "Copying updater JAR:"
        );

        log(
                "  FROM: "
                        + source
        );

        log(
                "  TO:   "
                        + destination
        );

        Path parent =
                destination.getParent();

        if (parent != null) {

            Files.createDirectories(
                    parent
            );
        }

        Files.copy(
                source,
                destination,
                StandardCopyOption.REPLACE_EXISTING
        );

        log(
                "Updater JAR copied successfully."
        );

        log(
                "Copied JAR size: "
                        + Files.size(destination)
                        + " bytes"
        );
    }

    private static void copyBundledRuntime(
            Path applicationDirectory,
            Path destination
    ) throws IOException {

        Path source =
                applicationDirectory
                        .resolve("runtime");

        log(
                "Copying bundled Java runtime:"
        );

        log(
                "  FROM: "
                        + source
        );

        log(
                "  TO:   "
                        + destination
        );

        if (!Files.isDirectory(source)) {

            throw new IOException(
                    "Bundled Java runtime directory does not exist: "
                            + source
            );
        }

        Files.walk(source)
                .forEach(sourcePath -> {

                    try {

                        Path relative =
                                source.relativize(
                                        sourcePath
                                );

                        Path destinationPath =
                                destination.resolve(
                                        relative
                                );

                        if (Files.isDirectory(sourcePath)) {

                            Files.createDirectories(
                                    destinationPath
                            );

                        } else {

                            Path parent =
                                    destinationPath.getParent();

                            if (parent != null) {

                                Files.createDirectories(
                                        parent
                                );
                            }

                            Files.copy(
                                    sourcePath,
                                    destinationPath,
                                    StandardCopyOption.REPLACE_EXISTING
                            );
                        }

                    } catch (IOException e) {

                        throw new RuntimeCopyException(
                                e
                        );
                    }
                });

        log(
                "Bundled Java runtime copied successfully."
        );
    }

    private static Path getUpdaterOutputLog() {

        return Path.of(
                System.getProperty("java.io.tmpdir"),
                "Vanta-updater-output.log"
        );
    }

    private static synchronized void log(
            String message
    ) {

        String line =
                "["
                        + java.time.LocalDateTime.now()
                        + "] "
                        + message
                        + System.lineSeparator();

        try {

            Files.writeString(
                    DEBUG_LOG,
                    line,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

        } catch (Exception ignored) {
            // Logging must never prevent updating.
        }
    }

    private static final class RuntimeCopyException
            extends RuntimeException {

        private RuntimeCopyException(
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