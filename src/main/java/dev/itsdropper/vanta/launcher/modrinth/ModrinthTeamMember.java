package dev.itsdropper.vanta.launcher.modrinth;

import dev.itsdropper.vanta.json.annotation.JsonIgnoreProperties;
import dev.itsdropper.vanta.json.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ModrinthTeamMember {

    @JsonProperty("team_id")
    private String teamId;

    @JsonProperty("user")
    private ModrinthUser user;

    @JsonProperty("role")
    private String role;

    public ModrinthTeamMember() {
    }

    public String getTeamId() {
        return teamId;
    }

    public ModrinthUser getUser() {
        return user;
    }

    public String getRole() {
        return role;
    }
}