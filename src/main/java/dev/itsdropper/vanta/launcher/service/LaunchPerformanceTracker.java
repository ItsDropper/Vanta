package dev.itsdropper.vanta.launcher.service;

import dev.itsdropper.vanta.launcher.model.Instance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * In-memory measurements for Vanta's launch-data preparation stage.
 * This intentionally does not claim to measure Minecraft's mod initialization.
 */
public final class LaunchPerformanceTracker {

    public record Sample(String loader, boolean modded, boolean optimized, long durationNanos) { }

    public record Summary(int baselineSamples, int optimizedSamples,
                          long baselineMedianMillis, long optimizedMedianMillis) { }

    private static final int MAX_SAMPLES = 120;
    private static final List<Sample> SAMPLES = new ArrayList<>();

    private LaunchPerformanceTracker() { }

    public static synchronized void record(Instance instance, boolean optimized, long durationNanos) {
        if (instance == null || durationNanos < 0) return;
        String loader = instance.getLoader() == null ? "Unknown" : instance.getLoader();
        boolean modded = !loader.equalsIgnoreCase("Vanilla");
        SAMPLES.add(new Sample(loader, modded, optimized, durationNanos));
        if (SAMPLES.size() > MAX_SAMPLES) {
            SAMPLES.remove(0);
        }
    }

    public static synchronized Summary summary(boolean modded) {
        List<Long> baseline = SAMPLES.stream()
                .filter(sample -> sample.modded() == modded && !sample.optimized())
                .map(Sample::durationNanos)
                .sorted()
                .toList();
        List<Long> optimized = SAMPLES.stream()
                .filter(sample -> sample.modded() == modded && sample.optimized())
                .map(Sample::durationNanos)
                .sorted()
                .toList();
        return new Summary(
                baseline.size(),
                optimized.size(),
                medianMillis(baseline),
                medianMillis(optimized)
        );
    }

    public static synchronized List<Sample> recentSamples() {
        return List.copyOf(SAMPLES);
    }

    public static synchronized void clear() {
        SAMPLES.clear();
    }

    private static long medianMillis(List<Long> values) {
        if (values.isEmpty()) return -1L;
        int middle = values.size() / 2;
        long median = values.size() % 2 == 0
                ? (values.get(middle - 1) + values.get(middle)) / 2
                : values.get(middle);
        return median / 1_000_000L;
    }
}
