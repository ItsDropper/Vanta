package dev.itsdropper.vanta.launcher.modrinth;

import dev.itsdropper.vanta.json.ObjectMapper;

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
        List<String> loaders = loaderFacetForSearch(loader);
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
        List<String> requestedLoaderIds = loaders == null ? List.of() : loaders.stream()
                .filter(loader -> loader != null && !loader.isBlank())
                .map(ModrinthLoaderIds::normalize)
                .filter(loader -> !loader.equals("minecraft"))
                .distinct()
                .toList();
        boolean localLoaderFilter = contentType == ModrinthContentType.MOD
                && requestedLoaderIds.stream().anyMatch(loader ->
                        !ModrinthLoaderIds.supportsSearchFacet(loader));
        List<String> apiLoaderFacets = localLoaderFilter ? List.of()
                : requestedLoaderIds;
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
                apiLoaderFacets,
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
        ModrinthSearchResult result = mapper.readValue(response.body(), ModrinthSearchResult.class);
        if (localLoaderFilter) {
            return filterLoaderVersionCompatibility(
                    result, contentType, requestedLoaderIds, versions == null ? List.of() : versions);
        }
        return result;
    }

    static List<String> loaderFacetForSearch(String loader) {
        if (loader == null || loader.isBlank()
                || !ModrinthLoaderIds.supportsSearchFacet(loader)) {
            return List.of();
        }
        return List.of(ModrinthLoaderIds.normalize(loader));
    }

    private static final long VERSION_FILTER_CACHE_TTL_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final java.util.Map<String, VersionFilterCacheEntry> VERSION_FILTER_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private record VersionFilterCacheEntry(List<ModrinthVersion> versions, long fetchedAtMillis) {}

    private List<ModrinthVersion> getVersionsForCompatibility(String projectId)
            throws IOException, InterruptedException {
        long now = System.currentTimeMillis();
        VersionFilterCacheEntry cached = VERSION_FILTER_CACHE.get(projectId);
        if (cached != null && now - cached.fetchedAtMillis() < VERSION_FILTER_CACHE_TTL_MILLIS) {
            return cached.versions();
        }

        List<ModrinthVersion> versions = getVersions(projectId);
        List<ModrinthVersion> immutable = versions == null ? List.of() : List.copyOf(versions);
        VERSION_FILTER_CACHE.put(projectId, new VersionFilterCacheEntry(immutable, System.currentTimeMillis()));
        return immutable;
    }

    private static final long TAG_CACHE_TTL_MILLIS = java.time.Duration.ofHours(6).toMillis();
    private static final java.util.Map<String, TagCacheEntry> TAG_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private record TagCacheEntry(List<ModrinthTag> tags, long fetchedAtMillis) {}

    public List<ModrinthTag> getGameVersionTags() throws IOException, InterruptedException {
        return getTags("game_version");
    }

    public List<ModrinthTag> getLoaderTags() throws IOException, InterruptedException {
        return getTags("loader");
    }

    public List<ModrinthTag> getCategoryTags() throws IOException, InterruptedException {
        return getTags("category");
    }

    public List<ModrinthTag> getLoaderTags(ModrinthContentType contentType)
            throws IOException, InterruptedException {
        return getTags("loader").stream()
                .filter(tag -> supportsContentType(tag, contentType))
                .toList();
    }

    public List<ModrinthTag> getCategoryTags(ModrinthContentType contentType)
            throws IOException, InterruptedException {
        return getTags("category").stream()
                .filter(tag -> supportsContentType(tag, contentType))
                .toList();
    }

    private boolean supportsContentType(ModrinthTag tag, ModrinthContentType contentType) {
        if (tag == null || contentType == null) return false;
        List<String> supported = tag.getSupportedProjectTypes();
        if (!supported.isEmpty()) return supported.contains(contentType.getApiValue());
        return tag.getProjectType() == null
                || tag.getProjectType().isBlank()
                || tag.getProjectType().equals(contentType.getApiValue());
    }

    private List<ModrinthTag> getTags(String tagType) throws IOException, InterruptedException {
        TagCacheEntry cached = TAG_CACHE.get(tagType);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.fetchedAtMillis() < TAG_CACHE_TTL_MILLIS) {
            return cached.tags();
        }
        synchronized (TAG_CACHE) {
            cached = TAG_CACHE.get(tagType);
            now = System.currentTimeMillis();
            if (cached != null && now - cached.fetchedAtMillis() < TAG_CACHE_TTL_MILLIS) {
                return cached.tags();
            }
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
            List<ModrinthTag> immutableTags = tags == null ? List.of() : List.copyOf(tags);
            TAG_CACHE.put(tagType, new TagCacheEntry(immutableTags, System.currentTimeMillis()));
            return immutableTags;
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
                loaderFacetForSearch(loader),
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
        if (loader == null || loader.isBlank()
                || ModrinthLoaderIds.normalize(loader).equals("minecraft")
                || ModrinthLoaderIds.supportsSearchFacet(loader)) {
            return result;
        }
        return filterLoaderVersionCompatibility(
                result, contentType, List.of(ModrinthLoaderIds.normalize(loader)),
                minecraftVersion == null || minecraftVersion.isBlank()
                        ? List.of() : List.of(minecraftVersion));
    }

    private ModrinthSearchResult filterLoaderVersionCompatibility(
            ModrinthSearchResult result,
            ModrinthContentType contentType,
            List<String> loaderIds,
            List<String> minecraftVersions
    ) throws IOException, InterruptedException {
        if (result == null || contentType != ModrinthContentType.MOD
                || result.getHits() == null) {
            return result;
        }

        List<String> requestedLoaders = loaderIds == null ? List.of() : loaderIds.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(ModrinthLoaderIds::normalize)
                .distinct()
                .toList();
        List<String> requestedVersions = minecraftVersions == null ? List.of() : minecraftVersions.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
        if (requestedLoaders.isEmpty() && requestedVersions.isEmpty()) return result;

        List<ModrinthSearchHit> compatible = new java.util.ArrayList<>();
        for (ModrinthSearchHit hit : result.getHits()) {
            if (hit == null || hit.getProjectId() == null || hit.getProjectId().isBlank()) continue;

            boolean supported = getVersionsForCompatibility(hit.getProjectId()).stream().anyMatch(version -> {
                boolean loaderMatches = requestedLoaders.isEmpty()
                        || (version.getLoaders() != null && version.getLoaders().stream()
                        .map(ModrinthLoaderIds::normalize).anyMatch(requestedLoaders::contains));
                boolean gameVersionMatches = requestedVersions.isEmpty()
                        || requestedVersions.stream().anyMatch(requested ->
                        ModrinthLoaderIds.supportsGameVersion(version.getGameVersions(), requested));
                return loaderMatches && gameVersionMatches;
            });
            if (supported) compatible.add(hit);
        }

        result.setHits(compatible);
        // The API's total is the unfiltered count. The local fallback only knows
        // the filtered count for this page, so expose that count rather than
        // advertising pagination that would lead to empty result pages.
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