package org.example.launcher;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class NativeExtractor {

    public static void extract(
            Path nativeJar,
            Path outputFolder
    ) throws Exception {

        if (nativeJar == null) {
            throw new IllegalArgumentException(
                    "Native JAR cannot be null."
            );
        }

        if (!Files.isRegularFile(nativeJar)) {
            throw new IllegalStateException(
                    "Native JAR does not exist: "
                            + nativeJar
            );
        }

        Files.createDirectories(
                outputFolder
        );

        try (InputStream input =
                     Files.newInputStream(nativeJar);
             ZipInputStream zip =
                     new ZipInputStream(input)) {

            ZipEntry entry;

            while ((entry = zip.getNextEntry()) != null) {

                try {

                    if (entry.isDirectory()) {
                        continue;
                    }

                    String name =
                            entry.getName();

                    if (name == null
                            || !name
                            .toLowerCase(
                                    java.util.Locale.ROOT
                            )
                            .endsWith(".dll")) {

                        continue;
                    }

                    /*
                     * Only extract the filename itself.
                     *
                     * Native libraries are loaded from one
                     * flat directory, so directory structure
                     * inside the JAR is not needed.
                     */
                    Path fileName =
                            Path.of(name)
                                    .getFileName();

                    if (fileName == null) {
                        continue;
                    }

                    String dllName =
                            fileName.toString();

                    /*
                     * Extra safety: make absolutely sure the
                     * resulting filename cannot contain path
                     * separators.
                     */
                    if (dllName.contains("/")
                            || dllName.contains("\\")) {

                        throw new IllegalStateException(
                                "Unsafe native library name: "
                                        + name
                        );
                    }

                    Path output =
                            outputFolder.resolve(
                                    dllName
                            );

                    Files.copy(
                            zip,
                            output,
                            StandardCopyOption.REPLACE_EXISTING
                    );

                } finally {

                    zip.closeEntry();
                }
            }
        }
    }

    public static void extractAll(
            List<Path> jars,
            Path outputFolder
    ) throws Exception {

        if (jars == null
                || jars.isEmpty()) {

            return;
        }

        Files.createDirectories(
                outputFolder
        );

        for (Path jar : jars) {

            if (jar == null) {
                continue;
            }

            extract(
                    jar,
                    outputFolder
            );
        }
    }
}