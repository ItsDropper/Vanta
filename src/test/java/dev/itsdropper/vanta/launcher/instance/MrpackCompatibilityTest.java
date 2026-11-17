package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class MrpackCompatibilityTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void forgeExportContainsThePinnedForgeDependency() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("forge-instance"));
        Instance instance = new Instance("forge", "Forge Pack", "1.20.1",
                "Forge", "47.2.0", instanceDirectory);
        Path archive = temporaryDirectory.resolve("forge.mrpack");

        MrpackExporter.exportMrpack(instance, archive);

        JsonNode index = readIndex(archive);
        assertEquals("1.20.1", index.path("dependencies").path("minecraft").asText());
        assertEquals("47.2.0", index.path("dependencies").path("forge").asText());
    }

    @Test
    void refusesToMislabelUnsupportedLoaderAsVanilla() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("vanta-instance"));
        Instance instance = new Instance("vanta", "Vanta Pack", "1.21.11",
                "VantaLoader", "0.1.0", instanceDirectory);
        Path archive = temporaryDirectory.resolve("vanta.mrpack");

        java.io.IOException failure = assertThrows(java.io.IOException.class,
                () -> MrpackExporter.exportMrpack(instance, archive));

        assertTrue(failure.getMessage().contains("cannot be represented"));
        assertTrue(failure.getMessage().contains("full instance backup"));
        assertFalse(Files.exists(archive));
    }

    @Test
    void rejectsUnknownLoaderDependencyInsteadOfImportingAsVanilla() throws Exception {
        JsonNode index = MAPPER.readTree("""
                {
                  "formatVersion": 1,
                  "game": "minecraft",
                  "versionId": "1.21.1",
                  "name": "Unsupported",
                  "dependencies": {
                    "minecraft": "1.21.1",
                    "vanta-loader": "0.1.0"
                  },
                  "files": []
                }
                """);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> MrpackInstaller.validateIndex(index));

        assertTrue(failure.getMessage().contains("unsupported loader dependency"));
    }

    @Test
    void rejectsManifestsThatDeclareMultipleLoaders() throws Exception {
        JsonNode index = MAPPER.readTree("""
                {
                  "formatVersion": 1,
                  "game": "minecraft",
                  "versionId": "1.20.1",
                  "name": "Conflicting",
                  "dependencies": {
                    "minecraft": "1.20.1",
                    "fabric-loader": "0.15.0",
                    "forge": "47.2.0"
                  },
                  "files": []
                }
                """);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> MrpackInstaller.validateIndex(index));

        assertTrue(failure.getMessage().contains("multiple competing"));
    }

    private static JsonNode readIndex(Path archive) throws Exception {
        try (InputStream input = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("modrinth.index.json".equals(entry.getName())) {
                    return MAPPER.readTree(zip);
                }
            }
        }
        throw new AssertionError("Exported archive has no modrinth.index.json");
    }
}
