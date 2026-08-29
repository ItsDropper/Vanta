package org.example.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;

import org.example.launcher.MinecraftLocator;
import org.example.launcher.model.Instance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class NativeInstaller {

    public static Path extract(
            Instance instance,
            JsonNode metadata
    ) throws Exception {

        Path nativesDirectory =
                instance
                        .getDirectory()
                        .resolve("natives");

        Files.createDirectories(
                nativesDirectory
        );

        JsonNode libraries =
                metadata.get("libraries");

        if (libraries == null
                || !libraries.isArray()) {

            return nativesDirectory;
        }

        Path librariesDirectory =
                MinecraftLocator
                        .getLibrariesDirectory();

        for (JsonNode library : libraries) {

            JsonNode downloads =
                    library.get("downloads");

            if (downloads == null) {
                continue;
            }

            JsonNode classifiers =
                    downloads.get("classifiers");

            if (classifiers == null) {
                continue;
            }

            JsonNode windows =
                    classifiers.get(
                            "natives-windows"
                    );

            if (windows == null) {
                continue;
            }

            JsonNode pathNode =
                    windows.get("path");

            if (pathNode == null) {
                continue;
            }

            String path =
                    pathNode.asText();

            Path nativeJar =
                    librariesDirectory.resolve(
                            path
                    );

            if (!Files.exists(nativeJar)) {
                continue;
            }

            System.out.println(
                    "Extracting natives: "
                            + nativeJar.getFileName()
            );

            try (ZipInputStream zip =
                         new ZipInputStream(
                                 Files.newInputStream(
                                         nativeJar
                                 )
                         )) {

                ZipEntry entry;

                while ((entry =
                        zip.getNextEntry()) != null) {

                    if (entry.isDirectory()) {
                        continue;
                    }

                    String fileName =
                            entry.getName();

                    if (!fileName.endsWith(".dll")) {
                        continue;
                    }

                    Path target =
                            nativesDirectory.resolve(
                                    Path.of(
                                            fileName
                                    ).getFileName()
                            );

                    Files.copy(
                            zip,
                            target,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }
            }
        }

        return nativesDirectory;
    }
}