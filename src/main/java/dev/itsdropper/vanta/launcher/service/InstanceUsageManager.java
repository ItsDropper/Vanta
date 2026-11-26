package dev.itsdropper.vanta.launcher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.itsdropper.vanta.launcher.MinecraftLocator;
import dev.itsdropper.vanta.launcher.model.Instance;

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

    private static final Path HISTORY_FILE =
            MinecraftLocator.getVantaDirectory().resolve("playtime-history.json");

    /** Seconds played per local calendar day (yyyy-MM-dd), kept from the first launch after this feature. */
    private static final Map<String, Long> DAILY = new java.util.TreeMap<>();

    /** Seconds played per hour of the day, local time. */
    private static final long[] HOURLY = new long[24];

    static {
        load();
        loadHistory();
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
        recordHistory(seconds);
    }

    /** Playtime per day for the last {@code days} days, oldest first. */
    public static synchronized long[] getDailySeconds(int days) {
        long[] values = new long[Math.max(1, days)];
        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = 0; i < values.length; i++) {
            java.time.LocalDate day = today.minusDays(values.length - 1L - i);
            values[i] = DAILY.getOrDefault(day.toString(), 0L);
        }
        return values;
    }

    /** Playtime per hour of the day, local time, index 0 is midnight. */
    public static synchronized long[] getHourlySeconds() {
        return HOURLY.clone();
    }

    private static void recordHistory(long seconds) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        DAILY.merge(now.toLocalDate().toString(), seconds, Long::sum);
        HOURLY[now.getHour()] += seconds;
        try {
            Files.createDirectories(HISTORY_FILE.getParent());
            Map<String, Object> out = new HashMap<>();
            out.put("daily", DAILY);
            out.put("hourly", HOURLY);
            MAPPER.writeValue(HISTORY_FILE.toFile(), out);
        } catch (IOException ignored) {
        }
    }

    private static void loadHistory() {
        try {
            if (!Files.exists(HISTORY_FILE)) return;
            Map<?, ?> loaded = MAPPER.readValue(HISTORY_FILE.toFile(), Map.class);
            if (loaded.get("daily") instanceof Map<?, ?> daily) {
                for (Map.Entry<?, ?> entry : daily.entrySet()) {
                    if (entry.getKey() instanceof String key && entry.getValue() instanceof Number n) {
                        DAILY.put(key, Math.max(0, n.longValue()));
                    }
                }
            }
            if (loaded.get("hourly") instanceof java.util.List<?> hourly) {
                for (int i = 0; i < Math.min(24, hourly.size()); i++) {
                    if (hourly.get(i) instanceof Number n) {
                        HOURLY[i] = Math.max(0, n.longValue());
                    }
                }
            }
        } catch (Exception ignored) {
        }
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
