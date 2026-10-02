package org.example.launcher.service;

import org.example.launcher.instance.*;
import org.example.launcher.modrinth.*;
import org.example.launcher.model.Instance;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class ModrinthCatalogService {
    private final ModrinthClient client;
    private final InstalledModScanner installedModScanner;

    public ModrinthCatalogService(ModrinthClient client, InstalledModScanner installedModScanner) {
        this.client = client;
        this.installedModScanner = installedModScanner;
    }

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

ModrinthVersion findCompatibleVersion(
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

ModrinthVersion findContentVersion(
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

        if (version == null
                || version.getGameVersions() == null
                || minecraftVersion == null
                || minecraftVersion.isBlank()) {

            return false;
        }

        /*
         * Exact match first.
         */
        if (version.getGameVersions().contains(
                minecraftVersion
        )) {
            return true;
        }

        /*
         * Minecraft 26.1.x versions are represented by Vanta
         * as 26.1, while Modrinth may tag individual releases as
         * 26.1.1, 26.1.2, etc.
         *
         * Treat 26.1 as the same minor release family.
         */
        String prefix =
                minecraftVersion + ".";

        for (String gameVersion :
                version.getGameVersions()) {

            if (gameVersion == null
                    || gameVersion.isBlank()) {
                continue;
            }

            if (gameVersion.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

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

String normalizeLoader(
            String loader
    ) {

        if (loader == null) {
            return "";
        }

        return loader
                .trim()
                .toLowerCase();
    }

String sanitizeFilename(
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
