package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;

import dev.itsdropper.vanta.launcher.MinecraftLocator;
import dev.itsdropper.vanta.launcher.Platform;
import dev.itsdropper.vanta.launcher.LibraryResolver;
import dev.itsdropper.vanta.launcher.model.Library;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import dev.itsdropper.vanta.ui.LauncherSettings;

public class MinecraftFileInstaller {

    private static final ObjectMapper LIBRARY_JSON = new ObjectMapper();

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

        JsonNode libraries = metadata.get("libraries");
        if (libraries == null || !libraries.isArray()) {
            return;
        }

        List<LibraryDownload> downloads = new ArrayList<>();

        for (JsonNode library : libraries) {
            if (!isAllowedOnCurrentPlatform(library)) {
                continue;
            }

            JsonNode libraryDownloads = library.get("downloads");
            if (libraryDownloads == null) {
                continue;
            }

            JsonNode artifact = libraryDownloads.get("artifact");
            String artifactPath = artifact == null
                    ? "" : artifact.path("path").asText("");
            if (!artifactPath.isBlank()) {
                downloads.add(new LibraryDownload(
                        artifact.path("url").asText(),
                        resolveLibraryPath(artifactPath),
                        getSha1(artifact)
                ));
            } else if (artifact != null) {
                JsonNode classifiers = libraryDownloads.get("classifiers");
                if (classifiers == null || !classifiers.isObject() || classifiers.isEmpty()) {
                    throw new IllegalStateException(
                            "Minecraft library artifact metadata has no path and no native classifiers: "
                                    + library.path("name").asText("<unnamed library>"));
                }
                // Native-only legacy metadata can contain an empty artifact
                // object. It is not a downloadable JAR; resolve its classifier below.
            }

            Library libraryModel = LIBRARY_JSON.treeToValue(library, Library.class);
            Library.Artifact nativeArtifact = LibraryResolver.findNativeArtifact(libraryModel);
            if (nativeArtifact != null) {
                if (nativeArtifact.path == null || nativeArtifact.path.isBlank()
                        || nativeArtifact.url == null || nativeArtifact.url.isBlank()) {
                    throw new IllegalStateException(
                            "Minecraft native classifier metadata is incomplete for "
                                    + library.path("name").asText("<unnamed library>"));
                }
                downloads.add(new LibraryDownload(
                        nativeArtifact.url,
                        resolveLibraryPath(nativeArtifact.path),
                        nativeArtifact.sha1
                ));
            }
        }

        if (downloads.isEmpty()) {
            return;
        }

        int workers = Math.min(
                LauncherSettings.getEffectiveDownloadThreads(downloads.size()),
                downloads.size()
        );

        System.out.println(
                "Installing " + downloads.size()
                        + " Minecraft libraries using "
                        + workers + " workers..."
        );

        ExecutorService executor =
                Executors.newFixedThreadPool(Math.max(1, workers));

        try {
            List<Future<?>> futures = new ArrayList<>(downloads.size());

            for (LibraryDownload download : downloads) {
                futures.add(executor.submit(() -> {
                    downloadIfNeeded(
                            download.url(),
                            download.target(),
                            download.sha1()
                    );
                    return null;
                }));
            }

            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static Path resolveLibraryPath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalStateException("Minecraft library artifact path is missing.");
        }
        Path root = MinecraftLocator.getLibrariesDirectory().toAbsolutePath().normalize();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalStateException("Minecraft library artifact path escapes the library directory.");
        }
        return target;
    }

    private record LibraryDownload(
            String url,
            Path target,
            String sha1
    ) {
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
        return switch (Platform.operatingSystem()) {
            case WINDOWS -> "windows";
            case LINUX -> "linux";
            case MACOS -> "osx";
            case UNKNOWN -> throw new IllegalStateException(
                    "Unsupported Minecraft host operating system: "
                            + System.getProperty("os.name", "unknown"));
        };
    }


    static boolean matchesArchitecture(
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
                || "x64".equals(requiredNormalized)) {
            return actualNormalized.equals("amd64")
                    || actualNormalized.equals("x86_64")
                    || actualNormalized.equals("x86-64")
                    || actualNormalized.equals("x64");
        }

        if ("64".equals(requiredNormalized)) {
            return actualNormalized.equals("amd64") || actualNormalized.equals("x86_64")
                    || actualNormalized.equals("x86-64") || actualNormalized.equals("x64")
                    || actualNormalized.equals("aarch64") || actualNormalized.equals("arm64")
                    || actualNormalized.equals("armv8") || actualNormalized.equals("armv8l")
                    || actualNormalized.equals("64");
        }

        if ("aarch64".equals(requiredNormalized) || "arm64".equals(requiredNormalized)) {
            return actualNormalized.equals("aarch64") || actualNormalized.equals("arm64")
                    || actualNormalized.equals("armv8") || actualNormalized.equals("armv8l");
        }

        if ("arm".equals(requiredNormalized) || "arm32".equals(requiredNormalized)) {
            return actualNormalized.equals("arm") || actualNormalized.equals("arm32")
                    || actualNormalized.equals("armv7") || actualNormalized.equals("armv7l")
                    || actualNormalized.equals("armv6") || actualNormalized.equals("armv6l");
        }

        if ("x86".equals(requiredNormalized) || "32".equals(requiredNormalized)
                || "x86_32".equals(requiredNormalized)) {
            return actualNormalized.equals("x86") || actualNormalized.equals("i386")
                    || actualNormalized.equals("i486") || actualNormalized.equals("i586")
                    || actualNormalized.equals("i686") || actualNormalized.equals("x86_32")
                    || actualNormalized.equals("x32") || actualNormalized.equals("32");
        }

        return requiredNormalized.equals(actualNormalized);
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

            // Keep the previous file until the replacement has been downloaded
            // and verified. DownloadUtil publishes its temporary file atomically.
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