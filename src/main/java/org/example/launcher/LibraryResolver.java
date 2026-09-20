package org.example.launcher;

import org.example.launcher.model.Library;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class LibraryResolver {

    private LibraryResolver() {
    }

    /**
     * Resolves a normal Minecraft library JAR.
     *
     * Supports:
     * - Modern Mojang downloads.artifact format
     * - Legacy Maven-coordinate format
     * - Libraries with platform rules
     * - Libraries whose artifact path is explicitly declared
     *
     * Returns null when the library is not applicable to this platform
     * or is a native-only library.
     */
    public static Path resolve(
            Path minecraftDirectory,
            Library library
    ) {
        if (library == null) {
            throw new IllegalArgumentException("Library cannot be null.");
        }

        if (!isAllowedOnCurrentPlatform(library)) {
            return null;
        }

        Path librariesDirectory = librariesDirectory();

        /*
         * Modern Mojang format:
         *
         * downloads:
         *   artifact:
         *     path: ...
         *
         * This is the authoritative path and should always be preferred.
         */
        if (library.downloads != null
                && library.downloads.artifact != null) {

            String path = library.downloads.artifact.path;

            if (path == null || path.isBlank()) {
                throw new IllegalStateException(
                        "Library artifact path is missing: "
                                + safeLibraryName(library)
                );
            }

            Path artifact = resolveSafePath(
                    librariesDirectory,
                    path,
                    "Library"
            );

            requireRegularFile(
                    artifact,
                    "Library"
            );

            return artifact;
        }

        /*
         * If the library has native classifiers but no normal artifact,
         * it is native-only and must not enter the normal classpath.
         */
        if (isNativeOnlyLibrary(library)) {
            return null;
        }

        /*
         * Legacy / Maven-style format.
         *
         * Used by older Minecraft versions and some third-party
         * libraries/loaders.
         */
        if (library.name != null && !library.name.isBlank()) {
            MavenCoordinate coordinate = parseCoordinate(library.name);

            Path artifact = librariesDirectory
                    .resolve(coordinate.groupPath)
                    .resolve(coordinate.artifact)
                    .resolve(coordinate.version)
                    .resolve(coordinate.fileName())
                    .normalize();

            requireInsideDirectory(
                    librariesDirectory,
                    artifact,
                    "Library"
            );

            requireRegularFile(
                    artifact,
                    "Library"
            );

            return artifact;
        }

        return null;
    }

    /**
     * Resolves the platform-specific native JAR.
     *
     * Supports:
     * - Old natives map + classifiers format
     * - ${arch} substitution
     * - Windows
     * - Linux
     * - macOS
     * - x86 / x86_64
     * - ARM32 / ARM64
     *
     * Newer Minecraft versions may not use the legacy natives map.
     * In that case this method attempts to identify an appropriate
     * native classifier directly from downloads.classifiers.
     */
    public static Path resolveNative(
            Path minecraftDirectory,
            Library library
    ) {
        if (library == null) {
            return null;
        }

        if (!isAllowedOnCurrentPlatform(library)) {
            return null;
        }

        if (library.downloads == null
                || library.downloads.classifiers == null
                || library.downloads.classifiers.isEmpty()) {
            return null;
        }

        Map<String, Library.Artifact> classifiers =
                library.downloads.classifiers;

        String osName = getOperatingSystem();
        String architecture = getArchitecture();

        /*
         * First handle the traditional Mojang format:
         *
         * natives:
         *   windows: natives-windows-${arch}
         */
        if (library.natives != null
                && !library.natives.isEmpty()) {

            String nativeClassifier =
                    findNativeClassifierFromMap(
                            library.natives,
                            osName
                    );

            if (nativeClassifier != null) {
                nativeClassifier =
                        substituteArchitecture(
                                nativeClassifier,
                                architecture
                        );

                Library.Artifact artifact =
                        classifiers.get(nativeClassifier);

                if (artifact != null) {
                    return resolveNativeArtifact(
                            artifact,
                            nativeClassifier
                    );
                }

                /*
                 * The manifest explicitly requested a classifier but
                 * it wasn't present. Do not silently select a random
                 * classifier.
                 */
                throw new IllegalStateException(
                        "Native classifier not found: "
                                + nativeClassifier
                                + " for "
                                + safeLibraryName(library)
                );
            }
        }

        /*
         * Newer manifests can have native classifiers without the
         * old natives map. Try to identify the current platform from
         * the classifier itself.
         */
        String detectedClassifier =
                findModernNativeClassifier(
                        classifiers,
                        osName,
                        architecture
                );

        if (detectedClassifier == null) {
            return null;
        }

        Library.Artifact artifact =
                classifiers.get(detectedClassifier);

        if (artifact == null) {
            return null;
        }

        return resolveNativeArtifact(
                artifact,
                detectedClassifier
        );
    }

    // =============================================================
    // NATIVE RESOLUTION
    // =============================================================

    private static Path resolveNativeArtifact(
            Library.Artifact artifact,
            String classifier
    ) {
        if (artifact.path == null
                || artifact.path.isBlank()) {

            throw new IllegalStateException(
                    "Native library path is missing for classifier: "
                            + classifier
            );
        }

        Path librariesDirectory = librariesDirectory();

        Path nativeJar = resolveSafePath(
                librariesDirectory,
                artifact.path,
                "Native library"
        );

        requireRegularFile(
                nativeJar,
                "Native library"
        );

        return nativeJar;
    }

    private static String findNativeClassifierFromMap(
            Map<String, String> natives,
            String currentOs
    ) {
        /*
         * Mojang normally uses:
         *
         * windows
         * linux
         * osx
         *
         * Keep aliases here for manifests generated by third-party
         * launchers/loaders.
         */
        String classifier = natives.get(currentOs);

        if (classifier != null && !classifier.isBlank()) {
            return classifier;
        }

        if ("osx".equals(currentOs)) {
            classifier = natives.get("macos");

            if (classifier != null && !classifier.isBlank()) {
                return classifier;
            }

            classifier = natives.get("mac");

            if (classifier != null && !classifier.isBlank()) {
                return classifier;
            }
        }

        return null;
    }

    private static String findModernNativeClassifier(
            Map<String, Library.Artifact> classifiers,
            String osName,
            String architecture
    ) {
        /*
         * We intentionally require the classifier to contain a known
         * native/platform identifier. This prevents accidentally
         * selecting an ordinary classifier such as "sources".
         */

        String[] osTokens;

        switch (osName) {
            case "windows" -> osTokens = new String[]{
                    "natives-windows",
                    "windows-natives"
            };

            case "linux" -> osTokens = new String[]{
                    "natives-linux",
                    "linux-natives"
            };

            case "osx" -> osTokens = new String[]{
                    "natives-osx",
                    "natives-macos",
                    "natives-mac",
                    "osx-natives",
                    "macos-natives"
            };

            default -> {
                return null;
            }
        }

        /*
         * Prefer an architecture-specific classifier.
         */
        for (String classifier : classifiers.keySet()) {
            if (matchesNativeClassifier(
                    classifier,
                    osTokens,
                    architecture
            )) {
                return classifier;
            }
        }

        /*
         * Some older libraries don't encode the architecture.
         */
        for (String classifier : classifiers.keySet()) {
            if (matchesNativeClassifier(
                    classifier,
                    osTokens,
                    null
            )) {
                return classifier;
            }
        }

        return null;
    }

    private static boolean matchesNativeClassifier(
            String classifier,
            String[] osTokens,
            String architecture
    ) {
        if (classifier == null || classifier.isBlank()) {
            return false;
        }

        String normalized =
                classifier.toLowerCase(Locale.ROOT);

        boolean osMatches = false;

        for (String token : osTokens) {
            if (normalized.contains(
                    token.toLowerCase(Locale.ROOT)
            )) {
                osMatches = true;
                break;
            }
        }

        if (!osMatches) {
            return false;
        }

        if (architecture == null) {
            return true;
        }

        /*
         * Architecture-specific native classifiers commonly use:
         *
         * 32
         * 64
         * x86
         * x86_64
         * amd64
         * arm32
         * arm64
         * aarch64
         */
        if ("64".equals(architecture)) {
            return normalized.contains("64")
                    || normalized.contains("x86_64")
                    || normalized.contains("amd64");
        }

        if ("32".equals(architecture)) {
            return normalized.contains("32")
                    || normalized.contains("x86");
        }

        if ("arm64".equals(architecture)) {
            return normalized.contains("arm64")
                    || normalized.contains("aarch64");
        }

        if ("arm32".equals(architecture)) {
            return normalized.contains("arm32")
                    || normalized.contains("arm");
        }

        return true;
    }

    private static String substituteArchitecture(
            String classifier,
            String architecture
    ) {
        if (classifier == null) {
            return null;
        }

        /*
         * Mojang commonly uses ${arch} with:
         *
         * 32
         * 64
         *
         * Keep those values for compatibility.
         */
        if ("64".equals(architecture)) {
            return classifier.replace(
                    "${arch}",
                    "64"
            );
        }

        if ("32".equals(architecture)) {
            return classifier.replace(
                    "${arch}",
                    "32"
            );
        }

        /*
         * ARM manifests sometimes don't use ${arch}; if they do,
         * prefer the conventional ARM64/ARM32 names.
         */
        return classifier.replace(
                "${arch}",
                architecture
        );
    }

    private static boolean isNativeOnlyLibrary(
            Library library
    ) {
        return library.downloads != null
                && library.downloads.artifact == null
                && library.downloads.classifiers != null
                && !library.downloads.classifiers.isEmpty()
                && library.natives != null
                && !library.natives.isEmpty();
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

        for (Library.Rule rule : library.rules) {
            if (rule == null
                    || rule.action == null
                    || rule.action.isBlank()) {
                continue;
            }

            boolean matches =
                    ruleMatchesCurrentPlatform(rule);

            String action =
                    rule.action.trim()
                            .toLowerCase(Locale.ROOT);

            if ("allow".equals(action)) {
                hasAllowRule = true;

                if (matches) {
                    matchedAllowRule = true;
                }

                continue;
            }

            if ("disallow".equals(action)
                    && matches) {
                return false;
            }
        }

        /*
         * If any allow rules exist, at least one must match.
         */
        if (hasAllowRule) {
            return matchedAllowRule;
        }

        /*
         * No allow rules means the library is allowed unless a
         * matching disallow rule rejected it.
         */
        return true;
    }

    private static boolean ruleMatchesCurrentPlatform(
            Library.Rule rule
    ) {
        if (rule.os == null) {
            return true;
        }

        if (rule.os.name != null
                && !rule.os.name.isBlank()) {

            String currentOs =
                    getOperatingSystem();

            if (!rule.os.name
                    .trim()
                    .equalsIgnoreCase(currentOs)) {
                return false;
            }
        }

        if (rule.os.arch != null
                && !rule.os.arch.isBlank()) {

            String actualArch =
                    System.getProperty(
                            "os.arch",
                            ""
                    );

            if (!matchesArchitecture(
                    rule.os.arch,
                    actualArch
            )) {
                return false;
            }
        }

        if (rule.os.version != null
                && !rule.os.version.isBlank()) {

            String currentVersion =
                    System.getProperty(
                            "os.version",
                            ""
                    );

            if (!matchesVersion(
                    rule.os.version,
                    currentVersion
            )) {
                return false;
            }
        }

        return true;
    }

    private static boolean matchesVersion(
            String expression,
            String actualVersion
    ) {
        try {
            return Pattern
                    .compile(expression)
                    .matcher(actualVersion)
                    .matches();

        } catch (RuntimeException ignored) {
            /*
             * A malformed third-party regex should not crash the
             * entire launcher. Treat it as non-matching.
             */
            return false;
        }
    }

    // =============================================================
    // OPERATING SYSTEM
    // =============================================================

    private static String getOperatingSystem() {
        String os =
                System.getProperty(
                        "os.name",
                        ""
                ).toLowerCase(Locale.ROOT);

        if (os.contains("win")) {
            return "windows";
        }

        if (os.contains("mac")
                || os.contains("darwin")) {
            return "osx";
        }

        if (os.contains("linux")
                || os.contains("unix")) {
            return "linux";
        }

        return os;
    }

    // =============================================================
    // ARCHITECTURE
    // =============================================================

    private static String getArchitecture() {
        String arch =
                System.getProperty(
                                "os.arch",
                                ""
                        ).toLowerCase(Locale.ROOT)
                        .trim();

        return switch (arch) {
            case "amd64",
                 "x86_64",
                 "x86-64",
                 "x64" -> "64";

            case "x86",
                 "i386",
                 "i486",
                 "i586",
                 "i686",
                 "x86_32",
                 "x32" -> "32";

            case "aarch64",
                 "arm64",
                 "armv8",
                 "armv8l" -> "arm64";

            case "arm",
                 "arm32",
                 "armv7",
                 "armv7l",
                 "armv6",
                 "armv6l" -> "arm32";

            default -> arch;
        };
    }

    private static boolean matchesArchitecture(
            String required,
            String actual
    ) {
        if (required == null
                || actual == null) {
            return false;
        }

        String requiredNormalized =
                required
                        .toLowerCase(Locale.ROOT)
                        .trim();

        String actualNormalized =
                actual
                        .toLowerCase(Locale.ROOT)
                        .trim();

        /*
         * x86 / 32-bit
         */
        if ("x86".equals(requiredNormalized)
                || "x86_32".equals(requiredNormalized)
                || "32".equals(requiredNormalized)) {

            return actualNormalized.equals("x86")
                    || actualNormalized.equals("i386")
                    || actualNormalized.equals("i486")
                    || actualNormalized.equals("i586")
                    || actualNormalized.equals("i686")
                    || actualNormalized.equals("x86_32")
                    || actualNormalized.equals("32");
        }

        /*
         * x86_64 / 64-bit
         */
        if ("x86_64".equals(requiredNormalized)
                || "amd64".equals(requiredNormalized)
                || "x64".equals(requiredNormalized)
                || "64".equals(requiredNormalized)) {

            return actualNormalized.equals("amd64")
                    || actualNormalized.equals("x86_64")
                    || actualNormalized.equals("x86-64")
                    || actualNormalized.equals("x64")
                    || actualNormalized.equals("64");
        }

        /*
         * ARM64
         */
        if ("aarch64".equals(requiredNormalized)
                || "arm64".equals(requiredNormalized)) {

            return actualNormalized.equals("aarch64")
                    || actualNormalized.equals("arm64")
                    || actualNormalized.equals("armv8")
                    || actualNormalized.equals("armv8l");
        }

        /*
         * ARM32
         */
        if ("arm".equals(requiredNormalized)
                || "arm32".equals(requiredNormalized)) {

            return actualNormalized.equals("arm")
                    || actualNormalized.equals("arm32")
                    || actualNormalized.equals("armv7")
                    || actualNormalized.equals("armv7l")
                    || actualNormalized.equals("armv6")
                    || actualNormalized.equals("armv6l");
        }

        return requiredNormalized.equals(
                actualNormalized
        );
    }

    // =============================================================
    // PATH SAFETY
    // =============================================================

    private static Path librariesDirectory() {
        return MinecraftLocator
                .getLibrariesDirectory()
                .toAbsolutePath()
                .normalize();
    }

    private static Path resolveSafePath(
            Path baseDirectory,
            String relativePath,
            String description
    ) {
        if (relativePath == null
                || relativePath.isBlank()) {

            throw new IllegalStateException(
                    description + " path is empty."
            );
        }

        /*
         * Minecraft artifact paths are slash-separated relative
         * paths. Reject Windows separators and absolute paths before
         * resolving them.
         */
        if (relativePath.contains("\\")
                || relativePath.startsWith("/")
                || relativePath.startsWith("\\")
                || isWindowsAbsolutePath(relativePath)) {

            throw new IllegalStateException(
                    "Unsafe "
                            + description.toLowerCase(Locale.ROOT)
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

    private static boolean isWindowsAbsolutePath(
            String path
    ) {
        return path.length() >= 2
                && Character.isLetter(path.charAt(0))
                && path.charAt(1) == ':';
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
        String coordinate = name.trim();

        /*
         * Maven coordinates normally look like:
         *
         * group:artifact:version
         * group:artifact:version:classifier
         */
        String[] parts =
                coordinate.split(":", -1);

        if (parts.length < 3
                || parts.length > 4) {

            throw new IllegalStateException(
                    "Invalid library Maven coordinate: "
                            + name
            );
        }

        String group = parts[0].trim();
        String artifact = parts[1].trim();
        String version = parts[2].trim();

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

        validateCoordinatePart(
                group,
                "group",
                name
        );

        String groupPath =
                group.replace('.', '/');

        validateCoordinatePart(
                artifact,
                "artifact",
                name
        );

        validateCoordinatePart(
                version,
                "version",
                name
        );

        if (classifier != null
                && !classifier.isBlank()) {

            validateCoordinatePart(
                    classifier,
                    "classifier",
                    name
            );
        }

        return new MavenCoordinate(
                groupPath,
                artifact,
                version,
                classifier
        );
    }

    private static void validateCoordinatePart(
            String value,
            String type,
            String originalCoordinate
    ) {
        if (value == null
                || value.isBlank()
                || value.equals(".")
                || value.equals("..")
                || value.contains("/")
                || value.contains("\\")
                || value.indexOf('\0') >= 0) {

            throw new IllegalStateException(
                    "Unsafe library Maven "
                            + type
                            + " in coordinate: "
                            + originalCoordinate
            );
        }
    }

    private static String safeLibraryName(
            Library library
    ) {
        if (library.name == null
                || library.name.isBlank()) {
            return "<unnamed library>";
        }

        return library.name;
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
            this.groupPath = groupPath;
            this.artifact = artifact;
            this.version = version;
            this.classifier = classifier;
        }

        private String fileName() {
            StringBuilder file =
                    new StringBuilder();

            file.append(artifact)
                    .append('-')
                    .append(version);

            if (classifier != null
                    && !classifier.isBlank()) {

                file.append('-')
                        .append(classifier);
            }

            file.append(".jar");

            return file.toString();
        }
    }
}