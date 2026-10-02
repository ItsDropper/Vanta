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
}
