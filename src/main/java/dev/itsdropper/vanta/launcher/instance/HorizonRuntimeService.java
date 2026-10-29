package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.MinecraftLocator;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Experimental bridge to the upstream Horizon launcher.
 *
 * This intentionally opens Horizon's own launcher rather than pretending its
 * process is the Java Minecraft process. Per-instance direct play is not yet
 * supported by an upstream integration contract.
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

    public static Process launch(Instance instance) throws Exception {
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

        InstalledRelease release = resolveLatestRelease();
        Path installRoot = MinecraftLocator.getVantaDirectory()
                .resolve("experimental")
                .resolve("horizon");
        Path versionDirectory = installRoot.resolve(release.version());
        Path executable = findLauncher(versionDirectory);

        if (executable == null) {
            Files.createDirectories(installRoot);
            Path archive = Files.createTempFile(installRoot, "horizon-", ".zip");
            Path staging = Files.createTempDirectory(installRoot, "extract-");
            try {
                downloadAndVerify(release, archive);
                extractSafely(archive, staging);
                Path stagedExecutable = findLauncher(staging);
                if (stagedExecutable == null) {
                    throw new IOException("The verified Horizon archive did not contain horizon-launcher.exe.");
                }
                if (Files.exists(versionDirectory)) {
                    deleteTree(versionDirectory);
                }
                moveDirectory(staging, versionDirectory);
                staging = null;
                executable = findLauncher(versionDirectory);
                if (executable == null) {
                    throw new IOException("Horizon launcher was not found after installation.");
                }
            } finally {
                Files.deleteIfExists(archive);
                if (staging != null) {
                    deleteTree(staging);
                }
            }
        }

        System.out.println("[Vanta Horizon] Opening Horizon launcher " + release.version()
                + ". Select the game/profile in Horizon; direct per-instance play is not wired yet.");
        return new ProcessBuilder(executable.toString())
                .directory(instance.getDirectory().toFile())
                .start();
    }

    private static InstalledRelease resolveLatestRelease() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES_API))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Vanta-Experimental-Horizon")
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request,
                HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("GitHub returned HTTP " + response.statusCode()
                    + " while checking the latest Horizon release.");
        }

        JsonNode release = MAPPER.readTree(response.body());
        String tag = release.path("tag_name").asText("");
        if (tag.isBlank()) {
            throw new IOException("The Horizon release response had no tag name.");
        }

        JsonNode assets = release.path("assets");
        for (JsonNode asset : assets) {
            if (!WINDOWS_ASSET.equals(asset.path("name").asText())) {
                continue;
            }
            String url = asset.path("browser_download_url").asText("");
            String digest = asset.path("digest").asText("");
            long size = asset.path("size").asLong(-1);
            if (url.isBlank() || !digest.matches("sha256:[0-9a-fA-F]{64}")) {
                throw new IOException("Horizon release is missing a valid SHA-256 digest for its Windows archive.");
            }
            if (size <= 0 || size > MAX_DOWNLOAD_BYTES) {
                throw new IOException("Horizon Windows archive has an unexpected size.");
            }
            return new InstalledRelease(tag, URI.create(url), digest.substring("sha256:".length()), size);
        }
        throw new IOException("No Windows x64 launcher archive was published in the latest Horizon release.");
    }

    private static void downloadAndVerify(InstalledRelease release, Path destination) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(release.downloadUri())
                .timeout(Duration.ofMinutes(3))
                .header("User-Agent", "Vanta-Experimental-Horizon")
                .GET()
                .build();
        HttpResponse<Path> response = HTTP.send(request,
                HttpResponse.BodyHandlers.ofFile(destination));
        if (response.statusCode() != 200) {
            Files.deleteIfExists(destination);
            throw new IOException("Horizon download failed with HTTP " + response.statusCode() + ".");
        }
        long downloaded = Files.size(destination);
        if (downloaded != release.expectedSize() || downloaded > MAX_DOWNLOAD_BYTES) {
            throw new IOException("Horizon archive size did not match GitHub release metadata.");
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
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
        try (InputStream file = Files.newInputStream(archive);
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
}
