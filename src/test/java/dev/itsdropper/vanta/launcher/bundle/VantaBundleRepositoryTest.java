package dev.itsdropper.vanta.launcher.bundle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VantaBundleRepositoryTest {
    private final VantaBundleRepository repository = new VantaBundleRepository();

    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesAndValidatesVersionedManifest() throws Exception {
        VantaBundleManifest manifest = repository.parse(validManifest());

        assertEquals("vanta-client", manifest.profileId());
        assertEquals("1.0.0", manifest.profileVersion());
        assertEquals("fabric", manifest.loader());
        assertEquals(2, manifest.bundles().size());
    }

    @Test
    void rejectsUnknownSchemaVersions() {
        IOException failure = assertThrows(IOException.class,
                () -> repository.parse(new String(validManifest()).replace("\"schemaVersion\": 1",
                        "\"schemaVersion\": 2").getBytes()));
        assertTrue(failure.getMessage().contains("Unsupported bundle manifest schema version"));
    }

    @Test
    void rejectsDuplicateBundleIds() {
        String json = new String(validManifest()).replace(
                "\"id\": \"performance\"", "\"id\": \"core\"");
        IOException failure = assertThrows(IOException.class,
                () -> repository.parse(json.getBytes()));
        assertTrue(failure.getMessage().contains("Duplicate bundle id"));
    }

    @Test
    void rejectsUnknownFieldsInsteadOfSilentlyIgnoringTypos() {
        String json = new String(validManifest()).replace("\"schemaVersion\": 1",
                "\"schemaVersion\": 1, \"schemVersion\": 1");
        assertThrows(IOException.class, () -> repository.parse(json.getBytes()));
    }

    @Test
    void rejectsOversizedManifest() {
        byte[] oversized = new byte[VantaBundleRepository.MAX_MANIFEST_BYTES + 1];
        assertThrows(IOException.class, () -> repository.parse(oversized));
    }

    @Test
    void loadsBuiltInVantaFabricStarterWithoutAUserSuppliedFile() throws Exception {
        VantaBundleManifest manifest = repository.loadBundled("profiles/vanta-fabric-starter.json");

        assertEquals("vanta-fabric-starter", manifest.profileId());
        assertEquals("fabric", manifest.loader());
        assertTrue(manifest.minecraftVersions().contains("1.21.11"));
        assertEquals(1, manifest.bundles().size());
        assertEquals("fabric-api", manifest.bundles().get(0).mods().get(0).projectId());
        assertTrue(manifest.bundles().get(0).mods().get(0).required());
    }

    @Test
    void rejectsBundledResourcePathTraversal() {
        assertThrows(IOException.class, () -> repository.loadBundled("profiles/../secrets.json"));
        assertThrows(IOException.class, () -> repository.loadBundled("../profiles/profile.json"));
    }

    @Test
    void loadsManifestFromDisk() throws Exception {
        Path file = temporaryDirectory.resolve("profile.json");
        Files.write(file, validManifest());

        assertEquals("vanta-client", repository.load(file).profileId());
    }

    private static byte[] validManifest() {
        return """
                {
                  "schemaVersion": 1,
                  "profileId": "vanta-client",
                  "profileVersion": "1.0.0",
                  "minecraftVersions": ["1.21.11", "1.21.10"],
                  "loader": "fabric",
                  "bundles": [
                    {
                      "id": "core",
                      "name": "Vanta Core",
                      "description": "Required client foundation.",
                      "required": true,
                      "defaultEnabled": true,
                      "mods": [
                        {
                          "projectId": "vanta-client-project",
                          "versionId": "pinned-version",
                          "required": true,
                          "defaultEnabled": true
                        }
                      ]
                    },
                    {
                      "id": "performance",
                      "name": "Performance",
                      "description": "Optional performance additions.",
                      "required": false,
                      "defaultEnabled": true,
                      "mods": [
                        {
                          "projectId": "sodium-project",
                          "versionId": null,
                          "required": true,
                          "defaultEnabled": true
                        },
                        {
                          "projectId": "optional-project",
                          "versionId": null,
                          "required": false,
                          "defaultEnabled": false
                        }
                      ]
                    }
                  ]
                }
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
