package org.example.launcher.modrinth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ModrinthProject {

    @JsonProperty("id")
    private String id;

    @JsonProperty("project_id")
    private String projectId;

    @JsonProperty("slug")
    private String slug;

    @JsonProperty("project_type")
    private String projectType;

    @JsonProperty("team")
    private String team;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @JsonProperty("body")
    private String body;

    @JsonProperty("icon_url")
    private String iconUrl;

    @JsonProperty("downloads")
    private int downloads;

    @JsonProperty("followers")
    private int followers;

    @JsonProperty("categories")
    private List<String> categories;

    @JsonProperty("versions")
    private List<String> versions;

    @JsonProperty("loaders")
    private List<String> loaders;

    @JsonProperty("date_created")
    private String dateCreated;

    @JsonProperty("date_modified")
    private String dateModified;

    @JsonProperty("license")
    private ModrinthLicense license;

    @JsonProperty("source_url")
    private String sourceUrl;

    @JsonProperty("issues_url")
    private String issuesUrl;

    @JsonProperty("wiki_url")
    private String wikiUrl;

    @JsonProperty("discord_url")
    private String discordUrl;

    public ModrinthProject() {
    }

    public String getId() {
        return id;
    }

    public String getProjectId() {
        return projectId != null
                ? projectId
                : id;
    }

    public String getSlug() {
        return slug;
    }

    public String getProjectType() {
        return projectType;
    }

    public String getTeam() {
        return team;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getBody() {
        return body;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public int getDownloads() {
        return downloads;
    }

    public int getFollows() {
        return followers;
    }

    public List<String> getCategories() {
        return categories;
    }

    public List<String> getVersions() {
        return versions;
    }

    public List<String> getLoaders() {
        return loaders;
    }

    public String getDateCreated() {
        return dateCreated;
    }

    public String getDateModified() {
        return dateModified;
    }

    public ModrinthLicense getLicense() {
        return license;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getIssuesUrl() {
        return issuesUrl;
    }

    public String getWikiUrl() {
        return wikiUrl;
    }

    public String getDiscordUrl() {
        return discordUrl;
    }
}