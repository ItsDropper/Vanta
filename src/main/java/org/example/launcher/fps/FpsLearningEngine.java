package org.example.launcher.fps;

import org.example.launcher.MinecraftLocator;
import org.example.launcher.model.Instance;
import org.example.launcher.instance.InstanceManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FpsLearningEngine {

    private static final int FEATURE_COUNT = 11;

    private FpsLearningEngine() {
    }

    public static Selection select(
            FpsHardwareProfile hardware,
            String minecraftVersion
    ) {
        List<Sample> samples =
                collectSamples(minecraftVersion);

        List<FpsTuningProfile> candidates =
                List.of(
                        FpsTuningProfile.maxFps(),
                        FpsTuningProfile.highFps(),
                        FpsTuningProfile.balanced()
                );

        if (samples.size() < 3) {
            return new Selection(
                    FpsTuningProfile.maxFps(),
                    samples.size(),
                    false
            );
        }

        double[] weights =
                train(samples);

        FpsTuningProfile best =
                candidates.stream()
                        .max(
                                Comparator.comparingDouble(
                                        profile ->
                                                predict(
                                                        weights,
                                                        features(
                                                                profile,
                                                                hardware
                                                        )
                                                )
                                )
                        )
                        .orElse(
                                FpsTuningProfile.maxFps()
                        );

        return new Selection(
                best,
                samples.size(),
                true
        );
    }

    private static List<Sample> collectSamples(
            String minecraftVersion
    ) {
        List<Sample> samples =
                new ArrayList<>();

        for (Instance instance :
                InstanceManager.discoverInstances()) {

            if (instance == null
                    || instance.getMinecraftVersion() == null
                    || !instance.getMinecraftVersion()
                    .equalsIgnoreCase(minecraftVersion)) {
                continue;
            }

            Path options =
                    instance.getDirectory()
                            .resolve("options.txt");

            if (!Files.isRegularFile(options)) {
                continue;
            }

            Map<String, String> values =
                    readOptions(options);

            Path benchmarks =
                    instance.getDirectory()
                            .resolve("config")
                            .resolve("performanceoverlay")
                            .resolve("benchmarks");

            if (!Files.isDirectory(benchmarks)) {
                continue;
            }

            try (var stream = Files.list(benchmarks)) {
                stream.filter(Files::isRegularFile)
                        .filter(path ->
                                path.getFileName()
                                        .toString()
                                        .toLowerCase(Locale.ROOT)
                                        .endsWith(".csv")
                        )
                        .forEach(path -> {
                            Double fps =
                                    readAverageFps(path);

                            if (fps != null
                                    && fps > 0.0) {
                                samples.add(
                                        new Sample(
                                                featureVector(
                                                        values
                                                ),
                                                fps
                                        )
                                );
                            }
                        });
            } catch (IOException ignored) {
            }
        }

        return samples;
    }

    private static double[] train(
            List<Sample> samples
    ) {
        double[] weights =
                new double[FEATURE_COUNT + 1];

        Arrays.fill(weights, 0.0);

        for (int epoch = 0; epoch < 900; epoch++) {
            for (Sample sample : samples) {
                double prediction =
                        predict(
                                weights,
                                sample.features()
                        );

                double error =
                        prediction - sample.target();

                weights[0] -=
                        0.00002 * error;

                for (int i = 0; i < FEATURE_COUNT; i++) {
                    weights[i + 1] -=
                            0.00002
                                    * error
                                    * sample.features()[i];
                }
            }
        }

        return weights;
    }

    private static double predict(
            double[] weights,
            double[] features
    ) {
        double result = weights[0];

        for (int i = 0; i < features.length; i++) {
            result +=
                    weights[i + 1]
                            * features[i];
        }

        return result;
    }

    private static double[] features(
            FpsTuningProfile profile,
            FpsHardwareProfile hardware
    ) {
        return new double[]{
                profile.renderDistance() / 32.0,
                profile.simulationDistance() / 32.0,
                profile.entityDistanceScaling() / 5.0,
                profile.particles() / 2.0,
                profile.mipmapLevels() / 4.0,
                profile.graphicsMode() / 2.0,
                profile.enableVsync() ? 1.0 : 0.0,
                profile.renderClouds() ? 1.0 : 0.0,
                profile.entityShadows() ? 1.0 : 0.0,
                hardware.logicalProcessors() / 32.0,
                Math.min(
                        hardware.memoryMb(),
                        65536
                ) / 65536.0
        };
    }

    private static double[] featureVector(
            Map<String, String> values
    ) {
        return new double[]{
                number(values, "renderDistance", 12) / 32.0,
                number(values, "simulationDistance", 12) / 32.0,
                number(values, "entityDistanceScaling", 1) / 5.0,
                number(values, "particles", 1) / 2.0,
                number(values, "mipmapLevels", 4) / 4.0,
                number(values, "graphicsMode", 1) / 2.0,
                bool(values, "enableVsync"),
                bool(values, "renderClouds"),
                bool(values, "entityShadows"),
                0.5,
                0.5
        };
    }

    private static Map<String, String> readOptions(
            Path path
    ) {
        Map<String, String> values =
                new java.util.HashMap<>();

        try {
            for (String line :
                    Files.readAllLines(
                            path,
                            StandardCharsets.UTF_8
                    )) {

                int separator =
                        line.indexOf(':');

                if (separator <= 0) {
                    continue;
                }

                values.put(
                        line.substring(0, separator),
                        line.substring(separator + 1)
                );
            }
        } catch (IOException ignored) {
        }

        return values;
    }

    private static double number(
            Map<String, String> values,
            String key,
            double fallback
    ) {
        try {
            return Double.parseDouble(
                    values.getOrDefault(
                            key,
                            Double.toString(fallback)
                    )
            );
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double bool(
            Map<String, String> values,
            String key
    ) {
        return Boolean.parseBoolean(
                values.getOrDefault(
                        key,
                        "false"
                )
        ) ? 1.0 : 0.0;
    }

    private static Double readAverageFps(
            Path csv
    ) {
        try {
            List<String> lines =
                    Files.readAllLines(
                            csv,
                            StandardCharsets.UTF_8
                    );

            if (lines.size() < 2) {
                return null;
            }

            String header =
                    lines.get(0);

            String delimiter =
                    header.contains("\t")
                            ? "\t"
                            : header.contains(";")
                            ? ";"
                            : ",";

            String[] columns =
                    header.split(
                            delimiter,
                            -1
                    );

            int fpsColumn = -1;

            for (int i = 0; i < columns.length; i++) {
                String normalized =
                        columns[i]
                                .trim()
                                .replace(""", "")
                                .toLowerCase(Locale.ROOT);

                if (normalized.equals("fps")
                        || normalized.equals("average_fps")
                        || normalized.equals("avg_fps")) {
                    fpsColumn = i;
                    break;
                }
            }

            if (fpsColumn < 0) {
                return null;
            }

            double sum = 0.0;
            int count = 0;

            for (int i = 1; i < lines.size(); i++) {
                String[] values =
                        lines.get(i)
                                .split(
                                        delimiter,
                                        -1
                                );

                if (fpsColumn >= values.length) {
                    continue;
                }

                try {
                    double fps =
                            Double.parseDouble(
                                    values[fpsColumn]
                                            .trim()
                                            .replace(""", "")
                            );

                    if (Double.isFinite(fps)
                            && fps > 0.0
                            && fps < 100000.0) {
                        sum += fps;
                        count++;
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            return count == 0
                    ? null
                    : sum / count;

        } catch (IOException ignored) {
            return null;
        }
    }

    public record Selection(
            FpsTuningProfile profile,
            int trainingSamples,
            boolean learned
    ) {
    }

    private record Sample(
            double[] features,
            double target
    ) {
    }
}
