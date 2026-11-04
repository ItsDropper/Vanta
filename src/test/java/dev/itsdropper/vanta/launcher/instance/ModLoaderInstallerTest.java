package dev.itsdropper.vanta.launcher.instance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModLoaderInstallerTest {
    @Test
    void mergesPatchIntoCopyAndReplacesCollidingClasses() throws Exception {
        Path vanilla = createZip(
                "net/minecraft/client/Minecraft.class", new byte[]{1, 2},
                "META-INF/TEST.SF", new byte[]{9},
                "assets/base.txt", new byte[]{3});
        Path patch = createZip(
                "net/minecraft/client/Minecraft.class", new byte[]{4, 5},
                "ModLoader.class", new byte[]{6});
        Path output = Files.createTempFile("vanta-modloader-output", ".jar");

        ModLoaderInstaller.mergeArchiveIntoClient(vanilla, patch, output);

        try (ZipFile result = new ZipFile(output.toFile())) {
            assertArrayEquals(new byte[]{4, 5},
                    result.getInputStream(result.getEntry("net/minecraft/client/Minecraft.class")).readAllBytes());
            assertArrayEquals(new byte[]{6},
                    result.getInputStream(result.getEntry("ModLoader.class")).readAllBytes());
            assertArrayEquals(new byte[]{3},
                    result.getInputStream(result.getEntry("assets/base.txt")).readAllBytes());
            assertFalse(result.stream().anyMatch(entry -> entry.getName().equals("META-INF/TEST.SF")));
        }
    }

    @Test
    void rejectsArchivePathTraversal() throws Exception {
        Path vanilla = createZip("Minecraft.class", new byte[]{1});
        Path patch = createZip("../escape.class", new byte[]{2});
        Path output = Files.createTempFile("vanta-modloader-invalid", ".jar");

        assertThrows(IOException.class,
                () -> ModLoaderInstaller.mergeArchiveIntoClient(vanilla, patch, output));
    }

    @Test
    void rejectsUnsupportedMinecraftVersions() {
        assertThrows(IllegalArgumentException.class,
                () -> ModLoaderInstaller.findLatestLoaderVersion("1.7.10"));
    }

    @Test
    void exposesOnlyPinnedArchiveVersions() {
        assertTrue(ModLoaderInstaller.findLatestLoaderVersion("1.6.2").equals("1.6.2"));
        assertTrue(ModLoaderInstaller.findLatestLoaderVersion("b1.8.1").equals("b1.8.1"));
    }

    private static Path createZip(Object... nameAndBytes) throws Exception {
        Path file = Files.createTempFile("vanta-modloader-fixture", ".zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(file))) {
            for (int i = 0; i < nameAndBytes.length; i += 2) {
                output.putNextEntry(new ZipEntry((String) nameAndBytes[i]));
                output.write((byte[]) nameAndBytes[i + 1]);
                output.closeEntry();
            }
        }
        return file;
    }
}
