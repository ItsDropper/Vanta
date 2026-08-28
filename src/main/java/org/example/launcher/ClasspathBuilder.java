package org.example.launcher;

import org.example.launcher.model.Library;
import org.example.launcher.model.VersionManifest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class ClasspathBuilder {

    public static List<Path> build(Path minecraftDirectory, String version) {

        LinkedHashSet<Path> classpath = new LinkedHashSet<>();

        VersionManifest fabric = VersionReader.readVersion(
                minecraftDirectory,
                version
        );

        VersionManifest vanilla = VersionReader.readVersion(
                minecraftDirectory,
                fabric.inheritsFrom
        );

        for (Library library : vanilla.libraries) {

            classpath.add(
                    LibraryResolver.resolve(minecraftDirectory, library)
            );

            Path nativeJar = LibraryResolver.resolveNative(
                    minecraftDirectory,
                    library
            );

            if (nativeJar != null) {
                classpath.add(nativeJar);
            }
        }

        for (Library library : fabric.libraries) {
            classpath.add(
                    LibraryResolver.resolve(minecraftDirectory, library)
            );
        }

        classpath.add(
                minecraftDirectory
                        .resolve("versions")
                        .resolve(vanilla.id)
                        .resolve(vanilla.id + ".jar")
        );

        return new ArrayList<>(classpath);
    }
}