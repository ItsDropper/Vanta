package org.example.launcher.service;

import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.entities.RichPresence;
import org.example.launcher.model.Instance;
import org.example.ui.LauncherSettings;



public final class DiscordPresenceService {

    private static final long VANTA_DISCORD_CLIENT_ID = 1555578319720816660L;

    private IPCClient client;
    private long connectedClientId;

    public synchronized void update(Instance instance) {
        if (!LauncherSettings.isDiscordPresenceEnabled()) {
            clear();
            return;
        }

        if (instance == null) {
            return;
        }

        try {
            ensureConnected(VANTA_DISCORD_CLIENT_ID);

            String details = LauncherSettings.isDiscordShowInstanceEnabled()
                    ? instance.getName()
                    : "Minecraft";

            StringBuilder stateBuilder = new StringBuilder();

            if (LauncherSettings.isDiscordShowVersionEnabled()) {
                stateBuilder.append(instance.getMinecraftVersion());
            }

            if (LauncherSettings.isDiscordShowLoaderEnabled()) {
                if (stateBuilder.length() > 0) {
                    stateBuilder.append(" • ");
                }
                stateBuilder.append(instance.getDisplayLoader());
            }

            String state = stateBuilder.length() > 0
                    ? stateBuilder.toString()
                    : "Minecraft";

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
                            .setStartTimestamp(System.currentTimeMillis())
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
        client.connect();
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
