package org.example.launcher.instance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.launcher.model.Instance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

        Path file =
                instance.getDirectory()
                        .resolve(FILE_NAME);

        MAPPER.writeValue(
                file.toFile(),
                mods
        );
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