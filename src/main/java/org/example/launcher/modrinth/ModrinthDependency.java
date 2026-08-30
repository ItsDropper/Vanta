package org.example.launcher.modrinth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ModrinthDependency {

    @JsonProperty("version_id")
    private String versionId;

    @JsonProperty("project_id")
    private String projectId;

    @JsonProperty("file_name")
    private String fileName;

    @JsonProperty("dependency_type")
    private String dependencyType;

    public String getVersionId() {
        return versionId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getDependencyType() {
        return dependencyType;
    }

    public boolean isRequired() {
        return "required".equalsIgnoreCase(
                dependencyType
        );
    }

    public boolean isOptional() {
        return "optional".equalsIgnoreCase(
                dependencyType
        );
    }

    public boolean isIncompatible() {
        return "incompatible".equalsIgnoreCase(
                dependencyType
        );
    }
}