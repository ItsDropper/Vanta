package org.example.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class DownloadUtil {

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    private static final HttpClient HTTP =
            HttpClient.newHttpClient();

    // =============================================================
    // JSON
    // =============================================================

    public static JsonNode downloadJson(
            String url
    ) throws Exception {

        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(url)
                        )
                        .GET()
                        .build();

        HttpResponse<String> response =
                HTTP.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {

            throw new IOException(
                    "HTTP "
                            + response.statusCode()
                            + " while downloading "
                            + url
            );
        }

        return MAPPER.readTree(
                response.body()
        );
    }

    // =============================================================
    // FILE
    // =============================================================

    public static void downloadFile(
            String url,
            Path target
    ) throws Exception {

        System.out.println(
                "Downloading: "
                        + url
        );

        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(url)
                        )
                        .GET()
                        .build();

        HttpResponse<InputStream> response =
                HTTP.send(
                        request,
                        HttpResponse.BodyHandlers.ofInputStream()
                );

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {

            response.body().close();

            throw new IOException(
                    "HTTP "
                            + response.statusCode()
                            + " while downloading "
                            + url
            );
        }

        Files.createDirectories(
                target.getParent()
        );

        Path temporary =
                target.resolveSibling(
                        target.getFileName()
                                + ".download"
                );

        try (InputStream input =
                     response.body()) {

            Files.copy(
                    input,
                    temporary,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }

        Files.move(
                temporary,
                target,
                StandardCopyOption.REPLACE_EXISTING
        );
    }
}