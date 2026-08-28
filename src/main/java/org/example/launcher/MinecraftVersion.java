package org.example.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MinecraftVersion {

    public static List<String> getInstalledVersions(Path minecraftDirectory) {
        List<String> versions = new ArrayList<>();

        Path versionsFolder = minecraftDirectory.resolve("versions");

        if (!Files.exists(versionsFolder)) {
            return versions;
        }

        try {
            Files.list(versionsFolder)
                    .filter(Files::isDirectory)
                    .forEach(folder -> versions.add(folder.getFileName().toString()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return versions;
    }

}