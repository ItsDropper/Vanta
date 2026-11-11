package dev.itsdropper.vanta.launcher.service;

import java.nio.file.Path;

/**
 * Transfers a Modrinth artifact to the install service's private staging path.
 * Implementations must either finish the transfer or throw without publishing
 * the destination as an installed mod.
 */
@FunctionalInterface
interface ModrinthArtifactDownloader {
    void download(String url, Path destination) throws Exception;
}
