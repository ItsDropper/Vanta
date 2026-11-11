package dev.itsdropper.vanta.launcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.model.VersionManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ConcurrentHashMap;
import dev.itsdropper.vanta.ui.LauncherSettings;

public class VersionReader {

    private record CacheEntry(long modified, long size, Object fileKey, VersionManifest manifest) { }

    private static final ConcurrentHashMap<Path, CacheEntry> CACHE = new ConcurrentHashMap<>();

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    public static VersionManifest readVersion(
            Path instanceDirectory
    ) {

        Path json =
                instanceDirectory
                        .resolve("minecraft")
                        .resolve("version.json");

        if (!Files.exists(json)) {
            return null;
        }

        try {
            if (LauncherSettings.isExperimentalLaunchCacheEnabled()) {
                BasicFileAttributes attributes = Files.readAttributes(json, BasicFileAttributes.class);
                CacheEntry cached = CACHE.get(json.toAbsolutePath().normalize());
                if (cached != null
                        && cached.modified() == attributes.lastModifiedTime().toMillis()
                        && cached.size() == attributes.size()
                        && java.util.Objects.equals(cached.fileKey(), attributes.fileKey())) {
                    return cached.manifest();
                }
            }

            JsonNode node =
                    MAPPER.readTree(
                            json.toFile()
                    );

            VersionManifest manifest =
                    MAPPER.treeToValue(
                            node,
                            VersionManifest.class
                    );

            /*
             * Fabric profiles inherit the Minecraft version.
             *
             * The launcher currently stores the complete
             * merged profile, so inheritance should normally
             * already be resolved during installation.
             *
             * This check makes failures explicit instead of
             * producing a mysterious launch error.
             */
            if (manifest.mainClass == null
                    || manifest.mainClass.isBlank()) {

                throw new IllegalStateException(
                        "Version metadata has no mainClass."
                );
            }

            if (manifest.libraries == null) {

                throw new IllegalStateException(
                        "Version metadata has no libraries."
                );
            }

            if (LauncherSettings.isExperimentalLaunchCacheEnabled()) {
                BasicFileAttributes attributes = Files.readAttributes(json, BasicFileAttributes.class);
                Path key = json.toAbsolutePath().normalize();
                if (CACHE.size() > 128) CACHE.clear();
                CACHE.put(key, new CacheEntry(
                        attributes.lastModifiedTime().toMillis(),
                        attributes.size(),
                        attributes.fileKey(),
                        manifest
                ));
            }
            return manifest;

        } catch (IOException e) {

            throw new IllegalStateException(
                    "Failed to read version metadata: "
                            + json,
                    e
            );
        }
    }

    public static void clearCache() {
        CACHE.clear();
    }
}