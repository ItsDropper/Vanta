package dev.itsdropper.vanta.launcher.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VersionManifest {

    public String id;

    /** Mojang manifest category: release, snapshot, old_beta, or old_alpha. */
    public String type;

    public String inheritsFrom;

    /** Legacy flat game-argument format used by historical clients. */
    public String minecraftArguments;

    public String mainClass;

    public List<Library> libraries;

    public JavaVersion javaVersion;

    public AssetIndex assetIndex;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JavaVersion {

        public int majorVersion;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssetIndex {

        public String id;

        public String url;
    }
}

