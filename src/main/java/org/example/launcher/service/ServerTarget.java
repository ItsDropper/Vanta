package org.example.launcher.service;

public record ServerTarget(String host, int port) {

    public ServerTarget {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Server host cannot be empty.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Server port is invalid.");
        }
    }
}
