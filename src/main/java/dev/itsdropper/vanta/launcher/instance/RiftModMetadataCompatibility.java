package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Applies narrowly-scoped compatibility normalization for original Rift 1.13.
 *
 * Rift's Gson deserializer leaves Listener.side null when a listener is
 * represented as an object and omits "side", despite the field initializer
 * declaring BOTH. Rift later dereferences that value during mod initialization.
 *
 * The normalizer edits only the instance-local copy of a mod JAR, keeps a
 * one-time original backup beside it, and is idempotent.
 */
public final class RiftModMetadataCompatibility {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String METADATA_ENTRY = "riftmod.json";
    private static final String BACKUP_SUFFIX = ".vanta-original";
    private static final int BUFFER_SIZE = 16 * 1024;

    private RiftModMetadataCompatibility() {
    }

    /**
     * Normalizes every top-level mod JAR in the supplied instance mods folder.
     *
     * @return number of JARs whose metadata was changed
     */
    public static int normalizeModsDirectory(Path modsDirectory) throws IOException {
        if (modsDirectory == null || !Files.isDirectory(modsDirectory)) {
            return 0;
        }

        int changed = 0;
        try (var files = Files.list(modsDirectory)) {
            for (Path jar : files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted()
                    .toList()) {
                if (normalizeJar(jar)) {
                    changed++;
                }
            }
        }
        return changed;
    }

    private static boolean normalizeJar(Path jarPath) throws IOException {
        JsonNode metadata;
        try (ZipFile jar = new ZipFile(jarPath.toFile())) {
            var entry = jar.getEntry(METADATA_ENTRY);
            if (entry == null) {
                return false;
            }
            try (InputStream input = jar.getInputStream(entry)) {
                metadata = MAPPER.readTree(input);
            }
        }

        if (!(metadata instanceof ObjectNode root)) {
            throw new IOException("Invalid Rift metadata in " + jarPath.getFileName()
                    + ": root must be a JSON object.");
        }

        JsonNode listenersNode = root.get("listeners");
        if (!(listenersNode instanceof ArrayNode listeners)) {
            return false;
        }

        int defaultsApplied = 0;
        for (JsonNode listenerNode : listeners) {
            if (listenerNode instanceof ObjectNode listener
                    && (!listener.has("side") || listener.get("side").isNull())) {
                listener.put("side", "both");
                defaultsApplied++;
            }
        }
        if (defaultsApplied == 0) {
            return false;
        }

        Path backup = jarPath.resolveSibling(jarPath.getFileName() + BACKUP_SUFFIX);
        Path temporary = Files.createTempFile(jarPath.getParent(), "vanta-rift-", ".jar.tmp");
        boolean replaced = false;
        try {
            try (ZipFile source = new ZipFile(jarPath.toFile());
                 OutputStream fileOutput = Files.newOutputStream(temporary);
                 ZipOutputStream output = new ZipOutputStream(fileOutput)) {
                Enumeration<? extends ZipEntry> entries = source.entries();
                byte[] buffer = new byte[BUFFER_SIZE];
                while (entries.hasMoreElements()) {
                    ZipEntry sourceEntry = entries.nextElement();
                    if (isSignatureFile(sourceEntry.getName())) {
                        // The archive is being transformed; stale JAR signatures
                        // must not remain and cause verification failures.
                        continue;
                    }

                    ZipEntry targetEntry = new ZipEntry(sourceEntry.getName());
                    if (sourceEntry.getTime() >= 0) {
                        targetEntry.setTime(sourceEntry.getTime());
                    }
                    output.putNextEntry(targetEntry);

                    if (METADATA_ENTRY.equals(sourceEntry.getName())) {
                        output.write(MAPPER.writerWithDefaultPrettyPrinter()
                                .writeValueAsBytes(root));
                    } else if (!sourceEntry.isDirectory()) {
                        try (InputStream input = source.getInputStream(sourceEntry)) {
                            int read;
                            while ((read = input.read(buffer)) != -1) {
                                output.write(buffer, 0, read);
                            }
                        }
                    }
                    output.closeEntry();
                }
            }

            // Preserve the exact pre-normalization mod before replacing it.
            if (!Files.exists(backup)) {
                Files.copy(jarPath, backup);
            }

            try {
                Files.move(temporary, jarPath, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, jarPath, StandardCopyOption.REPLACE_EXISTING);
            }
            replaced = true;
            System.out.println("[Vanta Rift Compatibility] Added side=both to "
                    + defaultsApplied + " listener(s) in " + jarPath.getFileName()
                    + "; original preserved as " + backup.getFileName());
            return true;
        } finally {
            if (!replaced) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static boolean isSignatureFile(String entryName) {
        String normalized = entryName.replace('\\', '/').toUpperCase(Locale.ROOT);
        if (!normalized.startsWith("META-INF/")) {
            return false;
        }
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        return fileName.startsWith("SIG-")
                || fileName.endsWith(".SF")
                || fileName.endsWith(".RSA")
                || fileName.endsWith(".DSA")
                || fileName.endsWith(".EC");
    }
}
