package org.example.launcher.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class UpdateService {

    private static final String API_BASE =
            "https://api.github.com/repos/ItsDropper/Vanta/releases";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public UpdateService() {
        httpClient = HttpClient.newHttpClient();
        objectMapper = new ObjectMapper();
    }

    public UpdateInfo checkForUpdate(
            String currentVersion
    ) throws IOException, InterruptedException {

        JsonNode release =
                requestJson(API_BASE + "/latest");

        return parseRelease(
                release,
                currentVersion
        );
    }

    public UpdateInfo getVersion(
            String currentVersion,
            String targetVersion
    ) throws IOException, InterruptedException {

        String normalized =
                targetVersion
                        .trim()
                        .replaceFirst("^[vV]", "");

        if (normalized.isBlank()) {
            throw new IllegalArgumentException(
                    "Vanta version cannot be blank."
            );
        }

        JsonNode release =
                requestJson(
                        API_BASE
                                + "/tags/v"
                                + normalized
                );

        return parseRelease(
                release,
                currentVersion
        );
    }

    public List<UpdateInfo> getAvailableVersions(
            String currentVersion
    ) throws IOException, InterruptedException {

        JsonNode releases =
                requestJson(
                        API_BASE
                                + "?per_page=100"
                );

        if (!releases.isArray()) {
            throw new IOException(
                    "GitHub did not return a release list."
            );
        }

        List<UpdateInfo> versions =
                new ArrayList<>();

        for (JsonNode release : releases) {

            if (release.path("draft").asBoolean(false)
                    || release.path("prerelease").asBoolean(false)) {
                continue;
            }

            try {
                UpdateInfo info =
                        parseRelease(
                                release,
                                currentVersion
                        );

                if (info.getLatestVersion() != null
                        && !info.getLatestVersion().isBlank()
                        && info.isDowngrade()) {
                    versions.add(info);
                }

            } catch (IOException ignored) {
                // A release without a complete Vanta package is not rollback-capable.
            }
        }

        versions.sort((a, b) ->
                UpdateInfo.compareVersions(
                        b.getLatestVersion(),
                        a.getLatestVersion()
                )
        );

        return versions;
    }

    private JsonNode requestJson(
            String url
    ) throws IOException, InterruptedException {

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header(
                                "Accept",
                                "application/vnd.github+json"
                        )
                        .header(
                                "User-Agent",
                                "Vanta-Launcher"
                        )
                        .GET()
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (response.statusCode() != 200) {
            throw new IOException(
                    "GitHub returned HTTP "
                            + response.statusCode()
            );
        }

        return objectMapper.readTree(
                response.body()
        );
    }

    private UpdateInfo parseRelease(
            JsonNode release,
            String currentVersion
    ) throws IOException {

        String latestVersion =
                release.path("tag_name").asText();

        String releaseNotes =
                release.path("body").asText("");

        if (latestVersion.isBlank()) {
            throw new IOException(
                    "GitHub release did not contain a version."
            );
        }

        String[] assets =
                findUpdateAssets(release);

        return new UpdateInfo(
                currentVersion,
                latestVersion,
                assets[0],
                assets[1],
                releaseNotes
        );
    }

    private String[] findUpdateAssets(
            JsonNode release
    ) throws IOException {

        JsonNode assets =
                release.path("assets");

        if (!assets.isArray()) {
            throw new IOException(
                    "GitHub release did not contain any assets."
            );
        }

        String downloadUrl = null;
        String checksumUrl = null;

        for (JsonNode asset : assets) {

            String name =
                    asset.path("name").asText();

            String assetUrl =
                    asset
                            .path("browser_download_url")
                            .asText();

            if (name.matches(
                    "(?i)Vanta-\\d+(?:\\.\\d+){1,2}\\.zip"
            )) {
                downloadUrl = assetUrl;
            }

            if (name.matches(
                    "(?i)Vanta-\\d+(?:\\.\\d+){1,2}\\.zip\\.sha256"
            )) {
                checksumUrl = assetUrl;
            }
        }

        if (downloadUrl == null
                || downloadUrl.isBlank()) {
            throw new IOException(
                    "No Vanta application ZIP was found "
                            + "in the selected GitHub release."
            );
        }

        if (checksumUrl == null
                || checksumUrl.isBlank()) {
            throw new IOException(
                    "No SHA-256 checksum was found "
                            + "for the selected Vanta release."
            );
        }

        return new String[]{
                downloadUrl,
                checksumUrl
        };
    }
}
