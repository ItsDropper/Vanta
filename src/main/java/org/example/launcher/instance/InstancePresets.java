package org.example.launcher.instance;

import org.example.launcher.model.InstancePreset;

import java.util.List;
import java.util.Optional;

public final class InstancePresets {

    private static final List<String> DEFAULT_VERSIONS = List.of(
            "26.2",
            "26.1.2",
            "26.1.1",
            "26.1",
            "1.21.11",
            "1.21.10",
            "1.21.8",
            "1.21.5",
            "1.21.4",
            "1.21.1",
            "1.20.6"
    );

    private static final List<InstancePreset> BUILT_IN_PRESETS = List.of(
            createPvpPreset(),
            createPerformancePreset(),
            createVanillaPlusPreset(),
            createShadersPreset(),
            createMinimalPreset(),
            createBuildingPreset()
    );

    private InstancePresets() {
        // Utility class
    }

    public static List<InstancePreset> getBuiltInPresets() {
        return BUILT_IN_PRESETS;
    }

    public static Optional<InstancePreset> getPresetById(
            String id
    ) {

        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        return BUILT_IN_PRESETS.stream()
                .filter(
                        preset ->
                                id.equalsIgnoreCase(
                                        preset.getId()
                                )
                )
                .findFirst();
    }

    private static InstancePreset createPvpPreset() {

        return new InstancePreset(
                "pvp",
                "PvP",
                "A competitive Fabric setup focused on high FPS, low overhead, and useful PvP utilities.",
                DEFAULT_VERSIONS,
                "Fabric",
                List.of(
                        "sodium",
                        "sodium-extra",
                        "reeses-sodium-options",
                        "lithium",
                        "ferrite-core",
                        "immediatelyfast",
                        "entityculling",
                        "moreculling",
                        "krypton",
                        "zoomify",
                        "appleskin",
                        "shulkerboxtooltip",
                        "dynamic-fps",
                        "anchor",
                        "marlow-crystal-optimizer"
                )
        );
    }

    private static InstancePreset createPerformancePreset() {

        return new InstancePreset(
                "performance",
                "Performance",
                "A lightweight setup focused on maximizing FPS and improving frame-time consistency.",
                DEFAULT_VERSIONS,
                "Fabric",
                List.of(
                        "sodium",
                        "sodium-extra",
                        "reeses-sodium-options",
                        "lithium",
                        "ferrite-core",
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
                DEFAULT_VERSIONS,
                "Fabric",
                List.of(
                        "sodium",
                        "sodium-extra",
                        "reeses-sodium-options",
                        "lithium",
                        "ferrite-core",
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
                DEFAULT_VERSIONS,
                "Fabric",
                List.of(
                        "sodium",
                        "sodium-extra",
                        "reeses-sodium-options",
                        "lithium",
                        "ferrite-core",
                        "immediatelyfast",
                        "entityculling",
                        "modernfix",
                        "iris",
                        "modmenu",
                        "dynamic-fps"
                )
        );
    }

    private static InstancePreset createBuildingPreset() {

        return new InstancePreset(
                "building",
                "Building",
                "A creative Fabric setup with tools for building, schematics, world editing, and construction.",
                DEFAULT_VERSIONS,
                "Fabric",
                List.of(
                        "sodium",
                        "sodium-extra",
                        "reeses-sodium-options",
                        "lithium",
                        "ferrite-core",
                        "immediatelyfast",
                        "entityculling",
                        "modmenu",
                        "litematica",
                        "axiom",
                        "worldedit"
                )
        );
    }

    private static InstancePreset createMinimalPreset() {

        return new InstancePreset(
                "minimal",
                "Minimal",
                "A small Fabric setup containing only the essentials for a fast and clean Minecraft installation.",
                DEFAULT_VERSIONS,
                "Fabric",
                List.of(
                        "sodium",
                        "lithium",
                        "modmenu"
                )
        );
    }
}