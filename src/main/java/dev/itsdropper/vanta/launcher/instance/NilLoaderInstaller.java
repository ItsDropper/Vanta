package dev.itsdropper.vanta.launcher.instance;

import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * NilLoader is a JVM agent, not a Minecraft launcher-profile overlay.
 * Keep its artifact instance-local and attach it with -javaagent at launch.
 */
public final class NilLoaderInstaller {
    public static final String VERSION = "1.4.1";
    private static final String MAVEN_BASE = "https://repo.sleeping.town/";
    private static final String ARTIFACT_PATH =
            "com/unascribed/nilloader/" + VERSION + "/nilloader-" + VERSION + ".jar";

    private NilLoaderInstaller() {
    }

    public static String findLatestLoaderVersion(String minecraftVersion) {
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            throw new IllegalArgumentException("Minecraft version is required.");
        }
        return VERSION;
    }

    public static Path agentPath(Instance instance) {
        String version = instance == null ? null : instance.getLoaderVersion();
        return agentPath(instance, version == null || version.isBlank() ? VERSION : version);
    }

    public static Path agentPath(Instance instance, String version) {
        if (instance == null || instance.getDirectory() == null) {
            throw new IllegalArgumentException("A valid NilLoader instance is required.");
        }
        String safeVersion = requireVersion(version);
        return instance.getDirectory().resolve("libraries")
                .resolve("com").resolve("unascribed").resolve("nilloader")
                .resolve(safeVersion).resolve("nilloader-" + safeVersion + ".jar")
                .toAbsolutePath().normalize();
    }

    public static void install(Instance instance) throws Exception {
        String version = instance == null ? null : instance.getLoaderVersion();
        install(instance, version == null || version.isBlank() ? VERSION : version);
    }

    public static void install(Instance instance, String version) throws Exception {
        String safeVersion = requireVersion(version);
        Path target = agentPath(instance, safeVersion);
        if (Files.isRegularFile(target) && hasAgentEntrypoint(target)) {
            return;
        }

        Files.deleteIfExists(target);
        String artifactPath = "com/unascribed/nilloader/" + safeVersion
                + "/nilloader-" + safeVersion + ".jar";
        DownloadUtil.downloadFile(MAVEN_BASE + artifactPath, target);
        if (!hasAgentEntrypoint(target)) {
            Files.deleteIfExists(target);
            throw new IOException(
                    "Downloaded NilLoader artifact does not declare a Java agent entrypoint.");
        }
    }

    private static String requireVersion(String version) {
        if (version == null || !version.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}")
                || version.contains("..")) {
            throw new IllegalArgumentException("NilLoader version is invalid.");
        }
        return version;
    }

    public static boolean hasAgentEntrypoint(Path jarPath) {
        if (jarPath == null || !Files.isRegularFile(jarPath)) {
            return false;
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) {
                return false;
            }
            Attributes attributes = manifest.getMainAttributes();
            String premain = attributes.getValue("Premain-Class");
            String agent = attributes.getValue("Agent-Class");
            return (premain != null && !premain.isBlank())
                    || (agent != null && !agent.isBlank());
        } catch (Exception ignored) {
            return false;
        }
    }
}
