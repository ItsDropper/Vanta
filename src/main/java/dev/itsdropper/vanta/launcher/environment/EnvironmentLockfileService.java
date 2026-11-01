package dev.itsdropper.vanta.launcher.environment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.itsdropper.vanta.launcher.MinecraftLocator;
import dev.itsdropper.vanta.launcher.LibraryResolver;
import dev.itsdropper.vanta.launcher.VersionReader;
import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.model.Library;
import dev.itsdropper.vanta.launcher.model.VersionManifest;
import dev.itsdropper.vanta.launcher.modrinth.InstalledMod;
import dev.itsdropper.vanta.launcher.modrinth.InstalledModScanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.OpenOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Captures and persists a portable lockfile for the observable portion of an instance.
 *
 * <p>Capture is read-only. It never downloads, modifies, removes, or repairs instance
 * files. Call it from a background executor because hashing can be I/O intensive.</p>
 */
public final class EnvironmentLockfileService {
    private static final String LOCKFILE_DIRECTORY = ".vanta";
    private static final String LOCKFILE_NAME = "environment.lock.json";
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final int MAX_MOD_FILES = 10_000;
    private static final int MAX_ENVIRONMENT_FILES = 20_000;
    private static final List<String> INSTANCE_ENVIRONMENT_DIRECTORIES = List.of("resourcepacks", "shaderpacks", "config");
    private static final Set<String> ENVIRONMENT_CATEGORIES = Set.of(
            "resourcepacks", "shaderpacks", "config", "minecraft", "libraries", "natives");
    private static final Set<Integer> SUPPORTED_SCHEMA_VERSIONS = Set.of(1, 2, EnvironmentLockfile.CURRENT_SCHEMA_VERSION);

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final InstalledModScanner modScanner;

    public EnvironmentLockfileService() {
        this(new InstalledModScanner());
    }

    public EnvironmentLockfileService(InstalledModScanner modScanner) {
        this.modScanner = java.util.Objects.requireNonNull(modScanner, "modScanner");
    }

    public EnvironmentLockfile capture(Instance instance) throws IOException {
        validateInstance(instance);

        Path instanceDirectory = instance.getDirectory().toAbsolutePath().normalize();
        Path modsDirectory = instanceDirectory.resolve("mods");

        if (Files.exists(modsDirectory, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(modsDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Instance mods path is not a directory: " + modsDirectory);
        }
        if (Files.isSymbolicLink(modsDirectory)) {
            throw new IOException("Refusing to scan a symbolic-link mods directory.");
        }

        List<EnvironmentLockfile.ModArtifact> artifacts = new ArrayList<>();
        if (Files.isDirectory(modsDirectory, LinkOption.NOFOLLOW_LINKS)) {
            List<Path> files;
            try (var stream = Files.list(modsDirectory)) {
                files = stream
                        .filter(path -> !Files.isSymbolicLink(path))
                        .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .limit(MAX_MOD_FILES + 1L)
                        .sorted(Comparator.comparing(
                                path -> path.getFileName().toString(),
                                String.CASE_INSENSITIVE_ORDER
                        )).toList();
            }
            if (files.size() > MAX_MOD_FILES) {
                throw new IOException("Instance contains more than " + MAX_MOD_FILES
                        + " mod-directory files; refusing an incomplete baseline.");
            }

            for (Path file : files) {
                InstalledMod metadata = modScanner.scanFile(file);
                FileFingerprint fingerprint = fingerprint(file);
                artifacts.add(new EnvironmentLockfile.ModArtifact(
                        file.getFileName().toString(),
                        fingerprint.sizeBytes(),
                        fingerprint.sha256(),
                        metadata == null ? null : metadata.getModId(),
                        metadata == null ? null : metadata.getVersion()
                ));
            }
        }

        List<EnvironmentLockfile.EnvironmentArtifact> environmentFiles = new ArrayList<>(
                captureEnvironmentFiles(instanceDirectory));
        environmentFiles.addAll(captureRuntimeArtifacts(instanceDirectory));
        if (environmentFiles.size() > MAX_ENVIRONMENT_FILES) {
            throw new IOException("Environment contains more than " + MAX_ENVIRONMENT_FILES
                    + " tracked files; refusing an incomplete baseline.");
        }
        environmentFiles.sort(Comparator
                .comparing(EnvironmentLockfile.EnvironmentArtifact::category)
                .thenComparing(EnvironmentLockfile.EnvironmentArtifact::relativePath,
                        String.CASE_INSENSITIVE_ORDER));

        EnvironmentLockfile lockfile = new EnvironmentLockfile(
                EnvironmentLockfile.CURRENT_SCHEMA_VERSION,
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                instance.getId(),
                instance.getMinecraftVersion(),
                instance.getLoader(),
                instance.getLoaderVersion(),
                null,
                artifacts,
                environmentFiles
        );
        validateLockfile(lockfile);
        return lockfile;
    }

    /**
     * Compares a live instance with a previously captured lockfile.
     * The comparison is read-only and reports only observed drift; it never reconciles files.
     */
    public EnvironmentDriftReport compare(
            Instance instance,
            EnvironmentLockfile baseline
    ) throws IOException {
        validateInstance(instance);
        validateLockfile(baseline);
        if (!baseline.instanceId().equals(instance.getId())) {
            throw new IOException("The saved environment baseline belongs to a different instance.");
        }
        EnvironmentLockfile current = capture(instance);
        validateLockfile(current);
        List<EnvironmentDriftReport.Change> changes = new ArrayList<>();

        if (!baseline.minecraftVersion().equals(current.minecraftVersion())) {
            changes.add(new EnvironmentDriftReport.Change(
                    EnvironmentDriftReport.ChangeType.MINECRAFT_VERSION_CHANGED,
                    null, null, null,
                    "Minecraft version changed from " + baseline.minecraftVersion()
                            + " to " + current.minecraftVersion()
            ));
        }

        String expectedLoader = loaderIdentity(baseline.loader(), baseline.loaderVersion());
        String actualLoader = loaderIdentity(current.loader(), current.loaderVersion());
        if (!expectedLoader.equals(actualLoader)) {
            changes.add(new EnvironmentDriftReport.Change(
                    EnvironmentDriftReport.ChangeType.LOADER_CHANGED,
                    null, null, null,
                    "Loader changed from " + expectedLoader + " to " + actualLoader
            ));
        }

        java.util.Map<String, EnvironmentLockfile.ModArtifact> expected = new java.util.TreeMap<>();
        for (EnvironmentLockfile.ModArtifact artifact : baseline.mods()) {
            expected.put(artifact.filename().toLowerCase(java.util.Locale.ROOT), artifact);
        }

        java.util.Map<String, EnvironmentLockfile.ModArtifact> actual = new java.util.TreeMap<>();
        for (EnvironmentLockfile.ModArtifact artifact : current.mods()) {
            actual.put(artifact.filename().toLowerCase(java.util.Locale.ROOT), artifact);
        }

        for (var entry : expected.entrySet()) {
            EnvironmentLockfile.ModArtifact observed = actual.get(entry.getKey());
            EnvironmentLockfile.ModArtifact recorded = entry.getValue();
            if (observed == null) {
                changes.add(new EnvironmentDriftReport.Change(
                        EnvironmentDriftReport.ChangeType.MOD_REMOVED,
                        recorded.filename(), recorded.sha256(), null,
                        "Previously recorded mod file is missing"
                ));
            } else if (!recorded.sha256().equalsIgnoreCase(observed.sha256())) {
                changes.add(new EnvironmentDriftReport.Change(
                        EnvironmentDriftReport.ChangeType.MOD_MODIFIED,
                        observed.filename(), recorded.sha256(), observed.sha256(),
                        "File contents differ from the recorded SHA-256"
                ));
            }
        }

        for (var entry : actual.entrySet()) {
            if (!expected.containsKey(entry.getKey())) {
                EnvironmentLockfile.ModArtifact artifact = entry.getValue();
                changes.add(new EnvironmentDriftReport.Change(
                        EnvironmentDriftReport.ChangeType.MOD_ADDED,
                        artifact.filename(), null, artifact.sha256(),
                        "File is present but was not recorded in the lockfile"
                ));
            }
        }


        java.util.Map<String, EnvironmentLockfile.EnvironmentArtifact> expectedFiles =
                environmentFileMap(baseline.environmentFiles());
        java.util.Map<String, EnvironmentLockfile.EnvironmentArtifact> actualFiles =
                environmentFileMap(current.environmentFiles());

        for (var entry : expectedFiles.entrySet()) {
            EnvironmentLockfile.EnvironmentArtifact recorded = entry.getValue();
            EnvironmentLockfile.EnvironmentArtifact observed = actualFiles.get(entry.getKey());
            String displayPath = recorded.category() + "/" + recorded.relativePath();
            if (observed == null) {
                changes.add(new EnvironmentDriftReport.Change(
                        EnvironmentDriftReport.ChangeType.ENVIRONMENT_FILE_REMOVED,
                        displayPath, recorded.sha256(), null,
                        "Previously recorded " + recorded.category() + " file is missing"
                ));
            } else if (!recorded.sha256().equalsIgnoreCase(observed.sha256())) {
                changes.add(new EnvironmentDriftReport.Change(
                        EnvironmentDriftReport.ChangeType.ENVIRONMENT_FILE_MODIFIED,
                        displayPath, recorded.sha256(), observed.sha256(),
                        "Recorded " + recorded.category() + " file contents differ"
                ));
            }
        }

        for (var entry : actualFiles.entrySet()) {
            if (!expectedFiles.containsKey(entry.getKey())) {
                EnvironmentLockfile.EnvironmentArtifact artifact = entry.getValue();
                changes.add(new EnvironmentDriftReport.Change(
                        EnvironmentDriftReport.ChangeType.ENVIRONMENT_FILE_ADDED,
                        artifact.category() + "/" + artifact.relativePath(),
                        null, artifact.sha256(),
                        "Unrecorded " + artifact.category() + " file is present"
                ));
            }
        }

        return new EnvironmentDriftReport(
                baseline.generatedAt(),
                current.generatedAt(),
                changes
        );
    }


    /**
     * Captures selected user-visible environment files without following symbolic links.
     * Worlds, logs, caches, and Vanta's own metadata are deliberately excluded.
     */
    private static List<EnvironmentLockfile.EnvironmentArtifact> captureEnvironmentFiles(
            Path instanceDirectory
    ) throws IOException {
        List<EnvironmentLockfile.EnvironmentArtifact> artifacts = new ArrayList<>();
        for (String category : INSTANCE_ENVIRONMENT_DIRECTORIES) {
            Path root = instanceDirectory.resolve(category);
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            if (Files.isSymbolicLink(root)
                    || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Instance " + category + " path must be a real directory.");
            }

            try (var stream = Files.walk(root)) {
                List<Path> files = stream
                        .filter(path -> !path.equals(root))
                        .filter(path -> !Files.isSymbolicLink(path))
                        .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .limit(MAX_ENVIRONMENT_FILES - artifacts.size() + 1L)
                        .sorted(Comparator.comparing(
                                path -> root.relativize(path).toString().replace(java.io.File.separatorChar, '/'),
                                String.CASE_INSENSITIVE_ORDER
                        ))
                        .toList();

                if (files.size() > MAX_ENVIRONMENT_FILES - artifacts.size()) {
                    throw new IOException("Environment contains more than "
                            + MAX_ENVIRONMENT_FILES + " tracked files; refusing an incomplete baseline.");
                }

                for (Path file : files) {
                    if (artifacts.size() >= MAX_ENVIRONMENT_FILES) {
                        throw new IOException("Environment contains more than "
                                + MAX_ENVIRONMENT_FILES + " tracked files; refusing an incomplete baseline.");
                    }
                    String relativePath = root.relativize(file).toString().replace(java.io.File.separatorChar, '/');
                    FileFingerprint fingerprint = fingerprint(file);
                    artifacts.add(new EnvironmentLockfile.EnvironmentArtifact(
                            category,
                            relativePath,
                            fingerprint.sizeBytes(),
                            fingerprint.sha256()
                    ));
                }
            }
        }
        artifacts.sort(Comparator
                .comparing(EnvironmentLockfile.EnvironmentArtifact::category)
                .thenComparing(EnvironmentLockfile.EnvironmentArtifact::relativePath,
                        String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(artifacts);
    }

    /**
     * Captures the instance's Minecraft client and the exact platform-resolved
     * classpath/native artifacts referenced by its installed version manifest.
     * This is read-only: the resolver validates existing files and never downloads them.
     */
    private static List<EnvironmentLockfile.EnvironmentArtifact> captureRuntimeArtifacts(
            Path instanceDirectory
    ) throws IOException {
        Path minecraftDirectory = instanceDirectory.resolve("minecraft");
        Path versionFile = minecraftDirectory.resolve("version.json");
        if (!Files.exists(versionFile, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        if (Files.isSymbolicLink(minecraftDirectory)
                || !Files.isDirectory(minecraftDirectory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(versionFile)
                || !Files.isRegularFile(versionFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Minecraft version metadata must be a regular file inside a real minecraft directory.");
        }

        VersionManifest manifest;
        try {
            manifest = VersionReader.readVersion(instanceDirectory);
        } catch (RuntimeException failure) {
            throw new IOException("Unable to read the installed Minecraft runtime manifest.", failure);
        }
        if (manifest == null || manifest.libraries == null) {
            throw new IOException("Installed Minecraft runtime manifest has no library list.");
        }

        List<EnvironmentLockfile.EnvironmentArtifact> artifacts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        addRuntimeArtifact(artifacts, seen, "minecraft", minecraftDirectory, versionFile);

        Path clientJar = minecraftDirectory.resolve("client.jar");
        if (!Files.isRegularFile(clientJar, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(clientJar)) {
            throw new IOException("Minecraft client JAR is missing or is not a regular file.");
        }
        addRuntimeArtifact(artifacts, seen, "minecraft", minecraftDirectory, clientJar);

        if (!manifest.libraries.isEmpty()) {
            Path vantaDirectory = MinecraftLocator.getVantaDirectoryPath();
            Path librariesDirectory = MinecraftLocator.getLibrariesDirectoryPath()
                    .toAbsolutePath().normalize();

            for (Library library : manifest.libraries) {
                if (library == null) {
                    continue;
                }
                try {
                    Path classpathArtifact = LibraryResolver.resolve(vantaDirectory, library);
                    if (classpathArtifact != null) {
                        addRuntimeArtifact(artifacts, seen, "libraries", librariesDirectory,
                                classpathArtifact);
                    }

                    Path nativeArtifact = LibraryResolver.resolveNative(vantaDirectory, library);
                    if (nativeArtifact != null) {
                        addRuntimeArtifact(artifacts, seen, "natives", librariesDirectory,
                                nativeArtifact);
                    }
                } catch (IllegalStateException failure) {
                    String name = library.name == null || library.name.isBlank()
                            ? "<unnamed library>" : library.name;
                    throw new IOException("Unable to resolve installed runtime dependency " + name + ".", failure);
                }
            }
        }

        return List.copyOf(artifacts);
    }

    private static void addRuntimeArtifact(
            List<EnvironmentLockfile.EnvironmentArtifact> artifacts,
            Set<String> seen,
            String category,
            Path root,
            Path file
    ) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedFile = file.toAbsolutePath().normalize();
        if (!normalizedFile.startsWith(normalizedRoot)) {
            throw new IOException("Resolved runtime artifact escapes its managed directory.");
        }
        String relativePath = normalizedRoot.relativize(normalizedFile)
                .toString().replace(java.io.File.separatorChar, '/');
        String identity = (category + "/" + relativePath).toLowerCase(java.util.Locale.ROOT);
        if (!seen.add(identity)) {
            return;
        }
        FileFingerprint fingerprint = fingerprint(normalizedFile);
        artifacts.add(new EnvironmentLockfile.EnvironmentArtifact(
                category, relativePath, fingerprint.sizeBytes(), fingerprint.sha256()));
        if (artifacts.size() > MAX_ENVIRONMENT_FILES) {
            throw new IOException("Environment contains more than " + MAX_ENVIRONMENT_FILES
                    + " tracked files; refusing an incomplete baseline.");
        }
    }

    private static java.util.Map<String, EnvironmentLockfile.EnvironmentArtifact> environmentFileMap(
            List<EnvironmentLockfile.EnvironmentArtifact> artifacts
    ) {
        java.util.Map<String, EnvironmentLockfile.EnvironmentArtifact> result = new java.util.TreeMap<>();
        for (EnvironmentLockfile.EnvironmentArtifact artifact : artifacts) {
            result.put((artifact.category() + "/" + artifact.relativePath())
                    .toLowerCase(java.util.Locale.ROOT), artifact);
        }
        return result;
    }

    private static String loaderIdentity(String loader, String version) {
        return loader + " " + (version == null || version.isBlank() ? "(version unknown)" : version);
    }

    /**
     * Captures the instance and atomically replaces its local lockfile.
     * The lockfile is stored under .vanta/ and contains no absolute paths.
     */
    public Path write(Instance instance) throws IOException {
        EnvironmentLockfile lockfile = capture(instance);
        Path instanceDirectory = instance.getDirectory().toAbsolutePath().normalize();
        Path metadataDirectory = instanceDirectory.resolve(LOCKFILE_DIRECTORY);

        if (Files.exists(metadataDirectory, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(metadataDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Vanta metadata path is not a directory.");
        }
        if (Files.isSymbolicLink(metadataDirectory)) {
            throw new IOException("Refusing to write through a symbolic-link Vanta metadata directory.");
        }

        Files.createDirectories(metadataDirectory);
        Path destination = metadataDirectory.resolve(LOCKFILE_NAME);
        if (Files.isSymbolicLink(destination)) {
            throw new IOException("Refusing to replace a symbolic-link environment lockfile.");
        }

        Path temporary = Files.createTempFile(metadataDirectory, "environment-", ".tmp");
        try {
            MAPPER.writeValue(temporary.toFile(), lockfile);
            try {
                Files.move(temporary, destination,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }

        return destination;
    }

    /**
     * Returns the canonical local lockfile path for an instance.
     */
    public Path getLockfilePath(Instance instance) {
        validateInstance(instance);
        return instance.getDirectory().toAbsolutePath().normalize()
                .resolve(LOCKFILE_DIRECTORY)
                .resolve(LOCKFILE_NAME);
    }

    /**
     * Checks whether a regular, non-symbolic-link baseline exists for this instance.
     */
    public boolean hasBaseline(Instance instance) {
        Path path = getLockfilePath(instance);
        Path metadataDirectory = path.getParent();
        return !Files.isSymbolicLink(metadataDirectory)
                && !Files.isSymbolicLink(path)
                && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Reads the saved baseline belonging to an instance.
     */
    public EnvironmentLockfile readBaseline(Instance instance) throws IOException {
        Path path = getLockfilePath(instance);
        if (Files.isSymbolicLink(path.getParent())) {
            throw new IOException("Refusing to read an environment baseline through a symbolic-link metadata directory.");
        }
        return read(path);
    }

    public EnvironmentLockfile read(Path lockfilePath) throws IOException {
        if (lockfilePath == null) {
            throw new IllegalArgumentException("Lockfile path cannot be null.");
        }
        if (Files.isSymbolicLink(lockfilePath)) {
            throw new IOException("Refusing to read a symbolic-link environment lockfile.");
        }

        EnvironmentLockfile lockfile = MAPPER.readValue(
                lockfilePath.toFile(),
                EnvironmentLockfile.class
        );
        validateLockfile(lockfile);
        return lockfile;
    }

    private static void validateInstance(Instance instance) {
        if (instance == null
                || instance.getId() == null || instance.getId().isBlank()
                || instance.getMinecraftVersion() == null || instance.getMinecraftVersion().isBlank()
                || instance.getLoader() == null || instance.getLoader().isBlank()
                || instance.getDirectory() == null) {
            throw new IllegalArgumentException("A complete instance is required to capture an environment.");
        }
        if (!Files.isDirectory(instance.getDirectory(), LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(instance.getDirectory())) {
            throw new IllegalArgumentException("Instance directory must be an existing, non-symbolic-link directory.");
        }
    }

    private static void validateLockfile(EnvironmentLockfile lockfile) throws IOException {
        if (lockfile == null
                || !SUPPORTED_SCHEMA_VERSIONS.contains(lockfile.schemaVersion())
                || blank(lockfile.generatedAt())
                || blank(lockfile.instanceId())
                || blank(lockfile.minecraftVersion())
                || blank(lockfile.loader())
                || lockfile.mods() == null) {
            throw new IOException("Environment lockfile is incomplete or uses an unsupported schema version.");
        }

        Set<String> filenames = new HashSet<>();
        for (EnvironmentLockfile.ModArtifact artifact : lockfile.mods()) {
            if (artifact == null
                    || blank(artifact.filename())
                    || artifact.filename().equals(".")
                    || artifact.filename().equals("..")
                    || artifact.filename().contains("/")
                    || artifact.filename().contains("\\")
                    || artifact.filename().contains(":")
                    || artifact.sizeBytes() < 0
                    || artifact.sha256() == null
                    || !artifact.sha256().matches("(?i)[0-9a-f]{64}")
                    || !filenames.add(artifact.filename().toLowerCase(java.util.Locale.ROOT))) {
                throw new IOException("Environment lockfile contains an invalid or duplicate mod artifact.");
            }
        }

        Set<String> environmentPaths = new HashSet<>();
        for (EnvironmentLockfile.EnvironmentArtifact artifact : lockfile.environmentFiles()) {
            if (artifact == null
                    || !ENVIRONMENT_CATEGORIES.contains(artifact.category())
                    || blank(artifact.relativePath())
                    || artifact.relativePath().startsWith("/")
                    || artifact.relativePath().contains("\\")
                    || artifact.relativePath().contains(":")
                    || java.util.Arrays.stream(artifact.relativePath().split("/"))
                            .anyMatch(part -> part.isBlank() || part.equals(".") || part.equals(".."))
                    || artifact.sizeBytes() < 0
                    || artifact.sha256() == null
                    || !artifact.sha256().matches("(?i)[0-9a-f]{64}")
                    || !environmentPaths.add((artifact.category() + "/" + artifact.relativePath())
                            .toLowerCase(java.util.Locale.ROOT))) {
                throw new IOException("Environment lockfile contains an invalid or duplicate environment file.");
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Hashes one regular file without following a final-component symlink and
     * rejects files that visibly change while being read.
     */
    private static FileFingerprint fingerprint(Path file) throws IOException {
        BasicFileAttributes before = Files.readAttributes(
                file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile()) {
            throw new IOException("Tracked environment artifact is no longer a regular file: " + file.getFileName());
        }

        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform.", impossible);
        }

        byte[] buffer = new byte[BUFFER_SIZE];
        Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try (SeekableByteChannel channel = Files.newByteChannel(file, options);
             InputStream input = Channels.newInputStream(channel)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        BasicFileAttributes after = Files.readAttributes(
                file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!after.isRegularFile()
                || before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || (before.fileKey() != null && !before.fileKey().equals(after.fileKey()))) {
            throw new IOException("Tracked environment artifact changed while being hashed: " + file.getFileName());
        }

        return new FileFingerprint(before.size(), HexFormat.of().formatHex(digest.digest()));
    }

    private record FileFingerprint(long sizeBytes, String sha256) {
    }
}
