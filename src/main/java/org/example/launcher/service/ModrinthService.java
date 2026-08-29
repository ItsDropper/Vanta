package org.example.launcher.service;

import org.example.launcher.modrinth.ModrinthClient;
import org.example.launcher.modrinth.ModrinthFile;
import org.example.launcher.modrinth.ModrinthProject;
import org.example.launcher.modrinth.ModrinthVersion;
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
import java.util.Comparator;
import java.util.List;

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
    // SEARCH
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

        return client.search(
                query,
                loader,
                instance.getMinecraftVersion()
        ).getHits();
    }

    // =============================================================
    // INSTALL
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

        String loader =
                normalizeLoader(
                        instance.getLoader()
                );

        String minecraftVersion =
                instance.getMinecraftVersion();

        List<ModrinthVersion> versions =
                client.getVersions(
                        project.getProjectId()
                );

        // ---------------------------------------------------------
        // FIND COMPATIBLE RELEASE
        // ---------------------------------------------------------

        ModrinthVersion compatibleVersion =
                versions.stream()
                        .filter(version ->
                                version.getGameVersions() != null
                                        && version.getGameVersions()
                                        .contains(
                                                minecraftVersion
                                        )
                        )
                        .filter(version ->
                                version.getLoaders() != null
                                        && version.getLoaders()
                                        .stream()
                                        .map(
                                                this::normalizeLoader
                                        )
                                        .anyMatch(
                                                loader::equals
                                        )
                        )
                        .filter(version ->
                                "release".equalsIgnoreCase(
                                        version.getVersionType()
                                )
                        )
                        .findFirst()
                        .orElse(null);

        // ---------------------------------------------------------
        // FALLBACK TO ANY COMPATIBLE VERSION
        // ---------------------------------------------------------

        if (compatibleVersion == null) {

            compatibleVersion =
                    versions.stream()
                            .filter(version ->
                                    version.getGameVersions() != null
                                            && version.getGameVersions()
                                            .contains(
                                                    minecraftVersion
                                            )
                            )
                            .filter(version ->
                                    version.getLoaders() != null
                                            && version.getLoaders()
                                            .stream()
                                            .map(
                                                    this::normalizeLoader
                                            )
                                            .anyMatch(
                                                    loader::equals
                                            )
                            )
                            .findFirst()
                            .orElse(null);
        }

        // ---------------------------------------------------------
        // NO COMPATIBLE VERSION
        // ---------------------------------------------------------

        if (compatibleVersion == null) {

            throw new IOException(
                    "No compatible version found for Minecraft "
                            + minecraftVersion
                            + " / "
                            + loader
            );
        }

        // ---------------------------------------------------------
        // FIND PRIMARY FILE
        // ---------------------------------------------------------

        if (compatibleVersion.getFiles() == null
                || compatibleVersion.getFiles().isEmpty()) {

            throw new IOException(
                    "Compatible Modrinth version has no files."
            );
        }

        ModrinthFile file =
                compatibleVersion
                        .getFiles()
                        .stream()
                        .filter(ModrinthFile::isPrimary)
                        .findFirst()
                        .orElse(
                                compatibleVersion
                                        .getFiles()
                                        .get(0)
                        );

        // ---------------------------------------------------------
        // MODS DIRECTORY
        // ---------------------------------------------------------

        Path modsDirectory =
                instance.getDirectory()
                        .resolve("mods");

        Files.createDirectories(
                modsDirectory
        );

        // ---------------------------------------------------------
        // SAFE FILENAME
        // ---------------------------------------------------------

        String filename =
                sanitizeFilename(
                        file.getFilename()
                );

        Path destination =
                modsDirectory.resolve(
                        filename
                );

        // ---------------------------------------------------------
        // DOWNLOAD
        // ---------------------------------------------------------

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
                        HttpResponse.BodyHandlers.ofInputStream()
                );

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {

            response.body().close();

            throw new IOException(
                    "Modrinth file download failed: HTTP "
                            + response.statusCode()
            );
        }

        try (InputStream input =
                     response.body()) {

            Files.copy(
                    input,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }

        return destination;
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