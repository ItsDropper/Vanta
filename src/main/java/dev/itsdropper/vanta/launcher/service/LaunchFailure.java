package dev.itsdropper.vanta.launcher.service;

public record LaunchFailure(
        String title,
        String description,
        String details
) {
    public static final String SECURITY_BLOCKED_TITLE = "Launch blocked by security policy";
}