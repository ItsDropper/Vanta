package dev.itsdropper.vanta.launcher.bundle;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Atomically persists the Vanta profile state for one Minecraft instance.
 */
public final class VantaBundleStateStore {
    public static final String FILE_NAME = "vanta-profile.json";

    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public VantaBundleState load(Instance instance) throws IOException {
        Path file = resolveFile(instance);
        if (!Files.exists(file)) {
            return null;
        }
        long size = Files.size(file);
        if (size <= 0 || size > VantaBundleRepository.MAX_MANIFEST_BYTES) {
            throw new IOException("Vanta profile state size is outside the allowed range.");
        }
        VantaBundleState state;
        try {
            state = mapper.readValue(file.toFile(), VantaBundleState.class);
        } catch (IOException malformed) {
            throw new IOException("Vanta profile state is invalid.", malformed);
        }
        validate(state);
        return state;
    }

    public void save(Instance instance, VantaBundleState state) throws IOException {
        Path file = resolveFile(instance);
        validate(state);
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(FILE_NAME + ".tmp-" + UUID.randomUUID());
        try {
            mapper.writeValue(temporary.toFile(), state);
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

    /**
     * Reconciles profile ownership against Vanta's explicit Modrinth install registry.
     * Manually installed files are never inferred or added to profile ownership.
     */
    public VantaBundleState reconcileManagedProjects(
            Instance instance,
            java.util.Set<String> registeredProjectIds
    ) throws IOException {
        VantaBundleState state = load(instance);
        if (state == null) return null;

        java.util.Set<String> registered = registeredProjectIds == null
                ? java.util.Set.of() : java.util.Set.copyOf(registeredProjectIds);
        java.util.List<String> reconciled = state.installedManagedProjectIds().stream()
                .filter(registered::contains)
                .toList();
        if (reconciled.equals(state.installedManagedProjectIds())) return state;

        VantaBundleState updated = new VantaBundleState(
                state.schemaVersion(),
                state.profileId(),
                state.profileVersion(),
                state.selectedBundleIds(),
                state.selectedModProjectIds(),
                reconciled,
                state.dismissedOfferVersion()
        );
        save(instance, updated);
        return updated;
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

    private static void validate(VantaBundleState state) throws IOException {
        if (state == null || state.schemaVersion() != VantaBundleState.SUPPORTED_SCHEMA_VERSION) {
            throw new IOException("Unsupported or missing Vanta profile state schema.");
        }
        if (state.profileId() == null || !state.profileId().matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IOException("Invalid Vanta profile id in instance state.");
        }
        if (state.profileVersion() == null || state.profileVersion().isBlank()) {
            throw new IOException("Vanta profile version is required in instance state.");
        }
        validateUniqueValues(state.selectedBundleIds(), "selected bundle ids");
        validateUniqueValues(state.selectedModProjectIds(), "selected mod project ids");
        validateUniqueValues(state.installedManagedProjectIds(), "managed project ids");
        if (state.dismissedOfferVersion() != null && state.dismissedOfferVersion().isBlank()) {
            throw new IOException("Dismissed offer version cannot be blank.");
        }
    }

    private static void validateUniqueValues(java.util.List<String> values, String field) throws IOException {
        Set<String> unique = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank() || !unique.add(value)) {
                throw new IOException("Vanta profile state contains invalid " + field + ".");
            }
        }
    }
}
