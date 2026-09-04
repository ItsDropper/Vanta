package org.example.launcher.java;

import org.example.launcher.MinecraftLocator;

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

public class JavaInstaller {

    private static final HttpClient HTTP =
            HttpClient.newBuilder()
                    .followRedirects(
                            HttpClient.Redirect.NORMAL
                    )
                    .build();

    // =============================================================
    // INSTALL JAVA
    // =============================================================

    public static Path install(
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
            JavaVerifier.verify(
                    existing,
                    version
            );

            return existing;
        }

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
         * works and is the expected Java version.
         */
        JavaVerifier.verify(
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
}