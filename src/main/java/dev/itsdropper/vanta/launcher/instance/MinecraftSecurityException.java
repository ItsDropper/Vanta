package dev.itsdropper.vanta.launcher.instance;

/**
 * A launch was refused because Mojang's official metadata does not yet provide
 * a verified, patched native font library for the requested Minecraft version.
 *
 * This is distinct from a damaged instance: reinstalling the instance cannot
 * fix an upstream metadata release that still selects the vulnerable library.
 */
public final class MinecraftSecurityException extends IllegalStateException {
    private final String minecraftVersion;
    private final String artifact;

    public MinecraftSecurityException(String minecraftVersion, String artifact) {
        super("Mojang's current metadata for " + minecraftVersion
                + " contains an unpatched FreeType artifact (" + artifact
                + "); refusing to launch with an unverified native font engine.");
        this.minecraftVersion = minecraftVersion;
        this.artifact = artifact;
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    public String artifact() {
        return artifact;
    }
}
