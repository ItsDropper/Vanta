package org.example.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JavaLocator {

    private static final HttpClient HTTP =
            HttpClient.newBuilder()
                    .followRedirects(
                            HttpClient.Redirect.NORMAL
                    )
                    .build();

    // =============================================================
    // GET JAVA
    // =============================================================

    public static String getJava(int requiredVersion)
            throws Exception {

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
                                    System.getProperty("java.home")
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
         * Finally use Vanta's own managed runtime.
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
         * Nothing exists, so download it.
         */
        System.out.println(
                "Java "
                        + requiredVersion
                        + " not found."
        );

        return downloadJava(
                requiredVersion
        ).toString();
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
    // DOWNLOAD JAVA
    // =============================================================

    private static Path downloadJava(
            int version
    ) throws Exception {

        Path javaDirectory =
                MinecraftLocator
                        .getVantaDirectory()
                        .resolve("java")
                        .resolve(
                                String.valueOf(version)
                        );

        Files.createDirectories(
                javaDirectory
        );

        Path existing =
                findJavaExecutable(
                        javaDirectory
                );

        if (existing != null) {
            return existing;
        }

        /*
         * Adoptium API:
         *
         * Windows
         * x64
         * JDK
         * HotSpot
         * Latest GA release
         */
        String url =
                "https://api.adoptium.net/v3/binary/latest/"
                        + version
                        + "/ga/windows/x64/jdk/hotspot/normal/eclipse";

        System.out.println(
                "Downloading Temurin Java "
                        + version
                        + "..."
        );

        Path zip =
                javaDirectory.resolve(
                        "java.zip"
                );

        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(url)
                        )
                        .GET()
                        .build();

        HttpResponse<InputStream> response =
                HTTP.send(
                        request,
                        HttpResponse.BodyHandlers
                                .ofInputStream()
                );

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {

            response.body().close();

            throw new IOException(
                    "Failed to download Java "
                            + version
                            + ". HTTP "
                            + response.statusCode()
            );
        }

        try (InputStream input =
                     response.body()) {

            Files.copy(
                    input,
                    zip,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }

        System.out.println(
                "Extracting Java "
                        + version
                        + "..."
        );

        extractZip(
                zip,
                javaDirectory
        );

        Files.deleteIfExists(
                zip
        );

        Path java =
                findJavaExecutable(
                        javaDirectory
                );

        if (java == null) {

            throw new IllegalStateException(
                    "Java "
                            + version
                            + " was downloaded, "
                            + "but java.exe could not "
                            + "be found."
            );
        }

        /*
         * Verify that the downloaded runtime actually
         * works before giving it to Minecraft.
         */
        verifyJava(
                java,
                version
        );

        System.out.println(
                "Java "
                        + version
                        + " installed successfully."
        );

        return java;
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

    // =============================================================
    // EXTRACT ZIP
    // =============================================================

    private static void extractZip(
            Path zipFile,
            Path destination
    ) throws IOException {

        try (ZipInputStream zip =
                     new ZipInputStream(
                             Files.newInputStream(
                                     zipFile
                             )
                     )) {

            ZipEntry entry;

            while ((entry =
                    zip.getNextEntry()) != null) {

                Path target =
                        destination.resolve(
                                entry.getName()
                        ).normalize();

                /*
                 * Prevent ZIP path traversal.
                 */
                if (!target.startsWith(
                        destination.normalize()
                )) {

                    throw new IOException(
                            "Unsafe ZIP entry: "
                                    + entry.getName()
                    );
                }

                if (entry.isDirectory()) {

                    Files.createDirectories(
                            target
                    );

                    continue;
                }

                Files.createDirectories(
                        target.getParent()
                );

                Files.copy(
                        zip,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        }
    }

    // =============================================================
    // VERIFY JAVA
    // =============================================================

    private static void verifyJava(
            Path java,
            int expectedVersion
    ) throws Exception {

        Process process =
                new ProcessBuilder(
                        java.toString(),
                        "-version"
                )
                        .redirectErrorStream(true)
                        .start();

        String output =
                new String(
                        process.getInputStream()
                                .readAllBytes()
                );

        int exitCode =
                process.waitFor();

        if (exitCode != 0) {

            throw new IllegalStateException(
                    "Downloaded Java failed verification:\n"
                            + output
            );
        }

        System.out.println(
                "Java verification successful:"
                        + "\n"
                        + output.trim()
        );
    }
}

