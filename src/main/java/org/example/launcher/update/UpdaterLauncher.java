package org.example.launcher.update;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public final class UpdaterLauncher {

    private static final Path DEBUG_LOG =
            Path.of(
                    System.getProperty("java.io.tmpdir"),
                    "Vanta-updater-launcher.log"
            );

    private UpdaterLauncher() {
    }

    public static Path getUpdaterDirectory() {

        String localAppData =
                System.getenv("LOCALAPPDATA");

        if (localAppData == null
                || localAppData.isBlank()) {

            throw new IllegalStateException(
                    "LOCALAPPDATA environment variable is missing."
            );
        }

        Path directory =
                Path.of(
                        localAppData,
                        "Vanta",
                        "updater"
                );

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

        Path updaterDirectory =
                getUpdaterDirectory();

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

        String javaHome = System.getenv("JAVA_HOME");

        Path javaExecutable;

        if (javaHome != null && !javaHome.isBlank()) {
            javaExecutable =
                    Path.of(javaHome, "bin", "java.exe")
                            .toAbsolutePath()
                            .normalize();
        } else {
            javaExecutable = Path.of("java.exe");
        }

        log("---------- PATHS ----------");

        log(
                "Updater directory: "
                        + updaterDirectory
        );

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

        log(
                "Updater Java: "
                        + javaExecutable
        );

        log("---------- VALIDATION ----------");

        if (!Files.isDirectory(updaterDirectory)) {

            log(
                    "Updater directory does not exist. "
                            + "Creating it."
            );

            Files.createDirectories(
                    updaterDirectory
            );
        }

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

        if (!Files.isRegularFile(javaExecutable)) {
            log(
                    "FAIL: system Java runtime does not exist."
            );

            throw new IllegalStateException(
                    "System Java runtime was not found: "
                            + javaExecutable
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

        log(
                "Application directory exists: "
                        + Files.isDirectory(
                        absoluteApplication
                )
        );

        log(
                "Vanta.exe exists: "
                        + Files.isRegularFile(
                        absoluteApplication.resolve(
                                "Vanta.exe"
                        )
                )
        );

        log(
                "Bundled Java exists: "
                        + Files.isRegularFile(
                        javaExecutable
                )
        );

        log("---------- UPDATER COMMAND ----------");

        List<String> command =
                List.of(
                        javaExecutable.toString(),
                        "-jar",
                        updaterJar.toString(),
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
                "Starting updater directly using bundled Java."
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
                                    updaterDirectory.toFile()
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
}

