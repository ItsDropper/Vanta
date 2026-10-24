package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.MinecraftLocator;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates a full local instance backup. Unlike a Modrinth export, this preserves
 * worlds, screenshots, options, configs, mods, resource packs, and instance metadata.
 * Transient logs and extracted native libraries are omitted to reduce backup size.
 */
public final class InstanceBackupService {
    private InstanceBackupService() { }

    public static void createBackup(Instance instance, Path output) throws IOException {
        if (instance == null || instance.getDirectory() == null) {
            throw new IllegalArgumentException("A valid instance is required.");
        }
        if (output == null) throw new IllegalArgumentException("Backup destination is required.");

        Path source = instance.getDirectory().toAbsolutePath().normalize();
        if (!Files.isDirectory(source)) throw new IOException("Instance directory does not exist.");
        Path destination = output.toAbsolutePath().normalize();
        if (destination.startsWith(source)) {
            throw new IOException("The backup must be saved outside the instance directory.");
        }
        if (destination.getParent() != null) Files.createDirectories(destination.getParent());

        Path temporary = destination.resolveSibling(destination.getFileName() + ".backup-in-progress");
        Files.deleteIfExists(temporary);
        long added = 0;
        try {
            try (OutputStream stream = Files.newOutputStream(temporary);
                 ZipOutputStream zip = new ZipOutputStream(stream)) {
                List<Path> files;
                try (var walk = Files.walk(source)) {
                    files = walk.filter(Files::isRegularFile)
                            .filter(path -> !path.getFileName().toString().equals(".installing"))
                            .filter(path -> !path.startsWith(source.resolve("logs")))
                            .filter(path -> !path.startsWith(source.resolve("natives")))
                            .sorted(Comparator.comparing(path -> source.relativize(path).toString()))
                            .toList();
                }
                byte[] buffer = new byte[64 * 1024];
                for (Path file : files) {
                    Path relative = source.relativize(file);
                    String entryName = relative.toString().replace('\\', '/');
                    zip.putNextEntry(new ZipEntry(entryName));
                    try (var input = Files.newInputStream(file)) {
                        int read;
                        while ((read = input.read(buffer)) != -1) zip.write(buffer, 0, read);
                    }
                    zip.closeEntry();
                    added++;
                }
                if (added == 0) throw new IOException("The instance contains no files to back up.");
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ex) {
            Files.deleteIfExists(temporary);
            if (ex instanceof IOException io) throw io;
            throw new IOException("Could not create instance backup.", ex);
        }
    }

    /**
     * Restores a full backup as a separate instance. Archive paths are validated
     * before writing, and an installation marker hides partial restores from discovery.
     */
    public static Instance restoreBackup(Path archive, String requestedName) throws IOException {
        if (archive == null || !Files.isRegularFile(archive)) {
            throw new IOException("Select an existing Vanta ZIP backup.");
        }
        if (requestedName == null || requestedName.isBlank()) {
            throw new IllegalArgumentException("Restored instance name cannot be empty.");
        }

        Path instancesRoot = MinecraftLocator.getInstancesDirectory().toAbsolutePath().normalize();
        String id = "restored-" + UUID.randomUUID().toString().substring(0, 12);
        Path target = instancesRoot.resolve(id).normalize();
        if (!target.startsWith(instancesRoot) || Files.exists(target)) {
            throw new IOException("Could not allocate a safe restore destination.");
        }

        Files.createDirectories(target);
        Path marker = target.resolve(".installing");
        Files.createFile(marker);
        boolean completed = false;
        try {
            Set<String> names = new HashSet<>();
            long expandedBytes = 0;
            int entries = 0;
            byte[] buffer = new byte[64 * 1024];
            try (InputStream input = Files.newInputStream(archive);
                 ZipInputStream zip = new ZipInputStream(input)) {
                java.util.zip.ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (++entries > 100_000) throw new IOException("Backup contains too many entries.");
                    String rawName = entry.getName();
                    if (rawName == null || rawName.isBlank() || rawName.indexOf('\0') >= 0) {
                        throw new IOException("Backup contains an invalid path.");
                    }
                    String normalizedName = rawName.replace('\\', '/');
                    Path relative = Path.of(normalizedName).normalize();
                    if (relative.isAbsolute() || relative.startsWith("..")
                            || normalizedName.startsWith("/") || normalizedName.matches("^[A-Za-z]:.*")) {
                        throw new IOException("Backup contains an unsafe path: " + rawName);
                    }
                    if (relative.toString().isBlank()) {
                        zip.closeEntry();
                        continue;
                    }
                    String key = relative.toString().replace('\\', '/');
                    if (!names.add(key)) throw new IOException("Backup contains duplicate paths: " + rawName);
                    Path output = target.resolve(relative).normalize();
                    if (!output.startsWith(target)) throw new IOException("Backup path escapes the restore directory.");
                    if (entry.isDirectory()) {
                        Files.createDirectories(output);
                    } else {
                        Files.createDirectories(output.getParent());
                        try (OutputStream out = Files.newOutputStream(output)) {
                            int read;
                            while ((read = zip.read(buffer)) != -1) {
                                expandedBytes += read;
                                if (expandedBytes > 100L * 1024 * 1024 * 1024) {
                                    throw new IOException("Backup expands beyond the 100 GiB safety limit.");
                                }
                                out.write(buffer, 0, read);
                            }
                        }
                    }
                    zip.closeEntry();
                }
            }

            Path metadata = target.resolve("instance.json");
            if (!Files.isRegularFile(metadata)) throw new IOException("This ZIP is not a Vanta instance backup (instance.json is missing).");
            ObjectMapper mapper = new ObjectMapper();
            Instance original;
            try {
                original = mapper.readValue(metadata.toFile(), Instance.class);
            } catch (Exception ex) {
                throw new IOException("The backup's instance metadata is invalid.", ex);
            }
            if (original.getMinecraftVersion() == null || original.getMinecraftVersion().isBlank()
                    || original.getLoader() == null || original.getLoader().isBlank()) {
                throw new IOException("The backup is missing Minecraft version or loader metadata.");
            }
            Instance restored = new Instance(id, requestedName.trim(), original.getMinecraftVersion(),
                    original.getLoader(), original.getLoaderVersion(), target, original.getIcon());
            mapper.writerWithDefaultPrettyPrinter().writeValue(metadata.toFile(), restored);
            Files.deleteIfExists(marker);
            completed = true;
            return restored;
        } finally {
            if (!completed) {
                try (var walk = Files.walk(target)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
                    });
                } catch (IOException ignored) { }
            }
        }
    }

}
