package dev.itsdropper.vanta.launcher.service;

import dev.itsdropper.vanta.launcher.modrinth.InstalledModScanner;
import dev.itsdropper.vanta.launcher.modrinth.*;
import dev.itsdropper.vanta.launcher.model.Instance;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ModrinthService {
    private final ModrinthClient client = new ModrinthClient();
    private final InstalledModScanner installedModScanner = new InstalledModScanner();
    private final ModrinthCatalogService catalog = new ModrinthCatalogService(client, installedModScanner);
    private final ModrinthDependencyResolver dependencies = new ModrinthDependencyResolver(client, installedModScanner);
    private final ModrinthInstallService installer = new ModrinthInstallService(client, installedModScanner, catalog, dependencies);
    private final ModrinthRepairService repair = new ModrinthRepairService(client, installedModScanner, catalog, dependencies, installer);

    public List<ModrinthProject> searchMods(String query) throws IOException, InterruptedException { return catalog.searchMods(query); }
    public List<ModrinthProject> searchMods(Instance instance, String query) throws IOException, InterruptedException { return catalog.searchMods(instance, query); }

    /**
     * Fast list search for the instance browser. Modrinth's search payload already
     * includes every field needed for result cards, so do not make a second request
     * to /project/{id} for each hit.
     */
    public List<ModrinthProject> searchModsFast(Instance instance, String query) throws IOException, InterruptedException {
        if (instance == null) throw new IllegalArgumentException("Instance cannot be null.");
        String loader = instance.getLoader() == null ? "" : instance.getLoader().trim().toLowerCase(Locale.ROOT);
        List<ModrinthSearchHit> hits = client.search(query, ModrinthContentType.MOD, loader, instance.getMinecraftVersion()).getHits();
        return mapSearchHits(hits);
    }

    /**
     * First-paint query matching the global browser: filter by loader but do not
     * wait for Modrinth's additional exact Minecraft-version facet.
     */
    public List<ModrinthProject> searchModsFastBroad(Instance instance, String query) throws IOException, InterruptedException {
        if (instance == null) throw new IllegalArgumentException("Instance cannot be null.");
        String loader = instance.getLoader() == null ? "" : instance.getLoader().trim().toLowerCase(Locale.ROOT);
        return mapSearchHits(client.search(query, ModrinthContentType.MOD, loader, null).getHits());
    }

    public List<ModrinthProject> getMostDownloadedModsFast(Instance instance) throws IOException, InterruptedException {
        if (instance == null) throw new IllegalArgumentException("Instance cannot be null.");
        String loader = instance.getLoader() == null ? "" : instance.getLoader().trim().toLowerCase(Locale.ROOT);
        List<ModrinthSearchHit> hits = client.getMostDownloaded(ModrinthContentType.MOD, loader, instance.getMinecraftVersion()).getHits();
        return mapSearchHits(hits);
    }

    public List<ModrinthProject> getMostDownloadedModsFastBroad(Instance instance) throws IOException, InterruptedException {
        if (instance == null) throw new IllegalArgumentException("Instance cannot be null.");
        String loader = instance.getLoader() == null ? "" : instance.getLoader().trim().toLowerCase(Locale.ROOT);
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
    public Path installMod(Instance instance, ModrinthProject project) throws IOException, InterruptedException { return installer.installMod(instance, project); }
    public Path installModVersion(Instance instance, String projectId, ModrinthVersion version) throws IOException, InterruptedException { return installer.installModVersion(instance, projectId, version); }
    public List<ResolvedMod> resolveModGraph(Instance instance, List<ModrinthProject> roots, String requiredProjectId, List<String> requiredVersions) throws IOException, InterruptedException { return dependencies.resolveModGraph(instance, roots, requiredProjectId, requiredVersions); }
    public List<Path> installResolvedGraph(Instance instance, List<ResolvedMod> resolved) throws IOException, InterruptedException { return installer.installResolvedGraph(instance, resolved); }
    public Path repairModDependency(Instance instance, String projectId, List<String> requiredVersions) throws IOException, InterruptedException { return repair.repairModDependency(instance, projectId, requiredVersions); }
    public Path installResourcePack(Instance instance, ModrinthProject project) throws IOException, InterruptedException { return installer.installResourcePack(instance, project); }
    public Path installShader(Instance instance, ModrinthProject project) throws IOException, InterruptedException { return installer.installShader(instance, project); }

    public record ResolvedMod(String projectId, ModrinthVersion version) {}
}
