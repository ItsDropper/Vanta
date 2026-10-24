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
}
