package org.example.launcher.service;

import org.example.launcher.modrinth.*;
import org.example.launcher.model.Instance;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModrinthService {

    private final ModrinthClient client;
    private final HttpClient httpClient;

    public ModrinthService() {

        client =
                new ModrinthClient();

        httpClient =
                HttpClient.newHttpClient();
    }

    // =============================================================
    // GLOBAL SEARCH
    // =============================================================

    public List<ModrinthProject> searchMods(
            String query
    ) throws IOException, InterruptedException {

        if (query == null || query.isBlank()) {

            throw new IllegalArgumentException(
                    "Search query cannot be empty."
            );
        }

        List<ModrinthSearchHit> hits =
                client.search(
                        query,
                        null,
                        null
                ).getHits();

        return resolveProjects(
                hits
        );
    }

    // =============================================================
    // INSTANCE SEARCH
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

        if (query == null || query.isBlank()) {

            throw new IllegalArgumentException(
                    "Search query cannot be empty."
            );
        }

        String loader =
                normalizeLoader(
                        instance.getLoader()
                );

        List<ModrinthSearchHit> hits =
                client.search(
                        query,
                        loader,
                        instance.getMinecraftVersion()
                ).getHits();

        return resolveProjects(
                hits
        );
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

        String loader =
                normalizeLoader(
                        instance.getLoader()
                );

        String minecraftVersion =
                instance.getMinecraftVersion();

        List<ModrinthSearchHit> hits =
                client.getMostDownloadedMods(
                        loader,
                        minecraftVersion
                ).getHits();

        return resolveProjects(
                hits
        );
    }

    // =============================================================
    // RESOLVE SEARCH HITS
    // =============================================================

    private List<ModrinthProject> resolveProjects(
            List<ModrinthSearchHit> hits
    ) throws IOException, InterruptedException {

        List<ModrinthProject> projects =
                new ArrayList<>();

        if (hits == null
                || hits.isEmpty()) {

            return projects;
        }

        for (
                ModrinthSearchHit hit
                : hits
        ) {

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

                    projects.add(
                            project
                    );
                }

            } catch (IOException ex) {

                ex.printStackTrace();
            }
        }

        return projects;
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

        Set<String> resolvingProjects =
                new HashSet<>();

        return installProject(
                instance,
                project.getProjectId(),
                resolvingProjects
        );
    }

    // =============================================================
    // INSTALL PROJECT
    // =============================================================

    private Path installProject(
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

        /*
         * Prevent dependency loops such as:
         *
         * A -> B -> A
         */
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

            // -----------------------------------------------------
            // GET VERSIONS
            // -----------------------------------------------------

            List<ModrinthVersion> versions =
                    client.getVersions(
                            projectId
                    );

            ModrinthVersion compatibleVersion =
                    findCompatibleVersion(
                            versions,
                            minecraftVersion,
                            loader
                    );

            if (compatibleVersion == null) {

                throw new IOException(
                        "No compatible version found for dependency "
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

                for (
                        ModrinthDependency dependency
                        : dependencies
                ) {

                    if (dependency == null) {
                        continue;
                    }

                    /*
                     * Incompatible dependencies are never
                     * automatically installed.
                     */
                    if (dependency.isIncompatible()) {
                        continue;
                    }

                    /*
                     * Optional dependencies are deliberately
                     * not installed automatically.
                     */
                    if (dependency.isOptional()) {
                        continue;
                    }

                    /*
                     * Required dependency must have a
                     * Modrinth project ID.
                     */
                    String dependencyProjectId =
                            dependency.getProjectId();

                    if (dependencyProjectId == null
                            || dependencyProjectId.isBlank()) {

                        continue;
                    }

                    installProject(
                            instance,
                            dependencyProjectId,
                            resolvingProjects
                    );
                }
            }

            // -----------------------------------------------------
            // FIND FILE
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

            // -----------------------------------------------------
            // MODS DIRECTORY
            // -----------------------------------------------------

            Path modsDirectory =
                    instance.getDirectory()
                            .resolve("mods");

            Files.createDirectories(
                    modsDirectory
            );

            // -----------------------------------------------------
            // FILENAME
            // -----------------------------------------------------

            String filename =
                    sanitizeFilename(
                            file.getFilename()
                    );

            Path destination =
                    modsDirectory.resolve(
                            filename
                    );

            // -----------------------------------------------------
            // DUPLICATE CHECK
            // -----------------------------------------------------

            if (Files.exists(destination)) {

                System.out.println(
                        "Skipping already installed mod: "
                                + filename
                );

                return destination;
            }

            // -----------------------------------------------------
            // DOWNLOAD
            // -----------------------------------------------------

            downloadFile(
                    file,
                    destination
            );

            return destination;

        } finally {

            resolvingProjects.remove(
                    projectId
            );
        }
    }

    // =============================================================
    // FIND COMPATIBLE VERSION
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

        // ---------------------------------------------------------
        // PREFER RELEASE
        // ---------------------------------------------------------

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

        // ---------------------------------------------------------
        // FALLBACK
        // ---------------------------------------------------------

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
    // COMPATIBILITY
    // =============================================================

    private boolean isCompatible(
            ModrinthVersion version,
            String minecraftVersion,
            String loader
    ) {

        if (version == null) {
            return false;
        }

        if (version.getGameVersions() == null
                || !version.getGameVersions()
                .contains(
                        minecraftVersion
                )) {

            return false;
        }

        if (version.getLoaders() == null) {
            return false;
        }

        return version.getLoaders()
                .stream()
                .map(
                        this::normalizeLoader
                )
                .anyMatch(
                        loader::equals
                );
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
                .filter(
                        ModrinthFile::isPrimary
                )
                .findFirst()
                .orElse(
                        version.getFiles().get(0)
                );
    }

    // =============================================================
    // DOWNLOAD
    // =============================================================

    private void downloadFile(
            ModrinthFile file,
            Path destination
    ) throws IOException, InterruptedException {

        if (file.getUrl() == null
                || file.getUrl().isBlank()) {

            throw new IOException(
                    "Modrinth file has no download URL."
            );
        }

        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(
                                        file.getUrl()
                                )
                        )
                        .GET()
                        .header(
                                "User-Agent",
                                "VantaLauncher/1.0"
                        )
                        .build();

        HttpResponse<InputStream> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers
                                .ofInputStream()
                );

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {

            response.body().close();

            throw new IOException(
                    "Modrinth file download failed: HTTP "
                            + response.statusCode()
            );
        }

        /*
         * Download to a temporary file first.
         *
         * This prevents a partially downloaded JAR from
         * being left in the mods directory if the connection
         * dies halfway through.
         */
        Path temporaryFile =
                destination.resolveSibling(
                        destination.getFileName()
                                + ".download"
                );

        try (InputStream input =
                     response.body()) {

            Files.copy(
                    input,
                    temporaryFile,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }

        Files.move(
                temporaryFile,
                destination,
                StandardCopyOption.REPLACE_EXISTING
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