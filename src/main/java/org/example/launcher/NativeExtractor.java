package org.example.launcher;

import java.io.InputStream;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.List;

public class NativeExtractor {

    public static void extract(Path nativeJar, Path outputFolder) throws Exception {

        Files.createDirectories(outputFolder);

        try (InputStream is = Files.newInputStream(nativeJar);
             ZipInputStream zip = new ZipInputStream(is)) {

            ZipEntry entry;

            while ((entry = zip.getNextEntry()) != null) {

                if (entry.getName().endsWith(".dll")) {

                    Path output = outputFolder.resolve(
                            Paths.get(entry.getName()).getFileName()
                    );

                    Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);

                }
            }
        }
    }

    public static void extractAll(List<Path> jars, Path outputFolder) throws Exception {

        Files.createDirectories(outputFolder);

        for (Path jar : jars) {

            if (jar.toString().contains("natives-windows")) {
                extract(jar, outputFolder);
            }
        }
    }
}