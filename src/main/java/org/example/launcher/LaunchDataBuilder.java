package org.example.launcher;

import org.example.launcher.model.VersionManifest;

import java.nio.file.Path;

public class LaunchDataBuilder {

    public static LaunchData build(Path minecraftDirectory, String version) {

        VersionManifest fabric = VersionReader.readVersion(
                minecraftDirectory,
                version
        );

        VersionManifest vanilla = VersionReader.readVersion(
                minecraftDirectory,
                fabric.inheritsFrom
        );

        LaunchData data = new LaunchData();

        data.mainClass = fabric.mainClass;

        data.jarPath = minecraftDirectory
                .resolve("versions")
                .resolve(vanilla.id)
                .resolve(vanilla.id + ".jar");

        return data;
    }

}