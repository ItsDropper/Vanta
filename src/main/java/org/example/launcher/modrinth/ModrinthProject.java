package org.example.launcher.modrinth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ModrinthProject {

    @JsonProperty("project_id")
    private String projectId;

    @JsonProperty("project_type")
    private String projectType;

    private String title;
    private String description;
    private String author;

    private List<String> categories;
    private List<String> versions;

    private int downloads;
    private int follows;

    @JsonProperty("icon_url")
    private String iconUrl;

    @JsonProperty("date_created")
    private String dateCreated;

    @JsonProperty("date_modified")
    private String dateModified;

    @JsonProperty("latest_version")
    private String latestVersion;

    private String license;
    private String slug;

    // =============================================================
    // GETTERS
    // =============================================================

    public String getProjectId() {
        return projectId;
    }

    public String getProjectType() {
        return projectType;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getAuthor() {
        return author;
    }

    public List<String> getCategories() {
        return categories;
    }

    public List<String> getVersions() {
        return versions;
    }

    public int getDownloads() {
        return downloads;
    }

    public int getFollows() {
        return follows;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public String getDateCreated() {
        return dateCreated;
    }

    public String getDateModified() {
        return dateModified;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getLicense() {
        return license;
    }

    public String getSlug() {
        return slug;
    }

    // =============================================================
    // DEBUG
    // =============================================================

    @Override
    public String toString() {
        return title;
    }
}

