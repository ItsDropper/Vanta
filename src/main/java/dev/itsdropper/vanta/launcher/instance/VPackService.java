package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Versioned, selectively exportable Vanta instance packs.
 * The pack always contains instance identity metadata; content groups are optional.
 */
public final class VPackService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PACK_MANIFEST = "vanta-pack.json";
    private static final String INSTANCE_METADATA = "instance.json";
    private static final int FORMAT_VERSION = 1;
    private static final long MAX_EXPANDED_BYTES = 100L * 1024 * 1024 * 1024;
    private static final int MAX_ENTRIES = 100_000;

    private VPackService() { }

    public record Options(
            boolean mods,
            boolean worlds,
            boolean configs,
            boolean resourcePacks,
            boolean shaderPacks,
            boolean screenshots,
            boolean gameSettings,
            boolean otherFiles
    ) {
        public static Options defaults() {
            return new Options(true, true, true, true, true, false, true, true);
        }

        Set<String> includedCategories() {
            Set<String> included = new HashSet<>();
            if (mods) included.add("mods");
            if (worlds) included.add("worlds");
            if (configs) included.add("configs");
            if (resourcePacks) included.add("resource-packs");
            if (shaderPacks) included.add("shader-packs");
            if (screenshots) included.add("screenshots");
            if (gameSettings) included.add("game-settings");
            if (otherFiles) included.add("other");
            return Set.copyOf(included);
        }
    }

    public record Verification(
            int entryCount,
            long expandedBytes,
            String instanceName,
            String minecraftVersion,
            String loader,
            Set<String> includedCategories
    ) { }

    public static void createVPack(Instance instance, Path output, Options options) throws IOException {
        if (instance == null || instance.getDirectory() == null) {
            throw new IllegalArgumentException("A valid instance is required.");
        }
        if (output == null || options == null) {
            throw new IllegalArgumentException("A destination and export selection are required.");
        }

        Path source = instance.getDirectory().toAbsolutePath().normalize();
        if (Files.isSymbolicLink(source)
                || !Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The instance directory is missing or is not a regular directory.");
        }
        Path destination = output.toAbsolutePath().normalize();
        if (destination.startsWith(source) || Files.isSymbolicLink(destination)) {
            throw new IOException("Choose a regular VPack destination outside the instance directory.");
        }
        if (destination.getParent() != null) Files.createDirectories(destination.getParent());

        Set<String> included = options.includedCategories();
        if (included.isEmpty()) {
            throw new IOException("Select at least one content group to export.");
        }

        Path temporary = destination.resolveSibling(
                destination.getFileName() + "." + UUID.randomUUID() + ".tmp");
        boolean completed = false;
        try {
            ObjectNode manifest = MAPPER.createObjectNode();
            manifest.put("format", "vanta-instance-pack");
            manifest.put("formatVersion", FORMAT_VERSION);
            manifest.put("instanceId", instance.getId());
            ArrayNode categories = manifest.putArray("includedCategories");
            included.stream().sorted().forEach(categories::add);

            byte[] manifestBytes = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
            byte[] instanceBytes = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(instance);
            long expanded = manifestBytes.length + instanceBytes.length;
            int entries = 2;
            byte[] buffer = new byte[64 * 1024];

            try (OutputStream stream = Files.newOutputStream(temporary);
                 ZipOutputStream zip = new ZipOutputStream(stream)) {
                putBytes(zip, INSTANCE_METADATA, instanceBytes);
                putBytes(zip, PACK_MANIFEST, manifestBytes);

                List<Path> files;
                try (var walk = Files.walk(source)) {
                    files = walk.filter(path -> !path.equals(source))
                            .sorted(Comparator.comparing(path -> source.relativize(path).toString()))
                            .toList();
                }

                for (Path file : files) {
                    Path relative = source.relativize(file);
                    String name = archiveName(relative);
                    String lowerName = name.toLowerCase(Locale.ROOT);
                    if (lowerName.equals(INSTANCE_METADATA) || lowerName.equals(PACK_MANIFEST)) continue;
                    if (lowerName.startsWith(INSTANCE_METADATA + "/")
                            || lowerName.startsWith(PACK_MANIFEST + "/")) {
                        throw new IOException("Instance contains a reserved VPack metadata path: " + name);
                    }
                    if (isTransient(name) || !included.contains(categoryFor(name))) continue;
                    if (Files.isSymbolicLink(file)) {
                        throw new IOException("Cannot export selected VPack content containing a symbolic link: "
                                + relative);
                    }
                    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                        if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) continue;
                        throw new IOException("Unsupported filesystem entry in instance: " + relative);
                    }

                    zip.putNextEntry(new ZipEntry(name));
                    try (InputStream input = Files.newInputStream(file)) {
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            expanded += read;
                            if (expanded > MAX_EXPANDED_BYTES) {
                                throw new IOException("Selected VPack content exceeds the 100 GiB safety limit.");
                            }
                            zip.write(buffer, 0, read);
                        }
                    }
                    zip.closeEntry();
                    if (++entries > MAX_ENTRIES) {
                        throw new IOException("Selected VPack content contains too many files.");
                    }
                }
            }

            verifyVPack(temporary);
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            completed = true;
        } finally {
            if (!completed) Files.deleteIfExists(temporary);
        }
    }

    public static Verification verifyVPack(Path archive) throws IOException {
        if (archive == null || !Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(archive)) {
            throw new IOException("Select an existing regular VPack archive.");
        }

        Set<String> names = new HashSet<>();
        Set<String> fileEntries = new HashSet<>();
        byte[] manifestBytes = null;
        byte[] instanceBytes = null;
        long expandedBytes = 0;
        int entries = 0;
        byte[] buffer = new byte[64 * 1024];

        try (InputStream input = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new IOException("VPack contains too many entries.");
                }
                String rawName = entry.getName();
                if (rawName == null || rawName.isBlank() || rawName.indexOf('\0') >= 0) {
                    throw new IOException("VPack contains an invalid path.");
                }
                String normalized = rawName.replace('\\', '/');
                Path relative;
                try {
                    relative = Path.of(normalized).normalize();
                } catch (java.nio.file.InvalidPathException failure) {
                    throw new IOException("VPack contains an invalid archive path.", failure);
                }
                if (relative.isAbsolute() || relative.startsWith("..") || normalized.startsWith("/")
                        || rawName.indexOf('\\') >= 0 || normalized.indexOf(':') >= 0
                        || normalized.matches("^[A-Za-z]:.*")) {
                    throw new IOException("VPack contains an unsafe path: " + rawName);
                }
                String name = relative.toString().replace('\\', '/');
                String canonicalEntryName = name + (entry.isDirectory() ? "/" : "");
                if (!normalized.equals(canonicalEntryName) || name.isBlank()) {
                    throw new IOException("VPack contains a non-canonical archive path: " + rawName);
                }
                String lowerName = name.toLowerCase(Locale.ROOT);
                if (lowerName.startsWith(INSTANCE_METADATA + "/")
                        || lowerName.startsWith(PACK_MANIFEST + "/")) {
                    throw new IOException("VPack contains a reserved metadata path: " + name);
                }
                String key = name.toLowerCase(Locale.ROOT);
                if (!names.add(key)) {
                    throw new IOException("VPack contains duplicate paths: " + rawName);
                }
                if (isTransient(name)) {
                    throw new IOException("VPack contains a transient or forbidden file: " + name);
                }

                boolean isManifest = key.equals(PACK_MANIFEST);
                boolean isInstance = key.equals(INSTANCE_METADATA);
                if ((isManifest && !name.equals(PACK_MANIFEST))
                        || (isInstance && !name.equals(INSTANCE_METADATA))) {
                    throw new IOException("VPack metadata paths must use their canonical names.");
                }
                if ((isManifest || isInstance) && entry.isDirectory()) {
                    throw new IOException("VPack metadata entries must be regular files.");
                }
                java.io.ByteArrayOutputStream special = isManifest || isInstance
                        ? new java.io.ByteArrayOutputStream() : null;
                if (!entry.isDirectory()) fileEntries.add(key);
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expandedBytes += read;
                    if (expandedBytes > MAX_EXPANDED_BYTES) {
                        throw new IOException("VPack expands beyond the 100 GiB safety limit.");
                    }
                    if (special != null) {
                        if (special.size() + read > 1024 * 1024) {
                            throw new IOException("VPack metadata exceeds the 1 MiB safety limit.");
                        }
                        special.write(buffer, 0, read);
                    }
                }
                if (isManifest) manifestBytes = special.toByteArray();
                if (isInstance) instanceBytes = special.toByteArray();
                zip.closeEntry();
            }
        } catch (java.util.zip.ZipException failure) {
            throw new IOException("VPack is corrupt or failed its CRC integrity check.", failure);
        }

        if (manifestBytes == null || instanceBytes == null) {
            throw new IOException("VPack is missing its manifest or instance metadata.");
        }

        final JsonNode manifest;
        final Instance original;
        try {
            manifest = MAPPER.readTree(manifestBytes);
            original = MAPPER.readValue(instanceBytes, Instance.class);
        } catch (Exception failure) {
            throw new IOException("VPack metadata is invalid.", failure);
        }
        if (manifest == null || original == null
                || !"vanta-instance-pack".equals(manifest.path("format").asText())
                || manifest.path("formatVersion").asInt(-1) != FORMAT_VERSION
                || !manifest.path("includedCategories").isArray()) {
            throw new IOException("Unsupported or invalid VPack format.");
        }
        if (original.getId() == null || original.getId().isBlank()
                || original.getName() == null || original.getName().isBlank()
                || original.getMinecraftVersion() == null || original.getMinecraftVersion().isBlank()
                || original.getLoader() == null || original.getLoader().isBlank()) {
            throw new IOException("VPack instance metadata is missing its identity, Minecraft version, or loader.");
        }
        String declaredId = manifest.path("instanceId").asText(null);
        if (declaredId == null || declaredId.isBlank() || !declaredId.equals(original.getId())) {
            throw new IOException("VPack manifest and instance metadata identify different instances.");
        }

        Set<String> included = new HashSet<>();
        for (JsonNode category : manifest.path("includedCategories")) {
            if (!category.isTextual() || category.asText().isBlank()
                    || !included.add(category.asText())) {
                throw new IOException("VPack contains invalid or duplicate content groups.");
            }
        }
        Set<String> validCategories = Set.of("mods", "worlds", "configs", "resource-packs",
                "shader-packs", "screenshots", "game-settings", "other");
        if (included.isEmpty() || !validCategories.containsAll(included)) {
            throw new IOException("VPack declares missing or unsupported content groups.");
        }

        for (String name : names) {
            if (name.equalsIgnoreCase(INSTANCE_METADATA) || name.equalsIgnoreCase(PACK_MANIFEST)) continue;
            if (!included.contains(categoryFor(name))) {
                throw new IOException("VPack contains content outside its declared selection: " + name);
            }

            // A regular file cannot also be an ancestor directory for another entry.
            int separator = name.indexOf('/');
            while (separator >= 0) {
                String parent = name.substring(0, separator).toLowerCase(Locale.ROOT);
                if (fileEntries.contains(parent)) {
                    throw new IOException("VPack contains a file/directory path collision: " + name);
                }
                separator = name.indexOf('/', separator + 1);
            }
        }

        return new Verification(entries, expandedBytes, original.getName(),
                original.getMinecraftVersion(), original.getLoader(), Set.copyOf(included));
    }

    public static Instance restoreVPack(Path archive, String requestedName) throws IOException {
        verifyVPack(archive);
        return InstanceBackupService.restoreBackup(archive, requestedName, true);
    }

    private static void putBytes(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static String archiveName(Path relative) {
        return relative.toString().replace('\\', '/');
    }

    private static boolean isTransient(String name) {
        String first = name.contains("/") ? name.substring(0, name.indexOf('/')) : name;
        return first.equalsIgnoreCase("logs") || first.equalsIgnoreCase("natives")
                || first.equalsIgnoreCase(".installing");
    }

    private static String categoryFor(String name) {
        String normalized = name.replace('\\', '/');
        String first = normalized.contains("/") ? normalized.substring(0, normalized.indexOf('/')) : normalized;
        return switch (first.toLowerCase(Locale.ROOT)) {
            case "mods" -> "mods";
            case "saves" -> "worlds";
            case "config" -> "configs";
            case "resourcepacks" -> "resource-packs";
            case "shaderpacks" -> "shader-packs";
            case "screenshots" -> "screenshots";
            case "options.txt", "servers.dat", "servers.dat_old", "optionsof.txt",
                    "optionsshaders.txt", "realms_persistence.json" -> "game-settings";
            default -> "other";
        };
    }
}
