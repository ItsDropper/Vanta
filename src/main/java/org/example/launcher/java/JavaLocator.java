package org.example.launcher.java;

import org.example.launcher.MinecraftLocator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JavaLocator {

    // =============================================================
    // GET JAVA
    // =============================================================

    public static String getJava(
            int requiredVersion
    ) throws Exception {

        if (requiredVersion <= 0) {
            throw new IllegalArgumentException(
                    "Invalid Java version: "
                            + requiredVersion
            );
        }

        /*
         * Minecraft 26.1+ requires Java 25.
         *
         * Always use Vanta's managed Java 25 instead of whatever
         * Java 25 happens to be installed on the machine.
         */
        if (requiredVersion == 25) {

            Path managed =
                    getManagedJava(requiredVersion);

            if (managed == null) {

                System.out.println(
                        "Vanta Java 25 not found. "
                                + "Installing managed Java 25..."
                );

                managed =
                        JavaInstaller.install(
                                requiredVersion
                        );
            }

            JavaVerifier.verify(
                    managed,
                    requiredVersion
            );

            System.out.println(
                    "Using Vanta-managed Java 25: "
                            + managed
            );

            return managed.toString();
        }

        /*
         * For older Java versions, first check the Java currently
         * running Vanta.
         */
        int currentVersion =
                getCurrentJavaVersion();

        if (currentVersion == requiredVersion) {

            Path java =
                    Path.of(
                                    System.getProperty(
                                            "java.home"
                                    )
                            )
                            .resolve("bin")
                            .resolve("java.exe");

            if (Files.isRegularFile(java)) {

                JavaVerifier.verify(
                        java,
                        requiredVersion
                );

                return java.toString();
            }
        }

        /*
         * Then check Vanta's own managed runtime.
         */
        Path managed =
                getManagedJava(
                        requiredVersion
                );

        if (managed != null) {

            System.out.println(
                    "Found Vanta Java "
                            + requiredVersion
                            + ": "
                            + managed
            );

            JavaVerifier.verify(
                    managed,
                    requiredVersion
            );

            return managed.toString();
        }

        /*
         * Then check Java installations already present on the
         * machine.
         */
        Path installed =
                findInstalledJava(
                        requiredVersion
                );

        if (installed != null) {

            System.out.println(
                    "Found installed Java "
                            + requiredVersion
                            + ": "
                            + installed
            );

            JavaVerifier.verify(
                    installed,
                    requiredVersion
            );

            return installed.toString();
        }

        /*
         * Nothing exists, so install it.
         */
        System.out.println(
                "Java "
                        + requiredVersion
                        + " not found."
        );

        Path installedJava =
                JavaInstaller.install(
                        requiredVersion
                );

        JavaVerifier.verify(
                installedJava,
                requiredVersion
        );

        return installedJava.toString();
    }

    // =============================================================
    // CURRENT JAVA
    // =============================================================

    private static int getCurrentJavaVersion() {

        return Runtime.version()
                .feature();
    }

    // =============================================================
    // MANAGED JAVA
    // =============================================================

    private static Path getManagedJava(
            int requiredVersion
    ) {

        Path javaDirectory =
                MinecraftLocator
                        .getVantaDirectory()
                        .resolve("java")
                        .resolve(
                                String.valueOf(
                                        requiredVersion
                                )
                        );

        if (!Files.isDirectory(javaDirectory)) {
            return null;
        }

        Path java =
                findJavaExecutable(
                        javaDirectory
                );

        if (java == null) {
            return null;
        }

        return java;
    }

    // =============================================================
    // FIND INSTALLED JAVA
    // =============================================================

    private static Path findInstalledJava(
            int requiredVersion
    ) {

        String programFiles =
                System.getenv("ProgramFiles");

        String javaHome =
                System.getenv("JAVA_HOME");

        Path[] locations = {

                javaHome != null
                        ? Path.of(javaHome)
                        : null,

                programFiles != null
                        ? Path.of(
                        programFiles,
                        "Java"
                )
                        : null,

                programFiles != null
                        ? Path.of(
                        programFiles,
                        "Eclipse Adoptium"
                )
                        : null,

                Path.of(
                        System.getProperty(
                                "user.home"
                        ),
                        ".jdks"
                )
        };

        for (Path location : locations) {

            if (location == null
                    || !Files.isDirectory(location)) {
                continue;
            }

            Path found =
                    searchDirectory(
                            location,
                            requiredVersion
                    );

            if (found != null) {
                return found;
            }
        }

        return null;
    }

    // =============================================================
    // SEARCH
    // =============================================================

    private static Path searchDirectory(
            Path directory,
            int requiredVersion
    ) {

        try {

            try (var stream =
                         Files.walk(
                                 directory,
                                 3
                         )) {

                return stream
                        .filter(Files::isDirectory)
                        .map(path ->
                                path.resolve("bin")
                                        .resolve("java.exe")
                        )
                        .filter(Files::isRegularFile)
                        .filter(path ->
                                isCorrectJavaVersion(
                                        path,
                                        requiredVersion
                                )
                        )
                        .findFirst()
                        .orElse(null);
            }

        } catch (IOException ignored) {

            return null;
        }
    }

    // =============================================================
    // VERSION CHECK
    // =============================================================

    private static boolean isCorrectJavaVersion(
            Path java,
            int requiredVersion
    ) {

        try {

            JavaVerifier.verify(
                    java,
                    requiredVersion
            );

            return true;

        } catch (Exception ignored) {

            return false;
        }
    }

    // =============================================================
    // FIND JAVA EXECUTABLE
    // =============================================================

    private static Path findJavaExecutable(
            Path directory
    ) {

        try {

            try (var stream =
                         Files.walk(
                                 directory,
                                 4
                         )) {

                return stream
                        .filter(Files::isRegularFile)
                        .filter(path ->
                                path.getFileName()
                                        .toString()
                                        .equalsIgnoreCase(
                                                "java.exe"
                                        )
                        )
                        .filter(path ->
                                path.getParent() != null
                                        && path.getParent()
                                        .getFileName()
                                        .toString()
                                        .equalsIgnoreCase(
                                                "bin"
                                        )
                        )
                        .findFirst()
                        .orElse(null);
            }

        } catch (IOException e) {

            return null;
        }
    }
}

