package dev.itsdropper.vanta.launcher.instance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

class ModrinthAppImporterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversOnlyDirectoriesContainingAnAppDatabase() throws Exception {
        Path root = Files.createDirectory(temporaryDirectory.resolve("ModrinthApp"));
        assertFalse(ModrinthAppImporter.looksLikeAppDirectory(root));
        Files.createFile(root.resolve("app.db"));
        assertTrue(ModrinthAppImporter.looksLikeAppDirectory(root));
        assertFalse(ModrinthAppImporter.looksLikeAppDirectory(temporaryDirectory.resolve("missing")));
    }

    @Test
    void mapsModrinthLoaderIdentifiersToVantaInstallers() throws Exception {
        assertEquals("Vanilla", ModrinthAppImporter.mapLoader("vanilla"));
        assertEquals("Fabric", ModrinthAppImporter.mapLoader("fabric"));
        assertEquals("NeoForge", ModrinthAppImporter.mapLoader("neo_forge"));
        assertEquals("Legacy Fabric", ModrinthAppImporter.mapLoader("legacy-fabric"));
        assertEquals("VantaLoader", ModrinthAppImporter.mapLoader("vanta_loader"));
        assertThrows(java.io.IOException.class, () -> ModrinthAppImporter.mapLoader("unknown-loader"));
    }

    @Test
    void readsCurrentSqliteDatabaseWithoutModifyingIt() throws Exception {
        Class.forName("org.sqlite.JDBC");
        Path root = Files.createDirectory(temporaryDirectory.resolve("ModrinthApp"));
        Path profiles = Files.createDirectory(root.resolve("profiles"));
        Path database = root.resolve("app.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE settings (
                        id INTEGER PRIMARY KEY,
                        custom_dir TEXT,
                        max_concurrent_downloads INTEGER,
                        mc_memory_max INTEGER,
                        mc_force_fullscreen INTEGER,
                        mc_game_resolution_x INTEGER,
                        mc_game_resolution_y INTEGER,
                        extra_launch_args JSONB,
                        locale TEXT
                    )
                    """);
            statement.execute("INSERT INTO settings VALUES (0, NULL, 0, NULL, NULL, NULL, NULL, 'null', NULL)");
            statement.execute("""
                    CREATE TABLE instances (
                        id TEXT PRIMARY KEY, path TEXT NOT NULL, name TEXT NOT NULL,
                        applied_content_set_id TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE instance_content_sets (
                        id TEXT PRIMARY KEY, instance_id TEXT NOT NULL, game_version TEXT NOT NULL,
                        loader TEXT NOT NULL, loader_version TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE instance_launch_overrides (
                        instance_id TEXT PRIMARY KEY, overrides JSONB NOT NULL
                    )
                    """);
        }

        var result = ModrinthAppImporter.importAll(root, null);
        assertEquals(0, result.importedInstances());
        assertTrue(result.skippedInstances().isEmpty());
        assertFalse(result.globalSettingsImported());
        assertTrue(Files.isDirectory(profiles));
        assertTrue(Files.isRegularFile(database));
    }
}
