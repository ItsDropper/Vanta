package org.example.launcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.launcher.model.VersionManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class VersionReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static VersionManifest readVersion(Path minecraftDirectory, String version) {

        Path json = minecraftDirectory
                .resolve("versions")
                .resolve(version)
                .resolve(version + ".json");

        if (!Files.exists(json)) {
            return null;
        }

        try {
            return MAPPER.readValue(json.toFile(), VersionManifest.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}