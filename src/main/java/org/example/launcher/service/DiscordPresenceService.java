package org.example.launcher.service;

import com.jagrosh.discordipc.DiscordBuild;
import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.entities.RichPresence;
import org.example.launcher.model.Instance;
import org.example.ui.LauncherSettings;

import java.time.OffsetDateTime;

public final class DiscordPresenceService {

    private IPCClient client;
    private long connectedClientId;

    public synchronized void update(Instance instance) {
        if (!LauncherSettings.isDiscordPresenceEnabled()) {
            clear();
            return;
        }

        String clientIdText = LauncherSettings.getDiscordClientId();
        if (clientIdText == null || clientIdText.isBlank() || instance == null) {
            return;
        }

        long clientId;
        try {
            clientId = Long.parseLong(clientIdText.trim());
        } catch (NumberFormatException ignored) {
            return;
        }

        try {
            ensureConnected(clientId);

            String details = LauncherSettings.isDiscordShowInstanceEnabled()
                    ? instance.getName()
                    : "Minecraft";

            String state = instance.getMinecraftVersion()
                    + " • "
                    + instance.getDisplayLoader();

            if (LauncherSettings.isDiscordShowPlaytimeEnabled()) {
                state += " • "
                        + InstanceUsageManager.formatPlaytime(
                                InstanceUsageManager.getPlaytimeSeconds(instance)
                        );
            }

            RichPresence presence =
                    new RichPresence.Builder()
                            .setDetails(details)
                            .setState(state)
                            .setStartTimestamp(OffsetDateTime.now())
                            .setLargeImageWithTooltip("vanta", "Vanta Launcher")
                            .setInstance(true)
                            .build();

            client.sendRichPresence(presence);

        } catch (Throwable ignored) {
            disconnectQuietly();
        }
    }

    public synchronized void clear() {
        if (client == null) return;

        try {
            client.sendRichPresence(null);
        } catch (Throwable ignored) {
        }
    }

    public synchronized void shutdown() {
        disconnectQuietly();
    }

    private void ensureConnected(long clientId) throws Exception {
        if (client != null && connectedClientId == clientId) {
            return;
        }

        disconnectQuietly();

        client = new IPCClient(clientId);
        connectedClientId = clientId;
        client.connect(DiscordBuild.ANY);
    }

    private void disconnectQuietly() {
        if (client != null) {
            try {
                client.close();
            } catch (Throwable ignored) {
            }
        }

        client = null;
        connectedClientId = 0;
    }
}
