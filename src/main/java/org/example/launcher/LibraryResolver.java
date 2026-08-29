package org.example.launcher;

import org.example.launcher.model.Library;

import java.nio.file.Files;
import java.nio.file.Path;

public class LibraryResolver {

    public static Path resolve(
            Path minecraftDirectory,
            Library library
    ) {

        if (library == null) {
            throw new IllegalArgumentException(
                    "Library cannot be null."
            );
        }

        Path librariesDirectory =
                MinecraftLocator
                        .getLibrariesDirectory();

        // =========================================================
        // MOJANG LIBRARY FORMAT
        // =========================================================

        if (library.downloads != null
                && library.downloads.artifact != null) {

            String path =
                    library.downloads.artifact.path;

            if (path == null
                    || path.isBlank()) {

                return null;
            }

            Path jar =
                    librariesDirectory
                            .resolve(path);

            if (!Files.exists(jar)) {

                throw new IllegalStateException(
                        "Library not found: "
                                + jar
                );
            }

            return jar;
        }

        // =========================================================
        // FABRIC LIBRARY FORMAT
        // =========================================================

        if (library.name != null
                && !library.name.isBlank()) {

            String[] parts =
                    library.name.split(":");

            if (parts.length >= 3) {

                String group =
                        parts[0]
                                .replace('.', '/');

                String artifact =
                        parts[1];

                String version =
                        parts[2];

                Path jar =
                        librariesDirectory
                                .resolve(group)
                                .resolve(artifact)
                                .resolve(version)
                                .resolve(
                                        artifact
                                                + "-"
                                                + version
                                                + ".jar"
                                );

                if (!Files.exists(jar)) {

                    throw new IllegalStateException(
                            "Library not found: "
                                    + jar
                    );
                }

                return jar;
            }
        }

        return null;
    }

    public static Path resolveNative(
            Path minecraftDirectory,
            Library library
    ) {

        if (library == null
                || library.downloads == null
                || library.downloads.classifiers == null) {

            return null;
        }

        Library.Artifact nativeArtifact =
                library.downloads.classifiers.get(
                        "natives-windows"
                );

        if (nativeArtifact == null
                || nativeArtifact.path == null
                || nativeArtifact.path.isBlank()) {

            return null;
        }

        Path nativeJar =
                librariesDirectory()
                        .resolve(
                                nativeArtifact.path
                        );

        if (!Files.exists(nativeJar)) {

            throw new IllegalStateException(
                    "Native library not found: "
                            + nativeJar
            );
        }

        return nativeJar;
    }

    private static Path librariesDirectory() {

        return MinecraftLocator
                .getLibrariesDirectory();
    }
}
