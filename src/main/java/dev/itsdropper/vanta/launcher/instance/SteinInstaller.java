package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.itsdropper.vanta.launcher.MinecraftLocator;
import dev.itsdropper.vanta.launcher.Platform;
import dev.itsdropper.vanta.launcher.java.JavaLocator;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Installs the official Stein Bridge and Stein Loader into one Vanta instance.
 *
 * <p>Stein's own installer is used as the authoritative patching implementation. It runs against
 * a temporary, instance-local .minecraft layout; its launcher profile is never imported or written
 * to the player's normal Minecraft directory. Only the patched client, Stein runtime, version
 * metadata and Stein-owned libraries are moved into Vanta after validation.
 */
public final class SteinInstaller {
    public static final String VERSION = "0.1.43";
    public static final String BRIDGE_VERSION = "0.1.28";
    public static final String LOADER_VERSION = "0.1.31";
    public static final String MINECRAFT_VERSION = "1.8.9";

    private static final String VERSION_ID = "1.8.9-SteinLoader";
    private static final String MAIN_CLASS = "dev.xavier.stein.bridge.launch.Main";
    private static final String INSTALLER_URL =
            "https://github.com/stein-project/installer/releases/download/v0.1.43/SteinLoader-Installer.jar";
    private static final String INSTALLER_SHA256 =
            "8bb2ce716acf69569bfd374adb278477640ff68b5a11f26ebc38d4c075bc87dc";
    private static final String PAULSCODE_PATH =
            "com/paulscode/librarylwjglopenal/20100824/librarylwjglopenal-20100824.jar";
    private static final long MAX_INSTALLER_BYTES = 80L * 1024L * 1024L;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private SteinInstaller() {
    }

    public static boolean supportsCurrentPlatform() {
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean x64 = architecture.equals("amd64") || architecture.equals("x86_64")
                || architecture.equals("x86-64") || architecture.equals("x64");
        boolean arm64 = architecture.equals("aarch64") || architecture.equals("arm64");
        return switch (Platform.operatingSystem()) {
            case WINDOWS, LINUX -> x64;
            case MACOS -> x64 || arm64;
            case UNKNOWN -> false;
        };
    }

    public static boolean supportsMinecraftVersion(String minecraftVersion) {
        return MINECRAFT_VERSION.equals(minecraftVersion == null ? "" : minecraftVersion.trim())
                && supportsCurrentPlatform();
    }

    public static String findLatestLoaderVersion() {
        return LOADER_VERSION;
    }

    public static Path javaExecutable(Instance instance) {
        if (instance == null) {
            throw new IllegalArgumentException("Stein instance cannot be null.");
        }
        Path executable = instance.getDirectory()
                .resolve("stein")
                .resolve("runtime")
                .resolve("bin")
                .resolve(Platform.javaExecutableName());
        if (!Files.isRegularFile(executable)) {
            throw new IllegalStateException(
                    "Stein's Java 25 runtime is missing or invalid. Repair this instance before launching.");
        }
        return executable;
    }

    public static String install(Instance instance, JsonNode vanillaMetadata) throws Exception {
        if (instance == null || vanillaMetadata == null) {
            throw new IllegalArgumentException("Stein installation requires an instance and vanilla metadata.");
        }
        if (!supportsMinecraftVersion(instance.getMinecraftVersion())) {
            throw new IllegalArgumentException("Stein Loader currently supports Minecraft 1.8.9 only.");
        }

        Path instanceDirectory = instance.getDirectory().toAbsolutePath().normalize();
        Path minecraftDirectory = instanceDirectory.resolve("minecraft");
        Path vanillaClient = minecraftDirectory.resolve("client.jar");
        Path vanillaPaulscode = MinecraftLocator.getLibrariesDirectory().resolve(PAULSCODE_PATH);

        if (!Files.isRegularFile(vanillaClient)) {
            throw new IllegalStateException("Minecraft client JAR is missing before Stein installation.");
        }
        if (!Files.isRegularFile(vanillaPaulscode)) {
            throw new IllegalStateException("Minecraft 1.8.9 Paulscode library is missing before Stein installation.");
        }

        Path stage = Files.createTempDirectory(instanceDirectory, ".stein-install-");
        try {
            prepareVanillaStage(stage, vanillaMetadata, vanillaClient, vanillaPaulscode);

            Path installerJar = obtainInstaller();
            runOfficialInstaller(installerJar, stage);

            Path generatedVersionDirectory = stage.resolve("versions").resolve(VERSION_ID);
            Path generatedJar = generatedVersionDirectory.resolve(VERSION_ID + ".jar");
            Path generatedJson = generatedVersionDirectory.resolve(VERSION_ID + ".json");
            Path generatedRuntime = generatedVersionDirectory.resolve("runtime");
            Path generatedJava = generatedRuntime.resolve("bin").resolve(Platform.javaExecutableName());

            if (!Files.isRegularFile(generatedJar) || Files.size(generatedJar) == 0) {
                throw new IOException("The official Stein installer did not produce a patched Minecraft client.");
            }
            if (!Files.isRegularFile(generatedJava)) {
                throw new IOException("The official Stein installer did not produce a usable Java 25 runtime.");
            }
            verifyJava25(generatedJava);

            JsonNode generatedMetadata = JSON.readTree(generatedJson.toFile());
            normalizeNativeLibraries(generatedMetadata);
            validateGeneratedMetadata(generatedMetadata);

            Path stagedLibraries = stage.resolve("libraries");
            Path sharedLibraries = MinecraftLocator.getLibrariesDirectory();
            copyRequiredLibraryTree(stagedLibraries.resolve("dev/xavier/stein"),
                    sharedLibraries.resolve("dev/xavier/stein"));
            copyRequiredLibraryTree(stagedLibraries.resolve("org/lwjgl"),
                    sharedLibraries.resolve("org/lwjgl"));

            Path steinDirectory = instanceDirectory.resolve("stein");
            Files.createDirectories(steinDirectory);
            Path runtimeTarget = steinDirectory.resolve("runtime");
            replaceDirectory(generatedRuntime, runtimeTarget);

            copyAtomically(generatedJar, vanillaClient);
            MinecraftVersionResolver.saveMetadata(instance, generatedMetadata);
            NativeInstaller.extract(instance, generatedMetadata);

            JSON.writerWithDefaultPrettyPrinter().writeValue(
                    steinDirectory.resolve("install.json").toFile(),
                    Map.of(
                            "installerVersion", VERSION,
                            "bridgeVersion", BRIDGE_VERSION,
                            "loaderVersion", LOADER_VERSION,
                            "minecraftVersion", MINECRAFT_VERSION,
                            "installedBy", "Vanta"
                    )
            );

            System.out.println("[Vanta Stein] Installed Stein Loader " + LOADER_VERSION
                    + " with Bridge " + BRIDGE_VERSION + " using installer " + VERSION + ".");
            return LOADER_VERSION;
        } finally {
            deleteTree(stage);
        }
    }

    private static void verifyJava25(Path javaExecutable) throws Exception {
        Path output = Files.createTempFile("stein-java-version-", ".log");
        try {
            Process process = new ProcessBuilder(javaExecutable.toString(), "-version")
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile())
                    .start();
            boolean finished;
            try {
                finished = process.waitFor(15, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw interrupted;
            }
            String versionOutput = Files.readString(output);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Stein's Java 25 runtime is missing or invalid: java -version timed out.");
            }
            if (process.exitValue() != 0
                    || !(versionOutput.contains("version \"25.")
                    || versionOutput.contains("version \"25\""))) {
                throw new IOException("Stein's Java 25 runtime is missing or invalid: " + versionOutput.trim());
            }
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private static void prepareVanillaStage(
            Path stage,
            JsonNode vanillaMetadata,
            Path vanillaClient,
            Path vanillaPaulscode
    ) throws IOException {
        Path versionDirectory = stage.resolve("versions/1.8.9");
        Path paulscodeTarget = stage.resolve("libraries").resolve(PAULSCODE_PATH);
        Files.createDirectories(versionDirectory);
        Files.createDirectories(paulscodeTarget.getParent());

        JSON.writerWithDefaultPrettyPrinter()
                .writeValue(versionDirectory.resolve("1.8.9.json").toFile(), vanillaMetadata);
        Files.copy(vanillaClient, versionDirectory.resolve("1.8.9.jar"),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        Files.copy(vanillaPaulscode, paulscodeTarget,
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        Files.createDirectories(stage.resolve("mods"));
    }

    private static Path obtainInstaller() throws Exception {
        Path cacheDirectory = MinecraftLocator.getVantaDirectory()
                .resolve("cache")
                .resolve("stein");
        Files.createDirectories(cacheDirectory);
        Path cached = cacheDirectory.resolve("SteinLoader-Installer-" + VERSION + ".jar");

        if (Files.isRegularFile(cached)
                && Files.size(cached) <= MAX_INSTALLER_BYTES
                && INSTALLER_SHA256.equalsIgnoreCase(sha256(cached))) {
            return cached;
        }
        Files.deleteIfExists(cached);

        Path temporary = Files.createTempFile(cacheDirectory, "stein-installer-", ".download");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(INSTALLER_URL))
                    .timeout(Duration.ofMinutes(3))
                    .header("User-Agent", "Vanta Launcher")
                    .GET()
                    .build();
            HttpResponse<Path> response = HTTP.send(
                    request,
                    HttpResponse.BodyHandlers.ofFile(temporary)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Stein installer download failed with HTTP " + response.statusCode() + ".");
            }
            long size = Files.size(temporary);
            if (size == 0 || size > MAX_INSTALLER_BYTES) {
                throw new IOException("Downloaded Stein installer has an invalid file size: " + size + ".");
            }
            String actualSha256 = sha256(temporary);
            if (!INSTALLER_SHA256.equalsIgnoreCase(actualSha256)) {
                throw new IOException("Stein installer SHA-256 mismatch. The downloaded file was rejected.");
            }
            moveReplacing(temporary, cached);
            return cached;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void runOfficialInstaller(Path installerJar, Path stage) throws Exception {
        String java = JavaLocator.getJava(25);
        Path log = stage.resolve("stein-installer.log");
        Process process = new ProcessBuilder(
                java,
                "-jar",
                installerJar.toString(),
                "--cli",
                "--mc",
                stage.toString(),
                "--mods",
                "keep"
        ).redirectErrorStream(true).redirectOutput(log.toFile()).start();

        boolean finished;
        try {
            finished = process.waitFor(15, TimeUnit.MINUTES);
        } catch (InterruptedException interrupted) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw interrupted;
        }
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
            throw new IOException("The official Stein installer timed out after 15 minutes.");
        }

        String output = Files.isRegularFile(log) ? Files.readString(log) : "";
        if (process.exitValue() != 0) {
            String details = output.length() > 12000
                    ? output.substring(output.length() - 12000)
                    : output;
            throw new IOException("The official Stein installer failed with exit code "
                    + process.exitValue() + ".\n" + details);
        }
        for (String line : output.split("\\R")) {
            if (!line.isBlank()) {
                System.out.println("[Vanta Stein Installer] " + line);
            }
        }
    }

    /**
     * Stein's installer writes native archives as ordinary artifact entries with a
     * platform rule. Vanta's resolver deliberately keeps native archives out of the
     * classpath and extracts them through the classifiers contract, so normalize
     * those entries without changing their Maven paths or bytes.
     */
    private static void normalizeNativeLibraries(JsonNode metadata) throws IOException {
        if (metadata == null || !metadata.path("libraries").isArray()) {
            throw new IOException("Stein version metadata has no library list.");
        }

        String operatingSystem = switch (Platform.operatingSystem()) {
            case WINDOWS -> "windows";
            case LINUX -> "linux";
            case MACOS -> "osx";
            case UNKNOWN -> throw new IOException("Stein does not support this operating system.");
        };

        int normalized = 0;
        for (JsonNode node : metadata.path("libraries")) {
            if (!(node instanceof ObjectNode library)
                    || !library.path("name").asText("").startsWith("dev.xavier.stein.lwjgl:")) {
                continue;
            }

            JsonNode downloadsNode = library.get("downloads");
            if (!(downloadsNode instanceof ObjectNode downloads)) {
                continue;
            }
            JsonNode artifact = downloads.get("artifact");
            if (artifact == null || !artifact.isObject()
                    || !artifact.path("path").asText("").contains("-natives-")) {
                continue;
            }

            ObjectNode classifiers = JSON.createObjectNode();
            classifiers.set("stein-natives", artifact.deepCopy());
            downloads.remove("artifact");
            downloads.set("classifiers", classifiers);

            ObjectNode natives = JSON.createObjectNode();
            natives.put(operatingSystem, "stein-natives");
            library.set("natives", natives);
            normalized++;
        }

        if (normalized == 0) {
            throw new IOException("Stein metadata did not contain any platform native archives.");
        }
    }

    private static void validateGeneratedMetadata(JsonNode metadata) throws IOException {
        if (metadata == null
                || !VERSION_ID.equals(metadata.path("id").asText())
                || !MAIN_CLASS.equals(metadata.path("mainClass").asText())
                || !metadata.path("minecraftArguments").isTextual()
                || !metadata.path("libraries").isArray()) {
            throw new IOException("The official Stein installer produced incomplete version metadata.");
        }

        boolean bridge = false;
        boolean loader = false;
        boolean lwjgl = false;
        boolean nativeLibraries = false;
        for (JsonNode library : metadata.path("libraries")) {
            String name = library.path("name").asText("");
            bridge |= name.startsWith("dev.xavier.stein:steinbridge:");
            loader |= name.startsWith("dev.xavier.stein:steinloader:");
            lwjgl |= name.startsWith("org.lwjgl:lwjgl:");
            nativeLibraries |= library.path("natives").isObject();
        }
        if (!bridge || !loader || !lwjgl || !nativeLibraries) {
            throw new IOException("Stein version metadata is missing the Bridge, Loader, or LWJGL 3 libraries.");
        }
    }

    private static void copyRequiredLibraryTree(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) {
            throw new IOException("The official Stein installer did not produce required libraries: " + source);
        }
        copyTree(source, target);
    }

    private static void replaceDirectory(Path source, Path target) throws IOException {
        Path next = target.resolveSibling(target.getFileName() + ".next");
        Path previous = target.resolveSibling(target.getFileName() + ".previous");
        deleteTree(next);
        deleteTree(previous);
        moveReplacing(source, next);

        boolean hadPrevious = Files.exists(target);
        if (hadPrevious) {
            moveReplacing(target, previous);
        }
        try {
            moveReplacing(next, target);
            deleteTree(previous);
        } catch (IOException failure) {
            deleteTree(target);
            if (hadPrevious && Files.exists(previous)) {
                moveReplacing(previous, target);
            }
            throw failure;
        }
    }

    private static void copyAtomically(Path source, Path target) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".stein-new");
        Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        moveReplacing(temporary, target);
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        Files.createDirectories(target.toAbsolutePath().normalize().getParent());
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void copyTree(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                Files.createDirectories(target.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException {
                Path destination = target.resolve(source.relativize(file));
                Files.createDirectories(destination.getParent());
                Files.copy(file, destination,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteTree(Path directory) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException failure)
                    throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[65536];
            for (int read; (read = input.read(buffer)) != -1; ) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest()).toLowerCase(Locale.ROOT);
    }
}
