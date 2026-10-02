package org.example.launcher.service;

import org.example.launcher.modrinth.InstalledModScanner;
import org.example.launcher.modrinth.*;
import org.example.launcher.model.Instance;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ModrinthService {
    private final ModrinthClient client = new ModrinthClient();
    private final InstalledModScanner installedModScanner = new InstalledModScanner();
    private final ModrinthCatalogService catalog = new ModrinthCatalogService(client, installedModScanner);
    private final ModrinthDependencyResolver dependencies = new ModrinthDependencyResolver(client, installedModScanner);
    private final ModrinthInstallService installer = new ModrinthInstallService(client, installedModScanner, catalog, dependencies);
    private final ModrinthRepairService repair = new ModrinthRepairService(client, installedModScanner, catalog, dependencies, installer);

    public List<ModrinthProject> searchMods(String query) throws IOException, InterruptedException { return catalog.searchMods(query); }
    public List<ModrinthProject> searchMods(Instance instance, String query) throws IOException, InterruptedException { return catalog.searchMods(instance, query); }
    public List<ModrinthProject> search(String query, ModrinthContentType contentType, String loader, String minecraftVersion) throws IOException, InterruptedException { return catalog.search(query, contentType, loader, minecraftVersion); }
    public List<ModrinthProject> getMostDownloadedMods(Instance instance) throws IOException, InterruptedException { return catalog.getMostDownloadedMods(instance); }
    public List<ModrinthProject> getMostDownloaded(ModrinthContentType contentType, String loader, String minecraftVersion) throws IOException, InterruptedException { return catalog.getMostDownloaded(contentType, loader, minecraftVersion); }
    public List<ModrinthProject> searchResourcePacks(String query, String minecraftVersion) throws IOException, InterruptedException { return catalog.searchResourcePacks(query, minecraftVersion); }
    public List<ModrinthProject> searchShaders(String query, String minecraftVersion) throws IOException, InterruptedException { return catalog.searchShaders(query, minecraftVersion); }
    public List<ModrinthProject> searchModpacks(String query, String minecraftVersion) throws IOException, InterruptedException { return catalog.searchModpacks(query, minecraftVersion); }
    public ModrinthProject getProjectBySlug(String slug) throws IOException, InterruptedException { return catalog.getProjectBySlug(slug); }
    public Path installMod(Instance instance, ModrinthProject project) throws IOException, InterruptedException { return installer.installMod(instance, project); }
    public List<ResolvedMod> resolveModGraph(Instance instance, List<ModrinthProject> roots, String requiredProjectId, List<String> requiredVersions) throws IOException, InterruptedException { return dependencies.resolveModGraph(instance, roots, requiredProjectId, requiredVersions); }
    public List<Path> installResolvedGraph(Instance instance, List<ResolvedMod> resolved) throws IOException, InterruptedException { return installer.installResolvedGraph(instance, resolved); }
    public Path repairModDependency(Instance instance, String projectId, List<String> requiredVersions) throws IOException, InterruptedException { return repair.repairModDependency(instance, projectId, requiredVersions); }
    public Path installResourcePack(Instance instance, ModrinthProject project) throws IOException, InterruptedException { return installer.installResourcePack(instance, project); }
    public Path installShader(Instance instance, ModrinthProject project) throws IOException, InterruptedException { return installer.installShader(instance, project); }

    public record ResolvedMod(String projectId, ModrinthVersion version) {}
}
