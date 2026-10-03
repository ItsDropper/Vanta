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
     * <p>The locator deliberately does not trust the Java classpath alone.
     * In a jpackage installation the application classes live below the
     * {@code app} directory while Vanta.exe and the bundled runtime live
     * at the installation root. When Vanta is started normally, the actual
     * process command is the strongest signal and is checked first.</p>
     *
     * <p>Development launches (for example IntelliJ/Gradle) normally do not
     * have a packaged Vanta.exe/runtime pair, so they are rejected instead
     * of accidentally treating a source/build directory as the installed
     * launcher.</p>
     */
    public static Path getApplicationDirectory() {
        Path fromProcess = findFromProcessCommand();
        if (fromProcess != null) {
            return fromProcess;
        }

        Path fromCodeSource = findFromCodeSource();
        if (fromCodeSource != null) {
            return fromCodeSource;
        }

        throw new IllegalStateException(
                "Could not locate Vanta installation. "
                        + "Rollback and launcher self-update require a packaged Vanta installation."
        );
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

            if (!EXECUTABLE_NAME.equalsIgnoreCase(
                    executable.getFileName().toString()
            )) {
                return null;
            }

            return findInstallationRoot(executable.getParent());
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
         * The code source therefore starts below the root. Walking upward
         * is intentional, but every candidate must satisfy the complete
         * installation signature before it is accepted.
         */
        for (int depth = 0; current != null && depth < 12; depth++) {
            if (isInstallationRoot(current)) {
                return current;
            }

            current = current.getParent();
        }

        return null;
    }

    private static boolean isInstallationRoot(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return false;
        }

        Path executable = directory.resolve(EXECUTABLE_NAME);
        Path runtime = directory.resolve(RUNTIME_DIRECTORY);

        if (!Files.isRegularFile(executable)
                || !Files.isDirectory(runtime)) {
            return false;
        }

        /*
         * The bundled runtime is part of the install signature. Checking
         * java.exe prevents an unrelated folder containing a file named
         * Vanta.exe from being mistaken for the launcher installation.
         */
        Path javaExecutable =
                runtime.resolve("bin").resolve(
                        isWindows()
                                ? "java.exe"
                                : "java"
                );

        return Files.isRegularFile(javaExecutable);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
    }
}
