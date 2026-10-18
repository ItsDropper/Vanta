package dev.itsdropper.vanta.launcher.service;

public record LaunchFailure(
        String title,
        String description,
        String details
) {
}