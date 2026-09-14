package org.example.launcher.instance;

import org.example.launcher.model.InstancePreset;

import java.util.List;

public final class InstancePresets {

    private InstancePresets() {
    }

    public static List<InstancePreset> getBuiltInPresets() {
        return List.of(
                createPvpPreset()
        );
    }

    private static InstancePreset createPvpPreset() {

        return new InstancePreset(
                "pvp",
                "PvP",
                "A lightweight Fabric setup with trusted, popular PvP and performance mods.",
                "1.21.11",
                "Fabric",
                List.of(
                        "sodium",
                        "lithium",
                        "modmenu"
                )
        );
    }
}