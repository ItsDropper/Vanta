package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiftModMetadataCompatibilityTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultsMissingListenerSidesAndPreservesOriginalArchive() throws Exception {
        Path mods = Files.createDirectory(temporaryDirectory.resolve("mods"));
        Path mod = mods.resolve("rei.jar");
        byte[] original = createModJar(mod, """
                {
                  "id": "roughlyenoughitems",
                  "listeners": [
                    {"class": "example.First", "priority": -15},
                    {"class": "example.Second", "priority": 50, "side": "client"},
                    {"class": "example.Third", "priority": 50, "side": null}
                  ]
                }
                """);

        assertEquals(1, RiftModMetadataCompatibility.normalizeModsDirectory(mods));
        assertTrue(Files.exists(mods.resolve("rei.jar.vanta-original")));
        assertTrue(java.util.Arrays.equals(original,
                Files.readAllBytes(mods.resolve("rei.jar.vanta-original"))));

        try (ZipFile jar = new ZipFile(mod.toFile());
             InputStream input = jar.getInputStream(jar.getEntry("riftmod.json"))) {
            JsonNode metadata = MAPPER.readTree(input);
            assertEquals("both", metadata.path("listeners").get(0).path("side").asText());
            assertEquals("client", metadata.path("listeners").get(1).path("side").asText());
            assertEquals("both", metadata.path("listeners").get(2).path("side").asText());
            assertTrue(jar.getEntry("example/Keep.class") != null);
        }

        assertEquals(0, RiftModMetadataCompatibility.normalizeModsDirectory(mods));
    }

    @Test
    void ignoresJarsWithoutRiftMetadata() throws Exception {
        Path mods = Files.createDirectory(temporaryDirectory.resolve("mods"));
        Path mod = mods.resolve("ordinary.jar");
        createModJar(mod, null);

        assertEquals(0, RiftModMetadataCompatibility.normalizeModsDirectory(mods));
        assertFalse(Files.exists(mods.resolve("ordinary.jar.vanta-original")));
    }

    private byte[] createModJar(Path path, String metadata) throws Exception {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(path))) {
            if (metadata != null) {
                output.putNextEntry(new ZipEntry("riftmod.json"));
                output.write(metadata.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.closeEntry();
            }
            output.putNextEntry(new ZipEntry("example/Keep.class"));
            output.write(new byte[]{1, 2, 3});
            output.closeEntry();
        }
        return Files.readAllBytes(path);
    }
}
