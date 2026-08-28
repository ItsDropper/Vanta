package org.example.launcher;

import org.example.launcher.model.Library;

import java.nio.file.Files;
import java.nio.file.Path;

public class LibraryResolver {

    public static Path resolve(Path minecraftDirectory, Library library) {

        String[] parts = library.name.split(":");

        String group = parts[0].replace(".", "/");
        String artifact = parts[1];
        String version = parts[2];

        return minecraftDirectory
                .resolve("libraries")
                .resolve(group)
                .resolve(artifact)
                .resolve(version)
                .resolve(artifact + "-" + version + ".jar");
    }


    public static Path resolveNative(Path minecraftDirectory, Library library) {

        String[] parts = library.name.split(":");

        if (parts.length < 4) {
            return null;
        }

        String group = parts[0].replace(".", "/");
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts[3];

        Path nativeJar = minecraftDirectory
                .resolve("libraries")
                .resolve(group)
                .resolve(artifact)
                .resolve(version)
                .resolve(artifact + "-" + version + "-" + classifier + ".jar");

        if (Files.exists(nativeJar)) {
            return nativeJar;
        }

        return null;
    }
}