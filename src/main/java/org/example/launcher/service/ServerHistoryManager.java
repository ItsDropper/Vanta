package org.example.launcher.service;

import com.fasterxml.jackson.core.type.TypeReference;
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

public final class ServerHistoryManager {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final Path FILE =
            MinecraftLocator.getVantaDirectory().resolve("server-history.json");

    private static final int MAX_ENTRIES = 10;

    private ServerHistoryManager() {
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
            return MAPPER.readValue(
                    FILE.toFile(),
                    new TypeReference<List<ServerHistoryEntry>>() {}
            );
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private static void save(List<ServerHistoryEntry> entries) {
        try {
            Files.createDirectories(FILE.getParent());
            MAPPER.writeValue(FILE.toFile(), entries);
        } catch (IOException ignored) {
        }
    }
}
