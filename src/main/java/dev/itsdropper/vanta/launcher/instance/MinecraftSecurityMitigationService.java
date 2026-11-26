package dev.itsdropper.vanta.launcher.instance;

import dev.itsdropper.vanta.launcher.MinecraftLocator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Installs and verifies the pinned Minecraft font/image mitigation agent.
 *
 * The agent is fetched from a fixed upstream release and accepted only when
 * its SHA-256 matches the release digest pinned in this source file.
 */
public final class MinecraftSecurityMitigationService {
    private static final String VERSION = "2.3.0";
    private static final URI DOWNLOAD = URI.create(
            "https://github.com/sunrisemc-tw/minecraft2shell-fix/releases/download/v"
                    + VERSION + "/m2sfix-agent-" + VERSION + ".jar");
    private static final String SHA256 =
            "c073f82a460559151f720f635266077248807eb6939a0eee4aa10d5081405bbe";
    private static final int MAX_DOWNLOAD_BYTES = 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private MinecraftSecurityMitigationService() {
    }

    /**
     * Returns a locally cached, checksum-verified agent. A corrupt cache is
     * replaced only after a fresh download has passed all verification checks.
     */
    public static Path ensureAgent() throws Exception {
        Path securityDirectory = MinecraftLocator.getVantaDirectoryPath().resolve("security");
        Files.createDirectories(securityDirectory);
        Path target = securityDirectory.resolve("m2sfix-agent-" + VERSION + ".jar");
        if (Files.isSymbolicLink(securityDirectory) || Files.isSymbolicLink(target)) {
            throw new IOException("Minecraft security agent cache must not be a symbolic link.");
        }

        if (Files.isRegularFile(target)) {
            try {
                validateAgent(target);
                return target;
            } catch (IOException invalidCache) {
                Files.delete(target);
            }
        }

        HttpRequest request = HttpRequest.newBuilder(DOWNLOAD)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Vanta-Minecraft-Security")
                .GET()
                .build();

        HttpResponse<InputStream> response = HTTP.send(
                request, HttpResponse.BodyHandlers.ofInputStream());
        byte[] bytes;
        try (InputStream input = response.body()) {
            if (response.statusCode() != 200 || !isTrustedReleaseHost(response.uri())) {
                throw new IOException("Could not retrieve the pinned Minecraft security agent "
                        + "(HTTP " + response.statusCode() + ").");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (output.size() + read > MAX_DOWNLOAD_BYTES) {
                    throw new IOException("Minecraft security agent download exceeded its size limit.");
                }
                output.write(buffer, 0, read);
            }
            bytes = output.toByteArray();
        }

        if (!SHA256.equalsIgnoreCase(sha256(bytes))) {
            throw new IOException("Minecraft security agent checksum verification failed.");
        }

        Path temporary = securityDirectory.resolve(
                target.getFileName() + "." + java.util.UUID.randomUUID() + ".tmp");
        try {
            Files.write(temporary, bytes);
            validateAgent(temporary);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }

        System.out.println("[Vanta Security] Installed verified Minecraft security agent "
                + VERSION + " (SHA-256 " + SHA256 + ").");
        return target;
    }

    static void validateAgent(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) <= 0
                || Files.size(file) > MAX_DOWNLOAD_BYTES
                || !SHA256.equalsIgnoreCase(sha256(Files.readAllBytes(file)))) {
            throw new IOException("Minecraft security agent is missing or failed SHA-256 verification.");
        }

        try (JarFile jar = new JarFile(file.toFile(), true)) {
            Manifest manifest = jar.getManifest();
            if (manifest == null
                    || !"dev.duckducktw.m2sfix.agent.FixAgent".equals(
                            manifest.getMainAttributes().getValue("Premain-Class"))
                    || !"true".equalsIgnoreCase(
                            manifest.getMainAttributes().getValue("Can-Retransform-Classes"))
                    || jar.getEntry("dev/duckducktw/m2sfix/agent/FixAgent.class") == null
                    || jar.getEntry("dev/duckducktw/m2sfix/agent/FixTransformer.class") == null
                    || jar.getEntry("dev/duckducktw/m2sfix/core/FaceGuard.class") == null
                    || jar.getEntry("META-INF/m2sfix-bridge.jar") == null) {
                throw new IOException("Minecraft security agent has an invalid or incomplete Java-agent manifest.");
            }
        }
    }

    private static boolean isTrustedReleaseHost(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            return false;
        }
        String host = uri.getHost();
        return host != null && (host.equalsIgnoreCase("github.com")
                || host.equalsIgnoreCase("release-assets.githubusercontent.com"));
    }

    private static String sha256(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IOException("SHA-256 is unavailable in this Java runtime.", impossible);
        }
    }
}
