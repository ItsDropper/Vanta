package org.example.launcher.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.example.launcher.MinecraftLocator;
import org.example.launcher.model.Instance;
import org.example.launcher.model.ServerHistoryEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ServerHistoryManager {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final Path FILE =
            MinecraftLocator.getVantaDirectory().resolve("server-history.json");

    private static final int MAX_ENTRIES = 10;

    private static final CopyOnWriteArrayList<Runnable> listeners =
            new CopyOnWriteArrayList<>();

    private ServerHistoryManager() {
    }

    public static void addListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public static synchronized void recordConnection(
            Instance instance,
            String host,
            int port
    ) {
        if (instance == null || host == null || host.isBlank()) {
            return;
        }

        List<ServerHistoryEntry> entries = load();
        entries.removeIf(entry ->
                entry.getHost().equalsIgnoreCase(host)
                        && entry.getPort() == port
                        && entry.getInstanceId().equals(instance.getId())
        );

        entries.add(new ServerHistoryEntry(
                host,
                port,
                instance.getId(),
                instance.getName(),
                System.currentTimeMillis()
        ));

        entries.sort(Comparator.comparingLong(
                ServerHistoryEntry::getLastPlayed
        ).reversed());

        if (entries.size() > MAX_ENTRIES) {
            entries = new ArrayList<>(entries.subList(0, MAX_ENTRIES));
        }

        save(entries);

        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (Throwable ignored) {
            }
        }
    }

    public static synchronized List<ServerHistoryEntry> getRecent() {
        return load().stream()
                .sorted(Comparator.comparingLong(
                        ServerHistoryEntry::getLastPlayed
                ).reversed())
                .toList();
    }

    private static List<ServerHistoryEntry> load() {
        if (!Files.exists(FILE)) {
            return new ArrayList<>();
        }

        try {
            JsonNode root = MAPPER.readTree(FILE.toFile());
            List<ServerHistoryEntry> entries = new ArrayList<>();

            if (root == null || root.isNull()) {
                return entries;
            }

            JsonNode values = root.isArray()
                    ? root
                    : root.has("entries") && root.get("entries").isArray()
                            ? root.get("entries")
                            : null;

            if (values == null) {
                return entries;
            }

            for (JsonNode value : values) {
                String host = text(value, "host");
                if (host == null || host.isBlank()) {
                    host = text(value, "address");
                }

                if (host == null || host.isBlank()) {
                    continue;
                }

                int port = value.has("port") && value.get("port").canConvertToInt()
                        ? value.get("port").asInt()
                        : 25565;

                if (port < 1 || port > 65535) {
                    port = 25565;
                }

                String instanceId = text(value, "instanceId");
                String instanceName = text(value, "instanceName");

                long lastPlayed = value.has("lastPlayed")
                        && value.get("lastPlayed").canConvertToLong()
                        ? value.get("lastPlayed").asLong()
                        : 0L;

                entries.add(new ServerHistoryEntry(
                        host,
                        port,
                        instanceId,
                        instanceName,
                        lastPlayed
                ));
            }

            return entries;
        } catch (Exception ex) {
            System.err.println(
                    "Vanta: failed to read server history from "
                            + FILE + ": " + ex.getMessage()
            );
            return new ArrayList<>();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull()
                ? value.asText()
                : null;
    }

    private static void save(List<ServerHistoryEntry> entries) {
        try {
            Files.createDirectories(FILE.getParent());
            MAPPER.writeValue(FILE.toFile(), entries);
        } catch (IOException ignored) {
        }
    }
}
