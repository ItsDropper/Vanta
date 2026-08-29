package org.example.launcher.modrinth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ModrinthFile {

    @JsonProperty("url")
    private String url;

    @JsonProperty("filename")
    private String filename;

    @JsonProperty("primary")
    private boolean primary;

    @JsonProperty("size")
    private long size;

    public String getUrl() {
        return url;
    }

    public String getFilename() {
        return filename;
    }

    public boolean isPrimary() {
        return primary;
    }

    public long getSize() {
        return size;
    }
}