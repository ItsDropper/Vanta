package org.example.launcher.update;

import java.nio.file.Path;

public final class ApplicationLocator {

    private ApplicationLocator() {
    }

    public static Path getApplicationDirectory() {

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

            Path appDirectory =
                    location;

            while (appDirectory != null) {

                Path executable =
                        appDirectory.resolve(
                                "Vanta.exe"
                        );

                Path runtime =
                        appDirectory.resolve(
                                "runtime"
                        );

                if (java.nio.file.Files.isRegularFile(
                        executable
                ) && java.nio.file.Files.isDirectory(
                        runtime
                )) {

                    return appDirectory;
                }

                appDirectory =
                        appDirectory.getParent();
            }

            throw new IllegalStateException(
                    "Could not locate Vanta installation."
            );

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to locate Vanta installation.",
                    e
            );
        }
    }
}