package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.MinecraftLocator;
import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.model.InstanceSettings;
import dev.itsdropper.vanta.ui.LauncherSettings;
import dev.itsdropper.vanta.ui.Localization;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Read-only Prism Launcher migration. Prism's launcher files are never modified;
 * each supported instance is recreated through Vanta's own installer and then
 * receives the original instance's user data.
 */
public final class PrismLauncherImporter {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> GENERATED_GAME_PATHS = List.of(
            "versions", "libraries", "assets", "natives", "logs",
            "mmc-pack.json", "instance.cfg", "patches", "libraries"
    );

    private PrismLauncherImporter() { }

    public record ImportResult(Path prismRoot, int importedInstances,
                               List<String> skippedInstances, boolean globalSettingsImported) {
        public ImportResult {
            skippedInstances = List.copyOf(skippedInstances);
        }
    }

    public static Path findDefaultPrismRoot() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path home = Path.of(System.getProperty("user.home", "."));
        List<Path> candidates = new ArrayList<>();
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                candidates.add(Path.of(appData, "PrismLauncher"));
                candidates.add(Path.of(appData, "prismlauncher"));
            }
            String local = System.getenv("LOCALAPPDATA");
            if (local != null && !local.isBlank()) candidates.add(Path.of(local, "PrismLauncher"));
        } else if (os.contains("mac")) {
            candidates.add(home.resolve("Library/Application Support/PrismLauncher"));
        } else {
            String xdg = System.getenv("XDG_DATA_HOME");
            Path dataHome = xdg == null || xdg.isBlank() || !Path.of(xdg).isAbsolute()
                    ? home.resolve(".local/share") : Path.of(xdg);
            candidates.add(dataHome.resolve("PrismLauncher"));
            candidates.add(home.resolve(".local/share/PrismLauncher"));
        }
        return candidates.stream().map(Path::toAbsolutePath).map(Path::normalize)
                .filter(PrismLauncherImporter::looksLikePrismRoot).findFirst().orElse(null);
    }

    public static boolean looksLikePrismRoot(Path root) {
        if (root == null || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return false;
        return Files.isDirectory(root.resolve("instances"), LinkOption.NOFOLLOW_LINKS)
                || findSettingsFile(root) != null;
    }

    public static ImportResult importAll(Path selectedRoot, Consumer<String> progress) throws Exception {
        if (selectedRoot == null) throw new IllegalArgumentException("Choose the Prism Launcher data directory.");
        Path root = selectedRoot.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The selected Prism Launcher directory is not a regular directory.");
        }

        Map<String, String> global = findSettingsFile(root) == null
                ? Map.of() : readIni(findSettingsFile(root));
        Path instancesRoot = resolveInstancesDirectory(root, global);
        if (!Files.isDirectory(instancesRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Could not find Prism's instances directory. Check the InstanceDir setting.");
        }

        boolean settingsImported = importGlobalSettings(global, root);
        List<Path> sourceInstances;
        try (Stream<Path> entries = Files.list(instancesRoot)) {
            sourceInstances = entries.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER)).toList();
        }

        List<String> skipped = new ArrayList<>();
        int imported = 0;
        for (Path source : sourceInstances) {
            String name = source.getFileName().toString();
            try {
                Path packFile = source.resolve("mmc-pack.json");
                Path cfgFile = source.resolve("instance.cfg");
                if (!Files.isRegularFile(packFile, LinkOption.NOFOLLOW_LINKS)
                        || !Files.isRegularFile(cfgFile, LinkOption.NOFOLLOW_LINKS)) {
                    skipped.add(name + ": missing mmc-pack.json or instance.cfg");
                    continue;
                }
                Map<String, String> instanceConfig = readIni(cfgFile);
                PrismProfile profile = readProfile(packFile, instanceConfig, name);
                if (progress != null) progress.accept("Importing " + profile.name() + "…");
                Instance target = installProfile(profile);
                try {
                    Path gameDirectory = Files.isDirectory(source.resolve(".minecraft"), LinkOption.NOFOLLOW_LINKS)
                            ? source.resolve(".minecraft") : source;
                    copyGameData(gameDirectory, target.getDirectory());
                    importInstanceSettings(instanceConfig, global, target, root);
                } catch (Exception failure) {
                    try { InstanceManager.deleteInstance(target); } catch (Exception cleanup) {
                        failure.addSuppressed(cleanup);
                    }
                    throw failure;
                }
                imported++;
            } catch (Exception failure) {
                skipped.add(name + ": " + safeMessage(failure));
            }
        }

        return new ImportResult(root, imported, skipped, settingsImported);
    }

    private record PrismProfile(String name, String minecraftVersion, String loader, String loaderVersion) { }

    private static PrismProfile readProfile(Path packFile, Map<String, String> config, String fallbackName)
            throws IOException {
        JsonNode root = JSON.readTree(packFile.toFile());
        JsonNode components = root.path("components");
        if (!components.isArray()) throw new IOException("mmc-pack.json has no components array.");

        String minecraft = null;
        String loader = "Vanilla";
        String loaderVersion = null;
        List<String> unknownLoaderComponents = new ArrayList<>();
        for (JsonNode component : components) {
            String uid = component.path("uid").asText("").toLowerCase(Locale.ROOT);
            String version = component.path("version").asText("").trim();
            if (uid.equals("net.minecraft")) {
                minecraft = version;
            } else if (!uid.isBlank() && !version.isBlank()) {
                String mapped = mapLoader(uid);
                if (mapped != null) {
                    loader = mapped;
                    loaderVersion = version;
                } else if (looksLikeLoaderComponent(uid)) {
                    unknownLoaderComponents.add(uid);
                }
            }
        }
        if (!unknownLoaderComponents.isEmpty()) {
            throw new IOException("Unsupported Prism loader component(s): "
                    + String.join(", ", unknownLoaderComponents));
        }
        if ("Vanilla".equals(loader) && containsModJars(packFile.getParent())) {
            throw new IOException("This profile contains mods but no supported loader component was found.");
        }
        if (minecraft == null || minecraft.isBlank()) {
            throw new IOException("Could not determine the Minecraft version from mmc-pack.json.");
        }
        String name = config.getOrDefault("name", fallbackName).trim();
        if (name.isBlank()) name = fallbackName;
        return new PrismProfile(name, minecraft, loader, loaderVersion);
    }

    private static boolean looksLikeLoaderComponent(String uid) {
        return uid.contains("loader") || uid.contains("forge") || uid.contains("quilt")
                || uid.contains("ornithe") || uid.contains("babric") || uid.contains("rift")
                || uid.contains("stein") || uid.contains("liteloader");
    }

    private static boolean containsModJars(Path instanceRoot) throws IOException {
        Path gameDirectory = Files.isDirectory(instanceRoot.resolve(".minecraft"), LinkOption.NOFOLLOW_LINKS)
                ? instanceRoot.resolve(".minecraft") : instanceRoot;
        Path mods = gameDirectory.resolve("mods");
        if (!Files.isDirectory(mods, LinkOption.NOFOLLOW_LINKS)) return false;
        try (Stream<Path> files = Files.list(mods)) {
            return files.anyMatch(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"));
        }
    }

    static String mapLoader(String uid) {
        if (uid.contains("fabric-loader")) return "Fabric";
        if (uid.contains("quilt-loader")) return "Quilt";
        if (uid.contains("neoforge")) return "NeoForge";
        if (uid.contains("forge")) return "Forge";
        if (uid.contains("ornithe")) return "Ornithe";
        if (uid.contains("legacyfabric")) return "Legacy Fabric";
        if (uid.contains("babric")) return "Babric";
        if (uid.contains("nilloader")) return "NilLoader";
        if (uid.contains("liteloader")) return "LiteLoader";
        if (uid.contains("rift")) return "Rift";
        if (uid.contains("stein")) return "Stein";
        if (uid.contains("modloader")) return "ModLoader";
        if (uid.contains("vanta-loader") || uid.contains("vantaloader")) return "VantaLoader";
        return null;
    }

    private static Instance installProfile(PrismProfile profile) throws Exception {
        String name = uniqueName(profile.name());
        String mc = profile.minecraftVersion();
        String version = profile.loaderVersion();
        return switch (profile.loader()) {
            case "Vanilla" -> InstanceInstaller.installVanilla(name, mc);
            case "Fabric" -> InstanceInstaller.installFabric(name, mc, requiredVersion(profile, version));
            case "Quilt" -> InstanceInstaller.installQuilt(name, mc, requiredVersion(profile, version));
            case "Forge" -> InstanceInstaller.installForge(name, mc, requiredVersion(profile, version));
            case "NeoForge" -> InstanceInstaller.installNeoForge(name, mc, requiredVersion(profile, version));
            case "Ornithe" -> InstanceInstaller.installOrnithe(name, mc, requiredVersion(profile, version));
            case "Legacy Fabric" -> InstanceInstaller.installLegacyFabric(name, mc, requiredVersion(profile, version));
            case "NilLoader" -> InstanceInstaller.installNilLoader(name, mc, requiredVersion(profile, version));
            case "Babric" -> {
                requireCurrentVersion(profile, BabricInstaller.findLatestLoaderVersion(mc));
                yield InstanceInstaller.installBabric(name, mc);
            }
            case "LiteLoader" -> {
                requireCurrentVersion(profile, LiteLoaderInstaller.findLatestLoaderVersion(mc));
                yield InstanceInstaller.installLiteLoader(name, mc);
            }
            case "Rift" -> {
                requireCurrentVersion(profile, RiftInstaller.findLatestLoaderVersion(mc));
                yield InstanceInstaller.installRift(name, mc);
            }
            case "Stein" -> {
                requireCurrentVersion(profile, SteinInstaller.findLatestLoaderVersion());
                yield InstanceInstaller.installSteinLoader(name, mc);
            }
            case "ModLoader" -> {
                requireCurrentVersion(profile, ModLoaderInstaller.findLatestLoaderVersion(mc));
                yield InstanceInstaller.installModLoader(name, mc);
            }
            case "VantaLoader" -> {
                requireCurrentVersion(profile, VantaLoaderInstaller.findLatestLoaderVersion());
                yield InstanceInstaller.installVantaLoader(name, mc);
            }
            default -> throw new IOException("Unsupported Prism loader: " + profile.loader());
        };
    }

    private static void requireCurrentVersion(PrismProfile profile, String currentVersion)
            throws IOException {
        if (profile.loaderVersion() == null || !profile.loaderVersion().equals(currentVersion)) {
            throw new IOException("Prism pins " + profile.loader() + " " + profile.loaderVersion()
                    + ", but Vanta's verified installer currently provides " + currentVersion
                    + ". This instance was left untouched rather than silently changing its loader version.");
        }
    }

    private static String requiredVersion(PrismProfile profile, String version) throws IOException {
        if (version == null || version.isBlank()) {
            throw new IOException("Prism profile does not specify the " + profile.loader() + " version.");
        }
        return version;
    }

    private static String uniqueName(String requested) {
        String base = requested == null || requested.isBlank() ? "Imported instance" : requested.trim();
        String candidate = base;
        int suffix = 2;
        Path instances = MinecraftLocator.getInstancesDirectory();
        while (Files.exists(instances.resolve(sanitizeId(candidate)))) {
            candidate = base + " (" + suffix++ + ")";
        }
        return candidate;
    }

    private static String sanitizeId(String name) {
        String id = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return id.isBlank() ? "instance" : id;
    }

    static void copyGameData(Path source, Path target) throws IOException {
        Path normalizedSource = source.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(source) || !Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Prism game directory is not a regular directory.");
        }
        try (Stream<Path> walk = Files.walk(normalizedSource)) {
            for (Path item : walk.sorted().toList()) {
                if (item.equals(normalizedSource)) continue;
                Path relative = normalizedSource.relativize(item);
                String first = relative.getName(0).toString();
                if (GENERATED_GAME_PATHS.contains(first) || first.startsWith(".") && first.equals(".fabric")) continue;
                if (Files.isSymbolicLink(item)) throw new IOException("Instance contains a symbolic link: " + relative);
                Path destination = normalizedTarget.resolve(relative).normalize();
                if (!destination.startsWith(normalizedTarget)) throw new IOException("Instance path escapes its destination.");
                if (Files.isDirectory(item, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(destination);
                } else if (Files.isRegularFile(item, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(item, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                } else {
                    throw new IOException("Unsupported filesystem entry in Prism instance: " + relative);
                }
            }
        }
    }

    private static void importInstanceSettings(Map<String, String> cfg, Map<String, String> global,
                                               Instance instance, Path prismRoot) throws IOException {
        InstanceSettings settings = new InstanceSettings();
        boolean overrideJava = bool(cfg, "OverrideJavaLocation", false);
        boolean overrideArgs = bool(cfg, "OverrideJavaArgs", false);
        boolean overrideMemory = bool(cfg, "OverrideMemory", false);
        boolean overrideWindow = bool(cfg, "OverrideWindow", false);

        String javaPath = overrideJava ? cfg.get("JavaPath") : global.get("JavaPath");
        String javaArgs = overrideArgs ? cfg.get("JvmArgs") : global.get("JvmArgs");
        String memory = overrideMemory ? cfg.get("MaxMemAlloc") : global.get("MaxMemAlloc");
        String width = overrideWindow ? cfg.get("MinecraftWinWidth") : global.get("MinecraftWinWidth");
        String height = overrideWindow ? cfg.get("MinecraftWinHeight") : global.get("MinecraftWinHeight");

        settings.setJavaPath(resolveConfiguredPath(prismRoot, javaPath));
        settings.setJavaArguments(javaArgs == null ? "" : javaArgs);
        settings.setRamMb(parseInt(memory, LauncherSettings.getDefaultRamMb(), 1024, 16384));
        settings.setWidth(parseInt(width, LauncherSettings.getDefaultWidth(), 640, 7680));
        settings.setHeight(parseInt(height, LauncherSettings.getDefaultHeight(), 480, 4320));
        settings.setFullscreen(false);
        InstanceManager.saveSettings(instance, settings);
    }

    private static boolean importGlobalSettings(Map<String, String> settings, Path prismRoot) {
        if (settings.isEmpty()) return false;
        boolean imported = false;
        if (settings.containsKey("JavaPath")) {
            LauncherSettings.setDefaultJavaPath(resolveConfiguredPath(prismRoot, settings.get("JavaPath")));
            imported = true;
        }
        if (settings.containsKey("JvmArgs")) {
            LauncherSettings.setDefaultJavaArguments(settings.get("JvmArgs"));
            imported = true;
        }
        if (settings.containsKey("MaxMemAlloc")) {
            LauncherSettings.setDefaultRamMb(parseInt(settings.get("MaxMemAlloc"),
                    LauncherSettings.getDefaultRamMb(), 1024, 16384));
            imported = true;
        }
        if (settings.containsKey("MinecraftWinWidth")) {
            LauncherSettings.setDefaultWidth(parseInt(settings.get("MinecraftWinWidth"),
                    LauncherSettings.getDefaultWidth(), 640, 7680));
            imported = true;
        }
        if (settings.containsKey("MinecraftWinHeight")) {
            LauncherSettings.setDefaultHeight(parseInt(settings.get("MinecraftWinHeight"),
                    LauncherSettings.getDefaultHeight(), 480, 4320));
            imported = true;
        }
        if (settings.containsKey("NumberOfConcurrentDownloads")) {
            LauncherSettings.setDownloadThreads(parseInt(settings.get("NumberOfConcurrentDownloads"),
                    LauncherSettings.getDownloadThreads(), 1, 16));
            imported = true;
        }
        if (settings.containsKey("NumberOfManualRetries")) {
            LauncherSettings.setDownloadRetries(parseInt(settings.get("NumberOfManualRetries"),
                    LauncherSettings.getDownloadRetries(), 1, 8));
            imported = true;
        }
        if (settings.containsKey("ShowGameTime")) {
            LauncherSettings.setShowPlaytimeEnabled(Boolean.parseBoolean(settings.get("ShowGameTime")));
            imported = true;
        }
        if (settings.containsKey("Language")) {
            String language = java.util.Locale.forLanguageTag(
                    settings.get("Language").replace('_', '-')).getLanguage();
            if (Localization.isSupported(language)) {
                LauncherSettings.setPreferredLanguageCode(language);
                imported = true;
            }
        }
        return imported;
    }

    private static String resolveConfiguredPath(Path prismRoot, String configured) {
        if (configured == null || configured.isBlank()) return "";
        try {
            Path path = Path.of(configured);
            return (path.isAbsolute() ? path : prismRoot.resolve(path)).normalize().toString();
        } catch (RuntimeException invalidPath) {
            return configured;
        }
    }

    static Path resolveInstancesDirectory(Path root, Map<String, String> settings) {
        String configured = settings.get("InstanceDir");
        if (configured == null || configured.isBlank()) return root.resolve("instances");
        Path path = Path.of(configured);
        if (!path.isAbsolute()) path = root.resolve(path);
        return path.normalize();
    }

    private static Path findSettingsFile(Path root) {
        for (String name : List.of("PrismLauncher.cfg", "prismlauncher.cfg", "multimc.cfg", "polymc.cfg")) {
            Path file = root.resolve(name);
            if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return file;
        }
        return null;
    }

    static Map<String, String> readIni(Path file) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        String section = "";
        for (String raw : Files.readAllLines(file)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0) continue;
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            values.put(key, value);
            if (!section.isEmpty()) values.put(section + "/" + key, value);
        }
        return values;
    }

    private static boolean bool(Map<String, String> values, String key, boolean fallback) {
        String value = values.get(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int parseInt(String value, int fallback, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(value))); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String safeMessage(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName() : failure.getMessage();
    }
}
