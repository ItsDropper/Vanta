package dev.itsdropper.vanta.launcher.service;

import dev.itsdropper.vanta.launcher.instance.*;
import dev.itsdropper.vanta.launcher.modrinth.*;
import dev.itsdropper.vanta.launcher.model.Instance;
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

        return ModrinthGraphInstallTransaction.execute(instance, () ->
                installModProject(instance, project.getProjectId(), new HashSet<>()));
    }

public Path installModVersion(
            Instance instance,
            String projectId,
            ModrinthVersion version
    ) throws IOException, InterruptedException {

        if (instance == null || projectId == null || projectId.isBlank() || version == null) {
            throw new IllegalArgumentException("Invalid mod version installation request.");
        }

        return ModrinthGraphInstallTransaction.execute(instance, () ->
                installModVersion(instance, projectId, version, new HashSet<>()));
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

        List<ModrinthService.ResolvedMod> validated = validateResolvedGraph(resolvedMods);
        if (validated.isEmpty()) {
            return List.of();
        }

        return ModrinthGraphInstallTransaction.execute(instance, () -> {
            List<Path> installed = new ArrayList<>(validated.size());
            for (ModrinthService.ResolvedMod resolved : validated) {
                installed.add(installResolvedVersion(
                        instance, resolved.projectId(), resolved.version()));
            }
            return List.copyOf(installed);
        });
    }


    private static List<ModrinthService.ResolvedMod> validateResolvedGraph(
            List<ModrinthService.ResolvedMod> resolvedMods
    ) throws IOException {
        Map<String, String> selectedVersions = new LinkedHashMap<>();
        List<ModrinthService.ResolvedMod> validated = new ArrayList<>(resolvedMods.size());

        for (ModrinthService.ResolvedMod resolved : resolvedMods) {
            if (resolved == null || resolved.projectId() == null || resolved.projectId().isBlank()
                    || resolved.version() == null || resolved.version().getId() == null
                    || resolved.version().getId().isBlank()) {
                throw new IOException("Resolved mod graph contains an incomplete entry.");
            }

            String previousVersion = selectedVersions.putIfAbsent(
                    resolved.projectId(), resolved.version().getId());
            if (previousVersion != null) {
                if (!previousVersion.equals(resolved.version().getId())) {
                    throw new IOException("Resolved mod graph selects conflicting versions for project "
                            + resolved.projectId() + ".");
                }
                continue;
            }
            validated.add(resolved);
        }
        return List.copyOf(validated);
    }

private void removeInstalledMod(
            Instance instance,
            String projectId
    ) throws IOException {

        if (instance == null || projectId == null || projectId.isBlank()) {
            return;
        }

        InstalledModRecord existing =
                InstalledModManager.findByProjectId(
                        instance,
                        projectId
                );

        if (existing != null && existing.getFilename() != null) {
            Path modsDirectory =
                    instance.getDirectory()
                            .resolve("mods");

            Path installedFile =
                    modsDirectory.resolve(existing.getFilename());

            Files.deleteIfExists(installedFile);
        }

        InstalledModManager.removeByProjectId(
                instance,
                projectId
        );
    }

private Path installResolvedVersion(
            Instance instance,
            String projectId,
            ModrinthVersion version
    ) throws IOException, InterruptedException {

        if (projectId == null || projectId.isBlank() || version == null
                || version.getId() == null || version.getId().isBlank()) {
            throw new IOException("Resolved Modrinth installation has an invalid project or version identity.");
        }
        if (version.getProjectId() != null && !version.getProjectId().isBlank()
                && !projectId.equals(version.getProjectId())) {
            throw new IOException("Resolved Modrinth version belongs to a different project: " + projectId);
        }

        ModrinthFile file = catalog.findPrimaryFile(version);
        if (file == null || file.getUrl() == null || file.getUrl().isBlank()) {
            throw new IOException("Modrinth version has no downloadable file: " + projectId);
        }

        Path modsDirectory = instance.getDirectory().resolve("mods").toAbsolutePath().normalize();
        Files.createDirectories(modsDirectory);

        InstalledModRecord existing = InstalledModManager.findByProjectId(instance, projectId);
        if (existing != null && version.getId().equals(existing.getVersionId())
                && existing.getFilename() != null) {
            Path existingFile = resolveTrackedModPath(modsDirectory, existing.getFilename());
            if (Files.isRegularFile(existingFile)) return existingFile;
        }

        String filename = catalog.sanitizeFilename(file.getFilename());
        if (filename == null || filename.isBlank()) {
            throw new IOException("Modrinth returned an invalid mod filename.");
        }
        Path installedFile = modsDirectory.resolve(filename).normalize();
        if (!installedFile.startsWith(modsDirectory)) {
            throw new IOException("Mod filename escapes the instance mods directory.");
        }

        Path previousFile = existing == null || existing.getFilename() == null
                ? null : resolveTrackedModPath(modsDirectory, existing.getFilename());
        if (Files.exists(installedFile) && (previousFile == null || !installedFile.equals(previousFile))) {
            throw new IOException("A different file already exists at " + installedFile.getFileName()
                    + "; refusing to overwrite an untracked mod.");
        }

        Path stagedFile = modsDirectory.resolve(".vanta-stage-" + UUID.randomUUID() + ".jar");
        Path rollbackFile = previousFile != null && Files.isRegularFile(previousFile)
                ? modsDirectory.resolve(".vanta-rollback-" + UUID.randomUUID() + ".jar") : null;
        boolean previousMoved = false;
        boolean newFilePublished = false;

        // Persist every destination and temporary path before the first filesystem
        // mutation so startup recovery can clean even a crash between publication
        // and the installed-mod registry update.
        ModrinthGraphInstallTransaction.registerManagedPath(instance, installedFile);
        ModrinthGraphInstallTransaction.registerManagedPath(instance, stagedFile);
        if (rollbackFile != null) {
            ModrinthGraphInstallTransaction.registerManagedPath(instance, rollbackFile);
        }

        try {
            // Download to a unique staging file; never touch the installed version during transfer.
            DownloadUtil.downloadFile(file.getUrl(), stagedFile);
            if (!Files.isRegularFile(stagedFile) || Files.size(stagedFile) == 0) {
                throw new IOException("Downloaded mod file is empty or missing.");
            }
            if (file.getSize() > 0 && Files.size(stagedFile) != file.getSize()) {
                throw new IOException("Downloaded mod size does not match Modrinth metadata.");
            }

            InstalledMod scanned = installedModScanner.scanFile(stagedFile);

            if (rollbackFile != null) {
                moveFile(previousFile, rollbackFile);
                previousMoved = true;
            }

            moveFile(stagedFile, installedFile);
            newFilePublished = true;

            InstalledModManager.add(instance, new InstalledModRecord(
                    projectId,
                    version.getId(),
                    scanned != null ? scanned.getModId() : null,
                    scanned != null ? scanned.getVersion() : version.getVersionNumber(),
                    installedFile.getFileName().toString()
            ));

            // Registry now points to the new file; only now is the previous file disposable.
            if (rollbackFile != null) {
                try { Files.deleteIfExists(rollbackFile); }
                catch (IOException cleanupFailure) { System.err.println("Could not clean up mod rollback file: " + cleanupFailure.getMessage()); }
            }
            if (previousFile != null && !previousFile.equals(installedFile)) {
                try { Files.deleteIfExists(previousFile); }
                catch (IOException cleanupFailure) { System.err.println("Could not clean up previous mod file: " + cleanupFailure.getMessage()); }
            }
            return installedFile;
        } catch (Exception failure) {
            if (newFilePublished) {
                try { Files.deleteIfExists(installedFile); }
                catch (IOException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            }
            if (previousMoved && rollbackFile != null && Files.exists(rollbackFile)) {
                try { moveFile(rollbackFile, previousFile); }
                catch (IOException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            }
            if (failure instanceof InterruptedException interrupted) throw interrupted;
            if (failure instanceof IOException io) throw io;
            throw new IOException("Could not install mod transactionally: " + projectId, failure);
        } finally {
            try { Files.deleteIfExists(stagedFile); }
            catch (IOException cleanupFailure) {
                System.err.println("Could not remove staged mod file: " + cleanupFailure.getMessage());
            }
            if (rollbackFile != null && Files.exists(rollbackFile) && !previousMoved) {
                try { Files.deleteIfExists(rollbackFile); }
                catch (IOException cleanupFailure) {
                    System.err.println("Could not remove unused mod rollback file: " + cleanupFailure.getMessage());
                }
            }
        }
    }

    private static Path resolveTrackedModPath(Path modsDirectory, String filename) throws IOException {
        Path resolved = modsDirectory.resolve(filename).normalize();
        if (!resolved.startsWith(modsDirectory)) {
            throw new IOException("Installed-mod registry contains an unsafe filename: " + filename);
        }
        return resolved;
    }

    private static void moveFile(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(source, destination);
        }
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

            List<ModrinthDependency> modDependencies =
                    version.getDependencies();

            if (modDependencies != null) {

                for (ModrinthDependency dependency :
                        modDependencies) {

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

            List<ModrinthDependency> modDependencies =
                    compatibleVersion.getDependencies();

            if (modDependencies != null) {

                for (ModrinthDependency dependency :
                        modDependencies) {

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

public Path installDataPack(
            Instance instance,
            Path worldDirectory,
            ModrinthProject project
    ) throws IOException, InterruptedException {
        if (instance == null || instance.getDirectory() == null) {
            throw new IllegalArgumentException("Instance cannot be null.");
        }
        if (worldDirectory == null) {
            throw new IllegalArgumentException("A Minecraft world must be selected.");
        }
        Path instanceRoot = instance.getDirectory().toAbsolutePath().normalize();
        Path world = worldDirectory.toAbsolutePath().normalize();
        Path saves = instanceRoot.resolve("saves").normalize();
        if (!world.startsWith(saves) || !Files.isRegularFile(world.resolve("level.dat"))) {
            throw new IOException("Selected directory is not a valid world belonging to this instance.");
        }
        if (project == null || !ModrinthContentType.DATA_PACK.getApiValue().equals(project.getProjectType())) {
            throw new IOException("Project is not a data pack.");
        }
        List<ModrinthVersion> versions = client.getVersions(project.getProjectId());
        ModrinthVersion compatible = catalog.findContentVersion(versions, instance.getMinecraftVersion());
        if (compatible == null) {
            throw new IOException("No compatible data pack version found for Minecraft "
                    + instance.getMinecraftVersion());
        }
        ModrinthFile file = catalog.findPrimaryFile(compatible);
        if (file == null || file.getUrl() == null || file.getUrl().isBlank()) {
            throw new IOException("Modrinth data pack version has no downloadable file.");
        }
        return installFile(file, world.resolve("datapacks"));
    }

@Deprecated
public Path installDataPack(
            Instance instance,
            ModrinthProject project
    ) throws IOException {
        throw new IOException("Data packs are world-specific. Select a Minecraft world before installing.");
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
