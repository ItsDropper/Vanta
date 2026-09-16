package org.example.launcher.service;

import org.example.launcher.instance.DownloadUtil;
import org.example.launcher.modrinth.*;
import org.example.launcher.model.Instance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModrinthService {

    private final ModrinthClient client;

    public ModrinthService() {
        client = new ModrinthClient();
    }

    // =============================================================
    // GLOBAL MOD SEARCH
    // =============================================================

    public List<ModrinthProject> searchMods(
            String query
    ) throws IOException, InterruptedException {

        return search(
                query,
                ModrinthContentType.MOD,
                null,
                null
        );
    }

    // =============================================================
    // INSTANCE MOD SEARCH
    // =============================================================

    public List<ModrinthProject> searchMods(
            Instance instance,
            String query
    ) throws IOException, InterruptedException {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        return search(
                query,
                ModrinthContentType.MOD,
                normalizeLoader(instance.getLoader()),
                instance.getMinecraftVersion()
        );
    }

    // =============================================================
    // GENERIC SEARCH
    // =============================================================

    public List<ModrinthProject> search(
            String query,
            ModrinthContentType contentType,
            String loader,
            String minecraftVersion
    ) throws IOException, InterruptedException {

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException(
                    "Search query cannot be empty."
            );
        }

        List<ModrinthSearchHit> hits =
                client.search(
                        query,
                        contentType,
                        loader,
                        minecraftVersion
                ).getHits();

        return resolveProjects(hits);
    }

    // =============================================================
    // MOST DOWNLOADED MODS
    // =============================================================

    public List<ModrinthProject> getMostDownloadedMods(
            Instance instance
    ) throws IOException, InterruptedException {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        return getMostDownloaded(
                ModrinthContentType.MOD,
                normalizeLoader(instance.getLoader()),
                instance.getMinecraftVersion()
        );
    }

    // =============================================================
    // MOST DOWNLOADED
    // =============================================================

    public List<ModrinthProject> getMostDownloaded(
            ModrinthContentType contentType,
            String loader,
            String minecraftVersion
    ) throws IOException, InterruptedException {

        List<ModrinthSearchHit> hits =
                client.getMostDownloaded(
                        contentType,
                        loader,
                        minecraftVersion
                ).getHits();

        return resolveProjects(hits);
    }

    // =============================================================
    // RESOURCE PACK SEARCH
    // =============================================================

    public List<ModrinthProject> searchResourcePacks(
            String query,
            String minecraftVersion
    ) throws IOException, InterruptedException {

        return search(
                query,
                ModrinthContentType.RESOURCE_PACK,
                null,
                minecraftVersion
        );
    }

    // =============================================================
    // SHADER SEARCH
    // =============================================================

    public List<ModrinthProject> searchShaders(
            String query,
            String minecraftVersion
    ) throws IOException, InterruptedException {

        return search(
                query,
                ModrinthContentType.SHADER,
                null,
                minecraftVersion
        );
    }

    // =============================================================
    // MODPACK SEARCH
    // =============================================================

    public List<ModrinthProject> searchModpacks(
            String query,
            String minecraftVersion
    ) throws IOException, InterruptedException {

        return search(
                query,
                ModrinthContentType.MODPACK,
                null,
                minecraftVersion
        );
    }

    // =============================================================
    // GET PROJECT BY SLUG
    // =============================================================

    public ModrinthProject getProjectBySlug(
            String slug
    ) throws IOException, InterruptedException {

        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException(
                    "Modrinth project slug cannot be empty."
            );
        }

        return client.getProject(slug);
    }

    // =============================================================
    // INSTALL MOD
    // =============================================================

    public Path installMod(
            Instance instance,
            ModrinthProject project
    ) throws IOException, InterruptedException {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        if (project == null) {
            throw new IllegalArgumentException(
                    "Project cannot be null."
            );
        }

        if (!ModrinthContentType.MOD
                .getApiValue()
                .equals(project.getProjectType())) {

            throw new IOException(
                    "Project is not a mod."
            );
        }

        Set<String> resolvingProjects =
                new HashSet<>();

        return installModProject(
                instance,
                project.getProjectId(),
                resolvingProjects
        );
    }

    // =============================================================
    // INSTALL MOD PROJECT
    // =============================================================

    private Path installModProject(
            Instance instance,
            String projectId,
            Set<String> resolvingProjects
    ) throws IOException, InterruptedException {

        if (projectId == null
                || projectId.isBlank()) {

            throw new IOException(
                    "Invalid Modrinth project ID."
            );
        }

        if (!resolvingProjects.add(projectId)) {
            throw new IOException(
                    "Circular Modrinth dependency detected: "
                            + projectId
            );
        }

        try {

            String loader =
                    normalizeLoader(
                            instance.getLoader()
                    );

            String minecraftVersion =
                    instance.getMinecraftVersion();

            List<ModrinthVersion> versions =
                    client.getVersions(projectId);

            ModrinthVersion compatibleVersion =
                    findCompatibleVersion(
                            versions,
                            minecraftVersion,
                            loader
                    );

            if (compatibleVersion == null) {
                throw new IOException(
                        "No compatible version found for "
                                + projectId
                                + " for Minecraft "
                                + minecraftVersion
                                + " / "
                                + loader
                );
            }

            // -----------------------------------------------------
            // DEPENDENCIES
            // -----------------------------------------------------

            List<ModrinthDependency> dependencies =
                    compatibleVersion.getDependencies();

            if (dependencies != null) {

                for (ModrinthDependency dependency
                        : dependencies) {

                    if (dependency == null) {
                        continue;
                    }

                    if (dependency.isIncompatible()) {
                        continue;
                    }

                    if (dependency.isOptional()) {
                        continue;
                    }

                    String dependencyProjectId =
                            dependency.getProjectId();

                    if (dependencyProjectId == null
                            || dependencyProjectId.isBlank()) {
                        continue;
                    }

                    installModProject(
                            instance,
                            dependencyProjectId,
                            resolvingProjects
                    );
                }
            }

            // -----------------------------------------------------
            // FILE
            // -----------------------------------------------------

            ModrinthFile file =
                    findPrimaryFile(
                            compatibleVersion
                    );

            if (file == null) {
                throw new IOException(
                        "Modrinth version has no downloadable file."
                );
            }

            return installFile(
                    file,
                    instance.getDirectory()
                            .resolve("mods")
            );

        } finally {

            resolvingProjects.remove(
                    projectId
            );
        }
    }

    // =============================================================
    // INSTALL RESOURCE PACK
    // =============================================================

    public Path installResourcePack(
            Instance instance,
            ModrinthProject project
    ) throws IOException, InterruptedException {

        return installContent(
                instance,
                project,
                ModrinthContentType.RESOURCE_PACK,
                "resourcepacks"
        );
    }

    // =============================================================
    // INSTALL SHADER
    // =============================================================

    public Path installShader(
            Instance instance,
            ModrinthProject project
    ) throws IOException, InterruptedException {

        return installContent(
                instance,
                project,
                ModrinthContentType.SHADER,
                "shaderpacks"
        );
    }

    // =============================================================
    // INSTALL CONTENT
    // =============================================================

    private Path installContent(
            Instance instance,
            ModrinthProject project,
            ModrinthContentType contentType,
            String directoryName
    ) throws IOException, InterruptedException {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        if (project == null) {
            throw new IllegalArgumentException(
                    "Project cannot be null."
            );
        }

        if (!contentType.getApiValue()
                .equals(project.getProjectType())) {

            throw new IOException(
                    "Project is not a "
                            + contentType.getDisplayName()
                            + "."
            );
        }

        List<ModrinthVersion> versions =
                client.getVersions(
                        project.getProjectId()
                );

        ModrinthVersion compatibleVersion =
                findContentVersion(
                        versions,
                        instance.getMinecraftVersion()
                );

        if (compatibleVersion == null) {
            throw new IOException(
                    "No compatible "
                            + contentType.getDisplayName()
                            + " version found for Minecraft "
                            + instance.getMinecraftVersion()
            );
        }

        ModrinthFile file =
                findPrimaryFile(
                        compatibleVersion
                );

        if (file == null) {
            throw new IOException(
                    "Modrinth version has no downloadable file."
            );
        }

        return installFile(
                file,
                instance.getDirectory()
                        .resolve(directoryName)
        );
    }

    // =============================================================
    // INSTALL FILE
    // =============================================================

    private Path installFile(
            ModrinthFile file,
            Path directory
    ) throws IOException {

        if (file.getUrl() == null
                || file.getUrl().isBlank()) {

            throw new IOException(
                    "Modrinth file has no download URL."
            );
        }

        Files.createDirectories(directory);

        String filename =
                sanitizeFilename(
                        file.getFilename()
                );

        Path destination =
                directory.resolve(filename);

        if (Files.exists(destination)) {
            return destination;
        }

        try {
            DownloadUtil.downloadFile(
                    file.getUrl(),
                    destination
            );
        } catch (Exception e) {
            throw new IOException(
                    "Failed to download Modrinth file: "
                            + file.getFilename(),
                    e
            );
        }

        return destination;
    }

    // =============================================================
    // RESOLVE SEARCH HITS
    // =============================================================

    private List<ModrinthProject> resolveProjects(
            List<ModrinthSearchHit> hits
    ) throws IOException, InterruptedException {

        List<ModrinthProject> projects =
                new ArrayList<>();

        if (hits == null || hits.isEmpty()) {
            return projects;
        }

        for (ModrinthSearchHit hit : hits) {

            if (hit == null) {
                continue;
            }

            String projectId =
                    hit.getProjectId();

            if (projectId == null
                    || projectId.isBlank()) {
                continue;
            }

            try {

                ModrinthProject project =
                        client.getProject(projectId);

                if (project != null) {
                    projects.add(project);
                }

            } catch (IOException ignored) {
                // One broken project should not destroy
                // the entire search result.
            }
        }

        return projects;
    }

    // =============================================================
    // FIND MOD VERSION
    // =============================================================

    private ModrinthVersion findCompatibleVersion(
            List<ModrinthVersion> versions,
            String minecraftVersion,
            String loader
    ) {

        if (versions == null
                || versions.isEmpty()) {
            return null;
        }

        ModrinthVersion release =
                versions.stream()
                        .filter(version ->
                                isCompatible(
                                        version,
                                        minecraftVersion,
                                        loader
                                )
                        )
                        .filter(version ->
                                "release".equalsIgnoreCase(
                                        version.getVersionType()
                                )
                        )
                        .findFirst()
                        .orElse(null);

        if (release != null) {
            return release;
        }

        return versions.stream()
                .filter(version ->
                        isCompatible(
                                version,
                                minecraftVersion,
                                loader
                        )
                )
                .findFirst()
                .orElse(null);
    }

    // =============================================================
    // FIND RESOURCE PACK / SHADER VERSION
    // =============================================================

    private ModrinthVersion findContentVersion(
            List<ModrinthVersion> versions,
            String minecraftVersion
    ) {

        if (versions == null
                || versions.isEmpty()) {
            return null;
        }

        ModrinthVersion release =
                versions.stream()
                        .filter(version ->
                                isGameVersionCompatible(
                                        version,
                                        minecraftVersion
                                )
                        )
                        .filter(version ->
                                "release".equalsIgnoreCase(
                                        version.getVersionType()
                                )
                        )
                        .findFirst()
                        .orElse(null);

        if (release != null) {
            return release;
        }

        return versions.stream()
                .filter(version ->
                        isGameVersionCompatible(
                                version,
                                minecraftVersion
                        )
                )
                .findFirst()
                .orElse(null);
    }

    // =============================================================
    // COMPATIBILITY
    // =============================================================

    private boolean isCompatible(
            ModrinthVersion version,
            String minecraftVersion,
            String loader
    ) {

        if (!isGameVersionCompatible(
                version,
                minecraftVersion
        )) {
            return false;
        }

        if (version.getLoaders() == null) {
            return false;
        }

        return version.getLoaders()
                .stream()
                .map(this::normalizeLoader)
                .anyMatch(loader::equals);
    }

    private boolean isGameVersionCompatible(
            ModrinthVersion version,
            String minecraftVersion
    ) {

        return version != null
                && version.getGameVersions() != null
                && version.getGameVersions()
                .contains(minecraftVersion);
    }

    // =============================================================
    // PRIMARY FILE
    // =============================================================

    private ModrinthFile findPrimaryFile(
            ModrinthVersion version
    ) {

        if (version == null
                || version.getFiles() == null
                || version.getFiles().isEmpty()) {
            return null;
        }

        return version.getFiles()
                .stream()
                .filter(ModrinthFile::isPrimary)
                .findFirst()
                .orElse(
                        version.getFiles().get(0)
                );
    }

    // =============================================================
    // LOADER
    // =============================================================

    private String normalizeLoader(
            String loader
    ) {

        if (loader == null) {
            return "";
        }

        return loader
                .trim()
                .toLowerCase();
    }

    // =============================================================
    // FILENAME
    // =============================================================

    private String sanitizeFilename(
            String filename
    ) {

        if (filename == null
                || filename.isBlank()) {

            throw new IllegalArgumentException(
                    "Modrinth returned an invalid filename."
            );
        }

        String safe =
                Path.of(filename)
                        .getFileName()
                        .toString();

        if (safe.equals(".")
                || safe.equals("..")
                || safe.isBlank()) {

            throw new IllegalArgumentException(
                    "Modrinth returned an invalid filename."
            );
        }

        return safe;
    }
}