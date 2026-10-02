package org.example.launcher.service;

import org.example.launcher.model.Instance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class MultiLaunchService {

    private final AccountService accountService;
    private final Map<String, LaunchService> services = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionStarts = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<LaunchService.LaunchState>> listeners =
            new CopyOnWriteArrayList<>();

    private volatile Instance latestInstance;
    private volatile LaunchFailure lastFailure;
    private volatile Instance failedInstance;

    public MultiLaunchService(AccountService accountService) {
        this.accountService = accountService;
    }

    private LaunchService serviceFor(Instance instance) {
        return services.computeIfAbsent(instance.getId(), id -> {
            LaunchService service = new LaunchService(accountService);

            service.addStateListener(state -> {
                if (state == LaunchService.LaunchState.RUNNING) {
                    latestInstance = instance;
                    sessionStarts.putIfAbsent(instance.getId(), System.currentTimeMillis());
                    InstanceUsageManager.markPlayed(instance);
                }

                if (state == LaunchService.LaunchState.IDLE
                        || state == LaunchService.LaunchState.ERROR) {
                    Long started = sessionStarts.remove(instance.getId());
                    if (started != null) {
                        long seconds = Math.max(
                                0,
                                (System.currentTimeMillis() - started) / 1000
                        );
                        InstanceUsageManager.addPlaytime(instance, seconds);
                    }

                    if (state == LaunchService.LaunchState.ERROR) {
                        lastFailure = service.getLastFailure();
                        failedInstance = service.getFailedInstance();
                    }
                }

                for (Consumer<LaunchService.LaunchState> listener : listeners) {
                    try {
                        listener.accept(state);
                    } catch (Throwable ignored) {
                    }
                }
            });

            return service;
        });
    }

    public void addStateListener(Consumer<LaunchService.LaunchState> listener) {
        listeners.add(listener);
        listener.accept(getState());
    }

    public void removeStateListener(Consumer<LaunchService.LaunchState> listener) {
        listeners.remove(listener);
    }

    public Process launch(Instance instance) throws Exception {
        return launch(instance, null);
    }

    public Process launch(
            Instance instance,
            ServerTarget server
    ) throws Exception {
        if (instance == null) {
            throw new IllegalArgumentException("No Minecraft instance selected.");
        }

        latestInstance = instance;
        return serviceFor(instance).launch(instance, server);
    }

    public void close() {
        Instance instance = latestInstance;
        if (instance != null) {
            close(instance);
        }
    }

    public void close(Instance instance) {
        if (instance == null) return;

        LaunchService service = services.get(instance.getId());
        if (service != null) {
            service.close();
        }
    }

    public long getSessionPlaytimeSeconds(Instance instance) {
        if (instance == null) return 0;

        Long started = sessionStarts.get(instance.getId());
        if (started == null) return 0;

        return Math.max(
                0,
                (System.currentTimeMillis() - started) / 1000
        );
    }

    public boolean isRunning(Instance instance) {
        LaunchService service = instance == null
                ? null
                : services.get(instance.getId());

        return service != null && service.isRunning();
    }

    public LaunchService.LaunchState getState(Instance instance) {
        LaunchService service = instance == null
                ? null
                : services.get(instance.getId());

        return service == null
                ? LaunchService.LaunchState.IDLE
                : service.getState();
    }

    public synchronized LaunchService.LaunchState getState() {
        for (LaunchService service : services.values()) {
            LaunchService.LaunchState state = service.getState();
            if (state == LaunchService.LaunchState.PREPARING
                    || state == LaunchService.LaunchState.STARTING
                    || state == LaunchService.LaunchState.CLOSING) {
                return state;
            }
        }

        for (LaunchService service : services.values()) {
            if (service.getState() == LaunchService.LaunchState.RUNNING) {
                return LaunchService.LaunchState.RUNNING;
            }
        }

        return LaunchService.LaunchState.IDLE;
    }

    public Instance getRunningInstance() {
        if (latestInstance != null && isRunning(latestInstance)) {
            return latestInstance;
        }

        for (Map.Entry<String, LaunchService> entry : services.entrySet()) {
            if (entry.getValue().isRunning()) {
                return entry.getValue().getRunningInstance();
            }
        }

        return null;
    }

    public ServerTarget getRunningServerTarget() {
        for (LaunchService service : services.values()) {
            if (service.isRunning()) {
                ServerTarget target = service.getRunningServerTarget();
                if (target != null) {
                    return target;
                }
            }
        }

        return null;
    }

    public LaunchFailure getLastFailure() {
        return lastFailure;
    }

    public Instance getFailedInstance() {
        return failedInstance;
    }

    public String getRecentOutput() {
        Instance instance = failedInstance != null ? failedInstance : latestInstance;
        if (instance == null) return "";

        LaunchService service = services.get(instance.getId());
        return service == null ? "" : service.getRecentOutput();
    }

    public Process getProcess() {
        Instance instance = latestInstance;
        if (instance == null) return null;

        LaunchService service = services.get(instance.getId());
        return service == null ? null : service.getProcess();
    }
}
