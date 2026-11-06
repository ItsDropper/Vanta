package dev.itsdropper.vanta.launcher.modrinth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ModrinthTag {
    @JsonProperty("name")
    private String name;

    @JsonProperty("icon")
    private String icon;

    @JsonProperty("project_type")
    private String projectType;

    @JsonProperty("header")
    private String header;

    @JsonProperty("supported_project_types")
    private List<String> supportedProjectTypes;

    @JsonProperty("version")
    private String version;

    @JsonProperty("version_type")
    private String versionType;

    @JsonProperty("date")
    private String date;

    @JsonProperty("major")
    private boolean major;

    public ModrinthTag() {
    }

    public String getName() { return name; }
    public String getIcon() { return icon; }
    public String getProjectType() { return projectType; }
    public String getHeader() { return header; }
    public List<String> getSupportedProjectTypes() {
        return supportedProjectTypes == null ? List.of() : supportedProjectTypes;
    }
    public String getVersion() { return version; }
    public String getVersionType() { return versionType; }
    public String getDate() { return date; }
    public boolean isMajor() { return major; }
}
