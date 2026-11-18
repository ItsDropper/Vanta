package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.MinecraftLocator;
import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.model.InstanceSettings;
import dev.itsdropper.vanta.ui.LauncherSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Imports local Modrinth App instances from its read-only app.db and profiles
 * directory. The source database and files are never modified.
 */
public final class ModrinthAppImporter {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ModrinthAppImporter() { }

    public record ImportResult(Path appDirectory, int importedInstances,
                               List<String> skippedInstances, boolean globalSettingsImported) {
        public ImportResult {
            skippedInstances = List.copyOf(skippedInstances);
        }
    }

    public static Path findDefaultAppDirectory() {
        Path home = Path.of(System.getProperty("user.home", "."));
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<Path> candidates = new ArrayList<>();
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                candidates.add(Path.of(appData, "ModrinthApp"));
                candidates.add(Path.of(appData, "com.modrinth.theseus"));
            }
        } else if (os.contains("mac")) {
            candidates.add(home.resolve("Library/Application Support/ModrinthApp"));
            candidates.add(home.resolve("Library/Application Support/com.modrinth.theseus"));
        } else {
            String xdg = System.getenv("XDG_DATA_HOME");
            Path data = xdg == null || xdg.isBlank() || !Path.of(xdg).isAbsolute()
                    ? home.resolve(".local/share") : Path.of(xdg);
            candidates.add(data.resolve("ModrinthApp"));
            candidates.add(data.resolve("com.modrinth.theseus"));
        }
        return candidates.stream().map(Path::toAbsolutePath).map(Path::normalize)
                .filter(ModrinthAppImporter::looksLikeAppDirectory).findFirst().orElse(null);
    }

    public static boolean looksLikeAppDirectory(Path directory) {
        return directory != null
                && Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(directory)
                && Files.isRegularFile(directory.resolve("app.db"), LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(directory.resolve("app.db"));
    }

    public static ImportResult importAll(Path selectedDirectory, Consumer<String> progress) throws Exception {
        if (!looksLikeAppDirectory(selectedDirectory)) {
            throw new IOException("Select a Modrinth App data directory containing app.db.");
        }

        Path appDirectory = selectedDirectory.toAbsolutePath().normalize();
        Class.forName("org.sqlite.JDBC");
        String jdbcUrl = "jdbc:sqlite:" + appDirectory.resolve("app.db").toUri() + "?mode=ro";

        try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
            JsonNode global = readGlobalSettings(connection);
            Path instancesRoot = resolveInstancesRoot(appDirectory, global);
            if (!Files.isDirectory(instancesRoot, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(instancesRoot)) {
                throw new IOException("The Modrinth profiles directory was not found. Check the app's custom directory.");
            }

            boolean settingsImported = importGlobalSettings(global);
            List<Profile> profiles = readProfiles(connection);
            List<String> skipped = new ArrayList<>();
            int imported = 0;

            for (Profile profile : profiles) {
                try {
                    Path source = instancesRoot.resolve(profile.path()).normalize();
                    if (!source.startsWith(instancesRoot) || Files.isSymbolicLink(source)
                            || !Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
                        skipped.add(profile.name() + ": instance directory is missing or unsafe");
                        continue;
                    }
                    if (profile.gameVersion().isBlank()) {
                        skipped.add(profile.name() + ": Minecraft version metadata is missing");
                        continue;
                    }
                    if (progress != null) progress.accept("Importing " + profile.name() + "…");
                    Instance target = PrismLauncherImporter.installImportedProfile(
                            profile.name(), profile.gameVersion(), profile.loader(), profile.loaderVersion());
                    try {
                        PrismLauncherImporter.copyGameData(source, target.getDirectory());
                        importInstanceSettings(profile.overrides(), target);
                    } catch (Exception failure) {
                        try { InstanceManager.deleteInstance(target); }
                        catch (Exception cleanup) { failure.addSuppressed(cleanup); }
                        throw failure;
                    }
                    imported++;
                } catch (Exception failure) {
                    skipped.add(profile.name() + ": " + safeMessage(failure));
                }
            }
            return new ImportResult(appDirectory, imported, skipped, settingsImported);
        }
    }

    private record Profile(String path, String name, String gameVersion,
                           String loader, String loaderVersion, JsonNode overrides) { }

    private static JsonNode readGlobalSettings(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT *, json(extra_launch_args) AS extra_launch_args_text FROM settings LIMIT 1")) {
            if (!result.next()) return JSON.createObjectNode();
            JsonNode node = JSON.createObjectNode();
            var metadata = result.getMetaData();
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                Object value = result.getObject(i);
                if (value != null) ((com.fasterxml.jackson.databind.node.ObjectNode) node)
                        .put(metadata.getColumnLabel(i), value.toString());
                if ("extra_launch_args_text".equals(metadata.getColumnLabel(i)) && value != null) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("extra_launch_args", value.toString());
                }
            }
            return node;
        }
    }

    private static Path resolveInstancesRoot(Path appDirectory, JsonNode settings) {
        String custom = settings.path("custom_dir").asText("");
        Path root = custom.isBlank() ? appDirectory : Path.of(custom).toAbsolutePath().normalize();
        return root.resolve("profiles").normalize();
    }

    private static List<Profile> readProfiles(Connection connection) throws SQLException, IOException {
        List<Profile> profiles = new ArrayList<>();
        if (tableExists(connection, "instances") && tableExists(connection, "instance_content_sets")) {
            String sql = """
                    SELECT i.path, i.name, c.game_version, c.loader, c.loader_version,
                           COALESCE(json(o.overrides), '{}') AS overrides
                    FROM instances i
                    LEFT JOIN instance_content_sets c
                      ON c.instance_id = i.id AND c.id = i.applied_content_set_id
                    LEFT JOIN instance_launch_overrides o ON o.instance_id = i.id
                    ORDER BY i.name COLLATE NOCASE
                    """;
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(sql)) {
                while (result.next()) {
                    profiles.add(new Profile(result.getString("path"), result.getString("name"),
                            nullToEmpty(result.getString("game_version")),
                            mapLoader(result.getString("loader")),
                            nullToEmpty(result.getString("loader_version")),
                            JSON.readTree(result.getString("overrides"))));
                }
            }
        } else if (tableExists(connection, "profiles")) {
            String sql = "SELECT path, name, game_version, mod_loader, mod_loader_version "
                    + "FROM profiles ORDER BY name COLLATE NOCASE";
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(sql)) {
                while (result.next()) {
                    profiles.add(new Profile(result.getString("path"), result.getString("name"),
                            nullToEmpty(result.getString("game_version")),
                            mapLoader(result.getString("mod_loader")),
                            nullToEmpty(result.getString("mod_loader_version")),
                            JSON.createObjectNode()));
                }
            }
        } else {
            throw new SQLException("Unsupported Modrinth App database schema: no instance metadata tables found.");
        }
        return profiles;
    }

    private static boolean tableExists(Connection connection, String name) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    static String mapLoader(String raw) throws IOException {
        String loader = raw == null ? "vanilla" : raw.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return switch (loader) {
            case "", "vanilla", "none" -> "Vanilla";
            case "fabric" -> "Fabric";
            case "quilt" -> "Quilt";
            case "forge" -> "Forge";
            case "neoforge", "neo" -> "NeoForge";
            case "ornithe" -> "Ornithe";
            case "legacyfabric" -> "Legacy Fabric";
            case "babric" -> "Babric";
            case "nilloader" -> "NilLoader";
            case "liteloader" -> "LiteLoader";
            case "rift" -> "Rift";
            case "stein" -> "Stein";
            case "modloader" -> "ModLoader";
            case "vanta", "vantaloader" -> "VantaLoader";
            default -> throw new IOException("Unsupported Modrinth loader: " + raw);
        };
    }

    private static boolean importGlobalSettings(JsonNode settings) {
        boolean imported = false;
        int memory = settings.path("mc_memory_max").asInt(-1);
        if (memory > 0) {
            LauncherSettings.setDefaultRamMb(memory);
            imported = true;
        }
        if (settings.has("mc_force_fullscreen")) {
            LauncherSettings.setDefaultFullscreen(settings.path("mc_force_fullscreen").asInt() != 0);
            imported = true;
        }
        int width = settings.path("mc_game_resolution_x").asInt(-1);
        int height = settings.path("mc_game_resolution_y").asInt(-1);
        if (width > 0 && height > 0) {
            LauncherSettings.setDefaultWidth(width);
            LauncherSettings.setDefaultHeight(height);
            imported = true;
        }
        int downloads = settings.path("max_concurrent_downloads").asInt(-1);
        if (downloads > 0) {
            LauncherSettings.setDownloadThreads(downloads);
            imported = true;
        }
        String args = settings.path("extra_launch_args").asText("");
        if (args.startsWith("[")) {
            try {
                List<String> values = JSON.readerForListOf(String.class).readValue(args);
                LauncherSettings.setDefaultJavaArguments(String.join(" ", values));
                imported = true;
            } catch (IOException ignored) { }
        }
        String locale = settings.path("locale").asText("");
        if (!locale.isBlank()) {
            String language = Locale.forLanguageTag(locale.replace("_", "-")).getLanguage();
            if (dev.itsdropper.vanta.ui.Localization.isSupported(language)) {
                LauncherSettings.setPreferredLanguageCode(language);
                imported = true;
            }
        }
        return imported;
    }

    private static void importInstanceSettings(JsonNode overrides, Instance instance) throws IOException {
        InstanceSettings settings = new InstanceSettings();
        String javaPath = overrides.path("java_path").asText("");
        if (isValidJavaPath(javaPath)) settings.setJavaPath(javaPath);
        JsonNode args = overrides.path("extra_launch_args");
        if (args.isArray()) {
            List<String> values = new ArrayList<>();
            args.forEach(value -> values.add(value.asText()));
            settings.setJavaArguments(String.join(" ", values));
        }
        JsonNode memory = overrides.path("memory");
        if (memory.has("maximum")) settings.setRamMb(memory.path("maximum").asInt(settings.getRamMb()));
        if (overrides.has("force_fullscreen")) {
            settings.setFullscreen(overrides.path("force_fullscreen").asBoolean());
        }
        JsonNode resolution = overrides.path("game_resolution");
        if (resolution.isArray() && resolution.size() == 2) {
            settings.setWidth(resolution.get(0).asInt(settings.getWidth()));
            settings.setHeight(resolution.get(1).asInt(settings.getHeight()));
        }
        InstanceManager.saveSettings(instance, settings);
    }

    private static boolean isValidJavaPath(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            Path path = Path.of(value).toAbsolutePath().normalize();
            return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path);
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }
    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
