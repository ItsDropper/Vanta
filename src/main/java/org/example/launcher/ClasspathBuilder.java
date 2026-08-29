package org.example.launcher;

import org.example.launcher.model.Instance;
import org.example.launcher.model.Library;
import org.example.launcher.model.VersionManifest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class ClasspathBuilder {

    public static List<Path> build(
            Instance instance
    ) {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        LinkedHashSet<Path> classpath =
                new LinkedHashSet<>();

        Path instanceDirectory =
                instance.getDirectory();

        Path vantaDirectory =
                MinecraftLocator.getVantaDirectory();

        VersionManifest manifest =
                VersionReader.readVersion(
                        instanceDirectory
                );

        if (manifest == null) {
            throw new IllegalStateException(
                    "Instance version metadata not found: "
                            + instance.getName()
            );
        }

        if (manifest.libraries != null) {

            for (Library library :
                    manifest.libraries) {

                Path jar =
                        LibraryResolver.resolve(
                                vantaDirectory,
                                library
                        );

                if (jar != null) {
                    classpath.add(jar);
                }
            }
        }

        Path gameJar =
                instanceDirectory
                        .resolve("minecraft")
                        .resolve("client.jar");

        if (!Files.exists(gameJar)) {
            throw new IllegalStateException(
                    "Minecraft client JAR not found: "
                            + gameJar
            );
        }

        classpath.add(gameJar);

        return new ArrayList<>(
                classpath
        );
    }

    public static List<Path> buildNativeJars(
            Instance instance
    ) {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        LinkedHashSet<Path> nativeJars =
                new LinkedHashSet<>();

        Path vantaDirectory =
                MinecraftLocator.getVantaDirectory();

        VersionManifest manifest =
                VersionReader.readVersion(
                        instance.getDirectory()
                );

        if (manifest == null) {
            throw new IllegalStateException(
                    "Instance version metadata not found: "
                            + instance.getName()
            );
        }

        if (manifest.libraries != null) {

            for (Library library :
                    manifest.libraries) {

                Path nativeJar =
                        LibraryResolver.resolveNative(
                                vantaDirectory,
                                library
                        );

                if (nativeJar != null) {
                    nativeJars.add(nativeJar);
                }
            }
        }

        return new ArrayList<>(
                nativeJars
        );
    }
}

