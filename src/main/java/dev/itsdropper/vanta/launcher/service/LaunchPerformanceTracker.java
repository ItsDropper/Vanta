package dev.itsdropper.vanta.launcher.service;

import dev.itsdropper.vanta.launcher.model.Instance;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/**
 * In-memory measurements for Vanta's launch-data preparation stage.
 * Samples are grouped by instance so different loaders and mod sets are never
 * compared as if they were the same workload.
 */
public final class LaunchPerformanceTracker {

    public record Sample(String instanceId, String instanceName, String loader,
                         boolean modded, boolean optimized, long durationNanos) { }

    public record Summary(String instanceId, String instanceName, String loader,
                          int baselineSamples, int optimizedSamples,
                          long baselineMedianMillis, long optimizedMedianMillis) { }

    private static final int MAX_SAMPLES = 120;
    private static final List<Sample> SAMPLES = new ArrayList<>();

    private LaunchPerformanceTracker() { }

    public static synchronized void record(Instance instance, boolean optimized, long durationNanos) {
        if (instance == null || durationNanos < 0) return;
        String loader = instance.getLoader() == null ? "Unknown" : instance.getLoader();
        boolean modded = !loader.equalsIgnoreCase("Vanilla") && hasEnabledMods(instance);
        SAMPLES.add(new Sample(
                instance.getId(),
                instance.getName() == null ? instance.getId() : instance.getName(),
                loader,
                modded,
                optimized,
                durationNanos
        ));
        if (SAMPLES.size() > MAX_SAMPLES) {
            SAMPLES.remove(0);
        }
    }

    private static boolean hasEnabledMods(Instance instance) {
        Path modsDirectory = instance.getDirectory().resolve("mods");
        if (!Files.isDirectory(modsDirectory)) return false;
        try (var entries = Files.list(modsDirectory)) {
            return entries.anyMatch(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar"));
        } catch (IOException ignored) {
            return false;
        }
    }

    /** Summary for the most recently launched instance in the requested group. */
    public static synchronized Summary summaryForMostRecent(boolean modded) {
        for (int i = SAMPLES.size() - 1; i >= 0; i--) {
            Sample sample = SAMPLES.get(i);
            if (sample.modded() == modded) {
                return summaryForInstance(sample.instanceId(), sample.instanceName(), sample.loader());
            }
        }
        return emptySummary();
    }

    public static synchronized Summary summaryForInstance(String instanceId) {
        for (int i = SAMPLES.size() - 1; i >= 0; i--) {
            Sample sample = SAMPLES.get(i);
            if (java.util.Objects.equals(sample.instanceId(), instanceId)) {
                return summaryForInstance(sample.instanceId(), sample.instanceName(), sample.loader());
            }
        }
        return emptySummary();
    }

    private static Summary summaryForInstance(String id, String name, String loader) {
        List<Long> baseline = SAMPLES.stream()
                .filter(sample -> java.util.Objects.equals(sample.instanceId(), id) && !sample.optimized())
                .map(Sample::durationNanos)
                .sorted()
                .toList();
        List<Long> optimized = SAMPLES.stream()
                .filter(sample -> java.util.Objects.equals(sample.instanceId(), id) && sample.optimized())
                .map(Sample::durationNanos)
                .sorted()
                .toList();
        return new Summary(id, name, loader, baseline.size(), optimized.size(),
                medianMillis(baseline), medianMillis(optimized));
    }

    private static Summary emptySummary() {
        return new Summary(null, null, null, 0, 0, -1L, -1L);
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
