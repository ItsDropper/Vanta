package org.example.launcher.update;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public final class ApplicationLocator {

    private static final String EXECUTABLE_NAME = "Vanta.exe";
    private static final String RUNTIME_DIRECTORY = "runtime";

    private ApplicationLocator() {
    }

    /**
     * Locates the root directory of the packaged Vanta installation.
     *
     * <p>jpackage runs Vanta from a private runtime located at
     * {@code <installation>/runtime}. That makes {@code java.home} a much
     * more reliable installation anchor than the process command, because
     * the process command may be java.exe/javaw.exe rather than Vanta.exe.</p>
     */
    public static Path getApplicationDirectory() {
        Path fromRuntime = findFromJavaHome();
        if (fromRuntime != null) {
            return fromRuntime;
        }

        Path fromProcess = findFromProcessCommand();
        if (fromProcess != null) {
            return fromProcess;
        }

        Path fromCodeSource = findFromCodeSource();
        if (fromCodeSource != null) {
            return fromCodeSource;
        }

        Path fromWorkingDirectory = findFromWorkingDirectory();
        if (fromWorkingDirectory != null) {
            return fromWorkingDirectory;
        }

        throw new IllegalStateException(
                "Could not locate Vanta installation. "
                        + "Rollback and launcher self update require a packaged Vanta installation."
        );
    }

    private static Path findFromJavaHome() {
        try {
            String javaHomeProperty =
                    System.getProperty("java.home");

            if (javaHomeProperty == null
                    || javaHomeProperty.isBlank()) {
                return null;
            }

            Path javaHome =
                    Path.of(javaHomeProperty)
                            .toAbsolutePath()
                            .normalize();

            /*
             * Packaged jpackage layout:
             *
             * Vanta/
             *   Vanta.exe
             *   runtime/
             *     bin/java.exe
             *
             * java.home therefore points directly at <Vanta>/runtime.
             */
            Path runtimeDirectory =
                    javaHome.getFileName() != null
                            && RUNTIME_DIRECTORY.equalsIgnoreCase(
                            javaHome.getFileName().toString()
                    )
                            ? javaHome
                            : null;

            if (runtimeDirectory == null) {
                return null;
            }

            return findInstallationRoot(
                    runtimeDirectory.getParent()
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Path findFromProcessCommand() {
        try {
            Optional<String> command =
                    ProcessHandle.current()
                            .info()
                            .command();

            if (command.isEmpty()) {
                return null;
            }

            Path executable =
                    Path.of(command.get())
                            .toAbsolutePath()
                            .normalize();

            Path start = executable.getParent();

            /*
             * The jpackage launcher can appear as Vanta.exe, java.exe,
             * or javaw.exe depending on how the application was started.
             * We therefore search from its directory rather than requiring
             * the process itself to be named Vanta.exe.
             */
            return findInstallationRoot(start);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Path findFromCodeSource() {
        try {
            Path location =
                    Path.of(
                                    ApplicationLocator.class
                                            .getProtectionDomain()
                                            .getCodeSource()
                                            .getLocation()
                                            .toURI()
                            )
                            .toAbsolutePath()
                            .normalize();

            Path start =
                    Files.isRegularFile(location)
                            ? location.getParent()
                            : location;

            return findInstallationRoot(start);
        } catch (URISyntaxException | RuntimeException ignored) {
            return null;
        }
    }

    private static Path findFromWorkingDirectory() {
        try {
            Path workingDirectory =
                    Path.of(
                            System.getProperty("user.dir", ".")
                    )
                    .toAbsolutePath()
                    .normalize();

            return findInstallationRoot(
                    workingDirectory
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Path findInstallationRoot(Path start) {
        Path current = start;

        /*
         * A normal jpackage layout is:
         *
         * Vanta/
         *   Vanta.exe
         *   runtime/
         *   app/
         *
         * The starting point may be runtime/bin, app, updater, or another
         * nested directory, so walk upward until the complete installation
         * signature is found.
         */
        for (int depth = 0;
             current != null && depth < 16;
             depth++) {

            if (isInstallationRoot(current)) {
                return current;
            }

            current = current.getParent();
        }

        return null;
    }

    private static boolean isInstallationRoot(Path directory) {
        if (directory == null
                || !Files.isDirectory(directory)) {
            return false;
        }

        Path executable =
                directory.resolve(
                        EXECUTABLE_NAME
                );

        Path runtime =
                directory.resolve(
                        RUNTIME_DIRECTORY
                );

        if (!Files.isRegularFile(executable)
                || !Files.isDirectory(runtime)) {
            return false;
        }

        /*
         * The bundled runtime is part of the install signature. Checking
         * both Java launchers makes this work with normal Windows jpackage
         * runtimes as well as layouts where only javaw.exe is present.
         */
        Path javaExecutable =
                runtime.resolve("bin").resolve("java.exe");

        Path javawExecutable =
                runtime.resolve("bin").resolve("javaw.exe");

        return Files.isRegularFile(javaExecutable)
                || Files.isRegularFile(javawExecutable);
    }
}
