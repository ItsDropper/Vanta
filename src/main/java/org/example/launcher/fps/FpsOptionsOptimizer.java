package org.example.launcher.fps;

import org.example.launcher.model.Instance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FpsOptionsOptimizer {

    private FpsOptionsOptimizer() {
    }

    public static void optimize(
            Instance instance,
            FpsHardwareProfile hardware,
            FpsTuningProfile profile
    ) throws IOException {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        if (hardware == null) {
            throw new IllegalArgumentException(
                    "Hardware profile cannot be null."
            );
        }

        if (profile == null) {
            throw new IllegalArgumentException(
                    "FPS tuning profile cannot be null."
            );
        }

        Path options =
                instance.getDirectory()
                        .resolve("options.txt");

        Map<String, String> replacements =
                new LinkedHashMap<>();

        replacements.put(
                "enableVsync",
                Boolean.toString(profile.enableVsync())
        );
        replacements.put(
                "graphicsMode",
                Integer.toString(profile.graphicsMode())
        );
        replacements.put(
                "renderClouds",
                Boolean.toString(profile.renderClouds())
        );
        replacements.put(
                "entityShadows",
                Boolean.toString(profile.entityShadows())
        );
        replacements.put(
                "biomeBlendRadius",
                Integer.toString(profile.biomeBlendRadius())
        );
        replacements.put(
                "particles",
                Integer.toString(profile.particles())
        );
        replacements.put(
                "mipmapLevels",
                Integer.toString(profile.mipmapLevels())
        );
        replacements.put(
                "entityDistanceScaling",
                Double.toString(
                        profile.entityDistanceScaling()
                )
        );
        replacements.put(
                "renderDistance",
                Integer.toString(
                        profile.renderDistance()
                )
        );
        replacements.put(
                "simulationDistance",
                Integer.toString(
                        profile.simulationDistance()
                )
        );

        // 260 is Minecraft's built-in Unlimited value.
        replacements.put("maxFps", "260");

        List<String> lines =
                Files.isRegularFile(options)
                        ? Files.readAllLines(
                                options,
                                StandardCharsets.UTF_8
                        )
                        : new ArrayList<>();

        Map<String, Boolean> written =
                new LinkedHashMap<>();

        for (String key : replacements.keySet()) {
            written.put(key, false);
        }

        List<String> output =
                new ArrayList<>(
                        Math.max(
                                lines.size(),
                                replacements.size()
                        )
                );

        for (String line : lines) {
            int separator = line.indexOf(':');

            if (separator <= 0) {
                output.add(line);
                continue;
            }

            String key =
                    line.substring(
                            0,
                            separator
                    );

            /*
             * Never touch key_* entries. Keybinds are deliberately
             * outside the FPS generator's ownership.
             */
            if (key.startsWith("key_")) {
                output.add(line);
                continue;
            }

            String replacement =
                    replacements.get(key);

            if (replacement == null) {
                output.add(line);
                continue;
            }

            output.add(
                    key
                            + ":"
                            + replacement
            );

            written.put(key, true);
        }

        for (Map.Entry<String, String> entry :
                replacements.entrySet()) {

            if (!written.get(entry.getKey())) {
                output.add(
                        entry.getKey()
                                + ":"
                                + entry.getValue()
                );
            }
        }

        Files.createDirectories(
                options.getParent()
        );

        Files.write(
                options,
                output,
                StandardCharsets.UTF_8
        );
    }
}
