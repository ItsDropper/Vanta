package org.example.launcher;

import org.example.launcher.instance.InstanceManager;
import org.example.launcher.model.Instance;
import org.example.launcher.model.InstanceSettings;
import org.example.launcher.model.VersionManifest;

import java.nio.file.Path;
import java.util.List;

public class LaunchDataBuilder {

    public static LaunchData build(
            Instance instance
    ) {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        // =========================================================
        // INSTANCE
        // =========================================================

        Path instanceDirectory =
                instance.getDirectory();

        // =========================================================
        // VERSION MANIFEST
        // =========================================================

        VersionManifest manifest =
                VersionReader.readVersion(
                        instanceDirectory
                );

        if (manifest == null) {

            throw new IllegalStateException(
                    "Version metadata not found for instance: "
                            + instance.getName()
            );
        }

        // =========================================================
        // SETTINGS
        // =========================================================

        InstanceSettings settings;

        try {

            settings =
                    InstanceManager.loadSettings(
                            instance
                    );

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to load settings for instance: "
                            + instance.getName(),
                    e
            );
        }

        // =========================================================
        // CLASSPATH
        // =========================================================

        List<Path> classpath =
                ClasspathBuilder.build(
                        instance
                );

        List<Path> nativeJars =
                ClasspathBuilder.buildNativeJars(
                        instance
                );

        // =========================================================
        // GAME JAR
        // =========================================================

        Path gameJar =
                instanceDirectory
                        .resolve("minecraft")
                        .resolve("client.jar");

        // =========================================================
        // BUILD
        // =========================================================

        LaunchData data =
                new LaunchData();

        // ---------------------------------------------------------
        // MINECRAFT
        // ---------------------------------------------------------

        data.minecraftDirectory =
                instanceDirectory;

        data.gameJar =
                gameJar;

        data.nativesDirectory =
                instanceDirectory
                        .resolve("natives");

        data.mainClass =
                manifest.mainClass;

        data.version =
                instance.getMinecraftVersion();

        // ---------------------------------------------------------
        // ASSETS
        // ---------------------------------------------------------

        if (manifest.assetIndex == null
                || manifest.assetIndex.id == null
                || manifest.assetIndex.id.isBlank()) {

            throw new IllegalStateException(
                    "Asset index missing for Minecraft "
                            + instance.getMinecraftVersion()
            );
        }

        data.assetIndex =
                manifest.assetIndex.id;

        // ---------------------------------------------------------
        // CLASSPATH
        // ---------------------------------------------------------

        data.classpath =
                classpath;

        data.nativeJars =
                nativeJars;

        // ---------------------------------------------------------
        // JAVA VERSION
        // ---------------------------------------------------------

        data.javaVersion =
                manifest.javaVersion != null
                        ? manifest.javaVersion.majorVersion
                        : 21;

        // =========================================================
        // SETTINGS
        // =========================================================

        data.ramMb =
                settings.getRamMb();

        data.javaArguments =
                settings.getJavaArguments();

        data.gameArguments =
                settings.getGameArguments();

        data.width =
                settings.getWidth();

        data.height =
                settings.getHeight();

        data.fullscreen =
                settings.isFullscreen();

        data.javaPath =
                settings.getJavaPath();

        return data;
    }
}

