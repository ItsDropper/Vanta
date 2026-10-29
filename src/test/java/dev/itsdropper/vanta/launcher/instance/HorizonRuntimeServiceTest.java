package dev.itsdropper.vanta.launcher.instance;

import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

final class HorizonRuntimeServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void profileIdIsStableAndDoesNotExposeTheInstanceName() {
        Instance first = instance("my-private-world-name");
        Instance same = instance("my-private-world-name");
        Instance other = instance("another-instance");

        String firstId = HorizonRuntimeService.stableProfileId(first);
        assertEquals(firstId, HorizonRuntimeService.stableProfileId(same));
        assertNotEquals(firstId, HorizonRuntimeService.stableProfileId(other));
        assertTrue(firstId.matches("vanta-[0-9a-f]{32}"));
        assertFalse(firstId.contains(first.getName()));
    }

    @Test
    void safelyExtractsNormalArchiveEntries() throws Exception {
        Path archive = tempDir.resolve("normal.zip");
        Path destination = tempDir.resolve("unpacked");
        writeZip(archive, "bin/readme.txt", "verified archive");

        HorizonRuntimeService.extractSafely(archive, destination);

        assertEquals("verified archive",
                Files.readString(destination.resolve("bin/readme.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsArchivePathTraversal() throws Exception {
        Path archive = tempDir.resolve("traversal.zip");
        Path destination = tempDir.resolve("unpacked");
        writeZip(archive, "../outside.txt", "must not escape");

        assertThrows(IOException.class,
                () -> HorizonRuntimeService.extractSafely(archive, destination));
        assertFalse(Files.exists(tempDir.resolve("outside.txt")));
    }

    private static Instance instance(String id) {
        return new Instance(id, id, "26.3", "Horizon", "",
                Path.of("instances", id), "SHIELD", Instance.ENGINE_HORIZON);
    }

    private static void writeZip(Path archive, String entryName, String contents) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(contents.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }
}
