package org.example.launcher.modrinth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ModrinthSearchHit {

    @JsonProperty("project_id")
    private String projectId;

    @JsonProperty("project_type")
    private String projectType;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("author")
    private String author;

    @JsonProperty("slug")
    private String slug;

    @JsonProperty("license")
    private String license;

    @JsonProperty("downloads")
    private int downloads;

    @JsonProperty("follows")
    private int follows;

    @JsonProperty("icon_url")
    private String iconUrl;

    public ModrinthSearchHit() {
    }

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

    public String getSlug() {
        return slug;
    }

    public String getLicense() {
        return license;
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
}