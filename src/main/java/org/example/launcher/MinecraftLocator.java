package org.example.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MinecraftLocator {

    public static Path getMinecraftDirectory() {
        String appData = System.getenv("APPDATA");

        if (appData == null) {
            return null;
        }

        Path minecraft = Paths.get(appData, ".minecraft");

        if (Files.exists(minecraft)) {
            return minecraft;
        }

        return null;
    }
}