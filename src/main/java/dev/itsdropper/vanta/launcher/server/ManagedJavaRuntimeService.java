package dev.itsdropper.vanta.launcher.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.MinecraftLocator;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads a private, per-user Temurin JRE when a server needs a newer Java
 * major than the runtime selected in its profile. It never changes system PATH.
 */
final class ManagedJavaRuntimeService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final long MAX_ARCHIVE_BYTES = 512L * 1024 * 1024;

    private ManagedJavaRuntimeService() {}

    static String resolveForServer(Path serverJar, String selectedJava, Consumer<String> log) throws IOException {
        int requiredMajor = requiredJavaMajor(serverJar);
        if (requiredMajor < 8) return selectedJava;

        int selectedMajor = detectJavaMajor(selectedJava);
        if (selectedMajor >= requiredMajor) return selectedJava;

        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                || !System.getProperty("os.arch", "").toLowerCase(Locale.ROOT).matches("amd64|x86_64")) {
            throw new IOException("This server requires Java " + requiredMajor
                    + ", but the selected runtime is Java " + (selectedMajor < 0 ? "unknown" : selectedMajor)
                    + ". Vanta's automatic runtime installer currently supports Windows x64. Install Java "
                    + requiredMajor + " and select its java executable.");
        }

        Path runtime = managedRuntimePath(requiredMajor);
        Path executable = runtime.resolve("bin").resolve("java.exe");
        if (!Files.isRegularFile(executable)) {
            log.accept("Vanta: this server requires Java " + requiredMajor
                    + "; downloading a private Temurin JRE. This does not change your system Java.");
            installTemurinJre(requiredMajor, runtime, log);
        } else {
            log.accept("Vanta: using Vanta-managed Java " + requiredMajor + ".");
        }
        return executable.toAbsolutePath().normalize().toString();
    }

    private static int requiredJavaMajor(Path jar) throws IOException {
        try (java.util.jar.JarFile archive = new java.util.jar.JarFile(jar.toFile())) {
            java.util.jar.JarEntry entry = archive.getJarEntry("net/minecraft/bundler/Main.class");
            if (entry == null) return -1;
            try (InputStream input = archive.getInputStream(entry)) {
                byte[] header = input.readNBytes(8);
                if (header.length < 8 || header[0] != (byte) 0xCA || header[1] != (byte) 0xFE
                        || header[2] != (byte) 0xBA || header[3] != (byte) 0xBE) return -1;
                int major = ((header[6] & 0xff) << 8) | (header[7] & 0xff);
                return major >= 45 ? major - 44 : -1;
            }
        }
    }

    private static int detectJavaMajor(String executable) {
        if (executable == null || executable.isBlank()) return -1;
        try {
            Process process = new ProcessBuilder(executable.trim(), "-version")
                    .redirectErrorStream(true).start();
            String output;
            try (InputStream input = process.getInputStream()) {
                output = new String(input.readNBytes(16 * 1024), java.nio.charset.StandardCharsets.UTF_8);
            }
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return -1;
            }
            Matcher matcher = Pattern.compile("(?:version\\s+\"|openjdk\\s+)(?:1\\.)?(\\d+)").matcher(output);
            return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static Path managedRuntimePath(int major) {
        return MinecraftLocator.getVantaDirectory().resolve("runtimes")
                .resolve("temurin-jre-" + major + "-windows-x64");
    }

    private static void installTemurinJre(int major, Path target, Consumer<String> log) throws IOException {
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path archive = parent.resolve("temurin-jre-" + major + ".zip.download");
        Path staging = parent.resolve("temurin-jre-" + major + ".installing");
        deleteTree(staging);
        Files.createDirectories(staging);
        try {
            String api = "https://api.adoptium.net/v3/assets/latest/" + major
                    + "/hotspot?architecture=x64&image_type=jre&os=windows&vendor=eclipse";
            HttpRequest metadataRequest = HttpRequest.newBuilder(URI.create(api))
                    .timeout(Duration.ofSeconds(30)).header("Accept", "application/json").GET().build();
            HttpResponse<String> metadata = HTTP.send(metadataRequest, HttpResponse.BodyHandlers.ofString());
            if (metadata.statusCode() < 200 || metadata.statusCode() >= 300) {
                throw new IOException("Temurin metadata request failed (HTTP " + metadata.statusCode() + ").");
            }
            JsonNode assets = JSON.readTree(metadata.body());
            String downloadUrl = assets.isArray() && !assets.isEmpty()
                    ? assets.get(0).path("binary").path("package").path("link").asText("") : "";
            if (!downloadUrl.startsWith("https://github.com/adoptium/")) {
                throw new IOException("Temurin did not return a trusted Windows JRE download URL.");
            }

            log.accept("Vanta: downloading Java " + major + " runtime from Eclipse Temurin...");
            HttpRequest downloadRequest = HttpRequest.newBuilder(URI.create(downloadUrl))
                    .timeout(Duration.ofMinutes(10)).GET().build();
            HttpResponse<InputStream> response = HTTP.send(downloadRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try { response.body().close(); } catch (IOException ignored) {}
                throw new IOException("Temurin download failed (HTTP " + response.statusCode() + ").");
            }
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (contentLength > MAX_ARCHIVE_BYTES) {
                response.body().close();
                throw new IOException("Java runtime archive exceeds the 512 MB safety limit.");
            }
            try (InputStream in = response.body()) {
                Files.copy(in, archive, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.size(archive) == 0 || Files.size(archive) > MAX_ARCHIVE_BYTES) {
                throw new IOException("Downloaded Java runtime archive has an invalid size.");
            }

            log.accept("Vanta: verifying and installing Java " + major + " into Vanta's local runtime directory...");
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    Path output = staging.resolve(entry.getName()).normalize();
                    if (!output.startsWith(staging) || entry.getName().startsWith("/")
                            || entry.getName().contains("\\\\") || entry.getName().matches("^[A-Za-z]:.*")) {
                        throw new IOException("Java runtime archive contains an unsafe path.");
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(output);
                    } else {
                        Files.createDirectories(output.getParent());
                        Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);
                    }
                    zip.closeEntry();
                }
            }
            Path javaExe;
            try (var paths = Files.walk(staging)) {
                javaExe = paths.filter(p -> p.getFileName().toString().equalsIgnoreCase("java.exe")
                                && p.getParent() != null && p.getParent().getFileName().toString().equalsIgnoreCase("bin"))
                        .findFirst().orElseThrow(() -> new IOException("Downloaded archive does not contain bin/java.exe."));
            }
            // Verify the downloaded executable really reports the requested major.
            int installedMajor = detectJavaMajor(javaExe.toAbsolutePath().toString());
            if (installedMajor != major) {
                throw new IOException("Downloaded runtime reported Java " + installedMajor
                        + " instead of requested Java " + major + ".");
            }
            deleteTree(target);
            try {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(staging, target);
            }
            log.accept("Vanta: Java " + major + " is installed and ready.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Java runtime download was interrupted.", e);
        } finally {
            Files.deleteIfExists(archive);
            deleteTree(staging);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
