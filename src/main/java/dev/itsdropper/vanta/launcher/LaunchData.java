package dev.itsdropper.vanta.launcher;

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

    public String versionType;

    public String minecraftArguments;

    public String loader;

    public String loaderVersion;

    public String neoForgeFmlVersion;

    public String neoFormVersion;

    public Path javaAgent;

    public Path minecraftMappings;

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

    public String serverHost;

    public int serverPort;
}

