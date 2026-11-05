package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;

/** Installs the official Mojang client mappings for a VantaLoader instance. */
public final class MinecraftMappingsInstaller {
    private static final long MAX_MAPPING_BYTES = 64L * 1024 * 1024;

    private MinecraftMappingsInstaller() { }

    public static Path install(Instance instance, JsonNode versionMetadata) throws Exception {
        if (instance == null || versionMetadata == null) {
            throw new IllegalArgumentException("Instance and Minecraft metadata are required.");
        }
        JsonNode download = versionMetadata.path("downloads").path("client_mappings");
        String url = download.path("url").asText("");
        String sha1 = download.path("sha1").asText("");
        long expectedSize = download.path("size").asLong(-1L);
        if (url.isBlank() || !sha1.matches("[0-9a-fA-F]{40}")
                || expectedSize <= 0 || expectedSize > MAX_MAPPING_BYTES) {
            throw new IOException("This Minecraft version does not provide valid official client mappings.");
        }

        URI mappingUri;
        try {
            mappingUri = URI.create(url);
        } catch (IllegalArgumentException invalidUrl) {
            throw new IOException("Minecraft client mappings URL is invalid.", invalidUrl);
        }
        if (!"https".equalsIgnoreCase(mappingUri.getScheme())
                || !"piston-data.mojang.com".equalsIgnoreCase(mappingUri.getHost())
                || mappingUri.getUserInfo() != null || mappingUri.getFragment() != null) {
            throw new IOException("Minecraft client mappings must come from Mojang's HTTPS asset host.");
        }

        Path target = instance.getDirectory().resolve("minecraft").resolve("client_mappings.txt")
                .toAbsolutePath().normalize();
        Path root = instance.getDirectory().toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Resolved Minecraft mappings path escapes the instance directory.");
        }
        DownloadUtil.downloadFile(url, target, sha1);
        long actualSize = Files.size(target);
        if (actualSize != expectedSize || actualSize > MAX_MAPPING_BYTES) {
            Files.deleteIfExists(target);
            throw new IOException("Downloaded Minecraft mappings have an unexpected file size.");
        }
        return target;
    }
}
