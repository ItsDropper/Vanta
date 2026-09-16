package org.example.launcher.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class UpdaterMain {

    public static void main(String[] args)
            throws Exception {

        if (args.length < 3 || args.length > 4) {
            System.err.println(
                    "Usage: UpdaterMain <pid> <zip> <application>"
            );
            System.exit(1);
        }

        long pid =
                Long.parseLong(args[0]);

        Path zip =
                Path.of(args[1])
                        .toAbsolutePath()
                        .normalize();

        Path applicationDirectory =
                Path.of(args[2])
                        .toAbsolutePath()
                        .normalize();


        waitForProcess(pid);

        if (!Files.isRegularFile(zip)) {
            throw new IOException(
                    "Update ZIP does not exist: " + zip
            );
        }

        Path parent =
                applicationDirectory.getParent();

        if (parent == null) {
            throw new IOException(
                    "Application directory has no parent."
            );
        }

        Path stagingDirectory =
                parent.resolve(
                        applicationDirectory.getFileName()
                                + ".update"
                );

        Path backupDirectory =
                parent.resolve(
                        applicationDirectory.getFileName()
                                + ".backup"
                );

        deleteDirectory(
                stagingDirectory
        );

        deleteDirectory(
                backupDirectory
        );

        Files.createDirectories(
                stagingDirectory
        );

        extractZip(
                zip,
                stagingDirectory
        );

        Path newExecutable =
                stagingDirectory.resolve(
                        "Vanta.exe"
                );

        if (!Files.isRegularFile(
                newExecutable
        )) {
            deleteDirectory(
                    stagingDirectory
            );

            throw new IOException(
                    "Update ZIP does not contain Vanta.exe."
            );
        }

        System.out.println(
                "Update extracted successfully."
        );

        moveApplication(
                applicationDirectory,
                stagingDirectory,
                backupDirectory
        );

        deleteDirectory(
                backupDirectory
        );

        Files.deleteIfExists(
                zip
        );

        System.out.println(
                "Vanta updated successfully."
        );

        startVanta(
                applicationDirectory
        );
    }

    private static void waitForProcess(
            long pid
    ) throws InterruptedException {

        while (ProcessHandle.of(pid).isPresent()) {
            TimeUnit.MILLISECONDS.sleep(250);
        }
    }

    private static void moveApplication(
            Path applicationDirectory,
            Path stagingDirectory,
            Path backupDirectory
    ) throws IOException {

        boolean backupCreated = false;

        try {

            Files.move(
                    applicationDirectory,
                    backupDirectory,
                    StandardCopyOption.ATOMIC_MOVE
            );

            backupCreated = true;


            Files.move(
                    stagingDirectory,
                    applicationDirectory,
                    StandardCopyOption.ATOMIC_MOVE
            );

        } catch (IOException error) {

            System.err.println(
                    "Update replacement failed."
            );

            if (backupCreated
                    && !Files.exists(
                    applicationDirectory
            )) {

                try {

                    Files.move(
                            backupDirectory,
                            applicationDirectory,
                            StandardCopyOption.ATOMIC_MOVE
                    );

                    System.err.println(
                            "Original Vanta installation restored."
                    );

                } catch (IOException rollbackError) {

                    error.addSuppressed(
                            rollbackError
                    );
                }
            }

            deleteDirectory(
                    stagingDirectory
            );

            throw error;
        }
    }

    private static void startVanta(
            Path applicationDirectory
    ) throws IOException {

        Path executable =
                applicationDirectory.resolve(
                        "Vanta.exe"
                );

        if (!Files.isRegularFile(executable)) {
            throw new IOException(
                    "Updated Vanta.exe was not found."
            );
        }

        new ProcessBuilder(
                executable.toString()
        )
                .directory(
                        applicationDirectory.toFile()
                )
                .start();
    }

    private static void extractZip(
            Path zip,
            Path destination
    ) throws IOException {

        Path normalizedDestination =
                destination
                        .toAbsolutePath()
                        .normalize();

        try (
                InputStream input =
                        Files.newInputStream(zip);

                ZipInputStream zipInput =
                        new ZipInputStream(input)
        ) {

            ZipEntry entry;

            while ((entry =
                    zipInput.getNextEntry()) != null) {

                Path output =
                        normalizedDestination
                                .resolve(
                                        entry.getName()
                                )
                                .normalize();

                if (!output.startsWith(
                        normalizedDestination
                )) {
                    throw new IOException(
                            "Unsafe ZIP entry: "
                                    + entry.getName()
                    );
                }

                if (entry.isDirectory()) {

                    Files.createDirectories(
                            output
                    );

                } else {

                    Path entryParent =
                            output.getParent();

                    if (entryParent != null) {
                        Files.createDirectories(
                                entryParent
                        );
                    }

                    Files.copy(
                            zipInput,
                            output,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }

                zipInput.closeEntry();
            }
        }
    }

    private static void deleteDirectory(
            Path directory
    ) throws IOException {

        if (!Files.exists(directory)) {
            return;
        }

        try (var paths =
                     Files.walk(directory)) {

            paths
                    .sorted(
                            Comparator.reverseOrder()
                    )
                    .forEach(path -> {

                        try {

                            Files.deleteIfExists(
                                    path
                            );

                        } catch (IOException e) {

                            throw new RuntimeException(
                                    e
                            );
                        }
                    });
        }
    }
}