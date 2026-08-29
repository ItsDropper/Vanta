package org.example.launcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.launcher.model.VersionManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class VersionReader {

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    public static VersionManifest readVersion(
            Path instanceDirectory
    ) {

        Path json =
                instanceDirectory
                        .resolve("minecraft")
                        .resolve("version.json");

        if (!Files.exists(json)) {
            return null;
        }

        try {

            JsonNode node =
                    MAPPER.readTree(
                            json.toFile()
                    );

            VersionManifest manifest =
                    MAPPER.treeToValue(
                            node,
                            VersionManifest.class
                    );

            /*
             * Fabric profiles inherit the Minecraft version.
             *
             * The launcher currently stores the complete
             * merged profile, so inheritance should normally
             * already be resolved during installation.
             *
             * This check makes failures explicit instead of
             * producing a mysterious launch error.
             */
            if (manifest.mainClass == null
                    || manifest.mainClass.isBlank()) {

                throw new IllegalStateException(
                        "Version metadata has no mainClass."
                );
            }

            if (manifest.libraries == null) {

                throw new IllegalStateException(
                        "Version metadata has no libraries."
                );
            }

            return manifest;

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Failed to read version metadata: "
                            + json,
                    e
            );
        }
    }
}