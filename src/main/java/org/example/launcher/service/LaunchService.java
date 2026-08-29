package org.example.launcher.service;

import org.example.launcher.LaunchData;
import org.example.launcher.LaunchDataBuilder;
import org.example.launcher.MinecraftLauncher;
import org.example.launcher.account.Account;
import org.example.launcher.model.Instance;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class LaunchService {

    public enum LaunchState {
        IDLE,
        PREPARING,
        STARTING,
        RUNNING,
        CLOSING,
        ERROR
    }

    private final AccountService accountService;

    private final List<Consumer<LaunchState>> stateListeners =
            new CopyOnWriteArrayList<>();

    private Process minecraftProcess;

    private LaunchState state =
            LaunchState.IDLE;

    public LaunchService(
            AccountService accountService
    ) {

        this.accountService =
                accountService;
    }

    // =============================================================
    // LISTENERS
    // =============================================================

    public void addStateListener(
            Consumer<LaunchState> listener
    ) {

        stateListeners.add(listener);

        // Immediately give the listener the current state.
        listener.accept(getState());
    }

    public void removeStateListener(
            Consumer<LaunchState> listener
    ) {

        stateListeners.remove(listener);
    }

    // =============================================================
    // LAUNCH
    // =============================================================

    public synchronized Process launch(
            Instance instance
    ) throws Exception {

        if (isRunning()) {
            return minecraftProcess;
        }

        if (instance == null) {

            throw new IllegalArgumentException(
                    "No Minecraft instance selected."
            );
        }

        setState(
                LaunchState.PREPARING
        );

        Account account =
                accountService.getCurrentAccount();

        if (account == null) {

            account =
                    accountService.login();
        }

        System.out.println(
                "Launching instance: "
                        + instance.getName()
        );

        System.out.println(
                "Minecraft version: "
                        + instance.getMinecraftVersion()
        );

        System.out.println(
                "Loader: "
                        + instance.getDisplayLoader()
        );

        LaunchData launchData =
                LaunchDataBuilder.build(
                        instance
                );

        setState(
                LaunchState.STARTING
        );

        minecraftProcess =
                MinecraftLauncher.launch(
                        account,
                        launchData
                );

        setState(
                LaunchState.RUNNING
        );

        monitorProcess(
                minecraftProcess
        );

        return minecraftProcess;
    }

    // =============================================================
    // PROCESS MONITOR
    // =============================================================

    private void monitorProcess(
            Process process
    ) {

        Thread monitor =
                new Thread(() -> {

                    try {

                        process.waitFor();

                    } catch (InterruptedException e) {

                        Thread.currentThread()
                                .interrupt();

                    } finally {

                        synchronized (this) {

                            if (minecraftProcess == process) {

                                minecraftProcess =
                                        null;

                                if (state !=
                                        LaunchState.ERROR) {

                                    setState(
                                            LaunchState.IDLE
                                    );
                                }
                            }
                        }
                    }
                });

        monitor.setDaemon(true);

        monitor.setName(
                "Vanta-Minecraft-Monitor"
        );

        monitor.start();
    }

    // =============================================================
    // CLOSE
    // =============================================================

    public synchronized void close() {

        if (!isRunning()) {

            minecraftProcess =
                    null;

            setState(
                    LaunchState.IDLE
            );

            return;
        }

        setState(
                LaunchState.CLOSING
        );

        Process process =
                minecraftProcess;

        process.destroy();

        Thread cleanup =
                new Thread(() -> {

                    try {

                        if (!process.waitFor(
                                5,
                                TimeUnit.SECONDS
                        )) {

                            process.destroyForcibly();
                        }

                    } catch (InterruptedException e) {

                        Thread.currentThread()
                                .interrupt();

                        process.destroyForcibly();

                    } finally {

                        synchronized (this) {

                            if (minecraftProcess ==
                                    process) {

                                minecraftProcess =
                                        null;

                                setState(
                                        LaunchState.IDLE
                                );
                            }
                        }
                    }
                });

        cleanup.setDaemon(true);

        cleanup.setName(
                "Vanta-Minecraft-Cleanup"
        );

        cleanup.start();
    }

    // =============================================================
    // STATE
    // =============================================================

    public synchronized boolean isRunning() {

        return minecraftProcess != null
                && minecraftProcess.isAlive();
    }

    public synchronized Process getProcess() {

        return minecraftProcess;
    }

    public synchronized LaunchState getState() {

        return state;
    }

    private synchronized void setState(
            LaunchState newState
    ) {

        state =
                newState;

        for (
                Consumer<LaunchState> listener
                : stateListeners
        ) {

            try {

                listener.accept(
                        newState
                );

            } catch (Throwable ex) {

                ex.printStackTrace();
            }
        }
    }
}