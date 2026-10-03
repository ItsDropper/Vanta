package org.example.launcher.fps;

import org.example.launcher.model.Instance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FpsOptionsOptimizer {

    private FpsOptionsOptimizer() {
    }

    public static void optimize(
            Instance instance,
            FpsHardwareProfile hardware
    ) throws IOException {

        if (instance == null) {
            throw new IllegalArgumentException("Instance cannot be null.");
        }

        Path options =
                instance.getDirectory().resolve("options.txt");

        Map<String, String> optimized =
                new LinkedHashMap<>();

        optimized.put("enableVsync", "false");
        optimized.put("graphicsMode", "0");
        optimized.put("renderClouds", "false");
        optimized.put("entityShadows", "false");
        optimized.put("biomeBlendRadius", "0");
        optimized.put("particles", "1");
        optimized.put("mipmapLevels", "0");
        optimized.put("entityDistanceScaling", "0.75");
        optimized.put(
                "renderDistance",
                chooseRenderDistance(hardware)
        );
        optimized.put(
                "simulationDistance",
                chooseSimulationDistance(hardware)
        );
        optimized.put("maxFps", "260");

        Map<String, String> existing =
                readOptions(options);

        /*
         * Only performance settings owned by this profile are changed.
         * Keybinds and every unrelated user option remain untouched.
         */
        existing.putAll(optimized);

        StringBuilder output =
                new StringBuilder();

        for (Map.Entry<String, String> entry :
                existing.entrySet()) {

            output.append(entry.getKey())
                    .append(':')
                    .append(entry.getValue())
                    .append(System.lineSeparator());
        }

        Files.writeString(
                options,
                output.toString(),
                StandardCharsets.UTF_8
        );
    }

    private static Map<String, String> readOptions(
            Path options
    ) throws IOException {

        Map<String, String> values =
                new LinkedHashMap<>();

        if (!Files.isRegularFile(options)) {
            return values;
        }

        List<String> lines =
                Files.readAllLines(
                        options,
                        StandardCharsets.UTF_8
                );

        for (String line : lines) {
            int separator = line.indexOf(':');

            if (separator <= 0) {
                continue;
            }

            String key =
                    line.substring(0, separator);

            String value =
                    line.substring(separator + 1);

            if (!key.startsWith("key_")) {
                values.put(key, value);
            } else {
                values.put(key, value);
            }
        }

        return values;
    }

    private static String chooseRenderDistance(
            FpsHardwareProfile hardware
    ) {
        if (hardware.memoryMb() >= 12288
                && hardware.logicalProcessors() >= 8) {
            return "16";
        }

        if (hardware.memoryMb() >= 8192
                && hardware.logicalProcessors() >= 6) {
            return "12";
        }

        return "10";
    }

    private static String chooseSimulationDistance(
            FpsHardwareProfile hardware
    ) {
        if (hardware.logicalProcessors() >= 8
                && hardware.memoryMb() >= 8192) {
            return "8";
        }

        return "6";
    }
}
