package org.example.launcher.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class UpdateService {

    private static final String RELEASES_API =
            "https://api.github.com/repos/ItsDropper/Vanta/releases/latest";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public UpdateService() {
        httpClient = HttpClient.newHttpClient();
        objectMapper = new ObjectMapper();
    }

    public UpdateInfo checkForUpdate(
            String currentVersion
    ) throws IOException, InterruptedException {

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(RELEASES_API))
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

        JsonNode release =
                objectMapper.readTree(
                        response.body()
                );

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
                            + "in the latest GitHub release."
            );
        }

        if (checksumUrl == null
                || checksumUrl.isBlank()) {

            throw new IOException(
                    "No SHA-256 checksum was found "
                            + "for the Vanta update."
            );
        }

        return new String[]{
                downloadUrl,
                checksumUrl
        };
    }
}