package org.example.launcher.service;

import org.example.launcher.LaunchData;
import org.example.launcher.LaunchDataBuilder;
import org.example.launcher.MinecraftLauncher;
import org.example.launcher.account.Account;
import org.example.launcher.model.Instance;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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

    private Instance runningInstance;

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

        if (listener == null) {
            throw new IllegalArgumentException(
                    "Listener cannot be null."
            );
        }

        stateListeners.add(
                listener
        );

        listener.accept(
                getState()
        );
    }

    public void removeStateListener(
            Consumer<LaunchState> listener
    ) {

        stateListeners.remove(
                listener
        );
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

        if (state == LaunchState.PREPARING
                || state == LaunchState.STARTING
                || state == LaunchState.CLOSING) {

            throw new IllegalStateException(
                    "Minecraft is already starting or closing."
            );
        }

        if (instance == null) {
            throw new IllegalArgumentException(
                    "No Minecraft instance selected."
            );
        }

        try {

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

            Process process =
                    MinecraftLauncher.launch(
                            account,
                            launchData
                    );

            if (process == null) {

                throw new IllegalStateException(
                        "Minecraft process was not created."
                );
            }

            if (!process.isAlive()) {

                throw new IllegalStateException(
                        "Minecraft exited immediately."
                );
            }

            minecraftProcess =
                    process;

            runningInstance =
                    instance;

            /*
             * The Minecraft process successfully exists and is alive.
             *
             * We intentionally do NOT wait for a specific Minecraft
             * console line here. Different Minecraft versions,
             * loaders, Java versions and environments can produce
             * different startup logs.
             *
             * The process itself is Vanta's source of truth.
             */

            setState(
                    LaunchState.RUNNING
            );

            monitorProcess(
                    process
            );

            monitorOutput(
                    process
            );

            return process;

        } catch (Exception e) {

            minecraftProcess =
                    null;

            runningInstance =
                    null;

            setState(
                    LaunchState.ERROR
            );

            throw e;
        }
    }

    // =============================================================
    // PROCESS OUTPUT
    // =============================================================

    private void monitorOutput(
            Process process
    ) {

        Thread outputThread =
                new Thread(() -> {

                    try (
                            BufferedReader reader =
                                    new BufferedReader(
                                            new InputStreamReader(
                                                    process.getInputStream()
                                            )
                                    )
                    ) {

                        String line;

                        while (
                                (line = reader.readLine())
                                        != null
                        ) {

                            System.out.println(
                                    "[Minecraft] "
                                            + line
                            );
                        }

                    } catch (Exception e) {

                        if (process.isAlive()) {

                            e.printStackTrace();
                        }
                    }
                });

        outputThread.setDaemon(
                true
        );

        outputThread.setName(
                "Vanta-Minecraft-Output"
        );

        outputThread.start();
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

                            if (minecraftProcess ==
                                    process) {

                                minecraftProcess =
                                        null;

                                runningInstance =
                                        null;

                                /*
                                 * The Minecraft process is gone.
                                 * Regardless of how it exited,
                                 * Vanta is no longer running it.
                                 */

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

        monitor.setDaemon(
                true
        );

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

            runningInstance =
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

                                runningInstance =
                                        null;

                                setState(
                                        LaunchState.IDLE
                                );
                            }
                        }
                    }
                });

        cleanup.setDaemon(
                true
        );

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

    public synchronized Instance getRunningInstance() {

        return runningInstance;
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