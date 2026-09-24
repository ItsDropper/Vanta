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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModrinthService {

    private final ModrinthClient client;
    private final InstalledModScanner installedModScanner;

    /*
     * Fabric metadata is expensive to obtain because it requires
     * downloading the actual JAR. Cache it for the duration of
     * the current resolver operation.
     */
    private final Map<String, InstalledMod> fabricMetadataCache =
            new HashMap<>();


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
    // GRAPH RESOLUTION
    // =============================================================

    public List<ResolvedMod> resolveModGraph(
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

        if (!success) {
            throw new IOException(
                    "Could not resolve compatible dependency graph. "
                            + "Check the Vanta debug log for the "
                            + "specific dependency conflict."
            );
        }

        List<ResolvedMod> result =
                new ArrayList<>();

        for (Map.Entry<String, ModrinthVersion> entry :
                resolved.entrySet()) {

            result.add(
                    new ResolvedMod(
                            entry.getKey(),
                            entry.getValue()
                    )
            );
        }

        return result;
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
            if (resolved.containsKey(rootProjectId)) continue;

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
                    resolving
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
            Set<String> resolving
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
                        resolving
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
            Set<String> resolving
    ) throws IOException, InterruptedException {

        List<ModrinthDependency> dependencies =
                version.getDependencies();

        if (dependencies == null
                || dependencies.isEmpty()) {

            return true;
        }

        System.out.println(
                "[Vanta DEBUG] Resolving dependencies for "
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
                    "[Vanta DEBUG]   Dependency: "
                            + dependencyProjectId
                            + " versionId="
                            + dependency.getVersionId()
            );

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
                            resolving
                    );

            if (!success) {

                System.out.println(
                        "[Vanta DEBUG]   FAILED dependency: "
                                + dependencyProjectId
                );

                return false;
            }
        }

        return true;
    }

    // =============================================================
    // CANDIDATES
    // =============================================================

    private final Map<String, List<ModrinthVersion>> versionCache =
            new HashMap<>();

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
                return Boolean.compare(
                        bRelease,
                        aRelease
                );
            }

            return 0;
        });

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

    // =============================================================
    // FABRIC METADATA
    // =============================================================

    private InstalledMod readFabricMetadata(
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

    // =============================================================
    // FABRIC VERSION CONSTRAINTS
    // =============================================================

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

    // =============================================================
    // GRAPH COMPATIBILITY
    // =============================================================

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

        InstalledMod requesting =
                readFabricMetadata(
                        requestingVersion
                );

        InstalledMod dependency =
                readFabricMetadata(
                        dependencyVersion
                );

        if (requesting == null
                || dependency == null) {

            return true;
        }

        String dependencyModId =
                dependency.getModId();

        String dependencyVersionNumber =
                dependency.getVersion();

        if (dependencyModId == null
                || dependencyVersionNumber == null) {

            return true;
        }

        for (DependencyRequirement requirement :
                requesting.getDependencies()) {

            if (requirement == null) {
                continue;
            }

            if (!dependencyModId.equals(
                    requirement.getModId()
            )) {
                continue;
            }

            boolean matches =
                    matchesFabricConstraint(
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

    // =============================================================
    // MODRINTH DEPENDENCY COMPATIBILITY
    // =============================================================

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

    // =============================================================
    // INSTALL RESOLVED GRAPH
    // =============================================================

    public List<Path> installResolvedGraph(
            Instance instance,
            List<ResolvedMod> resolvedMods
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

        for (ResolvedMod resolved :
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
                findPrimaryFile(version);

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
                        sanitizeFilename(
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

            return installResolvedVersion(
                    instance,
                    projectId,
                    version
            );

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

                    if (hasCompatibleInstalledDependency(
                            instance,
                            dependency,
                            compatibleVersion
                    )) {
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

            return installResolvedVersion(
                    instance,
                    projectId,
                    compatibleVersion
            );

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

    // =============================================================
    // REPAIR MOD DEPENDENCY
    // =============================================================


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

        fabricMetadataCache.clear();
        versionCache.clear();

        /*
         * Build the installed root set.
         *
         * Every installed mod starts as a root candidate. During repair,
         * we first try keeping every root. If that is impossible, we test
         * removing exactly one root at a time.
         */
        List<InstalledModRecord> installedMods =
                InstalledModManager.load(instance);

        List<ModrinthProject> roots =
                new ArrayList<>();

        Map<String, InstalledModRecord> installedByProject =
                new LinkedHashMap<>();

        for (InstalledModRecord record : installedMods) {

            if (record == null
                    || record.getProjectId() == null
                    || record.getProjectId().isBlank()) {
                continue;
            }

            String installedProjectId =
                    record.getProjectId();

            installedByProject.put(
                    installedProjectId,
                    record
            );

            ModrinthProject installedProject =
                    client.getProject(
                            installedProjectId
                    );

            if (installedProject == null) {
                continue;
            }

            roots.add(installedProject);
        }

        /*
         * Make sure the project being repaired is a root.
         */
        boolean alreadyPresent = false;

        for (ModrinthProject root : roots) {

            if (root != null
                    && projectId.equals(
                    root.getProjectId()
            )) {

                alreadyPresent = true;
                break;
            }
        }

        if (!alreadyPresent) {

            ModrinthProject repairedProject =
                    client.getProject(projectId);

            if (repairedProject == null) {
                throw new IOException(
                        "Could not find Modrinth project: "
                                + projectId
                );
            }

            roots.add(repairedProject);
        }

        /*
         * First attempt:
         *
         * Keep every installed root and solve the complete graph.
         */
        List<ResolvedMod> resolved = null;

        try {

            resolved =
                    resolveModGraph(
                            instance,
                            roots,
                            projectId,
                            requiredVersions
                    );

        } catch (IOException fullGraphFailure) {

            System.out.println(
                    "[Vanta] Full repair graph could not be resolved."
            );

            /*
             * The full graph is impossible.
             *
             * Now test whether exactly one installed root needs to be
             * removed from the root set.
             *
             * Important:
             * Removing a root does NOT necessarily remove the mod.
             * If another mod depends on it, the dependency resolver will
             * add it back and choose a compatible version.
             */
            List<ModrinthProject> successfulRoots = null;
            List<ResolvedMod> successfulResolution = null;
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
                fabricMetadataCache.clear();
                versionCache.clear();

                try {

                    List<ResolvedMod> candidateResolution =
                            resolveModGraph(
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
                                + "root can be removed to resolve it.",
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
        ResolvedMod repaired = null;

        for (ResolvedMod mod : resolved) {

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
         *
         * These are the roots that the successful repair determined
         * cannot remain in the environment.
         */
        Set<String> resolvedProjectIds =
                new HashSet<>();

        for (ResolvedMod mod : resolved) {

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
        for (ResolvedMod mod : resolved) {

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
         * Install the complete resolved graph.
         */
        List<Path> installed =
                installResolvedGraph(
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
                readFabricMetadata(
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

                if (!isCompatible(
                        version,
                        instance.getMinecraftVersion(),
                        normalizeLoader(
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

    // =============================================================
    // REMOVE INSTALLED MOD
    // =============================================================

    private void removeInstalledMod(
            Instance instance,
            String projectId
    ) throws IOException, InterruptedException {

        InstalledModRecord record =
                InstalledModManager.findByProjectId(
                        instance,
                        projectId
                );

        String modId =
                record != null
                        ? record.getModId()
                        : null;

        Path modsDirectory =
                instance.getDirectory()
                        .resolve("mods");

        if (Files.exists(modsDirectory)
                && modId != null
                && !modId.isBlank()) {

            List<InstalledMod> installed =
                    installedModScanner.scan(instance);

            for (InstalledMod mod : installed) {

                if (mod == null
                        || !modId.equals(
                        mod.getModId()
                )) {
                    continue;
                }

                Path file =
                        modsDirectory.resolve(
                                mod.getFilename()
                        );

                System.out.println(
                        "[Vanta] Removing conflicting mod: "
                                + file.getFileName()
                );

                Files.deleteIfExists(file);
            }
        }

        InstalledModManager.removeByProjectId(
                instance,
                projectId
        );
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

        for (ModrinthSearchHit hit :
                hits) {

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
                        client.getProject(
                                projectId
                        );

                if (project != null) {
                    projects.add(project);
                }

            } catch (IOException ignored) {
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

    // =============================================================
    // RESOLVED MOD
    // =============================================================

    public record ResolvedMod(
            String projectId,
            ModrinthVersion version
    ) {
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

        for (String required :
                requiredVersions) {

            if (required == null
                    || required.isBlank()) {
                continue;
            }

            String normalizedRequired =
                    normalizeVersionForComparison(
                            required
                    );

            if (normalizedCandidate.equals(
                    normalizedRequired
            )) {
                return true;
            }
        }

        return false;
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

    private record CompatibilityResult(
            boolean compatible,
            String reason
    ) {
        static CompatibilityResult ok() {
            return new CompatibilityResult(true, null);
        }

        static CompatibilityResult conflict(String reason) {
            return new CompatibilityResult(false, reason);
        }
    }

}