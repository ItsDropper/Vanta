package org.example.launcher.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class ServerHistoryEntry {

    private final String host;
    private final int port;
    private final String instanceId;
    private final String instanceName;
    private final long lastPlayed;

    @JsonCreator
    public ServerHistoryEntry(
            @JsonProperty("host") String host,
            @JsonProperty("port") int port,
            @JsonProperty("instanceId") String instanceId,
            @JsonProperty("instanceName") String instanceName,
            @JsonProperty("lastPlayed") long lastPlayed
    ) {
        this.host = host;
        this.port = port;
        this.instanceId = instanceId;
        this.instanceName = instanceName;
        this.lastPlayed = lastPlayed;
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getInstanceId() { return instanceId; }
    public String getInstanceName() { return instanceName; }
    public long getLastPlayed() { return lastPlayed; }

    public String getAddress() {
        return port == 25565 ? host : host + ":" + port;
    }

    public String getDisplayName() {
        if (host == null || host.isBlank()) {
            return "Minecraft Server";
        }

        String normalized = host.trim()
                .replaceAll("^\\.+|\\.+$", "");

        String[] parts = normalized.split("\\.");
        if (parts.length == 0) {
            return "Minecraft Server";
        }

        int index = Math.max(0, parts.length - 2);
        String name = parts[index]
                .replace('-', ' ')
                .replace('_', ' ')
                .trim();

        if (name.isBlank()) {
            return "Minecraft Server";
        }

        String[] words = name.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }

            if (result.length() > 0) {
                result.append(' ');
            }

            String lower = word.toLowerCase();

            if (lower.equals("pvp")) {
                result.append("PvP");
            } else if (lower.equals("pve")) {
                result.append("PvE");
            } else if (lower.equals("smp")) {
                result.append("SMP");
            } else if (lower.equals("hq")) {
                result.append("HQ");
            } else if (lower.equals("mc")) {
                result.append("MC");
            } else {
                result.append(
                        Character.toUpperCase(lower.charAt(0))
                ).append(lower.substring(1));
            }
        }

        return result.length() > 0
                ? result.toString()
                : "Minecraft Server";
    }
}
