package dev.itsdropper.vanta.launcher.environment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.modrinth.InstalledMod;
import dev.itsdropper.vanta.launcher.modrinth.InstalledModScanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
                files = stream.sorted(Comparator.comparing(
                        path -> path.getFileName().toString(),
                        String.CASE_INSENSITIVE_ORDER
                )).toList();
            }

            for (Path file : files) {
                if (Files.isSymbolicLink(file)
                        || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }

                InstalledMod metadata = modScanner.scanFile(file);
                artifacts.add(new EnvironmentLockfile.ModArtifact(
                        file.getFileName().toString(),
                        Files.size(file),
                        sha256(file),
                        metadata == null ? null : metadata.getModId(),
                        metadata == null ? null : metadata.getVersion()
                ));
            }
        }

        return new EnvironmentLockfile(
                EnvironmentLockfile.CURRENT_SCHEMA_VERSION,
                OffsetDateTime.now(ZoneOffset.UTC).toString(),
                instance.getId(),
                instance.getMinecraftVersion(),
                instance.getLoader(),
                instance.getLoaderVersion(),
                null,
                artifacts
        );
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

        return new EnvironmentDriftReport(
                baseline.generatedAt(),
                current.generatedAt(),
                changes
        );
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
        return !Files.isSymbolicLink(path)
                && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Reads the saved baseline belonging to an instance.
     */
    public EnvironmentLockfile readBaseline(Instance instance) throws IOException {
        return read(getLockfilePath(instance));
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
                || lockfile.schemaVersion() != EnvironmentLockfile.CURRENT_SCHEMA_VERSION
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
                    || artifact.sizeBytes() < 0
                    || artifact.sha256() == null
                    || !artifact.sha256().matches("(?i)[0-9a-f]{64}")
                    || !filenames.add(artifact.filename().toLowerCase(java.util.Locale.ROOT))) {
                throw new IOException("Environment lockfile contains an invalid or duplicate mod artifact.");
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String sha256(Path file) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform.", impossible);
        }

        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = Files.newInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
