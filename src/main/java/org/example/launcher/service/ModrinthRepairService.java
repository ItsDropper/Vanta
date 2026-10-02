package org.example.launcher.service;

import org.example.launcher.instance.*;
import org.example.launcher.modrinth.*;
import org.example.launcher.model.Instance;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class ModrinthRepairService {
    private final ModrinthClient client;
    private final InstalledModScanner installedModScanner;
    private final ModrinthCatalogService catalog;
    private final ModrinthDependencyResolver dependencies;
    private final ModrinthInstallService installer;

    public ModrinthRepairService(ModrinthClient client, InstalledModScanner installedModScanner,
                                 ModrinthCatalogService catalog, ModrinthDependencyResolver dependencies,
                                 ModrinthInstallService installer) {
        this.client = client;
        this.installedModScanner = installedModScanner;
        this.catalog = catalog;
        this.dependencies = dependencies;
        this.installer = installer;
    }

public Path repairModDependency(
            Instance instance,
            String projectId,
            List<String> requiredVersions
    ) throws IOException, InterruptedException {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException(
                    "Modrinth project ID cannot be empty."
            );
        }

        dependencies.clearCaches();

        /*
         * Build a narrow root set.
         *
         * Keep:
         * 1. The project being repaired.
         * 2. Installed mods that directly depend on it.
         *
         * Transitive dependencies are handled by dependencies.resolveModGraph().
         */
        List<InstalledModRecord> installedMods =
                InstalledModManager.load(instance);

        List<ModrinthProject> roots =
                new ArrayList<>();

        ModrinthProject repairedProject =
                client.getProject(projectId);

        if (repairedProject == null) {
            throw new IOException(
                    "Could not find Modrinth project: "
                            + projectId
            );
        }

        roots.add(repairedProject);

        /*
         * Find installed mods whose Modrinth version directly
         * depends on the project being repaired.
         */
        for (InstalledModRecord record : installedMods) {

            if (record == null
                    || record.getProjectId() == null
                    || record.getProjectId().isBlank()
                    || record.getVersionId() == null
                    || record.getVersionId().isBlank()) {
                continue;
            }

            /*
             * The repaired project is already a root.
             */
            if (projectId.equals(
                    record.getProjectId()
            )) {
                continue;
            }

            ModrinthVersion installedVersion = null;

            List<ModrinthVersion> installedVersions =
                    client.getVersions(
                            record.getProjectId()
                    );

            for (ModrinthVersion version : installedVersions) {

                if (version == null
                        || version.getId() == null) {
                    continue;
                }

                if (record.getVersionId().equals(
                        version.getId()
                )) {
                    installedVersion = version;
                    break;
                }
            }

            if (installedVersion == null) {
                continue;
            }

            if (installedVersion == null
                    || installedVersion.getDependencies() == null) {
                continue;
            }

            boolean dependsOnRepairedProject = false;

            for (ModrinthDependency dependency :
                    installedVersion.getDependencies()) {

                if (dependency == null
                        || !dependency.isRequired()) {
                    continue;
                }

                if (projectId.equals(
                        dependency.getProjectId()
                )) {
                    dependsOnRepairedProject = true;
                    break;
                }
            }

            if (!dependsOnRepairedProject) {
                continue;
            }

            ModrinthProject dependentProject =
                    client.getProject(
                            record.getProjectId()
                    );

            if (dependentProject == null) {
                continue;
            }

            roots.add(dependentProject);

            System.out.println(
                    "[Vanta Repair] Keeping dependent root "
                            + record.getProjectId()
                            + " -> "
                            + record.getModId()
            );
        }

        /*
         * First attempt:
         *
         * Keep the repaired project and all installed mods that
         * directly depend on it.
         */
        List<ModrinthService.ResolvedMod> resolved = null;

        try {

            resolved =
                    dependencies.resolveModGraph(
                            instance,
                            roots,
                            projectId,
                            requiredVersions
                    );

        } catch (IOException fullGraphFailure) {

            System.out.println(
                    "[Vanta] Narrow repair graph could not be resolved."
            );

            /*
             * The narrow graph is impossible.
             *
             * Now test whether exactly one dependent root needs to be
             * removed from the root set.
             *
             * Important:
             * Removing a root does NOT necessarily remove the mod.
             * If another mod depends on it, the dependency resolver will
             * add it back and choose a compatible version.
             */
            List<ModrinthProject> successfulRoots = null;
            List<ModrinthService.ResolvedMod> successfulResolution = null;
            String removedProjectId = null;

            for (ModrinthProject candidateToRemove : roots) {

                if (candidateToRemove == null
                        || candidateToRemove.getProjectId() == null
                        || candidateToRemove.getProjectId().isBlank()) {
                    continue;
                }

                String candidateProjectId =
                        candidateToRemove.getProjectId();

                /*
                 * Never remove the project we were explicitly asked to
                 * repair.
                 */
                if (projectId.equals(candidateProjectId)) {
                    continue;
                }

                List<ModrinthProject> reducedRoots =
                        new ArrayList<>();

                for (ModrinthProject root : roots) {

                    if (root == null
                            || root.getProjectId() == null) {
                        continue;
                    }

                    if (candidateProjectId.equals(
                            root.getProjectId()
                    )) {
                        continue;
                    }

                    reducedRoots.add(root);
                }

                System.out.println(
                        "[Vanta] Testing repair without root "
                                + candidateProjectId
                );

                /*
                 * Clear metadata caches between attempts so a failed
                 * branch cannot influence a later repair attempt.
                 */
                dependencies.clearCaches();

                try {

                    List<ModrinthService.ResolvedMod> candidateResolution =
                            dependencies.resolveModGraph(
                                    instance,
                                    reducedRoots,
                                    projectId,
                                    requiredVersions
                            );

                    /*
                     * We found one possible repair.
                     */
                    if (successfulResolution == null) {

                        successfulRoots =
                                reducedRoots;

                        successfulResolution =
                                candidateResolution;

                        removedProjectId =
                                candidateProjectId;

                    } else {

                        /*
                         * More than one root can be removed to make the
                         * graph valid. That is ambiguous, so do not
                         * silently choose one.
                         */
                        throw new IOException(
                                "Vanta found multiple possible "
                                        + "repair resolutions. "
                                        + "Manual intervention is required."
                        );
                    }

                } catch (IOException ignored) {

                    /*
                     * This root did not solve the conflict.
                     * Try the next root.
                     */
                }
            }

            if (successfulResolution == null) {

                throw new IOException(
                        "Vanta could not safely repair "
                                + projectId
                                + ": the dependency graph is "
                                + "unsatisfiable and no single installed "
                                + "dependent root can be removed to "
                                + "resolve it.",
                        fullGraphFailure
                );
            }

            resolved =
                    successfulResolution;

            System.out.println(
                    "[Vanta] Repair requires removing root "
                            + removedProjectId
            );
        }

        if (resolved == null || resolved.isEmpty()) {

            throw new IOException(
                    "Vanta could not safely repair "
                            + projectId
                            + ": the dependency graph is empty."
            );
        }

        /*
         * Make sure the requested project actually exists in the
         * resulting graph.
         */
        ModrinthService.ResolvedMod repaired = null;

        for (ModrinthService.ResolvedMod mod : resolved) {

            if (mod == null) {
                continue;
            }

            if (projectId.equals(
                    mod.projectId()
            )) {

                repaired = mod;
                break;
            }
        }

        if (repaired == null) {

            throw new IOException(
                    "Vanta could not safely repair "
                            + projectId
                            + ": the resolver did not produce "
                            + "a version for the repaired project."
            );
        }

        System.out.println(
                "[Vanta] Repair resolved "
                        + projectId
                        + " -> "
                        + repaired.version().getVersionNumber()
        );

        /*
         * Determine which currently installed mods are no longer present
         * in the final resolved graph.
         */
        Set<String> resolvedProjectIds =
                new HashSet<>();

        for (ModrinthService.ResolvedMod mod : resolved) {

            if (mod == null
                    || mod.projectId() == null) {
                continue;
            }

            resolvedProjectIds.add(
                    mod.projectId()
            );
        }

        /*
         * Remove installed mods that are not part of the final graph.
         *
         * This is done only after the entire graph has successfully
         * resolved, so a failed repair never partially modifies the
         * instance.
         */
        for (InstalledModRecord record :
                installedMods) {

            if (record == null
                    || record.getProjectId() == null
                    || record.getProjectId().isBlank()) {
                continue;
            }

            String installedProjectId =
                    record.getProjectId();

            if (projectId.equals(installedProjectId)) {
                continue;
            }

            if (!resolvedProjectIds.contains(
                    installedProjectId
            )) {

                System.out.println(
                        "[Vanta] Removing incompatible mod "
                                + installedProjectId
                );

                removeInstalledMod(
                        instance,
                        installedProjectId
                );
            }
        }

        /*
         * Remove installed versions that are being replaced by the
         * resolved graph.
         */
        for (ModrinthService.ResolvedMod mod : resolved) {

            if (mod == null
                    || mod.projectId() == null
                    || mod.version() == null) {
                continue;
            }

            InstalledModRecord existing =
                    InstalledModManager.findByProjectId(
                            instance,
                            mod.projectId()
                    );

            if (existing == null) {
                continue;
            }

            if (existing.getVersionId() != null
                    && existing.getVersionId().equals(
                    mod.version().getId()
            )) {

                continue;
            }

            removeInstalledMod(
                    instance,
                    mod.projectId()
            );
        }

        /*
         * Remove every existing version of the mod being repaired.
         *
         * This is necessary because the instance may contain the old
         * incompatible JAR even if the registry points at the new version.
         */
        removeInstalledMod(
                instance,
                projectId
        );

        /*
         * Install the complete resolved graph.
         */
        List<Path> installed =
                installer.installResolvedGraph(
                        instance,
                        resolved
                );

        /*
         * Locate the repaired mod through the installed-mod registry.
         */
        InstalledModRecord repairedRecord =
                InstalledModManager.findByProjectId(
                        instance,
                        projectId
                );

        if (repairedRecord != null
                && repairedRecord.getFilename() != null) {

            Path repairedFile =
                    instance.getDirectory()
                            .resolve("mods")
                            .resolve(
                                    repairedRecord.getFilename()
                            );

            if (Files.isRegularFile(repairedFile)) {
                return repairedFile;
            }
        }

        /*
         * Fallback: inspect the files installed by this repair.
         */
        InstalledMod repairedMetadata =
                dependencies.readFabricMetadata(
                        repaired.version()
                );

        if (repairedMetadata != null
                && repairedMetadata.getModId() != null) {

            for (Path path : installed) {

                if (!Files.isRegularFile(path)) {
                    continue;
                }

                InstalledMod installedMod =
                        installedModScanner.scanFile(
                                path
                        );

                if (installedMod == null
                        || installedMod.getModId() == null) {
                    continue;
                }

                if (repairedMetadata.getModId().equals(
                        installedMod.getModId()
                )) {

                    return path;
                }
            }
        }

        throw new IOException(
                "Vanta repaired "
                        + projectId
                        + " successfully, but could not locate "
                        + "the repaired mod after installation."
        );
    }

private LinkedHashMap<String, ModrinthVersion> loadInstalledGraph(
            Instance instance,
            String projectBeingRepaired
    ) throws IOException, InterruptedException {

        LinkedHashMap<String, ModrinthVersion> graph =
                new LinkedHashMap<>();

        List<InstalledModRecord> records =
                InstalledModManager.load(
                        instance
                );

        if (records == null) {
            return graph;
        }

        for (InstalledModRecord record :
                records) {

            if (record == null) {
                continue;
            }

            String projectId =
                    record.getProjectId();

            String versionId =
                    record.getVersionId();

            if (projectId == null
                    || projectId.isBlank()
                    || versionId == null
                    || versionId.isBlank()) {

                continue;
            }

            if (projectId.equals(
                    projectBeingRepaired
            )) {
                continue;
            }

            List<ModrinthVersion> versions =
                    client.getVersions(
                            projectId
                    );

            if (versions == null) {
                continue;
            }

            for (ModrinthVersion version :
                    versions) {

                if (!versionId.equals(
                        version.getId()
                )) {
                    continue;
                }

                if (!catalog.isCompatible(
                        version,
                        instance.getMinecraftVersion(),
                        dependencies.normalizeLoader(
                                instance.getLoader()
                        )
                )) {
                    continue;
                }

                graph.put(
                        projectId,
                        version
                );

                break;
            }
        }

        return graph;
    }

private void removeInstalledMod(
            Instance instance,
            String projectId
    ) throws IOException, InterruptedException {

        Path modsDirectory =
                instance.getDirectory()
                        .resolve("mods");

        if (!Files.isDirectory(modsDirectory)) {
            InstalledModManager.removeByProjectId(
                    instance,
                    projectId
            );
            return;
        }

        /*
         * First get the Fabric mod ID from the registry.
         */
        String modId = null;

        List<InstalledModRecord> records =
                InstalledModManager.load(instance);

        if (records != null) {

            for (InstalledModRecord record : records) {

                if (record == null
                        || !projectId.equals(
                        record.getProjectId()
                )) {
                    continue;
                }

                if (record.getModId() != null
                        && !record.getModId().isBlank()) {

                    modId = record.getModId();
                    break;
                }
            }
        }

        /*
         * Delete every physical JAR belonging to that Fabric mod ID.
         */
        if (modId != null
                && !modId.isBlank()) {

            List<InstalledMod> installed =
                    installedModScanner.scan(instance);

            if (installed != null) {

                for (InstalledMod mod : installed) {

                    if (mod == null
                            || !modId.equals(
                            mod.getModId()
                    )
                            || mod.getFilename() == null) {

                        continue;
                    }

                    Path file =
                            modsDirectory.resolve(
                                    catalog.sanitizeFilename(
                                            mod.getFilename()
                                    )
                            );

                    if (Files.isRegularFile(file)) {

                        System.out.println(
                                "[Vanta Repair] Deleting old mod: "
                                        + file.getFileName()
                        );

                        Files.deleteIfExists(file);
                    }
                }
            }
        }

        /*
         * Also delete any files tracked by the Modrinth project.
         */
        if (records != null) {

            for (InstalledModRecord record : records) {

                if (record == null
                        || !projectId.equals(
                        record.getProjectId()
                )
                        || record.getFilename() == null) {

                    continue;
                }

                Path file =
                        modsDirectory.resolve(
                                catalog.sanitizeFilename(
                                        record.getFilename()
                                )
                        );

                if (Files.isRegularFile(file)) {

                    System.out.println(
                            "[Vanta Repair] Deleting tracked mod: "
                                    + file.getFileName()
                    );

                    Files.deleteIfExists(file);
                }
            }
        }

        InstalledModManager.removeByProjectId(
                instance,
                projectId
        );
    }
}
