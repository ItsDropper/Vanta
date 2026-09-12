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

        if (!isAllowedOnCurrentPlatform(library)) {
            return null;
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
                || !isAllowedOnCurrentPlatform(library)
                || library.downloads == null
                || library.downloads.classifiers == null
                || library.natives == null) {

            return null;
        }

        String nativeClassifier =
                library.natives.get("windows");

        if (nativeClassifier == null
                || nativeClassifier.isBlank()) {

            return null;
        }

        nativeClassifier =
                nativeClassifier.replace(
                        "${arch}",
                        getArchitecture()
                );

        Library.Artifact nativeArtifact =
                library.downloads.classifiers.get(
                        nativeClassifier
                );

        if (nativeArtifact == null) {

            throw new IllegalStateException(
                    "Native classifier not found: "
                            + nativeClassifier
                            + " for "
                            + library.name
            );
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
    // PLATFORM RULES
    // =============================================================

    private static boolean isAllowedOnCurrentPlatform(
            Library library
    ) {

        if (library.rules == null
                || library.rules.isEmpty()) {

            return true;
        }

        boolean hasAllowRule = false;
        boolean matchedAllowRule = false;

        for (Library.Rule rule :
                library.rules) {

            if (rule == null
                    || rule.action == null) {

                continue;
            }

            String action =
                    rule.action.trim();

            // -----------------------------------------------------
            // IMPORTANT:
            // A rule without an OS restriction matches every
            // platform.
            // -----------------------------------------------------

            boolean matches =
                    ruleMatchesCurrentPlatform(rule);

            if ("allow".equalsIgnoreCase(action)) {

                hasAllowRule = true;

                if (matches) {
                    matchedAllowRule = true;
                }

            } else if ("disallow".equalsIgnoreCase(action)) {

                if (matches) {
                    return false;
                }
            }
        }

        /*
         * Mojang's rule semantics:
         *
         * If allow rules exist, the library is only allowed when
         * at least one allow rule matches the current platform.
         */
        if (hasAllowRule) {
            return matchedAllowRule;
        }

        /*
         * If there are no allow rules, the library is allowed unless
         * a matching disallow rule rejected it above.
         */
        return true;
    }

    private static boolean ruleMatchesCurrentPlatform(
            Library.Rule rule
    ) {

        if (rule.os == null) {
            return true;
        }

        // ---------------------------------------------------------
        // OPERATING SYSTEM
        // ---------------------------------------------------------

        if (rule.os.name != null
                && !rule.os.name.isBlank()) {

            String currentOs =
                    getOperatingSystem();

            if (!rule.os.name.equalsIgnoreCase(
                    currentOs
            )) {

                return false;
            }
        }

        // ---------------------------------------------------------
        // ARCHITECTURE
        // ---------------------------------------------------------

        if (rule.os.arch != null
                && !rule.os.arch.isBlank()) {

            String currentArch =
                    System.getProperty(
                            "os.arch",
                            ""
                    );

            if (!matchesArchitecture(
                    rule.os.arch,
                    currentArch
            )) {

                return false;
            }
        }

        // ---------------------------------------------------------
        // VERSION
        // ---------------------------------------------------------

        if (rule.os.version != null
                && !rule.os.version.isBlank()) {

            String currentVersion =
                    System.getProperty(
                            "os.version",
                            ""
                    );

            if (!currentVersion.matches(
                    rule.os.version
            )) {

                return false;
            }
        }

        return true;
    }

    private static boolean matchesArchitecture(
            String required,
            String actual
    ) {

        String requiredNormalized =
                required
                        .toLowerCase()
                        .trim();

        String actualNormalized =
                actual
                        .toLowerCase()
                        .trim();

        if ("x86".equals(requiredNormalized)
                || "32".equals(requiredNormalized)
                || "x86_32".equals(requiredNormalized)) {

            return actualNormalized.equals("x86")
                    || actualNormalized.equals("i386")
                    || actualNormalized.equals("i486")
                    || actualNormalized.equals("i586")
                    || actualNormalized.equals("i686")
                    || actualNormalized.contains("32");
        }

        if ("x86_64".equals(requiredNormalized)
                || "amd64".equals(requiredNormalized)
                || "64".equals(requiredNormalized)) {

            return actualNormalized.equals("amd64")
                    || actualNormalized.equals("x86_64")
                    || actualNormalized.equals("x86-64")
                    || actualNormalized.contains("64");
        }

        return requiredNormalized.equals(
                actualNormalized
        );
    }

    private static String getOperatingSystem() {

        String os =
                System.getProperty(
                        "os.name",
                        ""
                ).toLowerCase();

        if (os.contains("win")) {
            return "windows";
        }

        if (os.contains("mac")
                || os.contains("darwin")) {

            return "osx";
        }

        if (os.contains("linux")) {
            return "linux";
        }

        return os;
    }

    private static String getArchitecture() {

        String arch =
                System.getProperty(
                        "os.arch",
                        ""
                ).toLowerCase();

        if (arch.equals("amd64")
                || arch.equals("x86_64")
                || arch.equals("x64")) {

            return "64";
        }

        return "32";
    }

    // =============================================================
    // PATH SAFETY
    // =============================================================

    private static Path resolveSafePath(
            Path baseDirectory,
            String relativePath,
            String description
    ) {

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