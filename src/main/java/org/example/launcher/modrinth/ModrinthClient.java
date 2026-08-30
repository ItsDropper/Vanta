package org.example.launcher.modrinth;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class ModrinthClient {

    private static final String API_URL =
            "https://api.modrinth.com/v2";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public ModrinthClient() {

        httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(10)
                        )
                        .build();

        mapper =
                new ObjectMapper();
    }

    // =============================================================
    // SEARCH
    // =============================================================

    public ModrinthSearchResult search(
            String query
    ) throws IOException, InterruptedException {

        return search(
                query,
                null,
                null
        );
    }

    public ModrinthSearchResult search(
            String query,
            String loader,
            String minecraftVersion
    ) throws IOException, InterruptedException {

        if (query == null || query.isBlank()) {

            throw new IllegalArgumentException(
                    "Search query cannot be empty."
            );
        }

        String encodedQuery =
                encode(query);

        String facets =
                buildFacets(
                        loader,
                        minecraftVersion
                );

        URI uri =
                URI.create(
                        API_URL
                                + "/search?query="
                                + encodedQuery
                                + "&facets="
                                + encode(facets)
                );

        HttpRequest request =
                HttpRequest.newBuilder(uri)
                        .timeout(
                                Duration.ofSeconds(20)
                        )
                        .GET()
                        .header(
                                "User-Agent",
                                "VantaLauncher/1.0"
                        )
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        checkResponse(response);

        return mapper.readValue(
                response.body(),
                ModrinthSearchResult.class
        );
    }

    // =============================================================
    // MOST DOWNLOADED
    // =============================================================

    public ModrinthSearchResult getMostDownloadedMods(
            String loader,
            String minecraftVersion
    ) throws IOException, InterruptedException {

        String facets =
                buildFacets(
                        loader,
                        minecraftVersion
                );

        URI uri =
                URI.create(
                        API_URL
                                + "/search?facets="
                                + encode(facets)
                                + "&index=downloads"
                                + "&limit=10"
                );

        HttpRequest request =
                HttpRequest.newBuilder(uri)
                        .timeout(
                                Duration.ofSeconds(20)
                        )
                        .GET()
                        .header(
                                "User-Agent",
                                "VantaLauncher/1.0"
                        )
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        checkResponse(response);

        return mapper.readValue(
                response.body(),
                ModrinthSearchResult.class
        );
    }

    // =============================================================
    // FACETS
    // =============================================================

    private String buildFacets(
            String loader,
            String minecraftVersion
    ) {

        StringBuilder facets =
                new StringBuilder(
                        "[[\"project_type:mod\"]"
                );

        if (loader != null
                && !loader.isBlank()) {

            facets.append(
                    ",[\"categories:"
            );

            facets.append(
                    escapeFacetValue(loader)
            );

            facets.append(
                    "\"]"
            );
        }

        if (minecraftVersion != null
                && !minecraftVersion.isBlank()) {

            facets.append(
                    ",[\"versions:"
            );

            facets.append(
                    escapeFacetValue(
                            minecraftVersion
                    )
            );

            facets.append(
                    "\"]"
            );
        }

        facets.append(
                "]"
        );

        return facets.toString();
    }

    private String escapeFacetValue(
            String value
    ) {

        return value
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "\"",
                        "\\\""
                );
    }

    // =============================================================
    // PROJECT
    // =============================================================

    public ModrinthProject getProject(
            String projectId
    ) throws IOException, InterruptedException {

        if (projectId == null
                || projectId.isBlank()) {

            throw new IllegalArgumentException(
                    "Project ID cannot be empty."
            );
        }

        URI uri =
                URI.create(
                        API_URL
                                + "/project/"
                                + encode(projectId)
                );

        HttpRequest request =
                HttpRequest.newBuilder(uri)
                        .timeout(
                                Duration.ofSeconds(20)
                        )
                        .GET()
                        .header(
                                "User-Agent",
                                "VantaLauncher/1.0"
                        )
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        checkResponse(response);

        return mapper.readValue(
                response.body(),
                ModrinthProject.class
        );
    }

    // =============================================================
    // VERSIONS
    // =============================================================

    public List<ModrinthVersion> getVersions(
            String idOrSlug
    ) throws IOException, InterruptedException {

        if (idOrSlug == null
                || idOrSlug.isBlank()) {

            throw new IllegalArgumentException(
                    "Project ID cannot be empty."
            );
        }

        URI uri =
                URI.create(
                        API_URL
                                + "/project/"
                                + encode(idOrSlug)
                                + "/version"
                                + "?include_changelog=false"
                );

        HttpRequest request =
                HttpRequest.newBuilder(uri)
                        .timeout(
                                Duration.ofSeconds(20)
                        )
                        .GET()
                        .header(
                                "User-Agent",
                                "VantaLauncher/1.0"
                        )
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        checkResponse(response);

        return mapper.readValue(
                response.body(),
                mapper.getTypeFactory()
                        .constructCollectionType(
                                List.class,
                                ModrinthVersion.class
                        )
        );
    }

    // =============================================================
    // TEAM MEMBERS
    // =============================================================

    public List<ModrinthTeamMember> getTeamMembers(
            String teamId
    ) throws IOException, InterruptedException {

        URI uri =
                URI.create(
                        API_URL
                                + "/team/"
                                + encode(teamId)
                                + "/members"
                );

        HttpRequest request =
                HttpRequest.newBuilder(uri)
                        .timeout(
                                Duration.ofSeconds(20)
                        )
                        .GET()
                        .header(
                                "User-Agent",
                                "VantaLauncher/1.0"
                        )
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        checkResponse(response);

        return mapper.readValue(
                response.body(),
                mapper.getTypeFactory()
                        .constructCollectionType(
                                List.class,
                                ModrinthTeamMember.class
                        )
        );
    }

    // =============================================================
    // PROJECT TEAM MEMBERS
    // =============================================================

    public List<ModrinthTeamMember> getProjectTeamMembers(
            String projectId
    ) throws IOException, InterruptedException {

        if (projectId == null
                || projectId.isBlank()) {

            throw new IllegalArgumentException(
                    "Project ID cannot be empty."
            );
        }

        URI uri =
                URI.create(
                        API_URL
                                + "/project/"
                                + encode(projectId)
                                + "/members"
                );

        HttpRequest request =
                HttpRequest.newBuilder(uri)
                        .timeout(
                                Duration.ofSeconds(20)
                        )
                        .GET()
                        .header(
                                "User-Agent",
                                "VantaLauncher/1.0"
                        )
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        checkResponse(response);

        return mapper.readValue(
                response.body(),
                mapper.getTypeFactory()
                        .constructCollectionType(
                                List.class,
                                ModrinthTeamMember.class
                        )
        );
    }

    // =============================================================
    // HTTP
    // =============================================================

    private void checkResponse(
            HttpResponse<String> response
    ) throws IOException {

        if (response.statusCode() >= 200
                && response.statusCode() < 300) {

            return;
        }

        throw new IOException(
                "Modrinth API returned HTTP "
                        + response.statusCode()
                        + ": "
                        + response.body()
        );
    }

    // =============================================================
    // ENCODE
    // =============================================================

    private String encode(
            String value
    ) {

        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }
}