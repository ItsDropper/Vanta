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
         * First check the Java currently running Vanta.
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

            if (Files.exists(java)) {
                return java.toString();
            }
        }

        /*
         * Then check Java installations already
         * present on the machine.
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

            return installed.toString();
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

            return managed.toString();
        }

        /*
         * Nothing exists, so install it.
         */
        System.out.println(
                "Java "
                        + requiredVersion
                        + " not found."
        );

        return JavaInstaller
                .install(
                        requiredVersion
                )
                .toString();
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

        return findJavaExecutable(
                javaDirectory
        );
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
                        .filter(path -> {

                            Path fileName =
                                    path.resolve(
                                            "bin"
                                    ).resolve(
                                            "java.exe"
                                    );

                            if (!Files.exists(fileName)) {
                                return false;
                            }

                            String name =
                                    path.getFileName()
                                            .toString();

                            return name.contains(
                                    String.valueOf(
                                            requiredVersion
                                    )
                            );
                        })
                        .map(path ->
                                path.resolve("bin")
                                        .resolve("java.exe")
                        )
                        .findFirst()
                        .orElse(null);
            }

        } catch (IOException ignored) {

            return null;
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