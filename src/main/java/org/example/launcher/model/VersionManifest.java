package org.example.launcher.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VersionManifest {

    public String id;

    public String inheritsFrom;

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

