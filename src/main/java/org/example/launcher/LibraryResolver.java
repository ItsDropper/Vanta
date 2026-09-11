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
                        .getLibrariesDirectory()
                        .toAbsolutePath()
                        .normalize();

        // =========================================================
        // MOJANG LIBRARY FORMAT
        // =========================================================

        if (library.downloads != null
                && library.downloads.artifact != null) {

            String path =
                    library.downloads.artifact.path;

            if (path == null
                    || path.isBlank()) {

                throw new IllegalStateException(
                        "Library artifact path is missing."
                );
            }

            Path jar =
                    resolveSafePath(
                            librariesDirectory,
                            path,
                            "Library"
                    );

            requireRegularFile(
                    jar,
                    "Library"
            );

            return jar;
        }

        // =========================================================
        // NATIVES-ONLY LIBRARY
        // =========================================================

        if (library.downloads != null
                && library.downloads.artifact == null
                && library.downloads.classifiers != null
                && library.natives != null
                && !library.natives.isEmpty()) {

            /*
             * This library only provides native classifiers.
             * Its native JAR is handled separately by resolveNative().
             */
            return null;
        }

        // =========================================================
        // MAVEN / FABRIC LIBRARY FORMAT
        // =========================================================

        if (library.name != null
                && !library.name.isBlank()) {

            MavenCoordinate coordinate =
                    parseCoordinate(
                            library.name
                    );

            Path jar =
                    librariesDirectory
                            .resolve(
                                    coordinate.groupPath
                            )
                            .resolve(
                                    coordinate.artifact
                            )
                            .resolve(
                                    coordinate.version
                            )
                            .resolve(
                                    coordinate.fileName()
                            )
                            .normalize();

            requireInsideDirectory(
                    librariesDirectory,
                    jar,
                    "Library"
            );

            requireRegularFile(
                    jar,
                    "Library"
            );

            return jar;
        }

        /*
         * There is no usable classpath artifact.
         *
         * Do not silently manufacture a path from incomplete
         * metadata.
         */
        return null;
    }

    // =============================================================
    // NATIVE
    // =============================================================

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

        if (nativeArtifact == null) {
            return null;
        }

        String path =
                nativeArtifact.path;

        if (path == null
                || path.isBlank()) {

            throw new IllegalStateException(
                    "Native library path is missing."
            );
        }

        Path librariesDirectory =
                librariesDirectory();

        Path nativeJar =
                resolveSafePath(
                        librariesDirectory,
                        path,
                        "Native library"
                );

        requireRegularFile(
                nativeJar,
                "Native library"
        );

        return nativeJar;
    }

    // =============================================================
    // PATH SAFETY
    // =============================================================

    private static Path resolveSafePath(
            Path baseDirectory,
            String relativePath,
            String description
    ) {

        /*
         * Manifest paths should always use forward slashes.
         * Reject Windows separators as well so a malformed
         * manifest cannot escape through platform-specific paths.
         */
        if (relativePath.contains("\\")
                || relativePath.startsWith("/")
                || relativePath.startsWith("\\")) {

            throw new IllegalStateException(
                    "Unsafe "
                            + description.toLowerCase()
                            + " path: "
                            + relativePath
            );
        }

        Path resolved =
                baseDirectory
                        .resolve(relativePath)
                        .normalize();

        requireInsideDirectory(
                baseDirectory,
                resolved,
                description
        );

        return resolved;
    }

    private static void requireInsideDirectory(
            Path baseDirectory,
            Path path,
            String description
    ) {

        if (!path.startsWith(baseDirectory)) {

            throw new IllegalStateException(
                    description
                            + " path escapes libraries directory: "
                            + path
            );
        }
    }

    private static void requireRegularFile(
            Path path,
            String description
    ) {

        if (!Files.isRegularFile(path)) {

            throw new IllegalStateException(
                    description
                            + " not found or is not a regular file: "
                            + path
            );
        }
    }

    // =============================================================
    // MAVEN COORDINATES
    // =============================================================

    private static MavenCoordinate parseCoordinate(
            String name
    ) {

        String[] parts =
                name.trim()
                        .split(":");

        if (parts.length < 3
                || parts.length > 4) {

            throw new IllegalStateException(
                    "Invalid library Maven coordinate: "
                            + name
            );
        }

        String group =
                parts[0].trim();

        String artifact =
                parts[1].trim();

        String version =
                parts[2].trim();

        String classifier =
                parts.length == 4
                        ? parts[3].trim()
                        : null;

        if (group.isBlank()
                || artifact.isBlank()
                || version.isBlank()) {

            throw new IllegalStateException(
                    "Invalid library Maven coordinate: "
                            + name
            );
        }

        String groupPath =
                group.replace(
                        '.',
                        '/'
                );

        /*
         * Prevent malformed coordinates from creating paths outside
         * the shared libraries directory.
         */
        if (groupPath.startsWith("/")
                || groupPath.contains("..")
                || artifact.contains("/")
                || artifact.contains("\\")
                || artifact.contains("..")
                || version.contains("/")
                || version.contains("\\")
                || version.contains("..")
                || (classifier != null
                && (classifier.contains("/")
                || classifier.contains("\\")
                || classifier.contains("..")))) {

            throw new IllegalStateException(
                    "Unsafe library Maven coordinate: "
                            + name
            );
        }

        return new MavenCoordinate(
                groupPath,
                artifact,
                version,
                classifier
        );
    }

    // =============================================================
    // LIBRARIES DIRECTORY
    // =============================================================

    private static Path librariesDirectory() {

        return MinecraftLocator
                .getLibrariesDirectory()
                .toAbsolutePath()
                .normalize();
    }

    // =============================================================
    // MAVEN COORDINATE
    // =============================================================

    private static final class MavenCoordinate {

        private final String groupPath;
        private final String artifact;
        private final String version;
        private final String classifier;

        private MavenCoordinate(
                String groupPath,
                String artifact,
                String version,
                String classifier
        ) {

            this.groupPath =
                    groupPath;

            this.artifact =
                    artifact;

            this.version =
                    version;

            this.classifier =
                    classifier;
        }

        private String fileName() {

            StringBuilder file =
                    new StringBuilder();

            file.append(
                    artifact
            );

            file.append(
                    "-"
            );

            file.append(
                    version
            );

            if (classifier != null
                    && !classifier.isBlank()) {

                file.append(
                        "-"
                );

                file.append(
                        classifier
                );
            }

            file.append(
                    ".jar"
            );

            return file.toString();
        }
    }
}