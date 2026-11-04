package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public class InstalledModManager {

    private static final ObjectMapper MAPPER =
            new ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT);

    private static final String FILE_NAME =
            "vanta-mods.json";

    public static List<InstalledModRecord> load(
            Instance instance
    ) throws IOException {

        if (instance == null || instance.getDirectory() == null) {
            throw new IllegalArgumentException("A valid instance is required.");
        }

        Path file =
                instance.getDirectory()
                        .resolve(FILE_NAME);

        if (!Files.exists(file)) {
            return new ArrayList<>();
        }

        return MAPPER.readValue(
                file.toFile(),
                new TypeReference<List<InstalledModRecord>>() {}
        );
    }

    public static void save(
            Instance instance,
            List<InstalledModRecord> mods
    ) throws IOException {

        if (instance == null || instance.getDirectory() == null) {
            throw new IllegalArgumentException("A valid instance is required.");
        }
        if (mods == null) {
            throw new IllegalArgumentException("Installed mod records cannot be null.");
        }

        Path file =
                instance.getDirectory()
                        .resolve(FILE_NAME);

        Path temporary = file.resolveSibling(FILE_NAME + ".tmp-" + UUID.randomUUID());
        try {
            MAPPER.writeValue(temporary.toFile(), mods);
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

    public static void add(
            Instance instance,
            InstalledModRecord record
    ) throws IOException {

        List<InstalledModRecord> mods =
                load(instance);

        mods.removeIf(
                existing ->
                        existing.getProjectId()
                                .equals(record.getProjectId())
        );

        mods.add(record);

        save(
                instance,
                mods
        );
    }

    public static InstalledModRecord findByProjectId(
            Instance instance,
            String projectId
    ) throws IOException {

        return load(instance)
                .stream()
                .filter(
                        mod ->
                                projectId.equals(
                                        mod.getProjectId()
                                )
                )
                .findFirst()
                .orElse(null);
    }

    public static void removeByProjectId(
            Instance instance,
            String projectId
    ) throws IOException {

        List<InstalledModRecord> mods =
                load(instance);

        mods.removeIf(
                mod ->
                        projectId.equals(
                                mod.getProjectId()
                        )
        );

        save(
                instance,
                mods
        );
    }
}