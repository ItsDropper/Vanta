package dev.itsdropper.vanta.launcher.update;

import dev.itsdropper.vanta.launcher.Platform;

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

    /**
     * Terminate the original launcher JVM once the external updater is running.
     * Platform.exit() only shuts down JavaFX and does not guarantee that other
     * non-daemon threads have stopped.
     */
    public static void exitOriginalProcess() {
        System.exit(0);
    }

    public static Path getUpdaterDirectory() {

        Path directory =
                ApplicationLocator.getUpdaterDirectory(
                        ApplicationLocator.getApplicationDirectory()
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

        boolean windowsHost =
                Platform.operatingSystem() == Platform.OperatingSystem.WINDOWS;
        Path updaterJar = windowsHost ? getUpdaterJar() : null;

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
                        + (updaterJar == null ? "<embedded native helper>" : updaterJar)
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

        if (windowsHost && !Files.isRegularFile(updaterJar)) {

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

        if (windowsHost) {
            log("Updater JAR size: " + Files.size(updaterJar) + " bytes");
        }

        log(
                "Update ZIP size: "
                        + Files.size(absoluteZip)
                        + " bytes"
        );

        /*
         * Windows must run the updater from a copied runtime because
         * the operating system locks loaded executable/runtime files.
         * Linux and macOS re-enter the native launcher in helper mode;
         * their process can keep the mapped runtime alive while the
         * application bundle is replaced.
        Path tempUpdaterDirectory = createTemporaryUpdaterDirectory();
        List<String> command;

        log("---------- PREPARING EXTERNAL UPDATER ----------");
        log("Application image: " + absoluteApplication);
        log("Temporary updater workspace: " + tempUpdaterDirectory);

        if (windowsHost) {
            Path tempUpdaterJar = tempUpdaterDirectory.resolve("VantaUpdater.jar");
            Path tempRuntimeDirectory = tempUpdaterDirectory.resolve("runtime");
            Path bundledJava = ApplicationLocator.getJavaExecutable(absoluteApplication);
            Path tempJavaExecutable = tempRuntimeDirectory.resolve("bin")
                    .resolve(bundledJava.getFileName().toString());

            log("Windows requires an updater runtime outside the application image.");
            copyUpdaterJar(updaterJar, tempUpdaterJar);
            copyBundledRuntime(
                    absoluteApplication,
                    tempRuntimeDirectory,
                    bundledJava.getFileName().toString()
            );

            if (!Files.isRegularFile(tempJavaExecutable) || !Files.isExecutable(tempJavaExecutable)) {
                throw new IllegalStateException(
                        "External updater Java was not created or is not executable: " + tempJavaExecutable);
            }

            command = List.of(
                    tempJavaExecutable.toString(),
                    "-jar",
                    tempUpdaterJar.toString(),
                    Long.toString(processId),
                    absoluteZip.toString(),
                    absoluteApplication.toString()
            );
        } else {
            /*
             * Linux and macOS jpackage runtimes may omit the standalone
             * bin/java command. Re-enter the packaged native launcher in a
             * dedicated helper mode instead. The updater process remains
             * independent of the original launcher JVM.
             */
            Path nativeLauncher = ApplicationLocator.getLauncherExecutable(absoluteApplication);
            command = List.of(
                    nativeLauncher.toString(),
                    "--vanta-updater",
                    Long.toString(processId),
                    absoluteZip.toString(),
                    absoluteApplication.toString()
            );
            log("Unix updater will run through the packaged native launcher: " + nativeLauncher);
        }

        for (int i = 0; i < command.size(); i++) {
            log("[" + i + "] " + command.get(i));
        }

        log("Starting updater process and waiting for original Vanta PID " + processId + " to exit.");
        try {

            Process process =
                    new ProcessBuilder(command)
                            .directory(
                                    tempUpdaterDirectory.toFile()
                            )
                            .redirectErrorStream(true)
                            .redirectOutput(
                                    ProcessBuilder.Redirect.appendTo(
                                            getUpdaterOutputLog().toFile()
                                    )
                            )
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
            Path destination,
            String javaExecutableName
    ) throws IOException {
        Path source = ApplicationLocator.getRuntimeDirectory(applicationDirectory);
        log("Copying bundled Java runtime from " + source + " to " + destination);
        if (!Files.isDirectory(source)) {
            throw new IOException("Bundled Java runtime directory does not exist: " + source);
        }

        Files.createDirectories(destination);
        try (var paths = Files.walk(source)) {
            for (Path sourcePath : paths.toList()) {
                Path relative = source.relativize(sourcePath);
                Path destinationPath = destination.resolve(relative);
                if (Files.isSymbolicLink(sourcePath)) {
                    Path linkTarget = Files.readSymbolicLink(sourcePath);
                    Files.createDirectories(destinationPath.getParent());
                    Files.createSymbolicLink(destinationPath, linkTarget);
                } else if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(destinationPath);
                } else {
                    Files.createDirectories(destinationPath.getParent());
                    Files.copy(sourcePath, destinationPath,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
        Path javaExecutable = destination.resolve("bin")
                .resolve(javaExecutableName);
        if (!Files.isRegularFile(javaExecutable) || !Files.isExecutable(javaExecutable)) {
            throw new IOException("Copied updater Java runtime is not executable: " + javaExecutable);
        }
        log("Bundled Java runtime copied successfully.");
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