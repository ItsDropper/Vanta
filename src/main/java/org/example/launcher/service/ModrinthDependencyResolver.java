package org.example.launcher.service;

import org.example.launcher.instance.*;
import org.example.launcher.modrinth.*;
import org.example.launcher.model.Instance;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class ModrinthDependencyResolver {
    private final ModrinthClient client;
    private final InstalledModScanner installedModScanner;
    private final Map<String, InstalledMod> fabricMetadataCache = new HashMap<>();
    private final Map<String, String> fabricModIdProjectCache = new HashMap<>();
    private final Map<String, Map<String, String>> fabricApiModuleCache = new HashMap<>();
    private final Map<String, List<ModrinthVersion>> versionCache =
            new java.util.concurrent.ConcurrentHashMap<>();
    private boolean fastResolution = true;
    private static final int FAST_CANDIDATE_LIMIT = 6;

    public ModrinthDependencyResolver(ModrinthClient client, InstalledModScanner installedModScanner) {
        this.client = client;
        this.installedModScanner = installedModScanner;
    }

    public void clearCaches() {
        fabricMetadataCache.clear();
        fabricModIdProjectCache.clear();
        fabricApiModuleCache.clear();
        versionCache.clear();
    }

    public String normalizeLoader(String loader) {
        if (loader == null) return "";
        return loader.trim().toLowerCase();
    }

public List<ModrinthService.ResolvedMod> resolveModGraph(
            Instance instance,
            List<ModrinthProject> rootProjects,
            String requiredProjectId,
            List<String> requiredVersions
    ) throws IOException, InterruptedException {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        if (rootProjects == null || rootProjects.isEmpty()) {
            return List.of();
        }

        fabricMetadataCache.clear();
        versionCache.clear();
        fastResolution = true;
        fabricModIdProjectCache.clear();
        fabricApiModuleCache.clear();

        LinkedHashSet<String> roots =
                new LinkedHashSet<>();

        for (ModrinthProject project : rootProjects) {
            if (project == null
                    || project.getProjectId() == null
                    || project.getProjectId().isBlank()) {
                continue;
            }

            roots.add(project.getProjectId());
        }

        if (roots.isEmpty()) {
            return List.of();
        }

        /*
         * Fetch all root version lists concurrently. This removes the
         * sequential Modrinth network wait from FPS instance creation.
         */
        prefetchCandidates(instance, roots);

        if (requiredProjectId != null
                && !requiredProjectId.isBlank()
                && roots.remove(requiredProjectId)) {

            LinkedHashSet<String> orderedRoots =
                    new LinkedHashSet<>();

            orderedRoots.add(requiredProjectId);
            orderedRoots.addAll(roots);

            roots = orderedRoots;
        }

        LinkedHashMap<String, ModrinthVersion> resolved =
                new LinkedHashMap<>();

        List<String> rootList =
                new ArrayList<>(roots);

        boolean success =
                resolveRoots(
                        instance,
                        rootList,
                        requiredProjectId,
                        requiredVersions,
                        resolved,
                        new HashSet<>()
                );

        if (!success && fastResolution) {
            System.out.println(
                    "[Vanta DEBUG] Fast dependency resolution failed; "
                            + "retrying with the full candidate set."
            );
            fastResolution = false;
            resolved.clear();
            success =
                    resolveRoots(
                            instance,
                            rootList,
                            requiredProjectId,
                            requiredVersions,
                            resolved,
                            new HashSet<>()
                    );
        }

        if (!success) {
            throw new IOException(
                    "Could not resolve compatible dependency graph. "
                            + "Check the Vanta debug log for the "
                            + "specific dependency conflict."
            );
        }

        List<ModrinthService.ResolvedMod> result =
                new ArrayList<>();

        for (Map.Entry<String, ModrinthVersion> entry :
                resolved.entrySet()) {

            result.add(
                    new ModrinthService.ResolvedMod(
                            entry.getKey(),
                            entry.getValue()
                    )
            );
        }

        return result;
    }

private void prefetchCandidates(
            Instance instance,
            Collection<String> projectIds
    ) throws IOException, InterruptedException {

        int projectCount = projectIds == null ? 0 : projectIds.size();
        if (projectCount == 0) {
            return;
        }

        int workers = Math.min(8, Math.max(1, projectCount));
        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newFixedThreadPool(workers);

        try {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();

            for (String projectId : projectIds) {
                if (projectId == null || projectId.isBlank()) {
                    continue;
                }

                futures.add(executor.submit(() -> {
                    try {
                        getCompatibleCandidates(instance, projectId);
                    } catch (IOException | InterruptedException e) {
                        throw new java.util.concurrent.CompletionException(e);
                    }
                }));
            }

            for (java.util.concurrent.Future<?> future : futures) {
                try {
                    future.get();
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof java.util.concurrent.CompletionException
                            && cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    if (cause instanceof InterruptedException interrupted) {
                        throw interrupted;
                    }
                    if (cause instanceof IOException io) {
                        throw io;
                    }
                    throw new IOException(
                            "Failed to prefetch Modrinth candidates.",
                            cause
                    );
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

private boolean resolveRoots(
            Instance instance,
            List<String> roots,
            String requiredProjectId,
            List<String> requiredVersions,
            Map<String, ModrinthVersion> resolved,
            Set<String> resolving
    ) throws IOException, InterruptedException {

        if (resolved.keySet().containsAll(roots)) {
            return true;
        }

        String selectedRoot = null;
        List<ModrinthVersion> selectedCandidates = null;

        for (String rootProjectId : roots) {

            /*
             * A root may already have been selected while resolving
             * another root's dependency tree. That does NOT mean the
             * root's own required constraint can be skipped.
             *
             * This matters during repair: for example, Iris 1.10.x
             * can pull Sodium 0.8.x while the repair request explicitly
             * requires Sodium 0.9.1 or later. The required Sodium root
             * must reject that branch and let root backtracking try a
             * different Iris version.
             */
            if (resolved.containsKey(rootProjectId)) {

                if (requiredProjectId != null
                        && requiredProjectId.equals(rootProjectId)
                        && !matchesRequiredVersions(
                        resolved.get(rootProjectId),
                        requiredVersions
                )) {

                    System.out.println(
                            "[Vanta DEBUG] Existing required root "
                                    + rootProjectId
                                    + " -> "
                                    + resolved.get(rootProjectId).getVersionNumber()
                                    + " does not satisfy the repair constraint."
                    );

                    return false;
                }

                continue;
            }

            List<ModrinthVersion> candidates =
                    getCompatibleCandidates(instance, rootProjectId);

            List<ModrinthVersion> compatibleCandidates =
                    new ArrayList<>();

            String lastConflictReason = null;

            for (ModrinthVersion candidate : candidates) {
                if (requiredProjectId != null
                        && requiredProjectId.equals(rootProjectId)) {

                    if (!matchesRequiredVersions(
                            candidate,
                            requiredVersions
                    )) {
                        lastConflictReason =
                                "does not match the required version";

                        continue;
                    }
                }

                CompatibilityResult compatibility =
                        isCompatibleWithResolvedGraph(
                                instance,
                                rootProjectId,
                                candidate,
                                resolved
                        );

                if (!compatibility.compatible()) {
                    lastConflictReason = compatibility.reason();
                    continue;
                }

                compatibleCandidates.add(candidate);
            }

            if (compatibleCandidates.isEmpty()) {
                System.out.println(
                        "[Vanta DEBUG] Root "
                                + rootProjectId
                                + " has no compatible candidates "
                                + "with the current graph."
                );

                if (lastConflictReason != null) {
                    System.out.println(
                            "[Vanta DEBUG] Last conflict: "
                                    + lastConflictReason
                    );
                }

                return false;
            }

            if (selectedCandidates == null
                    || compatibleCandidates.size()
                    < selectedCandidates.size()) {

                selectedRoot = rootProjectId;
                selectedCandidates = compatibleCandidates;
            }
        }

        if (selectedRoot == null || selectedCandidates == null) {
            return true;
        }

        for (ModrinthVersion candidate : selectedCandidates) {
            System.out.println(
                    "[Vanta DEBUG] Trying root "
                            + selectedRoot
                            + " -> "
                            + candidate.getVersionNumber()
            );

            Map<String, ModrinthVersion> branch =
                    new LinkedHashMap<>(resolved);

            branch.put(selectedRoot, candidate);

            if (!resolveDependencies(
                    instance,
                    candidate,
                    branch,
                    resolving,
                    requiredProjectId,
                    requiredVersions
            )) {
                continue;
            }

            if (!resolveRoots(
                    instance,
                    roots,
                    requiredProjectId,
                    requiredVersions,
                    branch,
                    resolving
            )) {
                continue;
            }

            if (!isGraphConsistent(instance, branch)) {
                System.out.println(
                        "[Vanta DEBUG] Rejected complete graph after root "
                                + selectedRoot
                                + " "
                                + candidate.getVersionNumber()
                );

                continue;
            }

            resolved.clear();
            resolved.putAll(branch);

            return true;
        }

        return false;
    }

private boolean resolveProject(
            Instance instance,
            String projectId,
            ModrinthDependency requestedBy,
            List<String> requiredVersions,
            Map<String, ModrinthVersion> resolved,
            Set<String> resolving,
            String requiredProjectId,
            List<String> repairRequiredVersions
    ) throws IOException, InterruptedException {

        if (projectId == null || projectId.isBlank()) {
            return false;
        }

        ModrinthVersion selected = resolved.get(projectId);

        if (selected != null) {
            return satisfiesDependency(
                    instance,
                    selected,
                    requestedBy
            );
        }

        if (resolving.contains(projectId)) {
            return true;
        }

        List<ModrinthVersion> candidates =
                getCompatibleCandidates(
                        instance,
                        projectId
                );

        if (candidates.isEmpty()) {
            return false;
        }

        resolving.add(projectId);

        try {
            for (ModrinthVersion candidate : candidates) {

                if (requiredVersions != null
                        && !matchesRequiredVersions(
                        candidate,
                        requiredVersions
                )) {
                    continue;
                }

                if (requiredProjectId != null
                        && requiredProjectId.equals(projectId)
                        && !matchesRequiredVersions(
                        candidate,
                        repairRequiredVersions
                )) {
                    continue;
                }

                if (!satisfiesDependency(
                        instance,
                        candidate,
                        requestedBy
                )) {
                    continue;
                }

                CompatibilityResult compatibility =
                        isCompatibleWithResolvedGraph(
                                instance,
                                projectId,
                                candidate,
                                resolved
                        );

                if (!compatibility.compatible()) {
                    System.out.println(
                            "[Vanta DEBUG] Rejected "
                                    + projectId
                                    + " "
                                    + candidate.getVersionNumber()
                                    + ": "
                                    + compatibility.reason()
                    );

                    continue;
                }

                Map<String, ModrinthVersion> branch =
                        new LinkedHashMap<>(resolved);

                branch.put(projectId, candidate);

                if (!resolveDependencies(
                        instance,
                        candidate,
                        branch,
                        resolving,
                        requiredProjectId,
                        repairRequiredVersions
                )) {
                    continue;
                }

                if (!isGraphConsistent(
                        instance,
                        branch
                )) {
                    continue;
                }

                resolved.clear();
                resolved.putAll(branch);

                return true;
            }

            return false;

        } finally {
            resolving.remove(projectId);
        }
    }

private boolean resolveDependencies(
            Instance instance,
            ModrinthVersion version,
            Map<String, ModrinthVersion> resolved,
            Set<String> resolving,
            String requiredProjectId,
            List<String> requiredVersions
    ) throws IOException, InterruptedException {

        if (version == null) {
            return true;
        }

        /*
         * ---------------------------------------------------------
         * --------------------------------------------------------
         * FIRST: Resolve dependencies declared by Modrinth.
         * ---------------------------------------------------------
         */

        List<ModrinthDependency> dependencies =
                version.getDependencies();

        if (dependencies != null
                && !dependencies.isEmpty()) {

            System.out.println(
                    "[Vanta DEBUG] Resolving Modrinth dependencies for "
                            + version.getVersionNumber()
            );

            for (ModrinthDependency dependency :
                    dependencies) {

                if (dependency == null) {
                    continue;
                }

                if (dependency.isOptional()) {
                    continue;
                }

                if (dependency.isIncompatible()) {
                    continue;
                }

                String dependencyProjectId =
                        dependency.getProjectId();

                if (dependencyProjectId == null
                        || dependencyProjectId.isBlank()) {

                    continue;
                }

                System.out.println(
                        "[Vanta DEBUG]   Modrinth dependency: "
                                + dependencyProjectId
                                + " versionId="
                                + dependency.getVersionId()
                );

                /*
                 * A repair constraint is global to the entire graph.
                 * Reject a transitive selection of the wrong version
                 * immediately so the parent candidate can backtrack.
                 */
                if (requiredProjectId != null
                        && requiredProjectId.equals(dependencyProjectId)
                        && resolved.containsKey(dependencyProjectId)
                        && !matchesRequiredVersions(
                        resolved.get(dependencyProjectId),
                        requiredVersions
                )) {
                    return false;
                }

                /*
                 * If an existing selected dependency doesn't satisfy
                 * this requirement, this branch is invalid.
                 */
                if (resolved.containsKey(
                        dependencyProjectId
                )) {

                    ModrinthVersion selected =
                            resolved.get(
                                    dependencyProjectId
                            );

                    boolean satisfies =
                            satisfiesDependency(
                                    instance,
                                    selected,
                                    dependency
                            );

                    System.out.println(
                            "[Vanta DEBUG]   Existing dependency "
                                    + selected.getVersionNumber()
                                    + " satisfies="
                                    + satisfies
                    );

                    if (!satisfies) {
                        System.out.println(
                                "[Vanta DEBUG]   FAILED existing dependency: "
                                        + dependencyProjectId
                        );

                        return false;
                    }

                    continue;
                }

                boolean success =
                        resolveProject(
                                instance,
                                dependencyProjectId,
                                dependency,
                                null,
                                resolved,
                                resolving,
                                requiredProjectId,
                                requiredVersions
                        );

                if (!success) {

                    System.out.println(
                            "[Vanta DEBUG]   FAILED Modrinth dependency: "
                                    + dependencyProjectId
                    );

                    return false;
                }
            }
        }

        /*
         * ---------------------------------------------------------
         * SECOND: Inspect the actual Fabric JAR.
         *
         * This catches dependencies that the Modrinth project failed
         * to declare in its Modrinth metadata.
         * ---------------------------------------------------------
         */

        InstalledMod fabricMetadata =
                readFabricMetadata(version);

        if (fabricMetadata == null) {
            return true;
        }

        List<DependencyRequirement> fabricDependencies =
                fabricMetadata.getDependencies();

        if (fabricDependencies == null
                || fabricDependencies.isEmpty()) {

            return true;
        }

        System.out.println(
                "[Vanta DEBUG] Inspecting Fabric dependencies for "
                        + fabricMetadata.getModId()
                        + " "
                        + fabricMetadata.getVersion()
        );

        for (DependencyRequirement requirement :
                fabricDependencies) {

            if (requirement == null
                    || requirement.getModId() == null
                    || requirement.getModId().isBlank()) {

                continue;
            }

            String fabricDependencyModId =
                    requirement.getModId();

            String constraint =
                    requirement.getVersionConstraint();

            System.out.println(
                    "[Vanta DEBUG]   Fabric dependency: "
                            + fabricDependencyModId
                            + " ["
                            + constraint
                            + "]"
            );

            /*
             * Some Fabric dependencies are provided by the environment
             * itself rather than by another mod.
             */
            if (isEnvironmentDependencySatisfied(
                    instance,
                    fabricDependencyModId,
                    constraint
            )) {

                continue;
            }

            /*
             * Fabric API contains multiple internal API modules that
             * appear as individual Fabric dependencies.
             */
            if (isProvidedByFabricApi(
                    resolved,
                    fabricDependencyModId,
                    constraint
            )) {

                continue;
            }

            /*
             * See whether this Fabric mod is already represented
             * somewhere in the resolved graph.
             */
            ModrinthVersion resolvedDependency =
                    findResolvedFabricDependency(
                            resolved,
                            fabricDependencyModId
                    );

            if (resolvedDependency != null) {

                InstalledMod dependencyMetadata =
                        readFabricMetadata(
                                resolvedDependency
                        );

                if (dependencyMetadata == null
                        || dependencyMetadata.getVersion() == null) {

                    return false;
                }

                boolean satisfies =
                        matchesFabricConstraint(
                                dependencyMetadata.getVersion(),
                                constraint
                        );

                System.out.println(
                        "[Vanta DEBUG]   Existing Fabric dependency "
                                + fabricDependencyModId
                                + " "
                                + dependencyMetadata.getVersion()
                                + " satisfies="
                                + satisfies
                );

                if (!satisfies) {
                    return false;
                }

                continue;
            }

            /*
             * The dependency exists in fabric.mod.json but is not
             * currently represented in the graph.
             *
             * Find its Modrinth project and resolve it.
             */
            String dependencyProjectId =
                    findModrinthProjectForFabricModId(
                            instance,
                            fabricDependencyModId
                    );

            if (dependencyProjectId == null
                    || dependencyProjectId.isBlank()) {

                System.out.println(
                        "[Vanta DEBUG]   Could not find Modrinth project for Fabric mod "
                                + fabricDependencyModId
                );

                return false;
            }

            boolean success =
                    resolveFabricDependency(
                            instance,
                            dependencyProjectId,
                            fabricDependencyModId,
                            constraint,
                            resolved,
                            resolving
                    );

            if (!success) {

                System.out.println(
                        "[Vanta DEBUG]   FAILED Fabric dependency: "
                                + fabricDependencyModId
                                + " ["
                                + constraint
                                + "]"
                );

                return false;
            }
        }

        return true;
    }

private boolean isEnvironmentDependencySatisfied(
            Instance instance,
            String dependencyModId,
            String constraint
    ) {

        if (instance == null
                || dependencyModId == null
                || dependencyModId.isBlank()) {

            return false;
        }

        /*
         * Fabric Loader is provided by the Minecraft instance.
         */
        if ("fabricloader".equalsIgnoreCase(
                dependencyModId
        )) {

            if (!"fabric".equalsIgnoreCase(
                    instance.getLoader()
            )) {

                return false;
            }

            String loaderVersion =
                    instance.getLoaderVersion();

            if (loaderVersion == null
                    || loaderVersion.isBlank()) {

                return false;
            }

            boolean satisfies =
                    matchesFabricConstraint(
                            loaderVersion,
                            constraint
                    );

            System.out.println(
                    "[Vanta DEBUG]   Environment dependency: "
                            + "fabricloader "
                            + loaderVersion
                            + " ["
                            + constraint
                            + "] = "
                            + satisfies
            );

            return satisfies;
        }

        /*
         * Minecraft is provided by the instance itself.
         */
        if ("minecraft".equalsIgnoreCase(dependencyModId)) {

            String minecraftVersion =
                    instance.getMinecraftVersion();

            if (minecraftVersion == null
                    || minecraftVersion.isBlank()) {

                return false;
            }

            boolean satisfies =
                    matchesFabricConstraint(
                            minecraftVersion,
                            constraint
                    );

            System.out.println(
                    "[Vanta DEBUG]   Environment dependency: "
                            + "minecraft "
                            + minecraftVersion
                            + " ["
                            + constraint
                            + "] = "
                            + satisfies
            );

            return satisfies;
        }

        /*
         * Java is provided by the Java runtime used to launch
         * the Minecraft instance. It is not a Modrinth mod.
         *
         * Fabric dependency constraints use the Java major version,
         * e.g. >=21.
         */
        if ("java".equalsIgnoreCase(
                dependencyModId
        )) {

            int launcherJavaMajorVersion =
                    Runtime.version().feature();

            /*
             * Vanta may run on a different JVM than the Minecraft
             * process. Use the Minecraft version's required Java level
             * as the resolver floor instead of assuming Vanta's JVM is
             * the game's JVM.
             */
            int minecraftRequiredJava =
                    minimumJavaMajorForMinecraft(
                            instance.getMinecraftVersion()
                    );

            int javaMajorVersion =
                    Math.max(
                            launcherJavaMajorVersion,
                            minecraftRequiredJava
                    );

            String javaVersion =
                    Integer.toString(
                            javaMajorVersion
                    );

            boolean satisfies =
                    matchesFabricConstraint(
                            javaVersion,
                            constraint
                    );

            System.out.println(
                    "[Vanta DEBUG]   Environment dependency: "
                            + "java "
                            + javaVersion
                            + " ["
                            + constraint
                            + "] = "
                            + satisfies
            );

            return satisfies;
        }

        return false;
    }

private ModrinthVersion findResolvedFabricDependency(
            Map<String, ModrinthVersion> resolved,
            String fabricModId
    ) throws IOException {

        if (resolved == null
                || fabricModId == null
                || fabricModId.isBlank()) {

            return null;
        }

        for (ModrinthVersion version :
                resolved.values()) {

            if (version == null) {
                continue;
            }

            InstalledMod metadata =
                    readFabricMetadata(
                            version
                    );

            if (metadata == null
                    || metadata.getModId() == null) {

                continue;
            }

            if (fabricModId.equals(
                    metadata.getModId()
            )) {

                return version;
            }
        }

        return null;
    }

private String findModrinthProjectForFabricModId(
            Instance instance,
            String fabricModId
    ) throws IOException, InterruptedException {

        if (fabricModId == null || fabricModId.isBlank()) {
            return null;
        }

        if ("fabric".equalsIgnoreCase(fabricModId)) {
            fabricModIdProjectCache.put(fabricModId, "P7dR8mSH");
            return "P7dR8mSH";
        }

        String cached = fabricModIdProjectCache.get(fabricModId);
        if (cached != null) {
            return cached;
        }

        /*
         * Standalone Fabric IDs should be resolved directly through
         * Modrinth. Fabric API inspection is only relevant to IDs that
         * are actually in Fabric API's namespace.
         */
        List<ModrinthSearchHit> hits =
                client.search(
                        fabricModId,
                        ModrinthContentType.MOD,
                        normalizeLoader(instance.getLoader()),
                        instance.getMinecraftVersion()
                ).getHits();

        if (hits != null) {
            for (ModrinthSearchHit hit : hits) {
                if (hit == null
                        || hit.getProjectId() == null
                        || hit.getProjectId().isBlank()) {
                    continue;
                }

                String projectId = hit.getProjectId();

                for (ModrinthVersion version :
                        getCompatibleCandidates(instance, projectId)) {

                    InstalledMod metadata = readFabricMetadata(version);

                    if (metadata != null
                            && fabricModId.equals(metadata.getModId())) {

                        fabricModIdProjectCache.put(
                                fabricModId,
                                projectId
                        );

                        return projectId;
                    }
                }
            }
        }

        if (!fabricModId.startsWith("fabric-")) {
            return null;
        }

        /*
         * Fabric API contains internal modules. This is deliberately a
         * fallback because downloading API JARs is expensive.
         */
        for (ModrinthVersion version :
                getCompatibleCandidates(instance, "P7dR8mSH")) {

            ModrinthFile file = findPrimaryFile(version);

            if (file == null
                    || file.getUrl() == null
                    || file.getUrl().isBlank()) {
                continue;
            }

            Path tempFile =
                    Files.createTempFile(
                            "vanta-fabric-api-check-",
                            ".jar"
                    );

            try {
                try {
                    DownloadUtil.downloadFile(
                            file.getUrl(),
                            tempFile
                    );
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                } catch (IOException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IOException(
                            "Failed to download Fabric API candidate: "
                                    + e.getMessage(),
                            e
                    );
                }

                if (installedModScanner.containsFabricModId(
                        tempFile,
                        fabricModId
                )) {
                    fabricModIdProjectCache.put(
                            fabricModId,
                            "P7dR8mSH"
                    );
                    return "P7dR8mSH";
                }
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }

        return null;
    }

private boolean resolveFabricDependency(
            Instance instance,
            String projectId,
            String fabricModId,
            String constraint,
            Map<String, ModrinthVersion> resolved,
            Set<String> resolving
    ) throws IOException, InterruptedException {

        if (projectId == null
                || projectId.isBlank()
                || fabricModId == null
                || fabricModId.isBlank()) {

            return false;
        }

        /*
         * Prevent circular dependency resolution.
         */
        String resolveKey =
                "fabric:"
                        + fabricModId;

        if (!resolving.add(resolveKey)) {
            return true;
        }

        try {

            List<ModrinthVersion> candidates =
                    getCompatibleCandidates(
                            instance,
                            projectId
                    );

            if (candidates == null
                    || candidates.isEmpty()) {

                return false;
            }

            for (ModrinthVersion candidate :
                    candidates) {

                InstalledMod metadata =
                        readFabricMetadata(
                                candidate
                        );

                if (metadata == null
                        || metadata.getModId() == null
                        || metadata.getVersion() == null) {

                    continue;
                }

                /*
                 * Fabric API is a provider JAR containing many internal
                 * Fabric API modules. Those module IDs are NOT the JAR's
                 * own fabric.mod.json ID, so comparing metadata.getModId()
                 * directly against fabricDependencyModId incorrectly
                 * rejects every Fabric API candidate.
                 *
                 * For Fabric API, inspect the actual JAR for the requested
                 * module. For normal standalone mods, the Fabric mod ID must
                 * match exactly.
                 */
                boolean fabricApiProvider =
                        "P7dR8mSH".equals(projectId);

                if (fabricApiProvider) {
                    String cacheKey = candidate.getId();
                    Map<String, String> modules =
                            fabricApiModuleCache.get(cacheKey);

                    if (modules == null) {
                        ModrinthFile candidateFile =
                                findPrimaryFile(candidate);
                        if (candidateFile == null
                                || candidateFile.getUrl() == null
                                || candidateFile.getUrl().isBlank()) {
                            continue;
                        }

                        Path tempFile = Files.createTempFile(
                                "vanta-fabric-provider-", ".jar");
                        try {
                            try {
                                DownloadUtil.downloadFile(
                                        candidateFile.getUrl(), tempFile);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw e;
                            } catch (IOException e) {
                                throw e;
                            } catch (Exception e) {
                                throw new IOException(
                                        "Failed to download Fabric API candidate: "
                                                + e.getMessage(), e);
                            }
                            modules = installedModScanner.scanFabricModuleVersions(tempFile);
                            fabricApiModuleCache.put(cacheKey, modules);
                        } finally {
                            Files.deleteIfExists(tempFile);
                        }
                    }

                    String moduleVersion = modules.get(fabricModId);
                    if (moduleVersion == null) {
                        continue;
                    }

                    System.out.println(
                            "[Vanta DEBUG] Fabric API provides "
                                    + fabricModId + " " + moduleVersion
                                    + " via " + candidate.getVersionNumber()
                    );

                    /* Fabric API module constraints use the module's own
                     * version, not Fabric API's top-level 0.141.x version. */
                    if (!matchesFabricModuleConstraint(moduleVersion, constraint)) {
                        System.out.println(
                                "[Vanta DEBUG] Rejected Fabric API module "
                                        + fabricModId + " " + moduleVersion
                                        + " [" + constraint + "]"
                        );
                        continue;
                    }

                } else if (!fabricModId.equals(metadata.getModId())) {
                    continue;
                } else if (!matchesFabricConstraint(
                        metadata.getVersion(), constraint)) {

                    System.out.println(
                            "[Vanta DEBUG] Rejected Fabric dependency candidate "
                                    + candidate.getVersionNumber()
                                    + " for "
                                    + fabricModId
                                    + " ["
                                    + constraint
                                    + "]"
                    );

                    continue;
                }

                if (resolved.containsKey(projectId)) {

                    ModrinthVersion existing =
                            resolved.get(projectId);

                    /*
                     * Fabric API is a provider JAR: its own Fabric mod ID is
                     * "fabric", while the dependency may target one of its
                     * nested module IDs. Once Fabric API is already resolved,
                     * validate the requested module against THAT exact JAR.
                     * Do not compare the nested module ID to fabric.mod.json's
                     * top-level "fabric" ID.
                     */
                    if (fabricApiProvider) {

                        Map<String, String> existingModules =
                                fabricApiModuleCache.get(existing.getId());

                        if (existingModules != null) {
                            String existingModuleVersion =
                                    existingModules.get(fabricModId);

                            if (existingModuleVersion != null
                                    && matchesFabricModuleConstraint(
                                    existingModuleVersion,
                                    constraint
                            )) {
                                System.out.println(
                                        "[Vanta DEBUG] Existing Fabric API "
                                                + "provides "
                                                + fabricModId + " "
                                                + existingModuleVersion
                                                + " via "
                                                + existing.getVersionNumber()
                                );
                                return true;
                            }
                        }

                        continue;
                    }

                    InstalledMod existingMetadata =
                            readFabricMetadata(existing);

                    if (existingMetadata != null
                            && fabricModId.equals(existingMetadata.getModId())
                            && existingMetadata.getVersion() != null
                            && matchesFabricConstraint(
                            existingMetadata.getVersion(),
                            constraint
                    )) {
                        return true;
                    }

                    continue;
                }

                CompatibilityResult compatibility =
                        isCompatibleWithResolvedGraph(
                                instance,
                                projectId,
                                candidate,
                                resolved
                        );

                if (!compatibility.compatible()) {

                    System.out.println(
                            "[Vanta DEBUG] Rejected Fabric dependency candidate "
                                    + candidate.getVersionNumber()
                                    + ": "
                                    + compatibility.reason()
                    );

                    continue;
                }

                Map<String, ModrinthVersion> branch =
                        new LinkedHashMap<>(
                                resolved
                        );

                branch.put(
                        projectId,
                        candidate
                );

                if (!resolveDependencies(
                        instance,
                        candidate,
                        branch,
                        resolving,
                        null,
                        null
                )) {

                    continue;
                }

                if (!isGraphConsistent(
                        instance,
                        branch
                )) {

                    continue;
                }

                resolved.clear();
                resolved.putAll(branch);

                System.out.println(
                        "[Vanta DEBUG] Resolved Fabric dependency "
                                + fabricModId
                                + " -> "
                                + candidate.getVersionNumber()
                );

                return true;
            }

            return false;

        } finally {

            resolving.remove(resolveKey);
        }
    }

private List<ModrinthVersion> getCompatibleCandidates(
            Instance instance,
            String projectId
    ) throws IOException, InterruptedException {

        List<ModrinthVersion> versions =
                versionCache.get(projectId);

        if (versions == null) {

            versions =
                    client.getVersions(projectId);

            if (versions == null
                    || versions.isEmpty()) {

                versionCache.put(
                        projectId,
                        List.of()
                );

                return List.of();
            }

            versionCache.put(
                    projectId,
                    versions
            );
        }

        List<ModrinthVersion> candidates =
                new ArrayList<>();

        String minecraftVersion =
                instance.getMinecraftVersion();

        String loader =
                normalizeLoader(
                        instance.getLoader()
                );

        for (ModrinthVersion version :
                versions) {

            if (!isCompatible(
                    version,
                    minecraftVersion,
                    loader
            )) {
                continue;
            }

            if (findPrimaryFile(version) == null) {
                continue;
            }

            candidates.add(version);
        }

        candidates.sort((a, b) -> {

            boolean aRelease =
                    "release".equalsIgnoreCase(
                            a.getVersionType()
                    );

            boolean bRelease =
                    "release".equalsIgnoreCase(
                            b.getVersionType()
                    );

            if (aRelease != bRelease) {
                return Boolean.compare(bRelease, aRelease);
            }

            boolean aFeatured = a.isFeatured();
            boolean bFeatured = b.isFeatured();

            if (aFeatured != bFeatured) {
                return Boolean.compare(bFeatured, aFeatured);
            }

            int downloadComparison =
                    Integer.compare(
                            b.getDownloads(),
                            a.getDownloads()
                    );

            if (downloadComparison != 0) {
                return downloadComparison;
            }

            String aNumber = a.getVersionNumber() == null ? "" : a.getVersionNumber();
            String bNumber = b.getVersionNumber() == null ? "" : b.getVersionNumber();

            return bNumber.compareToIgnoreCase(aNumber);
        });

        if (fastResolution && candidates.size() > FAST_CANDIDATE_LIMIT) {
            return new ArrayList<>(
                    candidates.subList(0, FAST_CANDIDATE_LIMIT)
            );
        }

        return candidates;
    }

private boolean satisfiesDependency(
            Instance instance,
            ModrinthVersion candidate,
            ModrinthDependency dependency
    ) throws IOException, InterruptedException {

        if (candidate == null) {
            return false;
        }

        if (!isCompatible(
                candidate,
                instance.getMinecraftVersion(),
                normalizeLoader(
                        instance.getLoader()
                )
        )) {
            return false;
        }

        if (dependency == null) {
            return true;
        }

        String requiredVersionId =
                dependency.getVersionId();

        if (requiredVersionId != null
                && !requiredVersionId.isBlank()
                && !requiredVersionId.equals(
                candidate.getId()
        )) {
            return false;
        }

        return true;
    }

public InstalledMod readFabricMetadata(
            ModrinthVersion version
    ) throws IOException {

        if (version == null) {
            return null;
        }

        String cacheKey =
                version.getId();

        if (cacheKey != null) {

            InstalledMod cached =
                    fabricMetadataCache.get(
                            cacheKey
                    );

            if (cached != null) {
                return cached;
            }
        }

        ModrinthFile file =
                findPrimaryFile(version);

        if (file == null
                || file.getUrl() == null
                || file.getUrl().isBlank()) {

            return null;
        }

        Path tempFile = null;

        try {

            tempFile =
                    Files.createTempFile(
                            "vanta-resolver-",
                            ".jar"
                    );

            DownloadUtil.downloadFile(
                    file.getUrl(),
                    tempFile
            );

            InstalledMod metadata =
                    installedModScanner.scanFile(
                            tempFile
                    );

            if (metadata != null
                    && cacheKey != null) {

                fabricMetadataCache.put(
                        cacheKey,
                        metadata
                );
            }

            return metadata;

        } catch (Exception e) {

            throw new IOException(
                    "Failed to inspect Fabric metadata for "
                            + version.getVersionNumber(),
                    e
            );

        } finally {

            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

private boolean matchesFabricModuleConstraint(
            String version,
            String constraint
    ) {
        if (version == null || constraint == null) return false;
        String actual = normalizeVersionForComparison(version);
        String required = constraint.trim();
        if (required.isEmpty() || "*".equals(required)) return true;

        if (required.startsWith(">=")) {
            return compareSimpleVersions(actual,
                    normalizeVersionForComparison(required.substring(2).trim())) >= 0;
        }
        if (required.startsWith(">")) {
            return compareSimpleVersions(actual,
                    normalizeVersionForComparison(required.substring(1).trim())) > 0;
        }
        if (required.startsWith("<=")) {
            return compareSimpleVersions(actual,
                    normalizeVersionForComparison(required.substring(2).trim())) <= 0;
        }
        if (required.startsWith("<")) {
            return compareSimpleVersions(actual,
                    normalizeVersionForComparison(required.substring(1).trim())) < 0;
        }
        return matchesFabricConstraint(actual, required);
    }

private boolean matchesFabricConstraint(
            String version,
            String constraint
    ) {

        if (version == null
                || constraint == null) {

            return false;
        }

        version =
                normalizeVersionForComparison(
                        version
                );

        constraint =
                constraint.trim();

        if (constraint.isEmpty()
                || "*".equals(constraint)) {

            return true;
        }

        /*
         * Fabric commonly expresses Minecraft ranges like:
         *
         * >=1.21.11- <1.21.12-
         *
         * The trailing '-' is part of Fabric's version-range
         * syntax and must not be treated as part of the version.
         */
        constraint =
                constraint.replaceAll(
                        "(\\d+(?:\\.\\d+)+)-",
                        "$1"
                );

        String[] alternatives =
                constraint.split("\\|\\|");

        for (String alternative :
                alternatives) {

            if (matchesFabricAnd(
                    version,
                    alternative.trim()
            )) {

                return true;
            }
        }

        return false;
    }

private boolean matchesFabricAnd(
            String version,
            String constraint
    ) {

        if (constraint.isBlank()) {
            return true;
        }

        String[] parts =
                constraint.split("\\s+");

        for (String part : parts) {

            if (part.isBlank()) {
                continue;
            }

            if (containsWildcard(part)) {

                if (!matchesWildcard(
                        version,
                        part
                )) {
                    return false;
                }

                continue;
            }

            if (!VersionConstraintChecker.matches(
                    version,
                    part
            )) {
                return false;
            }
        }

        return true;
    }

private boolean containsWildcard(
            String constraint
    ) {

        return constraint.indexOf('x') >= 0
                || constraint.indexOf('X') >= 0
                || constraint.indexOf('*') >= 0;
    }

private boolean matchesWildcard(
            String version,
            String constraint
    ) {

        String normalized =
                constraint
                        .replace('*', 'x')
                        .toLowerCase();

        String[] required =
                normalized.split("\\.");

        String[] actual =
                normalizeVersionForComparison(version)
                        .split("\\.");

        for (int i = 0;
             i < required.length;
             i++) {

            String wanted =
                    required[i];

            if ("x".equals(wanted)) {
                return true;
            }

            if (i >= actual.length) {
                return false;
            }

            String actualPart =
                    numericPrefix(actual[i]);

            String wantedPart =
                    numericPrefix(wanted);

            if (!actualPart.equals(
                    wantedPart
            )) {
                return false;
            }
        }

        return true;
    }

private String normalizeVersionForComparison(
            String version
    ) {

        if (version == null) {
            return "";
        }

        int plus =
                version.indexOf('+');

        if (plus >= 0) {
            return version.substring(
                    0,
                    plus
            );
        }

        return version;
    }

private String numericPrefix(
            String value
    ) {

        if (value == null) {
            return "";
        }

        int end = 0;

        while (end < value.length()
                && Character.isDigit(
                value.charAt(end)
        )) {
            end++;
        }

        if (end == 0) {
            return value;
        }

        return value.substring(
                0,
                end
        );
    }

private CompatibilityResult isCompatibleWithResolvedGraph(
            Instance instance,
            String projectId,
            ModrinthVersion candidate,
            Map<String, ModrinthVersion> resolved
    ) throws IOException, InterruptedException {

        for (Map.Entry<String, ModrinthVersion> entry :
                resolved.entrySet()) {

            String otherProjectId = entry.getKey();
            ModrinthVersion otherVersion = entry.getValue();

            if (hasExplicitIncompatibility(candidate, otherProjectId)) {
                return CompatibilityResult.conflict(
                        candidate.getVersionNumber()
                                + " explicitly conflicts with "
                                + otherProjectId
                );
            }

            if (hasExplicitIncompatibility(otherVersion, projectId)) {
                return CompatibilityResult.conflict(
                        otherVersion.getVersionNumber()
                                + " explicitly conflicts with "
                                + projectId
                );
            }

            if (hasFabricBreakConflict(otherVersion, candidate)) {
                return CompatibilityResult.conflict(
                        otherVersion.getVersionNumber()
                                + " breaks "
                                + projectId
                );
            }

            if (hasFabricBreakConflict(candidate, otherVersion)) {
                return CompatibilityResult.conflict(
                        candidate.getVersionNumber()
                                + " breaks "
                                + otherProjectId
                );
            }

            if (!fabricDependencyAcceptsVersion(candidate, otherVersion)) {
                return CompatibilityResult.conflict(
                        projectId
                                + " "
                                + candidate.getVersionNumber()
                                + " does not accept "
                                + otherProjectId
                                + " "
                                + otherVersion.getVersionNumber()
                );
            }

            if (!fabricDependencyAcceptsVersion(otherVersion, candidate)) {
                return CompatibilityResult.conflict(
                        otherProjectId
                                + " "
                                + otherVersion.getVersionNumber()
                                + " does not accept "
                                + projectId
                                + " "
                                + candidate.getVersionNumber()
                );
            }
        }

        return CompatibilityResult.ok();
    }

private boolean isGraphConsistent(
            Instance instance,
            Map<String, ModrinthVersion> graph
    ) throws IOException, InterruptedException {

        for (Map.Entry<String, ModrinthVersion> entry :
                graph.entrySet()) {

            String projectId =
                    entry.getKey();

            ModrinthVersion version =
                    entry.getValue();

            for (Map.Entry<String, ModrinthVersion> other :
                    graph.entrySet()) {

                if (projectId.equals(
                        other.getKey()
                )) {
                    continue;
                }

                ModrinthVersion otherVersion =
                        other.getValue();

                if (hasExplicitIncompatibility(
                        version,
                        other.getKey()
                )) {
                    return false;
                }

                if (!fabricDependencyAcceptsVersion(
                        version,
                        otherVersion
                )) {
                    return false;
                }

                if (!fabricDependencyAcceptsVersion(
                        otherVersion,
                        version
                )) {
                    return false;
                }

                if (hasFabricBreakConflict(
                        version,
                        otherVersion
                )) {
                    return false;
                }

                if (hasFabricBreakConflict(
                        otherVersion,
                        version
                )) {
                    return false;
                }
            }
        }

        return true;
    }

private boolean hasExplicitIncompatibility(
            ModrinthVersion version,
            String projectId
    ) {

        if (version == null
                || projectId == null
                || version.getDependencies() == null) {

            return false;
        }

        for (ModrinthDependency dependency :
                version.getDependencies()) {

            if (dependency == null
                    || !dependency.isIncompatible()) {

                continue;
            }

            if (projectId.equals(
                    dependency.getProjectId()
            )) {
                return true;
            }
        }

        return false;
    }

private boolean fabricDependencyAcceptsVersion(
            ModrinthVersion requestingVersion,
            ModrinthVersion dependencyVersion
    ) throws IOException {

        if (requestingVersion == null || dependencyVersion == null) {
            return true;
        }

        String dependencyProjectId = dependencyVersion.getProjectId();
        if (dependencyProjectId == null || dependencyProjectId.isBlank()) {
            return true;
        }

        List<ModrinthDependency> modrinthDependencies =
                requestingVersion.getDependencies();

        if (modrinthDependencies != null) {
            for (ModrinthDependency requirement : modrinthDependencies) {
                if (requirement == null
                        || !dependencyProjectId.equals(requirement.getProjectId())) {
                    continue;
                }

                if (requirement.isIncompatible()) {
                    return false;
                }

                if (requirement.isOptional()) {
                    return true;
                }

                String requiredVersionId = requirement.getVersionId();
                return requiredVersionId == null
                        || requiredVersionId.isBlank()
                        || requiredVersionId.equals(dependencyVersion.getId());
            }
        }

        InstalledMod requesting = readFabricMetadata(requestingVersion);
        InstalledMod dependency = readFabricMetadata(dependencyVersion);

        if (requesting == null || dependency == null) {
            return true;
        }

        String dependencyModId = dependency.getModId();
        String dependencyVersionNumber = dependency.getVersion();

        if (dependencyModId == null || dependencyVersionNumber == null) {
            return true;
        }

        for (DependencyRequirement requirement : requesting.getDependencies()) {
            if (requirement == null
                    || !dependencyModId.equals(requirement.getModId())) {
                continue;
            }

            boolean matches = matchesFabricConstraint(
                    dependencyVersionNumber,
                    requirement.getVersionConstraint()
            );

            System.out.println(
                    "[Vanta] Fabric dependency check: "
                            + requesting.getModId()
                            + " "
                            + requesting.getVersion()
                            + " -> "
                            + dependencyModId
                            + " "
                            + dependencyVersionNumber
                            + " ["
                            + requirement.getVersionConstraint()
                            + "] = "
                            + matches
            );

            return matches;
        }

        return true;
    }


private boolean hasFabricBreakConflict(
            ModrinthVersion breakingVersion,
            ModrinthVersion otherVersion
    ) throws IOException, InterruptedException {

        if (breakingVersion == null
                || otherVersion == null) {
            return false;
        }

        InstalledMod breaking =
                readFabricMetadata(
                        breakingVersion
                );

        if (breaking == null
                || breaking.getBreaks() == null
                || breaking.getBreaks().isEmpty()) {
            return false;
        }

        InstalledMod other =
                readFabricMetadata(
                        otherVersion
                );

        if (other == null
                || other.getModId() == null
                || other.getVersion() == null) {
            return false;
        }

        for (DependencyRequirement requirement :
                breaking.getBreaks()) {

            if (requirement == null) {
                continue;
            }

            if (!other.getModId().equals(
                    requirement.getModId()
            )) {
                continue;
            }

            if (matchesFabricConstraint(
                    other.getVersion(),
                    requirement.getVersionConstraint()
            )) {

                System.out.println(
                        "[Vanta] Fabric break conflict: "
                                + breaking.getModId()
                                + " "
                                + breaking.getVersion()
                                + " breaks "
                                + other.getModId()
                                + " "
                                + other.getVersion()
                                + " ["
                                + requirement.getVersionConstraint()
                                + "]"
                );

                return true;
            }
        }

        return false;
    }

private boolean candidateDependenciesAcceptVersion(
            Instance instance,
            ModrinthVersion requestingVersion,
            ModrinthVersion dependencyVersion
    ) throws IOException, InterruptedException {

        if (requestingVersion == null
                || dependencyVersion == null) {

            return true;
        }

        String dependencyProjectId =
                findProjectIdForVersion(
                        dependencyVersion
                );

        if (dependencyProjectId == null) {
            return true;
        }

        List<ModrinthDependency> dependencies =
                requestingVersion.getDependencies();

        if (dependencies == null) {
            return true;
        }

        for (ModrinthDependency dependency :
                dependencies) {

            if (dependency == null
                    || dependency.isOptional()
                    || dependency.isIncompatible()) {

                continue;
            }

            if (!dependencyProjectId.equals(
                    dependency.getProjectId()
            )) {
                continue;
            }

            String requiredVersionId =
                    dependency.getVersionId();

            if (requiredVersionId != null
                    && !requiredVersionId.isBlank()
                    && !requiredVersionId.equals(
                    dependencyVersion.getId()
            )) {

                return false;
            }
        }

        return fabricDependencyAcceptsVersion(
                requestingVersion,
                dependencyVersion
        );
    }

private String findProjectIdForVersion(
            ModrinthVersion version
    ) {

        if (version == null) {
            return null;
        }

        return version.getProjectId();
    }

ModrinthVersion resolveDependencyVersion(
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

        String requiredVersionId =
                dependency.getVersionId();

        if (requiredVersionId != null
                && !requiredVersionId.isBlank()) {

            for (ModrinthVersion version :
                    versions) {

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

        return findCompatibleVersion(
                versions,
                instance.getMinecraftVersion(),
                normalizeLoader(
                        instance.getLoader()
                )
        );
    }

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

        for (ModrinthVersion version : versions) {

            if (!isCompatible(
                    version,
                    instance.getMinecraftVersion(),
                    normalizeLoader(
                            instance.getLoader()
                    )
            )) {
                continue;
            }

            InstalledMod metadata =
                    readFabricMetadata(version);

            if (metadata != null) {
                return metadata.getModId();
            }
        }

        return null;
    }

boolean hasCompatibleInstalledDependency(
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

        DependencyRequirement requirement =
                findDependencyRequirement(
                        instance,
                        requestingVersion,
                        dependencyMod.getModId()
                );

        if (requirement == null) {
            return false;
        }

        return matchesFabricConstraint(
                dependencyMod.getVersion(),
                requirement.getVersionConstraint()
        );
    }

private Path findInstalledModByFilename(
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

        return file;
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

        if (record != null
                && record.getFilename() != null) {

            Path file =
                    instance.getDirectory()
                            .resolve("mods")
                            .resolve(
                                    record.getFilename()
                            );

            if (Files.isRegularFile(file)) {

                InstalledMod installed =
                        installedModScanner.scanFile(
                                file
                        );

                if (installed != null) {
                    return installed;
                }
            }
        }

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

        InstalledMod requestingMod =
                readFabricMetadata(
                        requestingVersion
                );

        if (requestingMod == null) {
            return null;
        }

        for (DependencyRequirement requirement :
                requestingMod.getDependencies()) {

            if (requirement != null
                    && dependencyModId.equals(
                    requirement.getModId()
            )) {

                return requirement;
            }
        }

        return null;
    }

private boolean matchesRequiredVersions(
            ModrinthVersion candidate,
            List<String> requiredVersions
    ) {

        if (requiredVersions == null
                || requiredVersions.isEmpty()) {
            return true;
        }

        String candidateVersion =
                candidate.getVersionNumber();

        if (candidateVersion == null
                || candidateVersion.isBlank()) {
            return false;
        }

        String normalizedCandidate =
                normalizeModrinthVersion(
                        candidateVersion
                );

        /*
         * The crash parser represents:
         *
         *   "0.9.1 or later"
         *
         * as:
         *
         *   ["0.9.1", "later"]
         *
         * Reconstruct that as a lower-bound constraint.
         */
        for (int i = 0;
             i < requiredVersions.size();
             i++) {

            String required =
                    requiredVersions.get(i);

            if (required == null
                    || required.isBlank()) {
                continue;
            }

            String normalizedRequired =
                    normalizeVersionForComparison(
                            required
                    );

            /*
             * Exact version.
             */
            if (normalizedCandidate.equals(
                    normalizedRequired
            )) {
                return true;
            }

            /*
             * "X or later"
             */
            if (i + 1 < requiredVersions.size()
                    && "later".equalsIgnoreCase(
                    requiredVersions.get(i + 1)
            )) {

                if (compareSimpleVersions(
                        normalizedCandidate,
                        normalizedRequired
                ) >= 0) {
                    return true;
                }
            }

            /*
             * Wildcard such as 0.9.x.
             */
            if (normalizedRequired.endsWith(".x")) {

                String prefix =
                        normalizedRequired.substring(
                                0,
                                normalizedRequired.length() - 2
                        );

                if (normalizedCandidate.equals(prefix)
                        || normalizedCandidate.startsWith(
                        prefix + "."
                )) {
                    return true;
                }
            }
        }

        return false;
    }

private int compareSimpleVersions(
            String first,
            String second
    ) {

        String[] firstParts =
                first.split("\\.");

        String[] secondParts =
                second.split("\\.");

        int length =
                Math.max(
                        firstParts.length,
                        secondParts.length
                );

        for (int i = 0; i < length; i++) {

            int firstValue =
                    i < firstParts.length
                            ? parseVersionPart(
                            firstParts[i]
                    )
                            : 0;

            int secondValue =
                    i < secondParts.length
                            ? parseVersionPart(
                            secondParts[i]
                    )
                            : 0;

            if (firstValue != secondValue) {
                return Integer.compare(
                        firstValue,
                        secondValue
                );
            }
        }

        return 0;
    }

private int parseVersionPart(
            String value
    ) {

        String numeric =
                numericPrefix(value);

        if (numeric.isEmpty()) {
            return 0;
        }

        try {
            return Integer.parseInt(numeric);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

private String normalizeModrinthVersion(
            String version
    ) {

        if (version == null
                || version.isBlank()) {
            return "";
        }

        // Modrinth:
        // mc1.21.11-0.8.14-fabric
        //
        // Fabric:
        // 0.8.14+mc1.21.11

        if (version.startsWith("mc")
                && version.endsWith("-fabric")) {

            String body =
                    version.substring(
                            2,
                            version.length() - "-fabric".length()
                    );

            int separator =
                    body.indexOf('-');

            if (separator > 0
                    && separator < body.length() - 1) {

                String minecraftVersion =
                        body.substring(
                                0,
                                separator
                        );

                String modVersion =
                        body.substring(
                                separator + 1
                        );

                return normalizeVersionForComparison(
                        modVersion
                                + "+mc"
                                + minecraftVersion
                );
            }
        }

        return normalizeVersionForComparison(
                version
        );
    }

private boolean isProvidedByFabricApi(
            Map<String, ModrinthVersion> resolved,
            String fabricDependencyModId,
            String constraint
    ) throws IOException, InterruptedException {

        if (resolved == null
                || fabricDependencyModId == null
                || fabricDependencyModId.isBlank()) {
            return false;
        }

        /*
         * "fabric" is Fabric's special dependency and is provided by
         * Fabric API. Other IDs must not be assumed to be API modules.
         */
        if (!"fabric".equalsIgnoreCase(fabricDependencyModId)) {
            return false;
        }

        return resolved.containsKey("P7dR8mSH");
    }

    private int minimumJavaMajorForMinecraft(
            String minecraftVersion
    ) {
        if (minecraftVersion == null
                || minecraftVersion.isBlank()) {
            return 0;
        }

        if (minecraftVersion.startsWith("26.")) {
            return 25;
        }

        if (minecraftVersion.startsWith("1.20.5")
                || minecraftVersion.startsWith("1.20.6")
                || minecraftVersion.startsWith("1.21")) {
            return 21;
        }

        return 17;
    }

    private boolean isCompatible(ModrinthVersion version, String minecraftVersion, String loader) {
        if (!isGameVersionCompatible(version, minecraftVersion)) return false;
        if (version == null || version.getLoaders() == null || loader == null) return false;
        return version.getLoaders().stream().map(this::normalizeLoader).anyMatch(loader::equals);
    }

    private boolean isGameVersionCompatible(ModrinthVersion version, String minecraftVersion) {
        if (version == null || version.getGameVersions() == null || minecraftVersion == null || minecraftVersion.isBlank()) return false;
        if (version.getGameVersions().contains(minecraftVersion)) return true;
        String prefix = minecraftVersion + ".";
        return version.getGameVersions().stream().anyMatch(v -> v != null && v.startsWith(prefix));
    }

    private ModrinthFile findPrimaryFile(ModrinthVersion version) {
        if (version == null || version.getFiles() == null || version.getFiles().isEmpty()) return null;
        return version.getFiles().stream().filter(ModrinthFile::isPrimary).findFirst().orElse(version.getFiles().get(0));
    }

    private ModrinthVersion findCompatibleVersion(List<ModrinthVersion> versions, String minecraftVersion, String loader) {
        if (versions == null || versions.isEmpty()) return null;
        return versions.stream().filter(v -> isCompatible(v, minecraftVersion, loader)).filter(v -> "release".equalsIgnoreCase(v.getVersionType())).findFirst()
                .orElseGet(() -> versions.stream().filter(v -> isCompatible(v, minecraftVersion, loader)).findFirst().orElse(null));
    }
}
