package dev.itsdropper.vanta.launcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.itsdropper.vanta.launcher.instance.InstanceManager;
import dev.itsdropper.vanta.launcher.instance.NilLoaderInstaller;
import dev.itsdropper.vanta.launcher.instance.VantaLoaderInstaller;
import dev.itsdropper.vanta.launcher.loader.LoaderStackManager;
import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.model.InstanceSettings;
import dev.itsdropper.vanta.launcher.model.VersionManifest;
import dev.itsdropper.vanta.launcher.service.ServerTarget;

import java.nio.file.Path;
import java.util.List;

public class LaunchDataBuilder {

    public static LaunchData build(
            Instance instance
    ) {
        return build(instance, null);
    }

    public static LaunchData build(
            Instance instance,
            ServerTarget server
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

        // A saved stack is validated before reading the already-merged
        // version metadata. ClasspathBuilder and MinecraftLauncher then
        // consume that merged metadata through the normal launch path.
        LoaderStackManager.validateForLaunch(instance);

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

        data.versionType = manifest.type;
        data.minecraftArguments = manifest.minecraftArguments;

        data.loader =
                instance.getLoader();

        data.loaderVersion =
                instance.getLoaderVersion();

        if ("NeoForge".equalsIgnoreCase(instance.getLoader())) {
            if (data.loaderVersion == null || data.loaderVersion.isBlank()) {
                throw new IllegalStateException("NeoForge instance has no NeoForge version.");
            }
            Path versionJson = instanceDirectory.resolve("minecraft").resolve("version.json");
            try {
                JsonNode metadata = new ObjectMapper().readTree(versionJson.toFile());
                JsonNode gameArguments = metadata.path("arguments").path("game");
                data.neoForgeFmlVersion = argumentValue(gameArguments, "--fml.fmlVersion");
                data.neoFormVersion = argumentValue(gameArguments, "--fml.neoFormVersion");
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Could not read NeoForge launch metadata.", e);
            }
            if (data.neoForgeFmlVersion == null || data.neoForgeFmlVersion.isBlank()
                    || data.neoFormVersion == null || data.neoFormVersion.isBlank()) {
                throw new IllegalStateException(
                        "NeoForge launch metadata is missing its FML or NeoForm version. Repair this instance before launching.");
            }
        }

        if ("NilLoader".equalsIgnoreCase(instance.getLoader())) {
            Path agent = NilLoaderInstaller.agentPath(instance);
            if (!NilLoaderInstaller.hasAgentEntrypoint(agent)) {
                throw new IllegalStateException(
                        "NilLoader agent is missing or invalid. Repair this instance before launching.");
            }
            data.javaAgent = agent;
        }

        if ("VantaLoader".equalsIgnoreCase(instance.getLoader())) {
            data.minecraftMappings = instanceDirectory.resolve("minecraft").resolve("client_mappings.txt");
            if (!java.nio.file.Files.isRegularFile(data.minecraftMappings)) {
                throw new IllegalStateException("Official Minecraft client mappings are missing. Repair this VantaLoader instance before launching.");
            }
            Path agent = VantaLoaderInstaller.agentPath(instance);
            if (!VantaLoaderInstaller.isValidAgentJar(agent)) {
                throw new IllegalStateException(
                        "VantaLoader agent is missing or invalid. Repair this instance before launching.");
            }
            data.javaAgent = agent;
        }

        // ---------------------------------------------------------
        // ASSETS
        // ---------------------------------------------------------

        if (manifest.assetIndex != null
                && manifest.assetIndex.id != null
                && !manifest.assetIndex.id.isBlank()) {
            data.assetIndex = manifest.assetIndex.id;
        }

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

        if (server != null) {
            data.serverHost = server.host();
            data.serverPort = server.port();
        }

        return data;
    }

    private static String argumentValue(JsonNode arguments, String key) {
        if (arguments == null || !arguments.isArray()) {
            return null;
        }
        for (int i = 0; i + 1 < arguments.size(); i++) {
            JsonNode current = arguments.get(i);
            JsonNode next = arguments.get(i + 1);
            if (current != null && current.isTextual()
                    && key.equals(current.asText())
                    && next != null && next.isTextual()) {
                return next.asText();
            }
        }
        return null;
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

