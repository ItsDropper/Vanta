package org.example.launcher;

import java.nio.file.Path;
import java.util.List;

public class LaunchData {

    // =============================================================
    // MINECRAFT
    // =============================================================

    public Path minecraftDirectory;

    public Path gameJar;

    public Path nativesDirectory;

    public String mainClass;

    public String version;

    public String assetIndex;

    public List<Path> classpath;

    public List<Path> nativeJars;

    public int javaVersion;

    // =============================================================
    // INSTANCE SETTINGS
    // =============================================================

    public int ramMb;

    public String javaArguments;

    public String gameArguments;

    public int width;

    public int height;

    public boolean fullscreen;

    public String javaPath;
}

