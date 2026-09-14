package org.example.launcher.instance;

import org.example.launcher.model.InstancePreset;

import java.util.List;

public final class InstancePresets {

    private InstancePresets() {
    }

    public static List<InstancePreset> getBuiltInPresets() {
        return List.of(
                createPvpPreset(),
                createPerformancePreset(),
                createVanillaPlusPreset(),
                createShadersPreset(),
                createMinimalPreset()
        );
    }

    private static InstancePreset createPvpPreset() {

        return new InstancePreset(
                "pvp",
                "PvP",
                "A competitive Fabric setup focused on high FPS, low overhead, and useful PvP utilities.",
                List.of(
                        "1.21.11",
                        "1.21.10",
                        "1.21.8",
                        "1.21.5",
                        "1.21.4",
                        "1.21.1",
                        "1.20.6",
                        "26.1.2",
                        "26.1.1",
                        "26.1",
                        "26.2"
                ),
                "Fabric",
                List.of(
                        "sodium",
                        "sodium-extra",
                        "reeses-sodium-options",
                        "lithium",
                        "uXXizFIs",
                        "immediatelyfast",
                        "entityculling",
                        "moreculling",
                        "krypton",
                        "zoomify",
                        "appleskin",
                        "shulkerboxtooltip",
                        "dynamic-fps"
                )
        );
    }

    private static InstancePreset createPerformancePreset() {

        return new InstancePreset(
                "performance",
                "Performance",
                "A lightweight setup focused on maximizing FPS and improving frame-time consistency.",
                List.of(
                        "1.21.11",
                        "1.21.10",
                        "1.21.8",
                        "1.21.5",
                        "1.21.4",
                        "1.21.1",
                        "1.20.6",
                        "26.1.2",
                        "26.1.1",
                        "26.1",
                        "26.2"
                ),
                "Fabric",
                List.of(
                        "sodium",
                        "sodium-extra",
                        "reeses-sodium-options",
                        "lithium",
                        "uXXizFIs",
                        "immediatelyfast",
                        "entityculling",
                        "moreculling",
                        "krypton",
                        "dynamic-fps"
                )
        );
    }

    private static InstancePreset createVanillaPlusPreset() {

        return new InstancePreset(
                "vanilla-plus",
                "Vanilla+",
                "A clean vanilla-like experience with performance improvements and useful quality-of-life features.",
                List.of(
                        "1.21.11",
                        "1.21.10",
                        "1.21.8",
                        "1.21.5",
                        "1.21.4",
                        "1.21.1",
                        "1.20.6",
                        "26.1.2",
                        "26.1.1",
                        "26.1",
                        "26.2"
                ),
                "Fabric",
                List.of(
                        "sodium",
                        "sodium-extra",
                        "reeses-sodium-options",
                        "lithium",
                        "uXXizFIs",
                        "immediatelyfast",
                        "entityculling",
                        "modmenu",
                        "zoomify",
                        "appleskin",
                        "shulkerboxtooltip",
                        "dynamic-fps"
                )
        );
    }

    private static InstancePreset createShadersPreset() {

        return new InstancePreset(
                "shaders",
                "Shaders",
                "A performance-focused Fabric setup with Iris for shader support.",
                List.of(
                        "1.21.11",
                        "1.21.10",
                        "1.21.8",
                        "1.21.5",
                        "1.21.4",
                        "1.21.1",
                        "1.20.6",
                        "26.1.2",
                        "26.1.1",
                        "26.1",
                        "26.2"
                ),
                "Fabric",
                List.of(
                        "sodium",
                        "sodium-extra",
                        "reeses-sodium-options",
                        "lithium",
                        "uXXizFIs",
                        "immediatelyfast",
                        "entityculling",
                        "modernfix",
                        "iris",
                        "modmenu",
                        "dynamic-fps"
                )
        );
    }

    private static InstancePreset createMinimalPreset() {

        return new InstancePreset(
                "minimal",
                "Minimal",
                "A small Fabric setup containing only the essentials for a fast and clean Minecraft installation.",
                List.of(
                        "1.21.11",
                        "1.21.10",
                        "1.21.8",
                        "1.21.5",
                        "1.21.4",
                        "1.21.1",
                        "1.20.6",
                        "26.1.2",
                        "26.1.1",
                        "26.1",
                        "26.2"
                ),
                "Fabric",
                List.of(
                        "sodium",
                        "lithium",
                        "modmenu"
                )
        );
    }
}

