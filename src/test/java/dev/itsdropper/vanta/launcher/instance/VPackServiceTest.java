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
        Files.writeString(source.resolve(InstanceMetadataMigrator.BACKUP_FILE_NAME),
                "{\\"directory\\":\\"C:/Users/old-machine/Vanta/instances/profile-a\\"}");

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
        assertFalse(names.contains(InstanceMetadataMigrator.BACKUP_FILE_NAME));
    }

    @Test
    void exportsPortableMetadataAndClearsMachineSpecificJavaPath() throws Exception {
        Path source = Files.createDirectory(temporaryDirectory.resolve("portable-instance"));
        Instance instance = new Instance("portable", "Portable", "1.21.11",
                "Fabric", "0.16.0", source);
        Files.writeString(source.resolve("settings.json"), """
                {
                  "ramMb": 8192,
                  "width": 1920,
                  "height": 1080,
                  "fullscreen": false,
                  "javaArguments": "-Xmx4G",
                  "gameArguments": "",
                  "javaPath": "/home/alice/.local/jdk/bin/java"
                }
                """);
        Path archive = temporaryDirectory.resolve("portable.vpack");

        VPackService.createVPack(instance, archive, VPackService.Options.defaults());

        dev.itsdropper.vanta.json.JsonNode metadata =
                new dev.itsdropper.vanta.json.ObjectMapper().readTree(readEntry(archive, "instance.json"));
        assertTrue(metadata.path("directory").isNull());
        assertFalse(new String(readEntry(archive, "instance.json"),
                java.nio.charset.StandardCharsets.UTF_8).contains(source.toAbsolutePath().toString()));

        dev.itsdropper.vanta.json.JsonNode settings =
                new dev.itsdropper.vanta.json.ObjectMapper().readTree(readEntry(archive, "settings.json"));
        assertEquals("", settings.path("javaPath").asText());
        assertEquals(8192, settings.path("ramMb").asInt());
        assertEquals("-Xmx4G", settings.path("javaArguments").asText());
    }

    @Test
    void verifiesLegacyVPackMetadataWithoutDependingOnGsonPathBinding() throws Exception {
        Path archive = temporaryDirectory.resolve("legacy-metadata.vpack");
        String metadata = """
                {"id":"legacy-pack","name":"Legacy Pack","minecraftVersion":"1.20.1",
                 "loader":"Fabric","loaderVersion":"0.15.0",
                 "directory":{"oldPathRepresentation":"ignored"}}
                """;
        String manifest = """
                {"format":"vanta-instance-pack","formatVersion":1,
                 "instanceId":"legacy-pack","includedCategories":["mods"]}
                """;
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("instance.json"));
            zip.write(metadata.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("vanta-pack.json"));
            zip.write(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("mods/example.jar"));
            zip.write("mod".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        VPackService.Verification verification = VPackService.verifyVPack(archive);

        assertEquals("Legacy Pack", verification.instanceName());
        assertEquals("1.20.1", verification.minecraftVersion());
        assertEquals("Fabric", verification.loader());
        assertEquals(Set.of("mods"), verification.includedCategories());
    }

    @Test
    void rejectsOversizedSettingsInsteadOfParsingUnboundedInput() throws Exception {
        Path source = Files.createDirectory(temporaryDirectory.resolve("oversized-settings"));
        Instance instance = new Instance("oversized", "Oversized", "1.21.11",
                "Fabric", "0.16.0", source);
        Files.writeString(source.resolve("settings.json"), " ".repeat(1024 * 1024 + 1));
        Path archive = temporaryDirectory.resolve("oversized.vpack");

        assertThrows(IOException.class,
                () -> VPackService.createVPack(instance, archive, VPackService.Options.defaults()));
        assertFalse(Files.exists(archive));
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
        new dev.itsdropper.vanta.json.ObjectMapper().writerWithDefaultPrettyPrinter()
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

    @Test
    void rejectsNonCanonicalPathsThatNormalizeToAnotherEntry() throws Exception {
        Path source = Files.createDirectory(temporaryDirectory.resolve("noncanonical-source"));
        Instance instance = new Instance("profile-a", "Pack Test", "1.21.11",
                "Fabric", "0.16.0", source);
        write(source, "mods/example.jar");
        Path archive = temporaryDirectory.resolve("canonical.vpack");
        VPackService.createVPack(instance, archive,
                new VPackService.Options(true, false, false, false, false, false, false, false));

        Path rewritten = temporaryDirectory.resolve("noncanonical.vpack");
        rewriteArchive(archive, rewritten, "mods/example.jar", "mods/../mods/example.jar", null, false);

        assertThrows(IOException.class, () -> VPackService.verifyVPack(rewritten));
    }

    @Test
    void rejectsBackslashSeparatorsAndCaseVariantMetadataNames() throws Exception {
        Path source = Files.createDirectory(temporaryDirectory.resolve("separator-source"));
        Instance instance = new Instance("profile-a", "Pack Test", "1.21.11",
                "Fabric", "0.16.0", source);
        write(source, "mods/example.jar");
        Path archive = temporaryDirectory.resolve("separator-source.vpack");
        VPackService.createVPack(instance, archive,
                new VPackService.Options(true, false, false, false, false, false, false, false));

        Path backslashArchive = temporaryDirectory.resolve("backslash-path.vpack");
        rewriteArchive(archive, backslashArchive, "mods/example.jar", "mods\\example.jar", null, false);
        assertThrows(IOException.class, () -> VPackService.verifyVPack(backslashArchive));

        Path caseVariantArchive = temporaryDirectory.resolve("case-variant-metadata.vpack");
        rewriteArchive(archive, caseVariantArchive, "instance.json", "INSTANCE.JSON", null, false);
        assertThrows(IOException.class, () -> VPackService.verifyVPack(caseVariantArchive));
    }

    @Test
    void rejectsDirectoryEntriesOutsideTheDeclaredSelection() throws Exception {
        Path source = Files.createDirectory(temporaryDirectory.resolve("directory-source"));
        Instance instance = new Instance("profile-a", "Pack Test", "1.21.11",
                "Fabric", "0.16.0", source);
        write(source, "mods/example.jar");
        Path archive = temporaryDirectory.resolve("mods-only.vpack");
        VPackService.createVPack(instance, archive,
                new VPackService.Options(true, false, false, false, false, false, false, false));

        Path rewritten = temporaryDirectory.resolve("unselected-directory.vpack");
        rewriteArchive(archive, rewritten, null, null, "saves/", true);

        assertThrows(IOException.class, () -> VPackService.verifyVPack(rewritten));
    }

    @Test
    void rejectsFileAndDirectoryAncestorCollisionsBeforeRestore() throws Exception {
        Path source = Files.createDirectory(temporaryDirectory.resolve("collision-source"));
        Instance instance = new Instance("profile-a", "Pack Test", "1.21.11",
                "Fabric", "0.16.0", source);
        write(source, "mods/example.jar");
        Path archive = temporaryDirectory.resolve("valid-for-collision.vpack");
        VPackService.createVPack(instance, archive,
                new VPackService.Options(true, false, false, false, false, false, false, false));

        Path rewritten = temporaryDirectory.resolve("file-directory-collision.vpack");
        rewriteArchive(archive, rewritten, null, null, "mods", false);

        assertThrows(IOException.class, () -> VPackService.verifyVPack(rewritten));
    }

    private static void rewriteArchive(
            Path source, Path destination, String oldName, String newName,
            String additionalEntry, boolean directory
    ) throws IOException {
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(source));
             java.util.zip.ZipOutputStream output =
                     new java.util.zip.ZipOutputStream(Files.newOutputStream(destination))) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                if (oldName != null && name.equals(oldName)) name = newName;
                output.putNextEntry(new ZipEntry(name));
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                output.closeEntry();
                input.closeEntry();
            }
            if (additionalEntry != null) {
                output.putNextEntry(new ZipEntry(additionalEntry));
                if (!directory) output.write("collision".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    private static void write(Path root, String relative) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, relative);
    }

    private static byte[] readEntry(Path archive, String requestedName) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                zip.transferTo(bytes);
                zip.closeEntry();
                if (entry.getName().equals(requestedName)) return bytes.toByteArray();
            }
        }
        throw new IOException("Archive entry not found: " + requestedName);
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
