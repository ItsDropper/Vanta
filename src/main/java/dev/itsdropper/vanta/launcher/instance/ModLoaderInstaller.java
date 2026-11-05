package dev.itsdropper.vanta.launcher.instance;

import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipFile;

/**
 * Installs Risugami's historical ModLoader by merging a pinned, version-matched
 * patch archive into an instance-local copy of the vanilla client JAR.
 *
 * The shared Mojang client artifact is never modified.
 */
public final class ModLoaderInstaller {
    private static final String ARCHIVE_BASE = "https://b2.mcarchive.net/file/mcarchive/";
    private static final long MAX_ARCHIVE_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_EXPANDED_BYTES = 128L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 10_000;

    private static final Map<String, Artifact> ARTIFACTS = artifacts();

    private ModLoaderInstaller() {
    }

    public static String findLatestLoaderVersion(String minecraftVersion) {
        Artifact artifact = ARTIFACTS.get(minecraftVersion);
        if (artifact == null) {
            throw new IllegalArgumentException(
                    "No verified ModLoader archive is registered for Minecraft " + minecraftVersion + ".");
        }
        return artifact.version();
    }

    public static void install(Instance instance) throws Exception {
        if (instance == null || instance.getDirectory() == null) {
            throw new IllegalArgumentException("A valid ModLoader instance is required.");
        }
        String minecraftVersion = instance.getMinecraftVersion();
        Artifact artifact = ARTIFACTS.get(minecraftVersion);
        if (artifact == null) {
            throw new IllegalArgumentException(
                    "No verified ModLoader archive is registered for Minecraft " + minecraftVersion + ".");
        }

        Path root = instance.getDirectory().toAbsolutePath().normalize();
        Path clientJar = root.resolve("minecraft").resolve("client.jar").normalize();
        if (!clientJar.startsWith(root) || !Files.isRegularFile(clientJar)) {
            throw new IOException("The instance-local vanilla client JAR is missing.");
        }

        Path work = root.resolve("loader-cache").resolve("modloader").normalize();
        if (!work.startsWith(root)) {
            throw new IOException("ModLoader cache path escapes the instance directory.");
        }
        Files.createDirectories(work);
        Path archive = work.resolve("ModLoader-" + artifact.version() + ".zip");
        if (!Files.isRegularFile(archive) || !matchesSha256(archive, artifact.sha256())) {
            Files.deleteIfExists(archive);
            String url = ARCHIVE_BASE + artifact.sha256() + "/" + artifact.filename().replace(" ", "%20");
            DownloadUtil.downloadFile(url, archive);
            if (!Files.isRegularFile(archive)
                    || Files.size(archive) > MAX_ARCHIVE_BYTES
                    || !matchesSha256(archive, artifact.sha256())) {
                Files.deleteIfExists(archive);
                throw new IOException("ModLoader archive failed its pinned SHA-256 integrity check.");
            }
        }

        Path patched = work.resolve("client-modloader.jar");
        mergeArchiveIntoClient(clientJar, archive, patched);
        if (!Files.isRegularFile(patched) || Files.size(patched) == 0) {
            throw new IOException("ModLoader did not produce a valid client JAR.");
        }
        // Atomic replacement prevents a partial patched JAR from becoming launchable.
        try {
            Files.move(patched, clientJar, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(patched, clientJar, StandardCopyOption.REPLACE_EXISTING);
        }

        // ModLoader 1.2.5 resolves its installation root from a class resource
        // URL and passes that URI to File. When loaded from client.jar the
        // resource URI is jar:file:...!/ and is not a hierarchical file URI.
        // Load ModLoader from an exploded instance-local directory instead.
        extractBootstrapClasses(clientJar,
                root.resolve("modloader-bootstrap"));
    }

    static void mergeArchiveIntoClient(Path vanillaJar, Path patchArchive, Path outputJar)
            throws Exception {
        if (!Files.isRegularFile(vanillaJar) || !Files.isRegularFile(patchArchive)) {
            throw new IOException("Vanilla JAR and ModLoader archive must both exist.");
        }
        Map<String, Path> extracted = new LinkedHashMap<>();
        Path extractedRoot = Files.createTempDirectory("vanta-modloader-patch-");
        long expanded = 0;
        int count = 0;
        try {
            try (ZipInputStream input = new ZipInputStream(Files.newInputStream(patchArchive))) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = input.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    if (++count > MAX_ENTRIES) {
                        throw new IOException("ModLoader archive contains too many entries.");
                    }
                    String name = entry.getName().replace('\\', '/');
                    Path relative = Path.of(name).normalize();
                    if (name.startsWith("/") || relative.isAbsolute()
                            || relative.startsWith("..") || name.contains(":")) {
                        throw new IOException("Unsafe path in ModLoader archive: " + name);
                    }
                    if (isSignatureMetadata(name)) {
                        continue;
                    }
                    Path target = extractedRoot.resolve(relative).normalize();
                    if (!target.startsWith(extractedRoot)) {
                        throw new IOException("ModLoader archive entry escapes extraction directory.");
                    }
                    Files.createDirectories(target.getParent());
                    try (OutputStream output = Files.newOutputStream(target)) {
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            expanded += read;
                            if (expanded > MAX_EXPANDED_BYTES) {
                                throw new IOException("ModLoader archive expands beyond the safety limit.");
                            }
                            output.write(buffer, 0, read);
                        }
                    }
                    extracted.put(relative.toString().replace('\\', '/'), target);
                }
            }
            if (extracted.isEmpty()) {
                throw new IOException("ModLoader archive contains no patch files.");
            }

            Path temporary = outputJar.resolveSibling(outputJar.getFileName() + ".tmp");
            Files.deleteIfExists(temporary);
            try (ZipFile vanilla = new ZipFile(vanillaJar.toFile());
                 ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(temporary))) {
                byte[] buffer = new byte[8192];
                var entries = vanilla.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry original = entries.nextElement();
                    String name = original.getName();
                    if (isSignatureMetadata(name) || extracted.containsKey(name)) {
                        continue;
                    }
                    output.putNextEntry(new ZipEntry(name));
                    try (InputStream input = vanilla.getInputStream(original)) {
                        input.transferTo(output);
                    }
                    output.closeEntry();
                }
                for (Map.Entry<String, Path> patch : extracted.entrySet()) {
                    output.putNextEntry(new ZipEntry(patch.getKey()));
                    try (InputStream input = Files.newInputStream(patch.getValue())) {
                        input.transferTo(output);
                    }
                    output.closeEntry();
                }
            }
            try (ZipFile validation = new ZipFile(temporary.toFile())) {
                if (validation.size() == 0 || validation.getEntry("META-INF") != null) {
                    // Directory entries are optional; signature files are checked by name below.
                    if (validation.size() == 0) {
                        throw new IOException("Patched client JAR is empty.");
                    }
                }
                var entries = validation.entries();
                boolean hasClass = false;
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (name.endsWith(".class")) {
                        hasClass = true;
                    }
                    if (isSignatureMetadata(name)) {
                        throw new IOException("Patched client JAR still contains signature metadata.");
                    }
                }
                if (!hasClass) {
                    throw new IOException("Patched client JAR contains no class files.");
                }
            }
            Files.move(temporary, outputJar, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            try (var walk = Files.walk(extractedRoot)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                });
            }
        }
    }

    static void extractBootstrapClasses(Path clientJar, Path outputDirectory) throws IOException {
        if (!Files.isRegularFile(clientJar)) {
            throw new IOException("Patched ModLoader client JAR is missing.");
        }
        Files.createDirectories(outputDirectory);
        int extracted = 0;
        try (ZipFile jar = new ZipFile(clientJar.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith("ModLoader")
                        || !name.endsWith(".class") || name.contains("/")) {
                    continue;
                }
                Path target = outputDirectory.resolve(name).normalize();
                if (!target.getParent().equals(outputDirectory.normalize())) {
                    throw new IOException("Invalid ModLoader bootstrap class path: " + name);
                }
                Path temporary = Files.createTempFile(outputDirectory, ".modloader-", ".tmp");
                try {
                    try (InputStream input = jar.getInputStream(entry)) {
                        Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                    }
                    try {
                        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.ATOMIC_MOVE);
                    } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    extracted++;
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
        }
        if (extracted == 0 || !Files.isRegularFile(outputDirectory.resolve("ModLoader.class"))) {
            throw new IOException("ModLoader archive did not contain its ModLoader bootstrap class.");
        }
    }

    private static boolean isSignatureMetadata(String name) {
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        if (!upper.startsWith("META-INF/")) {
            return false;
        }
        if (upper.equals("META-INF/") || upper.equals("META-INF/MANIFEST.MF")) {
            return true;
        }
        return upper.endsWith(".SF") || upper.endsWith(".RSA") || upper.endsWith(".DSA")
                || upper.substring("META-INF/".length()).startsWith("SIG-");
    }

    private static boolean matchesSha256(Path file, String expected) throws Exception {
        if (expected == null || !expected.matches("(?i)[0-9a-f]{64}")) {
            return false;
        }
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expected);
    }

    private static Map<String, Artifact> artifacts() {
        Map<String, Artifact> values = new LinkedHashMap<>();
        add(values, "1.6.2", "0b14f5e261c9862989aa74313b59188cce10bea6724bae31130ce1e8e6a1c060", "ModLoader 1.6.2.zip");
        add(values, "1.6.1", "95fc5afdd9cc14d85cb41225fb689d7994f5994287ed9595e192026c06e7b536", "ModLoader 1.6.1.zip");
        add(values, "1.5.2", "0c355696c2f3ba405bb1f0f845dc51a6613c121eac25a6c7bc9d8046f2c941df", "ModLoader 1.5.2.zip");
        add(values, "1.5.1", "af7d7bca70b8bc08c75e96ec90a25432682dfc825aa4fe35485dcb390b1f7014", "ModLoader 1.5.1.zip");
        add(values, "1.5", "597d4d437a250986da84a9c7aee3ea653739608caf1d4a208f2006d8cbdfbc3d", "ModLoader 1.5.zip");
        add(values, "1.4.7", "685ead73c19531cf24062c7536737663421ed4170cfa582baddbbf6cba1544d2", "ModLoader 1.4.7.zip");
        add(values, "1.4.6", "f69b1f99b76c23cc1e076197375996e3b79feb369952ac692630f7b063709d5f", "ModLoader 1.4.6.zip");
        add(values, "1.4.5", "885b62bde6231b04d0189a06b082edfa48ea1474f22a5502ab40288563036b42", "ModLoader 1.4.5.zip");
        add(values, "1.4.4", "7d39b6d5e41bcd77edabd0aca3b43a10861a65ee9c2f9b358cedf8382d69c14e", "ModLoader 1.4.4.zip");
        add(values, "1.4.2", "861324b55c40e4af622e2a987c3c20ed4eb869ea89a004c93222058e394baec4", "ModLoader 1.4.2.zip");
        add(values, "1.3.2", "01a28a0a3d05634ce8745d34738b0617ddb285ad1584fb668874892c61e489eb", "ModLoader 1.3.2.zip");
        add(values, "1.3.1", "511881d7432cf740b753180a645ca6abb7cd63d09813e0089485c125d52c09a0", "ModLoader 1.3.1.zip");
        add(values, "1.2.5", "219370a86a15bfef8ff91f51fdd151e99391b771759183b19f72197452a28b79", "ModLoader 1.2.5.zip");
        add(values, "b1.8.1", "4135de0b0fddf6f9b39761a5261b82dae278b311237ec1cd936911b0b133919e", "ModLoader B1.8.1.zip");
        return Map.copyOf(values);
    }

    private static void add(Map<String, Artifact> values, String version, String hash, String filename) {
        values.put(version, new Artifact(version, hash, filename));
    }

    private record Artifact(String version, String sha256, String filename) {
    }
}
