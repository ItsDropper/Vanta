package dev.itsdropper.vanta.launcher.modrinth;

import dev.itsdropper.vanta.json.annotation.JsonIgnoreProperties;
import dev.itsdropper.vanta.json.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ModrinthLicense {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("url")
    private String url;

    public ModrinthLicense() {
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }
}