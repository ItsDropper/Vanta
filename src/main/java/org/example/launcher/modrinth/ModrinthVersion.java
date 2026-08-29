package org.example.launcher.modrinth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.example.launcher.modrinth.ModrinthFile;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ModrinthVersion {

    @JsonProperty("id")
    private String id;

    @JsonProperty("project_id")
    private String projectId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("version_number")
    private String versionNumber;

    @JsonProperty("version_type")
    private String versionType;

    @JsonProperty("game_versions")
    private List<String> gameVersions;

    @JsonProperty("loaders")
    private List<String> loaders;

    @JsonProperty("featured")
    private boolean featured;

    @JsonProperty("downloads")
    private int downloads;

    @JsonProperty("files")
    private List<ModrinthFile> files;

    // =============================================================
    // GETTERS
    // =============================================================

    public String getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getVersionNumber() {
        return versionNumber;
    }

    public String getVersionType() {
        return versionType;
    }

    public List<String> getGameVersions() {
        return gameVersions;
    }

    public List<String> getLoaders() {
        return loaders;
    }

    public boolean isFeatured() {
        return featured;
    }

    public int getDownloads() {
        return downloads;
    }

    public List<ModrinthFile> getFiles() {
        return files;
    }
}

