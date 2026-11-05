package dev.itsdropper.vanta.launcher.java;

import dev.itsdropper.vanta.launcher.MinecraftLocator;
import dev.itsdropper.vanta.launcher.Platform;

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

        String os = Platform.adoptiumOs();
        String architecture = Platform.adoptiumArchitecture();
        String url =
                "https://api.adoptium.net/v3/binary/latest/"
                        + version
                        + "/ga/" + os + "/" + architecture + "/jdk/hotspot/normal/eclipse";

        System.out.println(
                "Downloading Temurin Java "
                        + version
                        + "..."
        );

        boolean windows = Platform.operatingSystem() == Platform.OperatingSystem.WINDOWS;
        Path archive = javaDirectory.resolve(windows ? "java.zip" : "java.tar.gz");

        dev.itsdropper.vanta.launcher.instance.DownloadUtil.downloadFile(url, archive, null);

        System.out.println(
                "Extracting Java "
                        + version
                        + "..."
        );

        if (windows) {
            extractZip(archive, javaDirectory);
        } else {
            extractTarGz(archive, javaDirectory);
        }

        Files.deleteIfExists(archive);

        Path java =
                findJavaExecutable(
                        javaDirectory
                );

        if (java == null) {

            throw new IllegalStateException(
                    "Java "
                            + version
                            + " was downloaded, "
                            + "but the Java executable could not "
                            + "be found."
            );
        }

        /*
         * Verify that the downloaded runtime actually works and is
         * the expected Java version.
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
                                 6
                         )) {

                return stream
                        .filter(Files::isRegularFile)
                        .filter(path ->
                                path.getFileName()
                                        .toString()
                                        .equalsIgnoreCase(
.equalsIgnoreCase(
                                                Platform.javaExecutableName()
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
    // EXTRACT TAR.GZ (Linux/macOS)
    // =============================================================

    private static void extractTarGz(Path archive, Path destination) throws IOException {
        Process listing = new ProcessBuilder("tar", "-tzf", archive.toString())
                .redirectErrorStream(true).start();
        String entries;
        try (InputStream input = listing.getInputStream()) {
            entries = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        try {
            if (listing.waitFor() != 0) {
                throw new IOException("The downloaded Java archive could not be inspected.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while inspecting the Java archive.", interrupted);
        }

        Path root = destination.toAbsolutePath().normalize();
        for (String entry : entries.split("\\R")) {
            if (entry.isBlank()) continue;
            Path resolved = root.resolve(entry).normalize();
            if (Path.of(entry).isAbsolute() || !resolved.startsWith(root)) {
                throw new IOException("Unsafe Java archive entry rejected.");
            }
        }

        Process extraction = new ProcessBuilder(
                "tar", "-xzf", archive.toString(), "-C", root.toString())
                .redirectErrorStream(true).start();
        String output;
        try (InputStream input = extraction.getInputStream()) {
            output = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        try {
            if (extraction.waitFor() != 0) {
                throw new IOException("Java archive extraction failed: "
                        + (output.isBlank() ? "tar returned a non-zero exit code." : output));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while extracting the Java archive.", interrupted);
        }
    }

    // =============================================================
    // EXTRACT ZIP
    // =============================================================

    private static void extractZip(
            Path zipFile,
            Path destination
    ) throws IOException {

        Path normalizedDestination =
                destination
                        .toAbsolutePath()
                        .normalize();

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
                        normalizedDestination
                                .resolve(
                                        entry.getName()
                                )
                                .normalize();

                /*
                 * Prevent ZIP path traversal.
                 */
                if (!target.startsWith(
                        normalizedDestination
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

                if (target.getParent() != null) {

                    Files.createDirectories(
                            target.getParent()
                    );
                }

                Files.copy(
                        zip,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        }
    }
}

