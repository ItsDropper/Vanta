package org.example.launcher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.launcher.MinecraftLocator;
import org.example.launcher.model.Instance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class InstanceUsageManager {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final Path FILE =
            MinecraftLocator.getVantaDirectory().resolve("instance-usage.json");

    private static final Map<String, Usage> USAGE = new HashMap<>();

    static {
        load();
    }

    private InstanceUsageManager() {
    }

    public static synchronized void markPlayed(Instance instance) {
        if (instance == null) return;

        Usage usage = get(instance);
        usage.lastPlayed = System.currentTimeMillis();
        save();
    }

    public static synchronized void addPlaytime(Instance instance, long seconds) {
        if (instance == null || seconds <= 0) return;

        Usage usage = get(instance);
        usage.playtimeSeconds += seconds;
        usage.lastPlayed = System.currentTimeMillis();
        save();
    }

    public static synchronized long getPlaytimeSeconds(Instance instance) {
        if (instance == null) return 0;
        return get(instance).playtimeSeconds;
    }

    public static synchronized long getLastPlayed(Instance instance) {
        if (instance == null) return 0;
        return get(instance).lastPlayed;
    }

    public static synchronized List<Instance> sortByLastPlayed(List<Instance> instances) {
        List<Instance> sorted = new ArrayList<>(instances);
        sorted.sort(Comparator.comparingLong(
                InstanceUsageManager::getLastPlayed
        ).reversed());
        return sorted;
    }

    public static String formatPlaytime(long seconds) {
        if (seconds < 60) return seconds + "s";

        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m";

        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;

        if (hours < 100) {
            return remainingMinutes == 0
                    ? hours + "h"
                    : hours + "h " + remainingMinutes + "m";
        }

        return hours + "h";
    }

    private static Usage get(Instance instance) {
        return USAGE.computeIfAbsent(instance.getId(), key -> new Usage());
    }

    private static void load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (!Files.exists(FILE)) return;

            Map<?, ?> loaded = MAPPER.readValue(FILE.toFile(), Map.class);
            for (Map.Entry<?, ?> entry : loaded.entrySet()) {
                if (!(entry.getKey() instanceof String key)
                        || !(entry.getValue() instanceof Map<?, ?> value)) {
                    continue;
                }

                Usage usage = new Usage();
                Object lastPlayed = value.get("lastPlayed");
                Object playtime = value.get("playtimeSeconds");

                if (lastPlayed instanceof Number n) {
                    usage.lastPlayed = n.longValue();
                }
                if (playtime instanceof Number n) {
                    usage.playtimeSeconds = Math.max(0, n.longValue());
                }

                USAGE.put(key, usage);
            }
        } catch (Exception ignored) {
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            MAPPER.writeValue(FILE.toFile(), USAGE);
        } catch (IOException ignored) {
        }
    }

    private static final class Usage {
        public long lastPlayed;
        public long playtimeSeconds;
    }
}
