package dev.itsdropper.vanta.launcher.diagnostics;

import dev.itsdropper.vanta.launcher.diagnostics.ModBisectSession.ModInfo;
import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.instance.InstalledModManager;
import dev.itsdropper.vanta.launcher.modrinth.DependencyRequirement;
import dev.itsdropper.vanta.launcher.modrinth.InstalledMod;
import dev.itsdropper.vanta.launcher.modrinth.InstalledModScanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** Connects mod searches to a real instance: reads its mods and keeps its mod records in step with renames. */
public final class ModBisectService {

    /** Components the game provides itself, so they never count as a mod to switch on. */
    private static final Set<String> PLATFORM_IDS = Set.of(
            "minecraft", "java", "java-runtime", "fabricloader", "fabric-loader", "quilt_loader", "forge", "neoforge");

    private final InstalledModScanner scanner = new InstalledModScanner();

    public static Path modsDirectory(Instance instance) {
        return instance.getDirectory().resolve("mods");
    }

    public static boolean hasSavedSearch(Instance instance) {
        return Files.isRegularFile(ModBisectSession.journalFor(modsDirectory(instance)));
    }

    public Optional<ModBisectSession> resume(Instance instance) throws IOException {
        return ModBisectSession.resume(modsDirectory(instance), renamerFor(instance));
    }

    public ModBisectSession start(Instance instance) throws IOException {
        return ModBisectSession.create(modsDirectory(instance), inspect(instance), renamerFor(instance));
    }

    /** The enabled mod files of an instance with the ids they provide and require. */
    public List<ModInfo> inspect(Instance instance) throws IOException {
        Path mods = modsDirectory(instance);
        if (!Files.isDirectory(mods)) {
            return List.of();
        }
        List<Path> jars;
        try (Stream<Path> files = Files.list(mods)) {
            jars = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted().toList();
        }
        List<ModInfo> result = new ArrayList<>();
        for (Path jar : jars) {
            InstalledMod metadata = scanner.scanFile(jar);
            Set<String> provides = new LinkedHashSet<>();
            Set<String> requires = new LinkedHashSet<>();
            String modId = null;
            if (metadata != null) {
                modId = metadata.getModId();
                if (metadata.getDependencies() != null) {
                    for (DependencyRequirement dependency : metadata.getDependencies()) {
                        if (dependency != null && !dependency.isOptional() && dependency.getModId() != null
                                && !PLATFORM_IDS.contains(dependency.getModId().toLowerCase(Locale.ROOT))) {
                            requires.add(dependency.getModId());
                        }
                    }
                }
            }
            try {
                provides.addAll(scanner.scanFabricModuleVersions(jar).keySet());
            } catch (RuntimeException unreadable) {
                // A jar without readable module metadata simply provides nothing extra.
            }
            result.add(new ModInfo(jar.getFileName().toString(), modId, provides, requires));
        }
        return result;
    }

    private static ModBisectSession.Renamer renamerFor(Instance instance) {
        return (from, to) -> InstalledModManager.renameByFilename(instance, from, to);
    }
}
