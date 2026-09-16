package org.example.launcher.update;

import java.nio.file.Files;
import java.nio.file.Path;

public final class UpdaterLauncher {

    private UpdaterLauncher() {
    }

    public static Path getUpdaterDirectory() {

        return Path.of(
                System.getenv("LOCALAPPDATA"),
                "Vanta",
                "updater"
        );
    }

    public static Path getUpdaterJar() {

        return getUpdaterDirectory()
                .resolve("VantaUpdater.jar");
    }

    public static void ensureDirectory()
            throws Exception {

        Files.createDirectories(
                getUpdaterDirectory()
        );
    }

    public static void start(
            long processId,
            Path updateZip,
            Path applicationDirectory
    ) throws Exception {

        Path updaterJar =
                getUpdaterJar();

        if (!Files.isRegularFile(updaterJar)) {
            throw new IllegalStateException(
                    "Vanta updater JAR was not found: "
                            + updaterJar
            );
        }

        if (!Files.isRegularFile(updateZip)) {
            throw new IllegalArgumentException(
                    "Update ZIP was not found: "
                            + updateZip
            );
        }

        if (!Files.isDirectory(applicationDirectory)) {
            throw new IllegalArgumentException(
                    "Vanta application directory was not found: "
                            + applicationDirectory
            );
        }

        Path javaExecutable =
                applicationDirectory
                        .resolve("runtime")
                        .resolve("bin")
                        .resolve("java.exe");

        if (!Files.isRegularFile(javaExecutable)) {
            throw new IllegalStateException(
                    "Vanta bundled Java runtime was not found: "
                            + javaExecutable
            );
        }

        new ProcessBuilder(
                javaExecutable.toString(),
                "-jar",
                updaterJar.toString(),
                Long.toString(processId),
                updateZip.toAbsolutePath().toString(),
                applicationDirectory.toAbsolutePath().toString()
        )
                .directory(
                        getUpdaterDirectory().toFile()
                )
                .start();
    }
}