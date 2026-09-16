package org.example.launcher.service;

import org.example.launcher.instance.DownloadUtil;
import org.example.launcher.instance.MrpackInstaller;
import org.example.launcher.model.Instance;
import org.example.launcher.modrinth.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ModrinthContentService {

    private final ModrinthService modrinthService;
    private final ModrinthClient client;

    public ModrinthContentService() {
        this.modrinthService = new ModrinthService();
        this.client = new ModrinthClient();
    }

    public List<ModrinthProject> search(
            String query,
            ModrinthContentType type,
            String minecraftVersion
    ) throws IOException, InterruptedException {

        return modrinthService.search(
                query,
                type,
                null,
                minecraftVersion
        );
    }

    public List<ModrinthProject> search(
            String query,
            ModrinthContentType type,
            String loader,
            String minecraftVersion
    ) throws IOException, InterruptedException {

        return modrinthService.search(
                query,
                type,
                loader,
                minecraftVersion
        );
    }



    public List<ModrinthProject> getMostDownloaded(
            ModrinthContentType type,
            String loader,
            String minecraftVersion
    ) throws IOException, InterruptedException {

        ModrinthSearchResult result =
                client.getMostDownloaded(
                        type,
                        loader,
                        minecraftVersion
                );

        if (result == null || result.getHits() == null) {
            return List.of();
        }

        List<ModrinthProject> projects =
                new java.util.ArrayList<>();

        for (var hit : result.getHits()) {

            ModrinthProject project =
                    client.getProject(
                            hit.getProjectId()
                    );

            if (project != null) {
                projects.add(project);
            }
        }

        return projects;
    }







    public void installResourcePack(
            Instance instance,
            ModrinthProject project
    ) throws IOException, InterruptedException {

        modrinthService.installResourcePack(instance, project);
    }

    public void installShader(
            Instance instance,
            ModrinthProject project
    ) throws IOException, InterruptedException {

        modrinthService.installShader(instance, project);
    }

    public Instance installModpack(
            ModrinthProject project
    ) throws Exception {

        List<ModrinthVersion> versions = client.getVersions(project.getProjectId());

        if (versions == null || versions.isEmpty()) {
            throw new IOException("No versions found for modpack: " + project.getTitle());
        }

        ModrinthVersion selectedVersion = findModpackVersion(versions);

        if (selectedVersion == null) {
            throw new IOException(
                    "No .mrpack file found for modpack: " + project.getTitle()
            );
        }

        ModrinthFile file = findMrpackFile(selectedVersion);

        Path tempFile = Files.createTempFile(
                "vanta-modpack-",
                ".mrpack"
        );

        try {
            DownloadUtil.downloadFile(
                    file.getUrl(),
                    tempFile
            );

            return MrpackInstaller.importMrpack(tempFile);

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private ModrinthVersion findModpackVersion(
            List<ModrinthVersion> versions
    ) {

        for (ModrinthVersion version : versions) {
            if (findMrpackFile(version) != null) {
                return version;
            }
        }

        return null;
    }

    private ModrinthFile findMrpackFile(
            ModrinthVersion version
    ) {

        if (version == null || version.getFiles() == null) {
            return null;
        }

        for (ModrinthFile file : version.getFiles()) {
            if (file == null || file.getFilename() == null) {
                continue;
            }

            if (file.getFilename().toLowerCase().endsWith(".mrpack")) {
                return file;
            }
        }

        return null;
    }
}

