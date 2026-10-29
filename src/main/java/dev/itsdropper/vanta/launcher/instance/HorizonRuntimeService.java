package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.MinecraftLocator;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Experimental bridge to Horizon's documented command-line interface.
 * A Vanta profile is mapped to an isolated Horizon profile using a stable ID.
 */
public final class HorizonRuntimeService {

    private static final String RELEASES_API =
            "https://api.github.com/repos/horizon-rust/horizon/releases/latest";
    private static final String WINDOWS_ASSET =
            "horizon-launcher-windows-x64.zip";
    private static final long MAX_DOWNLOAD_BYTES = 200L * 1024 * 1024;
    private static final long MAX_EXTRACTED_BYTES = 512L * 1024 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HorizonRuntimeService() {
    }

    public static synchronized Process launch(Instance instance) throws Exception {
        validateTarget(instance);
        Path installRoot = MinecraftLocator.getVantaDirectory()
                .resolve("experimental")
                .resolve("horizon");
        InstalledRelease release = null;
        Path executable = null;
        Exception releaseLookupFailure = null;
        try {
            release = resolveLatestRelease();
            Path versionDirectory = installRoot.resolve(release.version());
            executable = findLauncher(versionDirectory);
            if (executable == null) {
                executable = installLauncher(release, installRoot, versionDirectory);
            }
        } catch (Exception ex) {
            releaseLookupFailure = ex;
        }

        if (executable == null) {
            executable = findNewestInstalledLauncher(installRoot);
            if (executable == null) {
                if (releaseLookupFailure != null) {
                    throw releaseLookupFailure;
                }
                throw new IOException("No usable Horizon launcher is installed.");
            }
            System.out.println("[Vanta Horizon] Using the cached Horizon launcher because the latest release could not be checked.");
        }

        String profileId = stableProfileId(instance);
        ensureHorizonProfile(executable, profileId, instance.getName());

        System.out.println("[Vanta Horizon] Launching Horizon profile " + profileId
                + " for Vanta instance '" + instance.getName() + "'.");
        System.out.println("[Vanta Horizon] Horizon owns its profile data; Vanta's Java mods and settings are not copied.");
        return new ProcessBuilder(
                executable.toString(),
                "play",
                "--instance",
                profileId
        ).directory(instance.getDirectory().toFile()).redirectErrorStream(true).start();
    }

    private static void validateTarget(Instance instance) throws IOException {
        if (instance == null || !instance.isHorizon()) {
            throw new IllegalArgumentException("A Horizon instance is required.");
        }
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            throw new IOException("The experimental Horizon integration currently supports Windows x64 only.");
        }
        if (!"amd64".equalsIgnoreCase(System.getProperty("os.arch"))
                && !"x86_64".equalsIgnoreCase(System.getProperty("os.arch"))) {
            throw new IOException("The experimental Horizon integration requires Windows x64.");
        }
        if (!"26.3".equals(instance.getMinecraftVersion())) {
            throw new IOException("Horizon currently targets Minecraft 26.3 only.");
        }
    }

    private static Path installLauncher(
            InstalledRelease release,
            Path installRoot,
            Path versionDirectory
    ) throws Exception {
        Files.createDirectories(installRoot);
        Path archive = Files.createTempFile(installRoot, "horizon-", ".zip");
        Path staging = Files.createTempDirectory(installRoot, "extract-");
        try {
            downloadAndVerify(release, archive);
            extractSafely(archive, staging);
            if (findLauncher(staging) == null) {
                throw new IOException("The verified Horizon archive did not contain horizon-launcher.exe.");
            }
            if (Files.exists(versionDirectory)) {
                deleteTree(versionDirectory);
            }
            moveDirectory(staging, versionDirectory);
            staging = null;
            Path executable = findLauncher(versionDirectory);
            if (executable == null) {
                throw new IOException("Horizon launcher was not found after installation.");
            }
            return executable;
        } finally {
            Files.deleteIfExists(archive);
            if (staging != null) {
                deleteTree(staging);
            }
        }
    }

    private static String stableProfileId(Instance instance) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(instance.getId().getBytes(StandardCharsets.UTF_8));
            return "vanta-" + HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable in this Java runtime.", ex);
        }
    }

    private static Path findNewestInstalledLauncher(Path installRoot) throws IOException {
        if (!Files.isDirectory(installRoot)) {
            return null;
        }
        try (var directories = Files.list(installRoot)) {
            List<Path> candidates = new ArrayList<>(directories
                    .filter(Files::isDirectory)
                    .toList());
            candidates.sort((left, right) -> {
                try {
                    return Files.getLastModifiedTime(right)
                            .compareTo(Files.getLastModifiedTime(left));
                } catch (IOException ignored) {
                    return 0;
                }
            });
            for (Path candidate : candidates) {
                Path launcher = findLauncher(candidate);
                if (launcher != null) {
                    return launcher;
                }
            }
        }
        return null;
    }

    private static void ensureHorizonProfile(
            Path executable,
            String profileId,
            String displayName
    ) throws Exception {
        CommandResult list = runCommand(
                List.of(executable.toString(), "--json", "instance", "list"),
                Duration.ofSeconds(45)
        );
        if (list.exitCode() != 0) {
            throw commandFailure("Could not inspect Horizon instances", list);
        }

        JsonNode instances;
        try {
            instances = MAPPER.readTree(list.stdout());
        } catch (Exception ex) {
            throw new IOException("Horizon returned invalid JSON while listing profiles.", ex);
        }
        if (!instances.isArray()) {
            throw new IOException("Horizon returned an unexpected instance-list response.");
        }

        boolean exists = false;
        boolean displayNameTaken = false;
        for (JsonNode entry : instances) {
            String existingId = entry.path("id").asText();
            String existingName = entry.path("name").asText();
            if (profileId.equals(existingId)) {
                if (!entry.path("java").isMissingNode() && !entry.path("java").isNull()) {
                    throw new IOException("The matching Horizon profile ID is already used by a Java instance.");
                }
                exists = true;
            } else if (("Vanta - " + displayName).equalsIgnoreCase(existingName)) {
                displayNameTaken = true;
            }
        }

        if (!exists) {
            CommandResult create = runCommand(
                    List.of(executable.toString(), "instance", "create", profileId),
                    Duration.ofMinutes(2)
            );
            if (create.exitCode() != 0) {
                throw commandFailure("Could not create the Horizon profile", create);
            }
        }

        String safeDisplayName = "Vanta - " + displayName;
        if (displayNameTaken) {
            safeDisplayName += " (" + profileId + ")";
        }
        CommandResult rename = runCommand(
                List.of(executable.toString(), "instance", "rename", profileId, safeDisplayName),
                Duration.ofSeconds(45)
        );
        if (rename.exitCode() != 0) {
            throw commandFailure("Could not name the Horizon profile", rename);
        }
    }

    private static CommandResult runCommand(List<String> command, Duration timeout) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(
                () -> readStream(process.getInputStream()));
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(
                () -> readStream(process.getErrorStream()));
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            throw new IOException("Horizon command timed out: " + command.get(1));
        }
        return new CommandResult(
                process.exitValue(),
                stdout.get(10, TimeUnit.SECONDS),
                stderr.get(10, TimeUnit.SECONDS)
        );
    }

    private static String readStream(java.io.InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    private static IOException commandFailure(String action, CommandResult result) {
        String detail = result.stderr().isBlank() ? result.stdout() : result.stderr();
        if (detail.length() > 1200) {
            detail = detail.substring(detail.length() - 1200);
        }
        return new IOException(action + " (exit " + result.exitCode() + "). "
                + detail.trim()
                + " If Horizon has not been signed in, open its launcher and sign in first.");
    }

    private static InstalledRelease resolveLatestRelease() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("https://api.github.com/repos/horizon-rust/horizon/releases?per_page=30"))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Vanta-Experimental-Horizon")
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("GitHub returned HTTP " + response.statusCode()
                    + " while checking Horizon launcher releases.");
        }

        JsonNode releases = MAPPER.readTree(response.body());
        if (!releases.isArray()) {
            throw new IOException("GitHub returned an unexpected Horizon releases response.");
        }
        for (JsonNode release : releases) {
            if (release.path("draft").asBoolean(true) || release.path("prerelease").asBoolean(true)) {
                continue;
            }
            String tag = release.path("tag_name").asText("");
            for (JsonNode asset : release.path("assets")) {
                if (!WINDOWS_ASSET.equals(asset.path("name").asText())) {
                    continue;
                }
                String url = asset.path("browser_download_url").asText("");
                String digest = asset.path("digest").asText("");
                long size = asset.path("size").asLong(-1);
                if (tag.isBlank() || url.isBlank()
                        || !digest.matches("sha256:[0-9a-fA-F]{64}")) {
                    throw new IOException("Horizon launcher release is missing a valid tag, URL, or SHA-256 digest.");
                }
                if (size <= 0 || size > MAX_DOWNLOAD_BYTES) {
                    throw new IOException("Horizon Windows archive has an unexpected size.");
                }
                return new InstalledRelease(tag, URI.create(url), digest.substring("sha256:".length()), size);
            }
        }
        throw new IOException("No stable Windows x64 Horizon launcher archive was found in the latest 30 releases.");
    }

    private static void downloadAndVerify(InstalledRelease release, Path destination) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(release.downloadUri())
                .timeout(Duration.ofMinutes(3))
                .header("User-Agent", "Vanta-Experimental-Horizon")
                .GET()
                .build();
        HttpResponse<Path> response = HTTP.send(request, HttpResponse.BodyHandlers.ofFile(destination));
        if (response.statusCode() != 200) {
            Files.deleteIfExists(destination);
            throw new IOException("Horizon download failed with HTTP " + response.statusCode() + ".");
        }
        long downloaded = Files.size(destination);
        if (downloaded != release.expectedSize() || downloaded > MAX_DOWNLOAD_BYTES) {
            throw new IOException("Horizon archive size did not match GitHub release metadata.");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equalsIgnoreCase(release.sha256())) {
            Files.deleteIfExists(destination);
            throw new IOException("Horizon archive SHA-256 verification failed; the archive was discarded.");
        }
    }

    private static void extractSafely(Path archive, Path destination) throws IOException {
        Path root = destination.toAbsolutePath().normalize();
        long total = 0;
        int entries = 0;
        try (var file = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(file)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 20_000) {
                    throw new IOException("Horizon archive contains too many entries.");
                }
                Path output = root.resolve(entry.getName()).normalize();
                if (!output.startsWith(root)) {
                    throw new IOException("Rejected unsafe path in Horizon archive.");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    try (var out = Files.newOutputStream(output)) {
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = zip.read(buffer)) >= 0) {
                            if (read == 0) continue;
                            total += read;
                            if (total > MAX_EXTRACTED_BYTES) {
                                throw new IOException("Horizon archive exceeded the extraction size limit.");
                            }
                            out.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static Path findLauncher(Path root) throws IOException {
        if (!Files.isDirectory(root)) return null;
        try (var paths = Files.walk(root, 6)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase("horizon-launcher.exe"))
                    .findFirst().orElse(null);
        }
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private record InstalledRelease(String version, URI downloadUri, String sha256, long expectedSize) {
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }
}
