package dev.itsdropper.vanta.launcher.update;

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

        if (processId <= 0 || updateZip == null || applicationDirectory == null) {
            throw new IllegalArgumentException("A valid process ID, update archive, and application directory are required.");
        }

        Path absoluteZip = updateZip.toAbsolutePath().normalize();
        Path absoluteApplication = applicationDirectory.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absoluteZip)) {
            throw new IOException("Update archive does not exist: " + absoluteZip);
        }
        if (!ApplicationLocator.isSupportedInstallation(absoluteApplication)) {
            throw new IOException("Unsupported Vanta application image: " + absoluteApplication);
        }

        Path tempUpdaterDirectory = createTemporaryUpdaterDirectory();
        log("Update archive: " + absoluteZip);
        log("Application image: " + absoluteApplication);
        log("Updater workspace: " + tempUpdaterDirectory);

        /*
         * Run the updater from a private copy of the complete application image.
         * This avoids Windows executable/DLL locks and avoids relying on a
         * standalone java/javaw binary that jpackage does not guarantee to ship.
         * The helper can then replace the original image on every supported OS.
         */
        Path helperApplication = tempUpdaterDirectory.resolve("application");
        copyApplicationImage(absoluteApplication, helperApplication);
        Path helperLauncher = ApplicationLocator.getLauncherExecutable(helperApplication);
        if (!Files.isRegularFile(helperLauncher) || !Files.isExecutable(helperLauncher)) {
            throw new IOException("Copied updater launcher is missing or not executable: " + helperLauncher);
        }

        List<String> command = List.of(
                helperLauncher.toString(),
                "--vanta-updater",
                Long.toString(processId),
                absoluteZip.toString(),
                absoluteApplication.toString()
        );

        log("Copied application image for isolated updater: " + helperApplication);
        log("Updater helper executable: " + helperLauncher);

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

    static void copyApplicationImage(Path source, Path destination) throws IOException {
        if (source == null || destination == null) {
            throw new IllegalArgumentException("Application source and destination are required.");
        }
        Path sourceRoot = source.toRealPath();
        Path destinationRoot = destination.toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) {
            throw new IOException("Application image source is not a directory: " + sourceRoot);
        }
        if (destinationRoot.startsWith(sourceRoot) || sourceRoot.startsWith(destinationRoot)) {
            throw new IOException("Updater copy must be outside the source application image.");
        }
        copyDirectoryContents(sourceRoot, destinationRoot, sourceRoot, new java.util.HashSet<>());
    }

    private static void copyDirectoryContents(
            Path sourceDirectory,
            Path destinationDirectory,
            Path sourceRoot,
            java.util.Set<Path> activeDirectories
    ) throws IOException {
        Path realDirectory = sourceDirectory.toRealPath();
        if (!realDirectory.startsWith(sourceRoot)) {
            throw new IOException("Application image contains a link outside its root: " + sourceDirectory);
        }
        if (!activeDirectories.add(realDirectory)) {
            throw new IOException("Application image contains a directory-link cycle: " + sourceDirectory);
        }

        Files.createDirectories(destinationDirectory);
        try (var children = Files.newDirectoryStream(realDirectory)) {
            for (Path child : children) {
                Path realChild = child.toRealPath();
                if (!realChild.startsWith(sourceRoot)) {
                    throw new IOException("Application image contains a link outside its root: " + child);
                }
                Path target = destinationDirectory.resolve(child.getFileName().toString());
                if (Files.isDirectory(realChild)) {
                    copyDirectoryContents(realChild, target, sourceRoot, activeDirectories);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(realChild, target,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        } finally {
            activeDirectories.remove(realDirectory);
        }
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
