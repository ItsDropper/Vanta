package org.example.launcher.service;

import org.example.launcher.instance.DownloadUtil;
import org.example.launcher.instance.InstalledModManager;
import org.example.launcher.instance.InstalledModRecord;
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
    private final InstalledModScanner installedModScanner;

    public ModrinthService() {
        client = new ModrinthClient();
        installedModScanner = new InstalledModScanner();
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
    // INSTALL SPECIFIC MOD VERSION
    // =============================================================

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

                    if (hasCompatibleInstalledDependency(
                            instance,
                            dependency,
                            version
                    )) {

                        System.out.println(
                                "[Vanta] Skipping compatible dependency: "
                                        + dependencyProjectId
                        );

                        continue;
                    }

                    ModrinthVersion dependencyVersion =
                            resolveDependencyVersion(
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

            ModrinthFile file =
                    findPrimaryFile(version);

            if (file == null) {
                throw new IOException(
                        "Modrinth version has no downloadable file."
                );
            }

            Path installedFile =
                    installFile(
                            file,
                            instance.getDirectory()
                                    .resolve("mods")
                    );

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

        } finally {

            resolvingProjects.remove(resolveKey);
        }
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

                    System.out.println(
                            "[Vanta] Dependency: "
                                    + dependencyProjectId
                                    + " versionId="
                                    + dependency.getVersionId()
                                    + " fileName="
                                    + dependency.getFileName()
                                    + " type="
                                    + dependency.getDependencyType()
                    );

                    if (hasCompatibleInstalledDependency(
                            instance,
                            dependency,
                            compatibleVersion
                    )) {

                        System.out.println(
                                "[Vanta] Skipping already compatible dependency: "
                                        + dependencyProjectId
                        );

                        continue;
                    }

                    ModrinthVersion dependencyVersion =
                            resolveDependencyVersion(
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

            ModrinthFile file =
                    findPrimaryFile(
                            compatibleVersion
                    );

            if (file == null) {
                throw new IOException(
                        "Modrinth version has no downloadable file."
                );
            }

            Path installedFile =
                    installFile(
                            file,
                            instance.getDirectory()
                                    .resolve("mods")
                    );

            InstalledMod installedMod =
                    installedModScanner.scanFile(
                            installedFile
                    );

            InstalledModManager.add(
                    instance,
                    new InstalledModRecord(
                            projectId,
                            compatibleVersion.getId(),
                            installedMod != null
                                    ? installedMod.getModId()
                                    : null,
                            installedMod != null
                                    ? installedMod.getVersion()
                                    : compatibleVersion.getVersionNumber(),
                            installedFile.getFileName()
                                    .toString()
                    )
            );

            return installedFile;

        } finally {

            resolvingProjects.remove(projectId);
        }
    }

    // =============================================================
    // FIND FABRIC MOD ID
    // =============================================================

    private String findFabricModId(
            Instance instance,
            String projectId
    ) throws IOException, InterruptedException {

        if (projectId == null
                || projectId.isBlank()) {

            return null;
        }

        List<ModrinthVersion> versions =
                client.getVersions(projectId);

        String loader = "fabric";

        for (ModrinthVersion version : versions) {

            if (!isCompatible(
                    version,
                    instance.getMinecraftVersion(),
                    loader
            )) {
                continue;
            }

            ModrinthFile file =
                    findPrimaryFile(version);

            if (file == null
                    || file.getUrl() == null
                    || file.getUrl().isBlank()) {
                continue;
            }

            Path tempFile =
                    Files.createTempFile(
                            "vanta-mod-",
                            ".jar"
                    );

            try {

                try {

                    DownloadUtil.downloadFile(
                            file.getUrl(),
                            tempFile
                    );

                } catch (Exception e) {

                    throw new IOException(
                            "Failed to download dependency metadata.",
                            e
                    );
                }

                InstalledMod installed =
                        installedModScanner.scanFile(
                                tempFile
                        );

                if (installed != null) {
                    return installed.getModId();
                }

            } finally {

                Files.deleteIfExists(tempFile);
            }
        }

        return null;
    }

    // =============================================================
    // CHECK INSTALLED DEPENDENCY
    // =============================================================

    private boolean hasCompatibleInstalledDependency(
            Instance instance,
            ModrinthDependency dependency,
            ModrinthVersion requestingVersion
    ) throws IOException, InterruptedException {

        String dependencyProjectId =
                dependency.getProjectId();

        if (dependencyProjectId == null
                || dependencyProjectId.isBlank()) {

            return false;
        }

        InstalledMod dependencyMod =
                findInstalledDependencyMod(
                        instance,
                        dependencyProjectId
                );

        if (dependencyMod == null
                || dependencyMod.getVersion() == null
                || dependencyMod.getVersion().isBlank()) {

            return false;
        }

        /*
         * Get the actual Fabric dependency ID and version constraint
         * from the requesting mod's fabric.mod.json.
         */
        DependencyRequirement requirement =
                findDependencyRequirement(
                        instance,
                        requestingVersion,
                        dependencyMod.getModId()
                );

        if (requirement == null) {
            return false;
        }

        boolean compatible =
                VersionConstraintChecker.matches(
                        dependencyMod.getVersion(),
                        requirement.getVersionConstraint()
                );

        System.out.println(
                "[Vanta] Dependency check: "
                        + dependencyMod.getModId()
                        + " "
                        + dependencyMod.getVersion()
                        + " against "
                        + requirement.getVersionConstraint()
                        + " -> "
                        + compatible
        );

        return compatible;
    }

    private InstalledMod findInstalledModByFilename(
            Instance instance,
            String filename
    ) throws IOException {

        if (filename == null
                || filename.isBlank()) {

            return null;
        }

        Path file =
                instance.getDirectory()
                        .resolve("mods")
                        .resolve(filename);

        if (!Files.isRegularFile(file)) {
            return null;
        }

        return installedModScanner.scanFile(file);
    }

    private InstalledMod findInstalledDependencyMod(
            Instance instance,
            String projectId
    ) throws IOException, InterruptedException {

        InstalledModRecord record =
                InstalledModManager.findByProjectId(
                        instance,
                        projectId
                );

        if (record != null) {

            InstalledMod installed =
                    findInstalledModByFilename(
                            instance,
                            record.getFilename()
                    );

            if (installed != null) {
                return installed;
            }
        }

        /*
         * Also check manually installed mods.
         */
        String fabricModId =
                findFabricModId(
                        instance,
                        projectId
                );

        if (fabricModId == null
                || fabricModId.isBlank()) {

            return null;
        }

        for (InstalledMod installed :
                installedModScanner.scan(instance)) {

            if (fabricModId.equals(
                    installed.getModId()
            )) {

                return installed;
            }
        }

        return null;
    }

    private DependencyRequirement findDependencyRequirement(
            Instance instance,
            ModrinthVersion requestingVersion,
            String dependencyModId
    ) throws IOException {

        if (requestingVersion == null
                || dependencyModId == null
                || dependencyModId.isBlank()) {

            return null;
        }

        ModrinthFile file =
                findPrimaryFile(requestingVersion);

        if (file == null
                || file.getUrl() == null
                || file.getUrl().isBlank()) {

            return null;
        }

        Path tempFile =
                Files.createTempFile(
                        "vanta-requesting-mod-",
                        ".jar"
                );

        try {

            try {

                DownloadUtil.downloadFile(
                        file.getUrl(),
                        tempFile
                );

            } catch (Exception e) {

                throw new IOException(
                        "Failed to inspect mod dependency metadata.",
                        e
                );
            }

            InstalledMod requestingMod =
                    installedModScanner.scanFile(
                            tempFile
                    );

            if (requestingMod == null) {
                return null;
            }

            for (DependencyRequirement requirement :
                    requestingMod.getDependencies()) {

                if (dependencyModId.equals(
                        requirement.getModId()
                )) {

                    return requirement;
                }
            }

            return null;

        } finally {

            Files.deleteIfExists(tempFile);
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

            System.out.println(
                    "[Vanta] File already exists: "
                            + filename
            );

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

    // =============================================================
    // RESOLVE DEPENDENCY VERSION
    // =============================================================

    private ModrinthVersion resolveDependencyVersion(
            Instance instance,
            ModrinthDependency dependency
    ) throws IOException, InterruptedException {

        String projectId =
                dependency.getProjectId();

        if (projectId == null
                || projectId.isBlank()) {

            return null;
        }

        List<ModrinthVersion> versions =
                client.getVersions(projectId);

        /*
         * If Modrinth explicitly specifies a version ID,
         * prefer that version when it is compatible with
         * the current instance.
         */
        String requiredVersionId =
                dependency.getVersionId();

        if (requiredVersionId != null
                && !requiredVersionId.isBlank()) {

            for (ModrinthVersion version : versions) {

                if (!requiredVersionId.equals(
                        version.getId()
                )) {
                    continue;
                }

                if (isCompatible(
                        version,
                        instance.getMinecraftVersion(),
                        normalizeLoader(
                                instance.getLoader()
                        )
                )) {

                    return version;
                }

                return null;
            }
        }

        /*
         * Otherwise select the newest compatible release.
         */
        return findCompatibleVersion(
                versions,
                instance.getMinecraftVersion(),
                normalizeLoader(
                        instance.getLoader()
                )
        );
    }
}