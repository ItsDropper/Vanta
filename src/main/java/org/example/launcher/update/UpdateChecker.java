package org.example.launcher.update;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

public class UpdateChecker {

    private final UpdateService updateService;

    public UpdateChecker() {
        updateService = new UpdateService();
    }

    public CompletableFuture<UpdateInfo> check() {
        return CompletableFuture.supplyAsync(() -> {

            try {
                String currentVersion =
                        loadCurrentVersion();

                return updateService.checkForUpdate(
                        currentVersion
                );

            } catch (Exception e) {

                throw new RuntimeException(
                        "Failed to check for Vanta updates.",
                        e
                );
            }
        });
    }

    private String loadCurrentVersion()
            throws IOException {

        try (InputStream input =
                     getClass()
                             .getClassLoader()
                             .getResourceAsStream(
                                     "version.properties"
                             )) {

            if (input == null) {
                throw new IOException(
                        "Missing version.properties."
                );
            }

            Properties properties =
                    new Properties();

            properties.load(input);

            String version =
                    properties.getProperty(
                            "version"
                    );

            if (version == null
                    || version.isBlank()) {

                throw new IOException(
                        "Missing Vanta version."
                );
            }

            return version.trim();
        }
    }
}