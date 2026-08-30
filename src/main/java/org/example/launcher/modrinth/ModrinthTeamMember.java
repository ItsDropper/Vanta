package org.example.launcher.modrinth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

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