package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Source-based installer for Stein's .steinmod ecosystem.
 *
 * <p>Each source is a publisher-owned steinmods.json index. Downloaded archives are
 * SHA-256 checked and their embedded steinmod.json identity and minimum Loader
 * requirement are validated before they are atomically published in the instance's
 * mods directory. This manager never deletes an untracked mod.
 */
public final class SteinModManager {
    private static final String SOURCES_FILE = "sources.json";
    private static final String INSTALLED_FILE = "installed-mods.json";
    private static final long MAX_INDEX_BYTES = 2L * 1024L * 1024L;
    private static final long MAX_MOD_BYTES = 128L * 1024L * 1024L;
    private static final long MAX_EXPANDED_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_ARCHIVE_ENTRIES = 20_000;
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private SteinModManager() {
    }

    public record Source(String name, String author, String homepage, String url) {
    }

    public record Release(
            String id,
            String modId,
            String name,
            String file,
            String version,
            String url,
            String sha256,
            String loader,
            String description,
            String sourceName,
            String sourceUrl
    ) {
    }

    public record Installed(
            String id,
            String modId,
            String name,
            String file,
            String version,
            String sha256,
            String sourceUrl
    ) {
    }

    private record SourceRecord(String url, String name, String author, String homepage) {
    }

    public static List<Source> loadSources(Instance instance) throws IOException {
        Path file = stateFile(instance, SOURCES_FILE);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        List<SourceRecord> records = JSON.readValue(file.toFile(),
                new TypeReference<List<SourceRecord>>() { });
        List<Source> sources = new ArrayList<>();
        for (SourceRecord record : records) {
            if (record == null || record.url() == null || record.url().isBlank()) {
                continue;
            }
            String name = record.name();
            if (name == null || name.isBlank()) {
                name = URI.create(record.url()).getHost();
            }
            sources.add(new Source(name, record.author() == null ? "" : record.author(),
                    record.homepage() == null ? "" : record.homepage(), record.url()));
        }
        return List.copyOf(sources);
    }

    public static Source addSource(Instance instance, String sourceUrl)
            throws IOException, InterruptedException {
        requireSteinInstance(instance);
        String normalized = validateHttpsUrl(sourceUrl);
        Index index = readSource(normalized);
        List<SourceRecord> records = readSourceRecords(instance);
        if (records.stream().noneMatch(record -> normalized.equals(record.url()))) {
            Source source = index.source();
            records.add(new SourceRecord(normalized, source.name(), source.author(), source.homepage()));
            writeJsonAtomically(stateFile(instance, SOURCES_FILE), records);
        }
        return index.source();
    }

    public static void removeSource(Instance instance, String sourceUrl) throws IOException {
        requireSteinInstance(instance);
        String normalized = validateHttpsUrl(sourceUrl);
        List<SourceRecord> records = readSourceRecords(instance);
        records.removeIf(record -> normalized.equals(record.url()));
        writeJsonAtomically(stateFile(instance, SOURCES_FILE), records);
        // Installed files are deliberately retained, matching Stein's source-removal semantics.
    }

    public static List<Release> refresh(Instance instance)
            throws IOException, InterruptedException {
        requireSteinInstance(instance);
        List<SourceRecord> records = readSourceRecords(instance);
        List<Release> releases = new ArrayList<>();
        java.util.Map<String, String> owners = new java.util.HashMap<>();
        for (SourceRecord record : records) {
            Index index = readSource(record.url());
            for (Release release : index.releases()) {
                String previousSource = owners.putIfAbsent(release.id(), release.sourceUrl());
                if (previousSource != null && !previousSource.equals(release.sourceUrl())) {
                    throw new IOException("Stein mod ID '" + release.id()
                            + "' is published by more than one configured source.");
                }
                if (previousSource == null) {
                    releases.add(release);
                }
            }
        }
        releases.sort(Comparator.comparing(Release::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(releases);
    }

    public static List<Installed> loadInstalled(Instance instance) throws IOException {
        Path file = stateFile(instance, INSTALLED_FILE);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        List<Installed> installed = JSON.readValue(file.toFile(),
                new TypeReference<List<Installed>>() { });
        return installed == null ? List.of() : List.copyOf(installed);
    }

    public static Path install(Instance instance, Release release)
            throws IOException, InterruptedException {
        requireSteinInstance(instance);
        validateRelease(release);
        if (compareVersions(SteinInstaller.LOADER_VERSION, release.loader()) < 0) {
            throw new IOException("This mod requires Stein Loader " + release.loader()
                    + " or newer; this instance has " + SteinInstaller.LOADER_VERSION + ".");
        }

        Path modsDirectory = instance.getDirectory().resolve("mods").toAbsolutePath().normalize();
        Files.createDirectories(modsDirectory);
        String filename = safeFilename(release.file());
        if (previous != null && previous.file() != null
                && previous.file().toLowerCase(Locale.ROOT).endsWith(".steinmod.disabled")) {
            filename += ".disabled";
        }
        Path destination = modsDirectory.resolve(filename).normalize();
        if (!destination.startsWith(modsDirectory)) {
            throw new IOException("Stein mod filename escapes the instance mods directory.");
        }

        List<Installed> installed = new ArrayList<>(loadInstalled(instance));
        Installed previous = installed.stream()
                .filter(item -> release.id().equals(item.id()))
                .findFirst().orElse(null);
        Path previousPath = previous == null ? null : trackedPath(modsDirectory, previous.file());
        if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
                && (previousPath == null || !destination.equals(previousPath))) {
            throw new IOException("A file already exists at " + filename
                    + " and is not tracked as this Stein mod. It will not be overwritten.");
        }

        Path staged = modsDirectory.resolve(".stein-stage-" + UUID.randomUUID() + ".steinmod");
        Path backup = previousPath != null && Files.isRegularFile(previousPath, LinkOption.NOFOLLOW_LINKS)
                ? modsDirectory.resolve(".stein-backup-" + UUID.randomUUID() + ".steinmod") : null;
        boolean oldMoved = false;
        boolean newPublished = false;
        try {
            downloadVerified(release, staged);
            validateArchive(staged, release);

            if (backup != null) {
                move(previousPath, backup, false);
                oldMoved = true;
            }
            move(staged, destination, true);
            newPublished = true;

            Installed replacement = new Installed(
                    release.id(), release.modId(), release.name(), filename,
                    release.version(), release.sha256(), release.sourceUrl());
            installed.removeIf(item -> release.id().equals(item.id()));
            installed.add(replacement);
            writeJsonAtomically(stateFile(instance, INSTALLED_FILE), installed);

            if (backup != null) {
                try {
                    Files.deleteIfExists(backup);
                } catch (IOException cleanupFailure) {
                    System.err.println("Could not remove Stein mod rollback file: "
                            + cleanupFailure.getMessage());
                }
            }
            return destination;
        } catch (Exception failure) {
            if (newPublished) {
                try {
                    Files.deleteIfExists(destination);
                } catch (IOException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            if (oldMoved && backup != null && Files.exists(backup)) {
                try {
                    move(backup, previousPath, true);
                } catch (IOException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            if (failure instanceof InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            }
            if (failure instanceof IOException io) {
                throw io;
            }
            throw new IOException("Could not install Stein mod " + release.id() + ".", failure);
        } finally {
            try {
                Files.deleteIfExists(staged);
            } catch (IOException cleanupFailure) {
                System.err.println("Could not remove staged Stein mod: " + cleanupFailure.getMessage());
            }
            if (backup != null && Files.exists(backup) && !oldMoved) {
                try {
                    Files.deleteIfExists(backup);
                } catch (IOException cleanupFailure) {
                    System.err.println("Could not remove unused Stein mod backup: "
                            + cleanupFailure.getMessage());
                }
            }
        }
    }

    public static List<Path> updateAll(Instance instance)
            throws IOException, InterruptedException {
        List<Release> releases = refresh(instance);
        List<Installed> installed = loadInstalled(instance);
        List<Path> updated = new ArrayList<>();
        for (Release release : releases) {
            Installed current = installed.stream()
                    .filter(item -> release.id().equals(item.id()))
                    .findFirst().orElse(null);
            if (current != null && (!release.version().equals(current.version())
                    || !release.sha256().equalsIgnoreCase(current.sha256()))) {
                updated.add(install(instance, release));
            }
        }
        return List.copyOf(updated);
    }

    public static void removeInstalled(Instance instance, String id) throws IOException {
        requireSteinInstance(instance);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Stein mod ID cannot be empty.");
        }
        List<Installed> installed = new ArrayList<>(loadInstalled(instance));
        Installed record = installed.stream().filter(item -> id.equals(item.id()))
                .findFirst().orElse(null);
        if (record == null) {
            return;
        }
        Path modsDirectory = instance.getDirectory().resolve("mods").toAbsolutePath().normalize();
        Path file = trackedPath(modsDirectory, record.file());
        if (Files.isSymbolicLink(file)) {
            throw new IOException("Refusing to remove a symbolic link from the mods directory.");
        }
        Path backup = modsDirectory.resolve(".stein-remove-" + UUID.randomUUID() + ".backup");
        boolean moved = false;
        try {
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                move(file, backup, false);
                moved = true;
            }
            installed.removeIf(item -> id.equals(item.id()));
            writeJsonAtomically(stateFile(instance, INSTALLED_FILE), installed);
            if (moved) {
                try {
                    Files.deleteIfExists(backup);
                } catch (IOException cleanupFailure) {
                    System.err.println("Could not remove Stein mod removal backup: "
                            + cleanupFailure.getMessage());
                }
            }
        } catch (Exception failure) {
            if (moved && Files.exists(backup)) {
                try {
                    move(backup, file, true);
                } catch (IOException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            if (failure instanceof IOException io) {
                throw io;
            }
            throw new IOException("Could not remove Stein mod " + id + ".", failure);
        }
    }

    private record Index(Source source, List<Release> releases) {
    }

    private static Index readSource(String sourceUrl) throws IOException {
        String normalized = validateHttpsUrl(sourceUrl);
        HttpRequest request = HttpRequest.newBuilder(URI.create(normalized))
                .timeout(Duration.ofSeconds(45))
                .header("User-Agent", "Vanta Launcher")
                .header("Accept", "application/json")
                .GET().build();
        try {
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw new IOException("Stein source returned HTTP " + response.statusCode() + ": " + normalized);
            }
            byte[] bytes;
            try (InputStream input = response.body()) {
                bytes = input.readNBytes((int) MAX_INDEX_BYTES + 1);
                if (bytes.length > MAX_INDEX_BYTES) {
                    throw new IOException("Stein source index exceeds the 2 MiB size limit.");
                }
            }
            return parseIndexDetails(bytes, normalized);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading Stein source: " + normalized, interrupted);
        }
    }

    static List<Release> parseIndex(byte[] bytes, String sourceUrl) throws IOException {
        return parseIndexDetails(bytes, validateHttpsUrl(sourceUrl)).releases();
    }

    private static Index parseIndexDetails(byte[] bytes, String normalizedSourceUrl) throws IOException {
        JsonNode root = JSON.readTree(bytes);
        if (root == null || root.path("format").asInt(-1) != 1
                || !root.path("mods").isArray() || root.path("mods").isEmpty()) {
            throw new IOException("Invalid Stein source index: expected format 1 and a non-empty mods array.");
        }
        String name = requiredText(root, "name");
        String author = requiredText(root, "author");
        String homepage = optionalHttpsUrl(root.path("homepage").asText(""));
        String declaredSource = optionalHttpsUrl(root.path("source").asText(""));
        Source source = new Source(name, author, homepage,
                declaredSource.isBlank() ? normalizedSourceUrl : declaredSource);
        List<Release> releases = new ArrayList<>();
        for (JsonNode mod : root.path("mods")) {
            releases.add(parseRelease(mod, source));
        }
        return new Index(source, List.copyOf(releases));
    }

    private static Release parseRelease(JsonNode node, Source source) throws IOException {
        Release release = new Release(
                requiredText(node, "id"),
                requiredText(node, "modId"),
                requiredText(node, "name"),
                requiredText(node, "file"),
                requiredText(node, "version"),
                validateHttpsUrl(requiredText(node, "url")),
                requiredText(node, "sha256").toLowerCase(Locale.ROOT),
                requiredText(node, "loader"),
                node.path("description").asText(""),
                source.name(),
                source.url()
        );
        validateRelease(release);
        return release;
    }

    private static void validateRelease(Release release) throws IOException {
        if (release == null || !release.id().matches("[A-Za-z0-9._-]{1,128}")
                || !release.modId().matches("[A-Za-z0-9._-]{1,128}")
                || release.version().isBlank()
                || !release.sha256().matches("(?i)[0-9a-f]{64}")
                || !release.loader().matches("[0-9]+(?:\\.[0-9]+){1,3}")) {
            throw new IOException("Stein source contains an invalid mod entry.");
        }
        safeFilename(release.file());
        validateHttpsUrl(release.url());
    }

    private static void downloadVerified(Release release, Path destination)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(release.url()))
                .timeout(Duration.ofMinutes(3))
                .header("User-Agent", "Vanta Launcher")
                .GET().build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            response.body().close();
            throw new IOException("Stein mod download returned HTTP " + response.statusCode() + ".");
        }
        try (InputStream input = response.body();
             var output = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[32 * 1024];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_MOD_BYTES) {
                    throw new IOException("Stein mod exceeds the 128 MiB download limit.");
                }
                output.write(buffer, 0, read);
            }
        } catch (IOException failure) {
            Files.deleteIfExists(destination);
            throw failure;
        }
        String actual = sha256(destination);
        if (!actual.equalsIgnoreCase(release.sha256())) {
            Files.deleteIfExists(destination);
            throw new IOException("SHA-256 verification failed for " + release.file() + ".");
        }
    }

    static void validateArchive(Path archive, Release release) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry metadata = zip.getEntry("steinmod.json");
            if (metadata == null || metadata.isDirectory() || metadata.getSize() > 1024 * 1024) {
                throw new IOException("The .steinmod archive has no valid steinmod.json manifest.");
            }
            long expanded = 0;
            int entries = 0;
            var iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                ZipEntry entry = iterator.nextElement();
                entries++;
                if (entries > MAX_ARCHIVE_ENTRIES) {
                    throw new IOException("The .steinmod archive contains too many entries.");
                }
                String name = entry.getName();
                if (name.startsWith("/") || name.startsWith("\\")
                        || name.matches("^[A-Za-z]:.*")
                        || List.of(name.split("/")).contains("..")
                        || name.indexOf('\\\\') >= 0) {
                    throw new IOException("The .steinmod archive contains an unsafe path.");
                }
                if (entry.getSize() > 0) {
                    expanded += entry.getSize();
                    if (expanded > MAX_EXPANDED_BYTES) {
                        throw new IOException("The .steinmod archive expands beyond the 256 MiB safety limit.");
                    }
                }
            }
            JsonNode manifest;
            try (InputStream input = zip.getInputStream(metadata)) {
                byte[] manifestBytes = input.readNBytes(1024 * 1024 + 1);
                if (manifestBytes.length > 1024 * 1024) {
                    throw new IOException("The embedded Stein manifest exceeds the 1 MiB size limit.");
                }
                manifest = JSON.readTree(manifestBytes);
            }
            if (manifest == null
                    || !release.modId().equals(requiredText(manifest, "id"))
                    || !release.version().equals(requiredText(manifest, "version"))) {
                throw new IOException("The .steinmod manifest identity or version does not match its source index.");
            }
            String minimumLoader = requiredText(manifest, "loader");
            if (compareVersions(SteinInstaller.LOADER_VERSION, minimumLoader) < 0) {
                throw new IOException("This mod requires Stein Loader " + minimumLoader
                        + " or newer; this instance has " + SteinInstaller.LOADER_VERSION + ".");
            }
        }
    }

    static int compareVersions(String left, String right) throws IOException {
        try {
            String[] a = left.split("\\.");
            String[] b = right.split("\\.");
            for (int i = 0; i < Math.max(a.length, b.length); i++) {
                int av = i < a.length ? Integer.parseInt(a[i]) : 0;
                int bv = i < b.length ? Integer.parseInt(b[i]) : 0;
                if (av != bv) {
                    return Integer.compare(av, bv);
                }
            }
            return 0;
        } catch (NumberFormatException invalid) {
            throw new IOException("Invalid numeric Stein Loader version.", invalid);
        }
    }

    private static String requiredText(JsonNode node, String field) throws IOException {
        String value = node == null ? "" : node.path(field).asText("").trim();
        if (value.isBlank()) {
            throw new IOException("Stein source is missing required field '" + field + "'.");
        }
        return value;
    }

    private static String validateHttpsUrl(String value) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException("Stein source URL cannot be empty.");
        }
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IOException("Stein source and download URLs must be valid HTTPS URLs.");
            }
            return uri.normalize().toString();
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid Stein source URL.", invalid);
        }
    }

    private static String optionalHttpsUrl(String value) throws IOException {
        return value == null || value.isBlank() ? "" : validateHttpsUrl(value);
    }

    private static String safeFilename(String filename) throws IOException {
        if (filename == null || filename.isBlank() || filename.length() > 180
                || !filename.matches("[A-Za-z0-9._ -]+\\.steinmod(?:\\.disabled)?")
                || filename.equals(".steinmod") || filename.equals(".steinmod.disabled")) {
            throw new IOException("Stein source contains an invalid .steinmod filename.");
        }
        return filename;
    }

    private static Path trackedPath(Path modsDirectory, String filename) throws IOException {
        String safe = safeFilename(filename);
        Path resolved = modsDirectory.resolve(safe).normalize();
        if (!resolved.startsWith(modsDirectory)) {
            throw new IOException("Stein installed-mod registry contains an unsafe filename.");
        }
        return resolved;
    }

    public static void updateTrackedFilename(Instance instance, String oldFilename, String newFilename)
            throws IOException {
        requireSteinInstance(instance);
        String oldSafe = safeFilename(oldFilename);
        String newSafe = safeFilename(newFilename);
        List<Installed> installed = new ArrayList<>(loadInstalled(instance));
        for (int i = 0; i < installed.size(); i++) {
            Installed item = installed.get(i);
            if (oldSafe.equals(item.file())) {
                installed.set(i, new Installed(item.id(), item.modId(), item.name(),
                        newSafe, item.version(), item.sha256(), item.sourceUrl()));
                writeJsonAtomically(stateFile(instance, INSTALLED_FILE), installed);
                return;
            }
        }
    }

    public static void removeTrackedFile(Instance instance, String filename) throws IOException {
        requireSteinInstance(instance);
        String safe = safeFilename(filename);
        for (Installed item : loadInstalled(instance)) {
            if (safe.equals(item.file())) {
                removeInstalled(instance, item.id());
                return;
            }
        }
        Path modsDirectory = instance.getDirectory().resolve("mods").toAbsolutePath().normalize();
        Path file = trackedPath(modsDirectory, safe);
        if (Files.isSymbolicLink(file)) {
            throw new IOException("Refusing to remove a symbolic link from the mods directory.");
        }
        Files.deleteIfExists(file);
    }

    private static List<SourceRecord> readSourceRecords(Instance instance) throws IOException {
        Path file = stateFile(instance, SOURCES_FILE);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return new ArrayList<>();
        }
        List<SourceRecord> records = JSON.readValue(file.toFile(),
                new TypeReference<List<SourceRecord>>() { });
        return records == null ? new ArrayList<>() : new ArrayList<>(records);
    }

    private static Path stateFile(Instance instance, String filename) throws IOException {
        requireSteinInstance(instance);
        Path directory = instance.getDirectory().resolve("stein").toAbsolutePath().normalize();
        Files.createDirectories(directory);
        return directory.resolve(filename);
    }

    private static void requireSteinInstance(Instance instance) {
        if (instance == null || instance.getDirectory() == null
                || !"Stein".equalsIgnoreCase(instance.getLoader())
                || !SteinInstaller.MINECRAFT_VERSION.equals(instance.getMinecraftVersion())) {
            throw new IllegalArgumentException("A Minecraft 1.8.9 Stein instance is required.");
        }
    }

    private static void writeJsonAtomically(Path destination, Object value) throws IOException {
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            JSON.writeValue(temporary.toFile(), value);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void move(Path source, Path destination, boolean replace) throws IOException {
        try {
            if (replace) {
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            if (replace) {
                Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, destination);
            }
        }
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }
}
