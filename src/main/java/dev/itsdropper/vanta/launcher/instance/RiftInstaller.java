package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.itsdropper.vanta.launcher.MinecraftLocator;
import dev.itsdropper.vanta.launcher.loader.LoaderStackConfiguration;
import dev.itsdropper.vanta.launcher.loader.LoaderStackManager;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Installs the original Dimensional Development Rift loader for Minecraft 1.13.
 *
 * This deliberately does not use RiftLoader forks or community 1.13.2 ports.
 * The profile mirrors the original project's LaunchWrapper tweaker and
 * dependency coordinates.
 */
public final class RiftInstaller {
    public static final String MINECRAFT_VERSION = "1.13";
    public static final String LOADER_VERSION = "1.0.4-87";
    // The original Maven host no longer resolves reliably. The archived
    // upstream GitHub release is the canonical source for this pinned binary.
    public static final String RIFT_RELEASE_URL =
            "https://github.com/DimensionalDevelopment/Rift/releases/download/v1.0.4-87/Rift-1.0.4-87.jar";
    public static final String MIXIN_REPOSITORY =
            "https://repo.spongepowered.org/repository/maven-public/";
    public static final String TWEAKER = "org.dimdev.riftloader.launch.RiftLoaderClientTweaker";
    public static final String LAUNCHWRAPPER_MAIN = "net.minecraft.launchwrapper.Launch";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RiftInstaller() {
    }

    public static String findLatestLoaderVersion(String minecraftVersion) {
        if (!MINECRAFT_VERSION.equals(minecraftVersion)) {
            throw new IllegalArgumentException(
                    "The original Rift release is only supported for Minecraft 1.13.");
        }
        return LOADER_VERSION;
    }

    public static JsonNode buildOverlay(String minecraftVersion) {
        findLatestLoaderVersion(minecraftVersion);

        ObjectNode overlay = MAPPER.createObjectNode();
        overlay.put("id", "rift-" + minecraftVersion + "-" + LOADER_VERSION);
        overlay.put("mainClass", LAUNCHWRAPPER_MAIN);
        overlay.put("type", "release");

        ArrayNode libraries = overlay.putArray("libraries");
        addLibrary(libraries, "org.dimdev:rift:" + LOADER_VERSION, MIXIN_REPOSITORY);
        addLibrary(libraries, "org.dimdev:mixin:0.7.11-SNAPSHOT", MIXIN_REPOSITORY);
        addLibrary(libraries, "net.minecraft:launchwrapper:1.12",
                "https://libraries.minecraft.net/");
        addLibrary(libraries, "org.ow2.asm:asm:6.2",
                "https://repo1.maven.org/maven2/");
        addLibrary(libraries, "org.ow2.asm:asm-commons:6.2",
                "https://repo1.maven.org/maven2/");
        addLibrary(libraries, "org.ow2.asm:asm-tree:6.2",
                "https://repo1.maven.org/maven2/");
        return overlay;
    }

    public static void install(Instance instance) throws Exception {
        if (instance == null || instance.getDirectory() == null) {
            throw new IllegalArgumentException("A valid Rift instance is required.");
        }
        String minecraftVersion = instance.getMinecraftVersion();
        String version = findLatestLoaderVersion(minecraftVersion);
        JsonNode overlay = buildOverlay(minecraftVersion);

        Path artifact = artifactPath(version);
        if (Files.exists(artifact) && !hasExpectedTweakerClass(artifact)) {
            Files.delete(artifact);
        }

        // Rift's original Maven server (www.dimdev.org) is no longer a
        // dependable download source. Fetch the exact pinned upstream release
        // asset instead; keep the Maven coordinate in the profile so the
        // normal classpath resolver still finds it in the shared library store.
        if (!hasExpectedTweakerClass(artifact)) {
            Files.createDirectories(artifact.getParent());
            DownloadUtil.downloadFile(RIFT_RELEASE_URL, artifact);
        }

        // The Rift binary is already installed from its GitHub release.
        // Download only the remaining profile libraries from their active
        // repositories.
        ObjectNode dependencyOverlay = overlay.deepCopy();
        ArrayNode dependencyLibraries = (ArrayNode) dependencyOverlay.path("libraries");
        for (int i = dependencyLibraries.size() - 1; i >= 0; i--) {
            if (dependencyLibraries.get(i).path("name").asText()
                    .equals("org.dimdev:rift:" + version)) {
                dependencyLibraries.remove(i);
            }
        }
        FabricInstaller.installLibraries(dependencyOverlay);
        verifyRiftArtifact(version);

        Path profileDirectory = instance.getDirectory().resolve("loader-profiles");
        Files.createDirectories(profileDirectory);
        Path profileFile = profileDirectory.resolve("rift.json");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(profileFile.toFile(), overlay);

        LoaderStackConfiguration configuration = new LoaderStackConfiguration(List.of(
                new LoaderStackConfiguration.Layer(
                        "rift",
                        version,
                        LoaderStackConfiguration.LayerType.PROFILE_OVERLAY,
                        100,
                        List.of(TWEAKER),
                        "loader-profiles/rift.json"
                )
        ));
        LoaderStackManager.apply(instance, configuration);
    }

    private static void addLibrary(ArrayNode libraries, String coordinate, String repository) {
        ObjectNode library = libraries.addObject();
        library.put("name", coordinate);
        library.put("url", repository);
    }

    private static Path artifactPath(String version) throws IOException {
        Path libraries = MinecraftLocator.getLibrariesDirectoryPath()
                .toAbsolutePath().normalize();
        Path artifact = libraries.resolve("org").resolve("dimdev").resolve("rift")
                .resolve(version).resolve("rift-" + version + ".jar").normalize();
        if (!artifact.startsWith(libraries)) {
            throw new IOException("Rift artifact path escapes the shared libraries directory.");
        }
        return artifact;
    }

    private static boolean hasExpectedTweakerClass(Path artifact) {
        if (!Files.isRegularFile(artifact)) {
            return false;
        }
        try (JarFile jar = new JarFile(artifact.toFile())) {
            return jar.getEntry("org/dimdev/riftloader/launch/RiftLoaderClientTweaker.class") != null;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void verifyRiftArtifact(String version) throws Exception {
        Path artifact = artifactPath(version);
        if (!Files.isRegularFile(artifact) || Files.size(artifact) == 0
                || !hasExpectedTweakerClass(artifact)) {
            throw new IOException("Rift library is missing or invalid after installation.");
        }
    }
}
