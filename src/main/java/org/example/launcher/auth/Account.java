package org.example.launcher.auth;

public class Account {

    public String username;
    public String uuid;
    public String accessToken;

    public Account() {
        // Needed for Jackson
    }

    public Account(String username, String uuid, String accessToken) {
        this.username = username;
        this.uuid = uuid;
        this.accessToken = accessToken;
    }
}