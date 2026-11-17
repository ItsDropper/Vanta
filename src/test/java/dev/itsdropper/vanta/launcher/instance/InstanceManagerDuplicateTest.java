package dev.itsdropper.vanta.launcher.instance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class InstanceManagerDuplicateTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void copiesWorldsConfigsAndSettingsButOmitsTransientLogsAndScreenshots() throws Exception {
        Path source = Files.createDirectory(temporaryDirectory.resolve("source"));
        Path target = Files.createDirectory(temporaryDirectory.resolve("target"));

        Files.createDirectories(source.resolve("saves/world/datapacks"));
        Files.createDirectories(source.resolve("config"));
        Files.createDirectories(source.resolve("logs"));
        Files.createDirectories(source.resolve("screenshots"));
        Files.writeString(source.resolve("saves/world/level.dat"), "world");
        Files.writeString(source.resolve("saves/world/datapacks/data.zip"), "datapack");
        Files.writeString(source.resolve("config/mod.json"), "config");
        Files.writeString(source.resolve("settings.json"), "settings");
        Files.writeString(source.resolve("instance.json"), "metadata");
        Files.writeString(source.resolve(".installing"), "interrupted marker");
        Files.writeString(source.resolve("logs/latest.log"), "transient");
        Files.writeString(source.resolve("screenshots/image.png"), "transient");

        InstanceManager.copyInstanceFiles(source, target);

        assertEquals("world", Files.readString(target.resolve("saves/world/level.dat")));
        assertEquals("datapack", Files.readString(target.resolve("saves/world/datapacks/data.zip")));
        assertEquals("config", Files.readString(target.resolve("config/mod.json")));
        assertEquals("settings", Files.readString(target.resolve("settings.json")));
        assertEquals("metadata", Files.readString(target.resolve("instance.json")));
        assertFalse(Files.exists(target.resolve(".installing")));
        assertFalse(Files.exists(target.resolve("logs")));
        assertFalse(Files.exists(target.resolve("screenshots")));
    }

    @Test
    void refusesToFollowSymbolicLinksOutsideTheInstance() throws Exception {
        Path source = Files.createDirectory(temporaryDirectory.resolve("source"));
        Path target = Files.createDirectory(temporaryDirectory.resolve("target"));
        Path outside = Files.writeString(temporaryDirectory.resolve("outside.txt"), "private data");
        Path link = source.resolve("external.txt");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            org.junit.jupiter.api.Assumptions.abort("Symbolic links are unavailable on this test host.");
        }

        IOException failure = assertThrows(IOException.class,
                () -> InstanceManager.copyInstanceFiles(source, target));

        assertTrue(failure.getMessage().contains("symbolic link"));
        assertEquals("private data", Files.readString(outside));
        assertFalse(Files.exists(target.resolve("external.txt")));
    }

    @Test
    void rejectsSymbolicLinkAsTheSourceDirectory() throws Exception {
        Path realSource = Files.createDirectory(temporaryDirectory.resolve("real-source"));
        Path sourceLink = temporaryDirectory.resolve("source-link");
        Path target = Files.createDirectory(temporaryDirectory.resolve("target"));
        try {
            Files.createSymbolicLink(sourceLink, realSource);
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            org.junit.jupiter.api.Assumptions.abort("Symbolic links are unavailable on this test host.");
        }

        assertThrows(IOException.class,
                () -> InstanceManager.copyInstanceFiles(sourceLink, target));
    }
}
