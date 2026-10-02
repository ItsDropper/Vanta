package org.example.launcher.service;

import org.example.launcher.instance.*;
import org.example.launcher.modrinth.*;
import org.example.launcher.model.Instance;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class ModrinthInstallService {
    private final ModrinthClient client;
    private final InstalledModScanner installedModScanner;
    private final ModrinthCatalogService catalog;
    private final ModrinthDependencyResolver dependencies;

    public ModrinthInstallService(ModrinthClient client, InstalledModScanner installedModScanner,
                                  ModrinthCatalogService catalog, ModrinthDependencyResolver dependencies) {
        this.client = client;
        this.installedModScanner = installedModScanner;
        this.catalog = catalog;
        this.dependencies = dependencies;
    }

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

public List<Path> installResolvedGraph(
            Instance instance,
            List<ModrinthService.ResolvedMod> resolvedMods
    ) throws IOException, InterruptedException {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        if (resolvedMods == null
                || resolvedMods.isEmpty()) {

            return List.of();
        }

        List<Path> installed =
                new ArrayList<>();

        for (ModrinthService.ResolvedMod resolved :
                resolvedMods) {

            if (resolved == null
                    || resolved.projectId() == null
                    || resolved.version() == null) {

                continue;
            }

            installed.add(
                    installResolvedVersion(
                            instance,
                            resolved.projectId(),
                            resolved.version()
                    )
            );
        }

        return installed;
    }

private Path installResolvedVersion(
            Instance instance,
            String projectId,
            ModrinthVersion version
    ) throws IOException, InterruptedException {

        ModrinthFile file =
                catalog.findPrimaryFile(version);

        if (file == null) {
            throw new IOException(
                    "Modrinth version has no downloadable file: "
                            + projectId
            );
        }

        Path modsDirectory =
                instance.getDirectory()
                        .resolve("mods");

        Files.createDirectories(modsDirectory);

        /*
         * If this project is already tracked and the exact same
         * version is installed, don't redownload it.
         */
        InstalledModRecord existing =
                InstalledModManager.findByProjectId(
                        instance,
                        projectId
                );

        if (existing != null
                && version.getId().equals(
                existing.getVersionId()
        )
                && existing.getFilename() != null) {

            Path existingFile =
                    modsDirectory.resolve(
                            existing.getFilename()
                    );

            if (Files.isRegularFile(existingFile)) {
                return existingFile;
            }
        }

        /*
         * Remove an older tracked version before installing the
         * replacement.
         */
        if (existing != null) {
            removeInstalledMod(
                    instance,
                    projectId
            );
        }

        Path installedFile =
                modsDirectory.resolve(
                        catalog.sanitizeFilename(
                                file.getFilename()
                        )
                );

        if (!Files.exists(installedFile)) {

            try {

                DownloadUtil.downloadFile(
                        file.getUrl(),
                        installedFile
                );

            } catch (Exception e) {

                throw new IOException(
                        "Failed to download mod: "
                                + file.getFilename(),
                        e
                );
            }
        }

        InstalledMod installedMod =
                installedModScanner.scanFile(
                        installedFile
                );

        InstalledModManager.add(
                instance,
                new InstalledModRecord(
                        projectId,
                        version.getId(),
                        installedMod != null
                                ? installedMod.getModId()
                                : null,
                        installedMod != null
                                ? installedMod.getVersion()
                                : version.getVersionNumber(),
                        installedFile.getFileName()
                                .toString()
                )
        );

        return installedFile;
    }

private Path installModVersion(
            Instance instance,
            String projectId,
            ModrinthVersion version,
            Set<String> resolvingProjects
    ) throws IOException, InterruptedException {

        if (projectId == null
                || projectId.isBlank()
                || version == null) {

            throw new IOException(
                    "Invalid dependency version."
            );
        }

        String resolveKey =
                projectId + ":" + version.getId();

        if (!resolvingProjects.add(resolveKey)) {

            throw new IOException(
                    "Circular dependency detected: "
                            + projectId
            );
        }

        try {

            List<ModrinthDependency> dependencies =
                    version.getDependencies();

            if (dependencies != null) {

                for (ModrinthDependency dependency :
                        dependencies) {

                    if (dependency == null
                            || dependency.isOptional()
                            || dependency.isIncompatible()) {

                        continue;
                    }

                    String dependencyProjectId =
                            dependency.getProjectId();

                    if (dependencyProjectId == null
                            || dependencyProjectId.isBlank()) {

                        continue;
                    }

                    if (dependencies.hasCompatibleInstalledDependency(
                            instance,
                            dependency,
                            version
                    )) {
                        continue;
                    }

                    ModrinthVersion dependencyVersion =
                            dependencies.resolveDependencyVersion(
                                    instance,
                                    dependency
                            );

                    if (dependencyVersion == null) {

                        throw new IOException(
                                "No compatible version found for dependency "
                                        + dependencyProjectId
                        );
                    }

                    installModVersion(
                            instance,
                            dependencyProjectId,
                            dependencyVersion,
                            resolvingProjects
                    );
                }
            }

            return installResolvedVersion(
                    instance,
                    projectId,
                    version
            );

        } finally {
            resolvingProjects.remove(resolveKey);
        }
    }

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
                    catalog.normalizeLoader(
                            instance.getLoader()
                    );

            String minecraftVersion =
                    instance.getMinecraftVersion();

            List<ModrinthVersion> versions =
                    client.getVersions(projectId);

            ModrinthVersion compatibleVersion =
                    catalog.findCompatibleVersion(
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

            List<ModrinthDependency> dependencies =
                    compatibleVersion.getDependencies();

            if (dependencies != null) {

                for (ModrinthDependency dependency :
                        dependencies) {

                    if (dependency == null
                            || dependency.isOptional()
                            || dependency.isIncompatible()) {

                        continue;
                    }

                    String dependencyProjectId =
                            dependency.getProjectId();

                    if (dependencyProjectId == null
                            || dependencyProjectId.isBlank()) {

                        continue;
                    }

                    if (dependencies.hasCompatibleInstalledDependency(
                            instance,
                            dependency,
                            compatibleVersion
                    )) {
                        continue;
                    }

                    ModrinthVersion dependencyVersion =
                            dependencies.resolveDependencyVersion(
                                    instance,
                                    dependency
                            );

                    if (dependencyVersion == null) {

                        throw new IOException(
                                "No compatible version found for dependency "
                                        + dependencyProjectId
                        );
                    }

                    installModVersion(
                            instance,
                            dependencyProjectId,
                            dependencyVersion,
                            resolvingProjects
                    );
                }
            }

            return installResolvedVersion(
                    instance,
                    projectId,
                    compatibleVersion
            );

        } finally {
            resolvingProjects.remove(projectId);
        }
    }

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
                catalog.findContentVersion(
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
                catalog.findPrimaryFile(
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
                catalog.sanitizeFilename(
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
}
