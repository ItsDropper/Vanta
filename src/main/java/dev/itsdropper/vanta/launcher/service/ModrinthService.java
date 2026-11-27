package dev.itsdropper.vanta.launcher.service;

import dev.itsdropper.vanta.launcher.modrinth.InstalledModScanner;
import dev.itsdropper.vanta.launcher.instance.InstalledModManager;
import dev.itsdropper.vanta.launcher.instance.InstalledModRecord;
import dev.itsdropper.vanta.launcher.modrinth.*;
import dev.itsdropper.vanta.launcher.model.Instance;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class ModrinthService {
    private static final Map<String, String> DEPENDENCY_PROJECT_SLUG_ALIASES = Map.of(
            "oneconfigv1", "oneconfig"
    );

    static String modrinthSlugForDependency(String modId) {
        if (modId == null || modId.isBlank()) return modId;
        String normalized = modId.trim().toLowerCase(Locale.ROOT);
        return DEPENDENCY_PROJECT_SLUG_ALIASES.getOrDefault(normalized, normalized);
    }

    private final ModrinthClient client = new ModrinthClient();
    private final InstalledModScanner installedModScanner = new InstalledModScanner();
    private final ModrinthCatalogService catalog = new ModrinthCatalogService(client, installedModScanner);
    private final ModrinthDependencyResolver dependencies = new ModrinthDependencyResolver(client, installedModScanner);
    private final ModrinthInstallService installer = new ModrinthInstallService(client, installedModScanner, catalog, dependencies);
    private final ModrinthRepairService repair = new ModrinthRepairService(client, installedModScanner, catalog, dependencies, installer);

    public List<ModrinthProject> searchMods(String query) throws IOException, InterruptedException { return catalog.searchMods(query); }

    public ModrinthSearchResult searchPage(
            String query,
            ModrinthContentType contentType,
            List<String> loaders,
            List<String> versions,
            List<String> categories,
            String index,
            int limit,
            int offset
    ) throws IOException, InterruptedException {
        return client.searchPage(
                query, contentType, loaders, versions, categories, index, limit, offset
        );
    }

    public List<ModrinthTag> getGameVersionTags() throws IOException, InterruptedException {
        return client.getGameVersionTags();
    }

    public List<ModrinthTag> getLoaderTags(ModrinthContentType contentType)
            throws IOException, InterruptedException {
        return client.getLoaderTags(contentType);
    }

    public List<ModrinthTag> getCategoryTags(ModrinthContentType contentType)
            throws IOException, InterruptedException {
        return client.getCategoryTags(contentType);
    }
    public List<ModrinthProject> searchMods(Instance instance, String query) throws IOException, InterruptedException { return catalog.searchMods(instance, query); }

    /**
     * Fast list search for the instance browser. Modrinth's search payload already
     * includes every field needed for result cards, so do not make a second request
     * to /project/{id} for each hit.
     */
    public List<ModrinthProject> searchModsFast(Instance instance, String query) throws IOException, InterruptedException {
        if (instance == null) throw new IllegalArgumentException("Instance cannot be null.");
        String loader = ModrinthLoaderIds.normalize(instance.getLoader());
        List<ModrinthSearchHit> hits = client.search(query, ModrinthContentType.MOD, loader, instance.getMinecraftVersion()).getHits();
        return mapSearchHits(hits);
    }

    /**
     * Paginated instance search. Loader and game version are implicit instance
     * constraints, not user-editable filters in BrowseModsView. Using the API
     * facets directly keeps total_hits and page boundaries accurate.
     */
    public ModrinthSearchResult searchModsPage(
            Instance instance,
            String query,
            String index,
            int limit,
            int offset
    ) throws IOException, InterruptedException {
        return searchModsPage(instance, query, index, limit, offset, true);
    }

    /**
     * Searches the instance mod catalog. Compatibility constraints are applied
     * only when requested by the caller; leaving them off deliberately exposes
     * the wider Modrinth catalog without changing the selected instance.
     */
    public ModrinthSearchResult searchModsPage(
            Instance instance,
            String query,
            String index,
            int limit,
            int offset,
            boolean compatibleOnly
    ) throws IOException, InterruptedException {
        if (instance == null) throw new IllegalArgumentException("Instance cannot be null.");
        List<String> loaders = List.of();
        List<String> versions = List.of();
        if (compatibleOnly) {
            String loader = ModrinthLoaderIds.normalize(instance.getLoader());
            loaders = loader == null || loader.isBlank()
                    || loader.equalsIgnoreCase("vanilla") ? List.of() : List.of(loader);
            versions = instance.getMinecraftVersion() == null
                    || instance.getMinecraftVersion().isBlank()
                    ? List.of() : List.of(instance.getMinecraftVersion());
        }
        return client.searchPage(
                query, ModrinthContentType.MOD, loaders, versions,
                List.of(), index, limit, offset
        );
    }

    public List<ModrinthProject> mapSearchHitsToProjects(List<ModrinthSearchHit> hits) {
        return mapSearchHits(hits);
    }

    /**
     * First-paint query matching the global browser: filter by loader but do not
     * wait for Modrinth's additional exact Minecraft-version facet.
     */
    public List<ModrinthProject> searchModsFastBroad(Instance instance, String query) throws IOException, InterruptedException {
        if (instance == null) throw new IllegalArgumentException("Instance cannot be null.");
        String loader = ModrinthLoaderIds.normalize(instance.getLoader());
        return mapSearchHits(client.search(query, ModrinthContentType.MOD, loader, null).getHits());
    }

    public List<ModrinthProject> getMostDownloadedModsFast(Instance instance) throws IOException, InterruptedException {
        if (instance == null) throw new IllegalArgumentException("Instance cannot be null.");
        String loader = ModrinthLoaderIds.normalize(instance.getLoader());
        List<ModrinthSearchHit> hits = client.getMostDownloaded(ModrinthContentType.MOD, loader, instance.getMinecraftVersion()).getHits();
        return mapSearchHits(hits);
    }

    public List<ModrinthProject> getMostDownloadedModsFastBroad(Instance instance) throws IOException, InterruptedException {
        if (instance == null) throw new IllegalArgumentException("Instance cannot be null.");
        String loader = ModrinthLoaderIds.normalize(instance.getLoader());
        return mapSearchHits(client.getMostDownloaded(ModrinthContentType.MOD, loader, null).getHits());
    }

    private List<ModrinthProject> mapSearchHits(List<ModrinthSearchHit> hits) {
        List<ModrinthProject> projects = new ArrayList<>();
        if (hits == null) return projects;
        for (ModrinthSearchHit hit : hits) {
            if (hit == null || hit.getProjectId() == null || hit.getProjectId().isBlank()) continue;
            projects.add(ModrinthProject.fromSearchHit(hit));
        }
        return projects;
    }

    public List<ModrinthProject> search(String query, ModrinthContentType contentType, String loader, String minecraftVersion) throws IOException, InterruptedException { return catalog.search(query, contentType, loader, minecraftVersion); }
    public List<ModrinthProject> getMostDownloadedMods(Instance instance) throws IOException, InterruptedException { return catalog.getMostDownloadedMods(instance); }
    public List<ModrinthProject> getMostDownloaded(ModrinthContentType contentType, String loader, String minecraftVersion) throws IOException, InterruptedException { return catalog.getMostDownloaded(contentType, loader, minecraftVersion); }
    public List<ModrinthProject> searchResourcePacks(String query, String minecraftVersion) throws IOException, InterruptedException { return catalog.searchResourcePacks(query, minecraftVersion); }
    public List<ModrinthProject> searchShaders(String query, String minecraftVersion) throws IOException, InterruptedException { return catalog.searchShaders(query, minecraftVersion); }
    public List<ModrinthProject> searchModpacks(String query, String minecraftVersion) throws IOException, InterruptedException { return catalog.searchModpacks(query, minecraftVersion); }
    public ModrinthProject getProjectBySlug(String slug) throws IOException, InterruptedException { return catalog.getProjectBySlug(slug); }
    public Path installMod(Instance instance, ModrinthProject project) throws IOException, InterruptedException {
        return installer.installMod(instance, project);
    }

    public Path installMod(Instance instance, ModrinthProject project,
                           java.util.function.Consumer<String> progress)
            throws IOException, InterruptedException {
        return installer.installMod(instance, project, progress);
    }
    public Path installModVersion(Instance instance, String projectId, ModrinthVersion version) throws IOException, InterruptedException { return installer.installModVersion(instance, projectId, version); }
    public List<ResolvedMod> resolveModGraph(Instance instance, List<ModrinthProject> roots, String requiredProjectId, List<String> requiredVersions) throws IOException, InterruptedException { return dependencies.resolveModGraph(instance, roots, requiredProjectId, requiredVersions); }
    public List<ResolvedMod> resolveModGraph(
            Instance instance,
            List<ModrinthProject> roots,
            java.util.Map<String, String> pinnedVersionIds
    ) throws IOException, InterruptedException {
        return dependencies.resolveModGraph(instance, roots, null, List.of(), pinnedVersionIds);
    }
    public List<Path> installResolvedGraph(Instance instance, List<ResolvedMod> resolved) throws IOException, InterruptedException { return installer.installResolvedGraph(instance, resolved); }
    public Path repairModDependency(Instance instance, String projectId, List<String> requiredVersions) throws IOException, InterruptedException { return repair.repairModDependency(instance, projectId, requiredVersions); }
    public Path installResourcePack(Instance instance, ModrinthProject project) throws IOException, InterruptedException { return installer.installResourcePack(instance, project); }
    public Path installShader(Instance instance, ModrinthProject project) throws IOException, InterruptedException { return installer.installShader(instance, project); }
    @Deprecated
    public Path installDataPack(Instance instance, ModrinthProject project) throws IOException { return installer.installDataPack(instance, project); }
    public Path installDataPack(Instance instance, Path worldDirectory, ModrinthProject project) throws IOException, InterruptedException { return installer.installDataPack(instance, worldDirectory, project); }

    /**
     * Repairs mandatory dependencies declared by installed mod metadata before
     * Minecraft is started. Optional recommendations are deliberately ignored.
     */
    public List<InstalledModScanner.MissingDependency> repairMissingRequiredDependencies(
            Instance instance,
            java.util.function.Consumer<String> progress
    ) throws IOException, InterruptedException {
        Objects.requireNonNull(instance, "instance");
        java.util.function.Consumer<String> reporter = progress == null ? ignored -> { } : progress;
        List<InstalledModScanner.MissingDependency> missing =
                installedModScanner.findMissingRequiredDependencies(instance);
        if (missing.isEmpty()) return List.of();

        List<InstalledModScanner.MissingDependency> repaired = new ArrayList<>();
        for (InstalledModScanner.MissingDependency dependency : missing) {
            String modId = dependency.modId();
            String projectSlug = modrinthSlugForDependency(modId);
            reporter.accept("Resolving required dependency " + modId
                    + (projectSlug.equalsIgnoreCase(modId) ? "" : " (Modrinth: " + projectSlug + ")") + "...");
            ModrinthProject project = null;
            try {
                project = catalog.getProjectBySlug(projectSlug);
            } catch (IOException notFoundBySlug) {
                // Some loader IDs have a different upstream project slug.
            }

            if (project == null) {
                List<ModrinthProject> candidates = catalog.searchMods(instance, projectSlug);
                if (candidates != null) {
                    project = candidates.stream()
                            .filter(candidate -> candidate != null
                                    && projectSlug.equalsIgnoreCase(candidate.getSlug()))
                            .findFirst().orElse(null);
                }
            }

            if (project == null || project.getProjectId() == null
                    || project.getProjectId().isBlank()) {
                throw new IOException("A required mod dependency '" + modId
                        + "' declared by '" + dependency.requestingModId()
                        + "' is missing, and Vanta could not identify an exact Modrinth project. "
                        + "Minecraft was not started.");
            }

            reporter.accept("Installing " + project.getTitle() + "...");
            repairModDependency(instance, project.getProjectId(), List.of());

            boolean installed = installedModScanner.scan(instance).stream()
                    .anyMatch(mod -> mod != null && mod.getModId() != null
                            && modId.equalsIgnoreCase(mod.getModId()));
            if (!installed) {
                throw new IOException("Vanta attempted to install required dependency '" + modId
                        + "' but it is still missing. Minecraft was not started.");
            }
            repaired.add(dependency);
        }

        List<InstalledModScanner.MissingDependency> remaining =
                installedModScanner.findMissingRequiredDependencies(instance);
        if (!remaining.isEmpty()) {
            String unresolved = remaining.stream()
                    .map(InstalledModScanner.MissingDependency::modId)
                    .distinct().collect(java.util.stream.Collectors.joining(", "));
            throw new IOException("Required mod dependencies remain unresolved: "
                    + unresolved + ". Minecraft was not started.");
        }
        return List.copyOf(repaired);
    }

    /**
     * Finds newer Modrinth releases for mods installed through Vanta.
     * Untracked jars are deliberately left alone because Vanta cannot safely
     * identify their upstream project.
     */
    public List<ModUpdate> checkForModUpdates(Instance instance)
            throws IOException, InterruptedException {
        Objects.requireNonNull(instance, "instance");
        List<ModUpdate> updates = new ArrayList<>();
        Path modsDirectory = instance.getDirectory().resolve("mods").toAbsolutePath().normalize();

        for (InstalledModRecord installed : InstalledModManager.load(instance)) {
            if (installed == null || installed.getProjectId() == null
                    || installed.getProjectId().isBlank()) {
                continue;
            }

            String filename = installed.getFilename();
            if (filename == null || filename.isBlank()) {
                continue;
            }
            Path installedPath = modsDirectory.resolve(filename).normalize();
            if (!installedPath.startsWith(modsDirectory)) {
                continue;
            }

            List<ModrinthVersion> versions = client.getVersions(installed.getProjectId());
            ModrinthVersion latest = catalog.findCompatibleVersion(
                    versions, instance.getMinecraftVersion(), instance.getLoader());
            if (latest == null || latest.getId() == null || latest.getId().isBlank()) {
                continue;
            }

            boolean artifactMissing = !Files.isRegularFile(installedPath);
            if (artifactMissing || !latest.getId().equals(installed.getVersionId())) {
                updates.add(new ModUpdate(installed, latest, artifactMissing));
            }
        }
        return List.copyOf(updates);
    }

    /**
     * Installs one checked update through the normal transactional installer.
     * Disabled mods remain disabled after replacement.
     */
    public Path installTrackedModUpdate(Instance instance, ModUpdate update)
            throws IOException, InterruptedException {
        return installTrackedModUpdate(instance, update, ignored -> { });
    }

    public Path installTrackedModUpdate(
            Instance instance,
            ModUpdate update,
            java.util.function.Consumer<String> progress
    ) throws IOException, InterruptedException {
        Objects.requireNonNull(update, "update");
        InstalledModRecord previous = update.installed();
        if (previous == null || previous.getProjectId() == null
                || previous.getProjectId().isBlank()) {
            throw new IOException("The installed mod record has no valid project identity.");
        }

        boolean wasDisabled = previous.getFilename() != null
                && previous.getFilename().toLowerCase(java.util.Locale.ROOT).endsWith(".jar.disabled");
        Path installed = installer.installModVersion(instance, previous.getProjectId(), update.latest(), progress);
        if (!wasDisabled) {
            return installed;
        }

        Path disabled = installed.resolveSibling(installed.getFileName() + ".disabled");
        try {
            try {
                Files.move(installed, disabled, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(installed, disabled);
            }

            InstalledModRecord replacement = new InstalledModRecord(
                    previous.getProjectId(),
                    update.latest().getId(),
                    previous.getModId(),
                    update.latest().getVersionNumber(),
                    disabled.getFileName().toString());
            InstalledModManager.add(instance, replacement);
            return disabled;
        } catch (IOException failure) {
            if (Files.exists(disabled) && !Files.exists(installed)) {
                try {
                    Files.move(disabled, installed, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }
    }

    public record ModUpdate(InstalledModRecord installed, ModrinthVersion latest, boolean artifactMissing) {}

    public record ResolvedMod(String projectId, ModrinthVersion version) {}
}
