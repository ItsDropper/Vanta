package org.example.launcher.service;

public record LaunchFailure(
        String title,
        String description,
        String details
) {
}