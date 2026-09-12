package org.example.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;

import org.example.launcher.MinecraftLocator;
import org.example.launcher.model.Instance;

import java.nio.file.Files;
import java.nio.file.Path;

public class MinecraftFileInstaller {

    // =============================================================
    // CLIENT
    // =============================================================

    public static void installClient(
            Instance instance,
            JsonNode metadata
    ) throws Exception {

        JsonNode downloads =
                metadata.get("downloads");

        if (downloads == null) {
            throw new IllegalStateException(
                    "Minecraft downloads metadata is missing."
            );
        }

        JsonNode client =
                downloads.get("client");

        if (client == null) {
            throw new IllegalStateException(
                    "Minecraft client download is missing."
            );
        }

        String url =
                client
                        .get("url")
                        .asText();

        String sha1 =
                getSha1(client);

        Path minecraftDirectory =
                instance
                        .getDirectory()
                        .resolve("minecraft");

        Files.createDirectories(
                minecraftDirectory
        );

        Path target =
                minecraftDirectory.resolve(
                        "client.jar"
                );

        downloadIfNeeded(
                url,
                target,
                sha1
        );
    }

    // =============================================================
    // LIBRARIES
    // =============================================================

    public static void installLibraries(
            JsonNode metadata
    ) throws Exception {

        JsonNode libraries =
                metadata.get("libraries");

        if (libraries == null
                || !libraries.isArray()) {

            return;
        }

        Path librariesDirectory =
                MinecraftLocator
                        .getLibrariesDirectory();

        for (JsonNode library : libraries) {

            // -----------------------------------------------------
            // PLATFORM RULES
            // -----------------------------------------------------

            if (!isAllowedOnCurrentPlatform(library)) {
                continue;
            }

            JsonNode downloads =
                    library.get("downloads");

            if (downloads == null) {
                continue;
            }

            // -----------------------------------------------------
            // NORMAL JAR
            // -----------------------------------------------------

            JsonNode artifact =
                    downloads.get("artifact");

            if (artifact != null) {

                String url =
                        artifact
                                .get("url")
                                .asText();

                String path =
                        artifact
                                .get("path")
                                .asText();

                String sha1 =
                        getSha1(artifact);

                Path target =
                        librariesDirectory.resolve(
                                path
                        );

                downloadIfNeeded(
                        url,
                        target,
                        sha1
                );
            }

            // -----------------------------------------------------
            // NATIVES
            // -----------------------------------------------------

            JsonNode classifiers =
                    downloads.get("classifiers");

            JsonNode natives =
                    library.get("natives");

            if (classifiers == null
                    || natives == null
                    || !natives.isObject()) {

                continue;
            }

            JsonNode windowsClassifier =
                    natives.get("windows");

            if (windowsClassifier == null
                    || windowsClassifier.isNull()) {

                continue;
            }

            String classifier =
                    windowsClassifier.asText();

            if (classifier == null
                    || classifier.isBlank()) {

                continue;
            }

            classifier =
                    classifier.replace(
                            "${arch}",
                            getArchitecture()
                    );

            JsonNode nativeArtifact =
                    classifiers.get(
                            classifier
                    );

            if (nativeArtifact == null) {

                throw new IllegalStateException(
                        "Native classifier not found: "
                                + classifier
                                + " for "
                                + library
                                .path("name")
                                .asText()
                );
            }

            String url =
                    nativeArtifact
                            .get("url")
                            .asText();

            String path =
                    nativeArtifact
                            .get("path")
                            .asText();

            String sha1 =
                    getSha1(nativeArtifact);

            Path target =
                    librariesDirectory.resolve(
                            path
                    );

            downloadIfNeeded(
                    url,
                    target,
                    sha1
            );
        }
    }

    // =============================================================
    // PLATFORM RULES
    // =============================================================

    private static boolean isAllowedOnCurrentPlatform(
            JsonNode library
    ) {

        JsonNode rules =
                library.get("rules");

        if (rules == null
                || !rules.isArray()
                || rules.isEmpty()) {

            return true;
        }

        boolean hasAllowRule =
                false;

        boolean matchedAllowRule =
                false;

        for (JsonNode rule : rules) {

            if (rule == null
                    || !rule.isObject()) {

                continue;
            }

            String action =
                    rule
                            .path("action")
                            .asText();

            if (action.isBlank()) {
                continue;
            }

            boolean matches =
                    ruleMatchesCurrentPlatform(rule);

            if ("allow".equalsIgnoreCase(action)) {

                /*
                 * The existence of an allow rule means the library
                 * must match at least one allow rule.
                 *
                 * This must be set BEFORE checking whether the rule
                 * matches the current platform.
                 */
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
         * If allow rules exist, at least one of them must match.
         */
        if (hasAllowRule) {
            return matchedAllowRule;
        }

        /*
         * No allow rules means the library is allowed unless a
         * matching disallow rule rejected it above.
         */
        return true;
    }

    private static boolean ruleMatchesCurrentPlatform(
            JsonNode rule
    ) {

        JsonNode os =
                rule.get("os");

        if (os == null
                || !os.isObject()) {

            return true;
        }

        // ---------------------------------------------------------
        // OS NAME
        // ---------------------------------------------------------

        String requiredName =
                os
                        .path("name")
                        .asText();

        if (!requiredName.isBlank()) {

            String currentOs =
                    getOperatingSystem();

            if (!requiredName.equalsIgnoreCase(
                    currentOs
            )) {

                return false;
            }
        }

        // ---------------------------------------------------------
        // ARCHITECTURE
        // ---------------------------------------------------------

        String requiredArch =
                os
                        .path("arch")
                        .asText();

        if (!requiredArch.isBlank()) {

            String actualArch =
                    System.getProperty(
                            "os.arch",
                            ""
                    );

            if (!matchesArchitecture(
                    requiredArch,
                    actualArch
            )) {

                return false;
            }
        }

        // ---------------------------------------------------------
        // VERSION
        // ---------------------------------------------------------

        String requiredVersion =
                os
                        .path("version")
                        .asText();

        if (!requiredVersion.isBlank()) {

            String actualVersion =
                    System.getProperty(
                            "os.version",
                            ""
                    );

            if (!actualVersion.matches(
                    requiredVersion
            )) {

                return false;
            }
        }

        return true;
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

        if ("x86_64".equals(requiredNormalized)
                || "amd64".equals(requiredNormalized)
                || "64".equals(requiredNormalized)) {

            return actualNormalized.equals("amd64")
                    || actualNormalized.equals("x86_64")
                    || actualNormalized.equals("x86-64")
                    || actualNormalized.contains("64");
        }

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

        return requiredNormalized.equals(
                actualNormalized
        );
    }

    // =============================================================
    // DOWNLOAD / VERIFY
    // =============================================================

    private static void downloadIfNeeded(
            String url,
            Path target,
            String expectedSha1
    ) throws Exception {

        if (Files.exists(target)) {

            if (expectedSha1 == null
                    || expectedSha1.isBlank()) {

                return;
            }

            String actualSha1 =
                    calculateSha1(target);

            if (actualSha1.equalsIgnoreCase(
                    expectedSha1
            )) {

                return;
            }

            System.out.println(
                    "Checksum mismatch. Re-downloading: "
                            + target
            );

            Files.deleteIfExists(
                    target
            );
        }

        DownloadUtil.downloadFile(
                url,
                target,
                expectedSha1
        );
    }

    // =============================================================
    // SHA-1
    // =============================================================

    private static String calculateSha1(
            Path file
    ) throws Exception {

        java.security.MessageDigest digest =
                java.security.MessageDigest.getInstance(
                        "SHA-1"
                );

        try (java.io.InputStream input =
                     Files.newInputStream(file)) {

            byte[] buffer =
                    new byte[8192];

            int read;

            while ((read = input.read(buffer)) != -1) {

                digest.update(
                        buffer,
                        0,
                        read
                );
            }
        }

        byte[] hash =
                digest.digest();

        StringBuilder result =
                new StringBuilder(
                        hash.length * 2
                );

        for (byte value : hash) {

            result.append(
                    String.format(
                            "%02x",
                            value & 0xff
                    )
            );
        }

        return result.toString();
    }

    // =============================================================
    // METADATA
    // =============================================================

    private static String getSha1(
            JsonNode node
    ) {

        JsonNode sha1 =
                node.get("sha1");

        if (sha1 == null
                || sha1.isNull()) {

            return null;
        }

        String value =
                sha1.asText();

        if (value == null
                || value.isBlank()) {

            return null;
        }

        return value;
    }
}