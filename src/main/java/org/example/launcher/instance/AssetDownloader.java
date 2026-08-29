package org.example.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AssetDownloader {

    private static final int THREAD_COUNT = 8;

    private static final HttpClient HTTP =
            HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .build();

    public static void download(
            JsonNode assetObjects,
            Path objectsDirectory
    ) throws Exception {

        if (assetObjects == null
                || !assetObjects.isObject()) {

            throw new IllegalArgumentException(
                    "Invalid asset objects."
            );
        }

        Files.createDirectories(
                objectsDirectory
        );

        List<AssetTask> tasks =
                new ArrayList<>();

        Iterator<Map.Entry<String, JsonNode>> fields =
                assetObjects.fields();

        while (fields.hasNext()) {

            Map.Entry<String, JsonNode> entry =
                    fields.next();

            String assetPath =
                    entry.getKey();

            JsonNode object =
                    entry.getValue();

            JsonNode hashNode =
                    object.get("hash");

            if (hashNode == null) {
                continue;
            }

            String hash =
                    hashNode.asText();

            if (hash.length() < 2) {
                continue;
            }

            String prefix =
                    hash.substring(0, 2);

            Path target =
                    objectsDirectory
                            .resolve(prefix)
                            .resolve(hash);

            /*
             * If the file already exists and has the correct
             * SHA-1, there is nothing to download.
             */
            if (Files.exists(target)
                    && verifySha1(target, hash)) {

                continue;
            }

            tasks.add(
                    new AssetTask(
                            assetPath,
                            hash,
                            prefix,
                            target
                    )
            );
        }

        int total =
                tasks.size();

        if (total == 0) {

            System.out.println(
                    "All assets are already installed."
            );

            return;
        }

        System.out.println(
                "Downloading "
                        + total
                        + " missing assets using "
                        + THREAD_COUNT
                        + " workers..."
        );

        ExecutorService executor =
                Executors.newFixedThreadPool(
                        THREAD_COUNT
                );

        try {

            List<Future<?>> futures =
                    new ArrayList<>();

            for (int i = 0;
                 i < tasks.size();
                 i++) {

                AssetTask task =
                        tasks.get(i);

                int number =
                        i + 1;

                futures.add(
                        executor.submit(() -> {
                            try {
                                downloadAsset(
                                        task,
                                        number,
                                        total
                                );
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                );
            }

            for (Future<?> future : futures) {

                future.get();
            }

        } finally {

            executor.shutdown();
        }

        System.out.println(
                "Asset download complete."
        );
    }

    private static void downloadAsset(
            AssetTask task,
            int number,
            int total
    ) throws Exception {

        System.out.println(
                "Downloading asset "
                        + number
                        + "/"
                        + total
                        + ": "
                        + task.assetPath
        );

        Files.createDirectories(
                task.target.getParent()
        );

        String url =
                "https://resources.download.minecraft.net/"
                        + task.prefix
                        + "/"
                        + task.hash;

        Path temporary =
                task.target.resolveSibling(
                        task.hash + ".download"
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

            throw new IllegalStateException(
                    "HTTP "
                            + response.statusCode()
                            + " while downloading asset: "
                            + task.assetPath
            );
        }

        try (InputStream input =
                     response.body()) {

            Files.copy(
                    input,
                    temporary,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }

        /*
         * Never install a corrupted asset.
         */
        if (!verifySha1(
                temporary,
                task.hash
        )) {

            Files.deleteIfExists(
                    temporary
            );

            throw new IllegalStateException(
                    "SHA-1 mismatch for asset: "
                            + task.assetPath
            );
        }

        Files.move(
                temporary,
                task.target,
                StandardCopyOption.REPLACE_EXISTING
        );
    }

    private static boolean verifySha1(
            Path file,
            String expected
    ) throws Exception {

        MessageDigest digest =
                MessageDigest.getInstance(
                        "SHA-1"
                );

        try (InputStream input =
                     Files.newInputStream(file)) {

            byte[] buffer =
                    new byte[8192];

            int read;

            while ((read =
                    input.read(buffer)) != -1) {

                digest.update(
                        buffer,
                        0,
                        read
                );
            }
        }

        StringBuilder result =
                new StringBuilder();

        for (byte value :
                digest.digest()) {

            result.append(
                    String.format(
                            "%02x",
                            value
                    )
            );
        }

        return result
                .toString()
                .equalsIgnoreCase(expected);
    }

    private record AssetTask(
            String assetPath,
            String hash,
            String prefix,
            Path target
    ) {
    }

    public static void downloadFile(
            String url,
            Path target
    ) throws Exception {

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

            throw new IllegalStateException(
                    "HTTP "
                            + response.statusCode()
                            + " while downloading: "
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