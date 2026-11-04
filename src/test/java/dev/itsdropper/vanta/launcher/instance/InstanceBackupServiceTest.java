package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class InstanceBackupServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createdBackupCanBeVerifiedWithoutExtraction() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("instance"));
        Instance instance = new Instance("test-id", "Test Instance", "1.21.1",
                "fabric", "0.16.0", instanceDirectory);
        new ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValue(instanceDirectory.resolve("instance.json").toFile(), instance);
        Files.createDirectories(instanceDirectory.resolve("mods"));
        Files.writeString(instanceDirectory.resolve("mods/example.jar"), "mod-data");

        Path archive = temporaryDirectory.resolve("test-backup.zip");
        InstanceBackupService.createBackup(instance, archive);

        InstanceBackupService.BackupVerification report =
                InstanceBackupService.verifyBackup(archive);

        assertEquals("Test Instance", report.instanceName());
        assertEquals("1.21.1", report.minecraftVersion());
        assertEquals("fabric", report.loader());
        assertEquals(2, report.entryCount());
        assertTrue(report.expandedBytes() > 0);
        assertTrue(Files.exists(instanceDirectory.resolve("mods/example.jar")));
    }

    @Test
    void rejectsArchiveWithoutVantaInstanceMetadata() throws Exception {
        Path archive = temporaryDirectory.resolve("missing-metadata.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("readme.txt"));
            zip.write("not a Vanta backup".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertThrows(IOException.class, () -> InstanceBackupService.verifyBackup(archive));
    }

    @Test
    void rejectsArchivePathTraversalBeforeRestore() throws Exception {
        Path archive = temporaryDirectory.resolve("unsafe.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("../outside.txt"));
            zip.write("unsafe".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertThrows(IOException.class, () -> InstanceBackupService.verifyBackup(archive));
        assertFalse(Files.exists(temporaryDirectory.resolve("outside.txt")));
    }
}
