package org.example.launcher.fps;

public record FpsTuningProfile(
        String name,
        int renderDistance,
        int simulationDistance,
        double entityDistanceScaling,
        int particles,
        int mipmapLevels,
        int graphicsMode,
        boolean renderClouds,
        boolean entityShadows,
        int biomeBlendRadius,
        boolean enableVsync
) {
    public static FpsTuningProfile maxFps() {
        return new FpsTuningProfile(
                "MAX_FPS",
                8,
                5,
                0.5,
                2,
                0,
                0,
                false,
                false,
                0,
                false
        );
    }

    public static FpsTuningProfile highFps() {
        return new FpsTuningProfile(
                "HIGH_FPS",
                12,
                6,
                0.65,
                2,
                0,
                0,
                false,
                false,
                0,
                false
        );
    }

    public static FpsTuningProfile balanced() {
        return new FpsTuningProfile(
                "BALANCED",
                16,
                8,
                0.75,
                1,
                2,
                0,
                false,
                false,
                0,
                false
        );
    }
}
