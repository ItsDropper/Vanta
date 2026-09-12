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
                resolveJavaVersion(
                        manifest,
                        data.version
                );

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

    // =============================================================
    // JAVA VERSION
    // =============================================================

    private static int resolveJavaVersion(
            VersionManifest manifest,
            String minecraftVersion
    ) {

        /*
         * Modern Minecraft metadata explicitly tells us which
         * Java version is required. Always prefer that value.
         */
        if (manifest.javaVersion != null
                && manifest.javaVersion.majorVersion > 0) {

            return manifest.javaVersion.majorVersion;
        }

        /*
         * Older Minecraft metadata does not contain javaVersion.
         *
         * Fall back based on the Minecraft version instead of
         * blindly using Java 21.
         */

        if (minecraftVersion == null
                || minecraftVersion.isBlank()) {

            return 8;
        }

        String version =
                minecraftVersion.trim();

        /*
         * Snapshot / Alpha / Beta / Classic and other very old
         * versions are safest on Java 8.
         */
        if (version.startsWith("a")
                || version.startsWith("b")
                || version.startsWith("c")
                || version.startsWith("rd-")
                || version.startsWith("inf-")) {

            return 8;
        }

        /*
         * Versions before 1.17 use Java 8.
         */
        if (version.startsWith("1.")) {

            String[] parts =
                    version.substring(2).split("\\.");

            try {

                int minor =
                        Integer.parseInt(parts[0]);

                if (minor <= 16) {
                    return 8;
                }

                if (minor == 17) {
                    return 16;
                }

                /*
                 * 1.18+ normally uses Java 17 unless its metadata
                 * explicitly specifies something else.
                 */
                if (minor >= 18) {
                    return 17;
                }

            } catch (NumberFormatException ignored) {
                // Fall through to the safe legacy default.
            }
        }

        /*
         * Unknown metadata without a Java requirement.
         *
         * Java 8 is preferable to assuming a modern runtime for
         * an unknown legacy version.
         */
        return 8;
    }
}

