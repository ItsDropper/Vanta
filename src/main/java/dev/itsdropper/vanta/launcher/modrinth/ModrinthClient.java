package dev.itsdropper.vanta.launcher.modrinth;

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
import java.util.ArrayList;

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
                        .followRedirects(
                                HttpClient.Redirect.NORMAL
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
                ModrinthContentType.MOD,
                null,
                null
        );
    }

    public ModrinthSearchResult search(
            String query,
            String loader,
            String minecraftVersion
    ) throws IOException, InterruptedException {

        return search(
                query,
                ModrinthContentType.MOD,
                loader,
                minecraftVersion
        );
    }

    public ModrinthSearchResult search(
            String query,
            ModrinthContentType contentType,
            String loader,
            String minecraftVersion
    ) throws IOException, InterruptedException {
        List<String> loaders = loader == null || loader.isBlank()
                ? List.of() : List.of(ModrinthLoaderIds.normalize(loader));
        List<String> versions = minecraftVersion == null || minecraftVersion.isBlank()
                ? List.of() : List.of(minecraftVersion);
        ModrinthSearchResult result = searchPage(
                query, contentType, loaders, versions, List.of(),
                "relevance", 10, 0
        );
        return filterLoaderVersionCompatibility(result, contentType, loader, minecraftVersion);
    }

    /**
     * Full Modrinth search endpoint. Each facet group is OR'd internally and
     * groups are AND'd by Modrinth. Search results retain the server's total_hits
     * so the UI can calculate real pagination rather than guessing from page size.
     */
    public ModrinthSearchResult searchPage(
            String query,
            ModrinthContentType contentType,
            List<String> loaders,
            List<String> versions,
            List<String> categories,
            String index,
            int limit,
            int offset
    ) throws IOException, InterruptedException {
        if (contentType == null) contentType = ModrinthContentType.MOD;
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Modrinth search limit must be between 1 and 100.");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Modrinth search offset cannot be negative.");
        }
        String normalizedIndex = index == null || index.isBlank() ? "relevance" : index;
        if (!List.of("relevance", "downloads", "follows", "newest", "updated").contains(normalizedIndex)) {
            throw new IllegalArgumentException("Unsupported Modrinth search index: " + normalizedIndex);
        }

        StringBuilder url = new StringBuilder(API_URL).append("/search?");
        if (query != null && !query.isBlank()) {
            url.append("query=").append(encode(query.trim())).append('&');
        }
        url.append("facets=").append(encode(buildFacets(
                contentType,
                loaders == null ? List.of() : loaders,
                versions == null ? List.of() : versions,
                categories == null ? List.of() : categories
        )));
        url.append("&index=").append(encode(normalizedIndex));
        url.append("&limit=").append(limit);
        url.append("&offset=").append(offset);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .header("User-Agent", "VantaLauncher/1.0")
                .build();
        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString()
        );
        checkResponse(response);
        return mapper.readValue(response.body(), ModrinthSearchResult.class);
    }

    private static final java.util.Map<String, List<ModrinthTag>> TAG_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public List<ModrinthTag> getGameVersionTags() throws IOException, InterruptedException {
        return getTags("game_version");
    }

    public List<ModrinthTag> getLoaderTags() throws IOException, InterruptedException {
        return getTags("loader");
    }

    public List<ModrinthTag> getCategoryTags() throws IOException, InterruptedException {
        return getTags("category");
    }

    private List<ModrinthTag> getTags(String tagType) throws IOException, InterruptedException {
        List<ModrinthTag> cached = TAG_CACHE.get(tagType);
        if (cached != null) return cached;
        synchronized (TAG_CACHE) {
            cached = TAG_CACHE.get(tagType);
            if (cached != null) return cached;
            URI uri = URI.create(API_URL + "/tag/" + tagType);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .header("User-Agent", "VantaLauncher/1.0")
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString()
            );
            checkResponse(response);
            List<ModrinthTag> tags = mapper.readValue(
                    response.body(),
                    mapper.getTypeFactory().constructCollectionType(List.class, ModrinthTag.class)
            );
            cached = List.copyOf(tags);
            TAG_CACHE.put(tagType, cached);
            return cached;
        }
    }

    // =============================================================
    // MOST DOWNLOADED
    // =============================================================

    public ModrinthSearchResult getMostDownloadedMods(
            String loader,
            String minecraftVersion
    ) throws IOException, InterruptedException {

        return getMostDownloaded(
                ModrinthContentType.MOD,
                loader,
                minecraftVersion
        );
    }

    public ModrinthSearchResult getMostDownloaded(
            ModrinthContentType contentType,
            String loader,
            String minecraftVersion
    ) throws IOException, InterruptedException {

        if (contentType == null) {
            contentType = ModrinthContentType.MOD;
        }

        String facets = buildFacets(
                contentType,
                loader == null || loader.isBlank() ? List.of() : List.of(loader),
                minecraftVersion == null || minecraftVersion.isBlank() ? List.of() : List.of(minecraftVersion),
                List.of()
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

        ModrinthSearchResult result = mapper.readValue(
                response.body(),
                ModrinthSearchResult.class
        );
        return filterLoaderVersionCompatibility(result, contentType, loader, minecraftVersion);
    }

    private ModrinthSearchResult filterLoaderVersionCompatibility(
            ModrinthSearchResult result,
            ModrinthContentType contentType,
            String loader,
            String minecraftVersion
    ) throws IOException, InterruptedException {
        if (result == null
                || contentType != ModrinthContentType.MOD
                || loader == null
                || loader.isBlank()
                || loader.equalsIgnoreCase("vanilla")
                || result.getHits() == null) {
            return result;
        }

        String loaderId = ModrinthLoaderIds.normalize(loader);
        List<ModrinthSearchHit> compatible = new java.util.ArrayList<>();
        for (ModrinthSearchHit hit : result.getHits()) {
            if (hit == null || hit.getProjectId() == null || hit.getProjectId().isBlank()) {
                continue;
            }

            boolean supported = getVersions(hit.getProjectId()).stream().anyMatch(version ->
                    version.getLoaders() != null
                            && version.getLoaders().stream()
                            .map(ModrinthLoaderIds::normalize)
                            .anyMatch(loaderId::equals)
                            && (minecraftVersion == null || minecraftVersion.isBlank()
                                || ModrinthLoaderIds.supportsGameVersion(
                                        version.getGameVersions(), minecraftVersion))
            );
            if (supported) {
                compatible.add(hit);
            }
        }

        result.setHits(compatible);
        result.setTotalHits(compatible.size());
        return result;
    }

    // =============================================================
    // FACETS
    // =============================================================

    private String buildFacets(
            ModrinthContentType contentType,
            List<String> loaders,
            List<String> versions,
            List<String> categories
    ) {
        List<List<String>> groups = new java.util.ArrayList<>();
        groups.add(List.of("project_type:" + contentType.getApiValue()));
        addFacetGroup(groups, "categories", loaders, true);
        addFacetGroup(groups, "versions", versions, false);
        addFacetGroup(groups, "categories", categories, false);

        StringBuilder json = new StringBuilder("[");
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            if (groupIndex > 0) json.append(',');
            json.append('[');
            List<String> group = groups.get(groupIndex);
            for (int valueIndex = 0; valueIndex < group.size(); valueIndex++) {
                if (valueIndex > 0) json.append(',');
                json.append('"').append(escapeFacetValue(group.get(valueIndex))).append('"');
            }
            json.append(']');
        }
        return json.append(']').toString();
    }

    private void addFacetGroup(
            List<List<String>> groups,
            String facet,
            List<String> values,
            boolean normalizeLoaderIds
    ) {
        if (values == null || values.isEmpty()) return;
        List<String> normalized = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> facet + ":" + (normalizeLoaderIds
                        ? ModrinthLoaderIds.normalize(value) : value.trim()))
                .distinct()
                .toList();
        if (!normalized.isEmpty()) groups.add(normalized);
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