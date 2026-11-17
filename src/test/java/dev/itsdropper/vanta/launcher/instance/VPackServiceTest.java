package dev.itsdropper.vanta.launcher.instance;

import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class VPackServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exportsOnlySelectedContentAndAlwaysIncludesInstanceMetadata() throws Exception {
        Path source = Files.createDirectory(temporaryDirectory.resolve("instance"));
        Instance instance = new Instance("profile-a", "Pack Test", "1.21.11",
                "VantaLoader", "0.1.0", source);
        write(source, "mods/example.jar");
        write(source, "saves/world/level.dat");
        write(source, "config/mod.json");
        write(source, "resourcepacks/example.zip");
        write(source, "shaderpacks/example.zip");
        write(source, "screenshots/example.png");
        write(source, "options.txt");
        write(source, "custom/extra.dat");
        write(source, "logs/latest.log");
        write(source, "natives/native.dll");

        Path archive = temporaryDirectory.resolve("selected.vpack");
        VPackService.createVPack(instance, archive,
                new VPackService.Options(true, false, true, false, false, false, true, false));

        VPackService.Verification report = VPackService.verifyVPack(archive);
        Set<String> names = readNames(archive);

        assertEquals("Pack Test", report.instanceName());
        assertEquals("VantaLoader", report.loader());
        assertEquals(Set.of("mods", "configs", "game-settings"), report.includedCategories());
        assertTrue(names.contains("instance.json"));
        assertTrue(names.contains("vanta-pack.json"));
        assertTrue(names.contains("mods/example.jar"));
        assertTrue(names.contains("config/mod.json"));
        assertTrue(names.contains("options.txt"));
        assertFalse(names.contains("saves/world/level.dat"));
        assertFalse(names.contains("resourcepacks/example.zip"));
        assertFalse(names.contains("shaderpacks/example.zip"));
        assertFalse(names.contains("screenshots/example.png"));
        assertFalse(names.contains("custom/extra.dat"));
        assertFalse(names.contains("logs/latest.log"));
        assertFalse(names.contains("natives/native.dll"));
    }

    @Test
    void rejectsAnExportWithNoSelectedContentGroups() throws Exception {
        Path source = Files.createDirectory(temporaryDirectory.resolve("instance"));
        Instance instance = new Instance("profile-a", "Pack Test", "1.21.11",
                "Fabric", "0.16.0", source);

        assertThrows(IOException.class, () -> VPackService.createVPack(instance,
                temporaryDirectory.resolve("empty.vpack"),
                new VPackService.Options(false, false, false, false, false, false, false, false)));
        assertFalse(Files.exists(temporaryDirectory.resolve("empty.vpack")));
    }

    @Test
    void rejectsSymbolicLinksInsteadOfSilentlyPackagingExternalData() throws Exception {
        Path source = Files.createDirectory(temporaryDirectory.resolve("instance"));
        Path outside = Files.writeString(temporaryDirectory.resolve("outside.txt"), "private");
        try {
            Files.createSymbolicLink(source.resolve("external.txt"), outside);
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            org.junit.jupiter.api.Assumptions.abort("Symbolic links are unavailable on this test host.");
        }
        Instance instance = new Instance("profile-a", "Pack Test", "1.21.11",
                "Fabric", "0.16.0", source);

        VPackService.Options includeOtherFiles =
                new VPackService.Options(true, true, true, true, true, false, true, true);
        assertThrows(IOException.class, () -> VPackService.createVPack(instance,
                temporaryDirectory.resolve("linked.vpack"), includeOtherFiles));
        assertFalse(Files.exists(temporaryDirectory.resolve("linked.vpack")));
    }

    @Test
    void ignoresSymlinksInsideContentGroupsThatWereNotSelected() throws Exception {
        Path source = Files.createDirectory(temporaryDirectory.resolve("instance"));
        Path outside = Files.writeString(temporaryDirectory.resolve("outside.txt"), "not selected");
        Files.createDirectories(source.resolve("screenshots"));
        try {
            Files.createSymbolicLink(source.resolve("screenshots/external.png"), outside);
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            org.junit.jupiter.api.Assumptions.abort("Symbolic links are unavailable on this test host.");
        }
        Instance instance = new Instance("profile-a", "Pack Test", "1.21.11",
                "Fabric", "0.16.0", source);

        Path archive = temporaryDirectory.resolve("without-screenshots.vpack");
        VPackService.createVPack(instance, archive, VPackService.Options.defaults());

        assertTrue(VPackService.verifyVPack(archive).includedCategories().contains("mods"));
        assertFalse(readNames(archive).contains("screenshots/external.png"));
    }

    @Test
    void rejectsZipBackupsThatAreNotVantaPacks() throws Exception {
        Path source = Files.createDirectory(temporaryDirectory.resolve("instance"));
        Instance instance = new Instance("profile-a", "Pack Test", "1.21.11",
                "Fabric", "0.16.0", source);
        Path archive = temporaryDirectory.resolve("backup.zip");
        new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter()
                .writeValue(source.resolve("instance.json").toFile(), instance);
        InstanceBackupService.createBackup(instance, archive);

        assertThrows(IOException.class, () -> VPackService.verifyVPack(archive));
    }

    @Test
    void rejectsArchiveEntriesOutsideTheDeclaredSelection() throws Exception {
        Path source = Files.createDirectory(temporaryDirectory.resolve("instance"));
        Instance instance = new Instance("profile-a", "Pack Test", "1.21.11",
                "Fabric", "0.16.0", source);
        write(source, "mods/example.jar");
        Path archive = temporaryDirectory.resolve("tampered.vpack");
        VPackService.createVPack(instance, archive,
                new VPackService.Options(true, false, false, false, false, false, false, false));

        Path rewritten = temporaryDirectory.resolve("tampered-rewritten.vpack");
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive));
             java.util.zip.ZipOutputStream output = new java.util.zip.ZipOutputStream(Files.newOutputStream(rewritten))) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = input.getNextEntry()) != null) {
                output.putNextEntry(new ZipEntry(entry.getName()));
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                output.closeEntry();
                input.closeEntry();
            }
            output.putNextEntry(new ZipEntry("saves/world/level.dat"));
            output.write("unselected".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }

        assertThrows(IOException.class, () -> VPackService.verifyVPack(rewritten));
    }

    private static void write(Path root, String relative) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, relative);
    }

    private static Set<String> readNames(Path archive) throws IOException {
        Set<String> names = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                while (zip.read() != -1) { }
                zip.closeEntry();
            }
        }
        return names;
    }
}
