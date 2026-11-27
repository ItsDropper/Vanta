package dev.itsdropper.vanta.launcher.bundle;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Persists the exact validated manifest used to create a profile instance.
 * Keeping the snapshot local makes later profile inspection and update checks
 * independent of the original import file remaining available.
 */
public final class VantaBundleManifestSnapshotStore {
    public static final String FILE_NAME = "vanta-profile-manifest.json";

    private final ObjectMapper mapper = new ObjectMapper();

    public void save(Instance instance, VantaBundleManifest manifest) throws IOException {
        VantaBundleRepository.validate(manifest);
        Path file = resolveFile(instance);
        byte[] bytes = mapper.writeValueAsBytes(manifest);
        if (bytes.length == 0 || bytes.length > VantaBundleRepository.MAX_MANIFEST_BYTES) {
            throw new IOException("Vanta profile manifest snapshot exceeds the allowed size.");
        }

        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(FILE_NAME + ".tmp-" + UUID.randomUUID());
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public VantaBundleManifest load(Instance instance) throws IOException {
        Path file = resolveFile(instance);
        if (!Files.isRegularFile(file)) return null;
        long size = Files.size(file);
        if (size <= 0 || size > VantaBundleRepository.MAX_MANIFEST_BYTES) {
            throw new IOException("Vanta profile manifest snapshot size is outside the allowed range.");
        }
        return new VantaBundleRepository().parse(Files.readAllBytes(file));
    }

    private static Path resolveFile(Instance instance) throws IOException {
        if (instance == null || instance.getDirectory() == null) {
            throw new IllegalArgumentException("A valid Minecraft instance is required.");
        }
        Path directory = instance.getDirectory().toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) {
            throw new IOException("Minecraft instance directory does not exist.");
        }
        return directory.resolve(FILE_NAME);
    }
}
