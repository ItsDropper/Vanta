package org.example.launcher.service;

import org.example.launcher.LaunchData;
import org.example.launcher.LaunchDataBuilder;
import org.example.launcher.MinecraftLauncher;
import org.example.launcher.account.Account;
import org.example.launcher.model.Instance;
import org.example.ui.views.RepairView;

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

    private Instance failedInstance;

    private LaunchState state =
            LaunchState.IDLE;

    private final StringBuilder outputBuffer =
            new StringBuilder();

    private LaunchFailure lastFailure;

    private boolean failureHandled;

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

        outputBuffer.setLength(0);

        lastFailure = null;

        failedInstance = null;

        failureHandled = false;

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

            minecraftProcess =
                    process;

            runningInstance =
                    instance;

            monitorOutput(
                    process
            );

            monitorProcess(
                    process
            );

            /*
             * Minecraft can take several seconds to initialize.
             * Do not wait for the process to finish before marking
             * it as running.
             */
            setState(
                    LaunchState.RUNNING
            );

            return process;

        } catch (Exception e) {

            minecraftProcess = null;

            runningInstance = null;

            failedInstance = instance;

            lastFailure =
                    new LaunchFailure(
                            "Minecraft could not be started",
                            "Vanta was unable to start this Minecraft instance.",
                            buildDetails(
                                    e
                            )
                    );

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

                            synchronized (outputBuffer) {

                                outputBuffer.append(
                                        line
                                ).append(
                                        '\n'
                                );

                                if (outputBuffer.length()
                                        > 100_000) {

                                    outputBuffer.delete(
                                            0,
                                            outputBuffer.length()
                                                    - 100_000
                                    );
                                }
                            }

                            System.out.println(
                                    "[Minecraft] "
                                            + line
                            );

                            checkForRepairableFailure(
                                    process
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

    private void checkForRepairableFailure(
            Process process
    ) {

        synchronized (this) {

            if (minecraftProcess != process) {
                return;
            }

            if (failureHandled) {
                return;
            }

            String output =
                    getRecentOutput();

            List<RepairView.RepairIssue> issues =
                    LaunchFailureParser.parse(
                            output
                    );

            if (issues.isEmpty()) {
                return;
            }

            failureHandled = true;

            /*
             * Preserve the instance before the process monitor
             * clears runningInstance.
             */
            System.out.println(
                    "[Vanta DEBUG] Failure detected. Running instance: "
                            + runningInstance
            );

            failedInstance =
                    runningInstance;

            lastFailure =
                    new LaunchFailure(
                            "Minecraft could not start",
                            "Vanta found a problem with this instance and stopped Minecraft before the Fabric error screen appeared.",
                            output
                    );

            System.out.println(
                    "[Vanta] Detected Minecraft startup failure."
            );

            /*
             * Stop Minecraft so its own Fabric error screen
             * does not remain visible.
             */
            process.destroyForcibly();

            setState(
                    LaunchState.ERROR
            );
        }
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

                        int exitCode =
                                process.waitFor();

                        synchronized (this) {

                            if (minecraftProcess !=
                                    process) {

                                return;
                            }

                            /*
                             * Preserve the instance that failed
                             * before clearing the running state.
                             */
                            if (failedInstance == null) {

                                failedInstance =
                                        runningInstance;
                            }

                            minecraftProcess =
                                    null;

                            runningInstance =
                                    null;

                            /*
                             * If the output monitor already detected
                             * a Fabric/dependency failure, it owns
                             * the failure state.
                             */
                            if (failureHandled) {

                                return;
                            }

                            if (exitCode != 0) {

                                if (lastFailure == null) {

                                    String output =
                                            getRecentOutput();

                                    List<RepairView.RepairIssue> issues =
                                            LaunchFailureParser.parse(
                                                    output
                                            );

                                    String title =
                                            issues.isEmpty()
                                                    ? "Minecraft stopped unexpectedly"
                                                    : "Minecraft could not start";

                                    String description =
                                            issues.isEmpty()
                                                    ? "Minecraft closed with an error while starting or running."
                                                    : "Vanta found a problem that may be repairable.";

                                    lastFailure =
                                            new LaunchFailure(
                                                    title,
                                                    description,
                                                    output
                                            );
                                }

                                setState(
                                        LaunchState.ERROR
                                );

                            } else {

                                setState(
                                        LaunchState.IDLE
                                );
                            }
                        }

                    } catch (InterruptedException e) {

                        Thread.currentThread()
                                .interrupt();

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
    // FAILURE
    // =============================================================

    public synchronized LaunchFailure getLastFailure() {

        return lastFailure;
    }

    public synchronized Instance getFailedInstance() {

        return failedInstance;
    }

    public String getRecentOutput() {

        synchronized (outputBuffer) {

            return outputBuffer.toString();
        }
    }

    private String buildDetails(
            Exception exception
    ) {

        String output =
                getRecentOutput();

        if (output.isBlank()) {

            return exception.getMessage() != null
                    ? exception.getMessage()
                    : exception.toString();
        }

        return output;
    }

    // =============================================================
    // CLOSE
    // =============================================================

    public synchronized void close() {

        if (!isRunning()) {

            minecraftProcess = null;

            runningInstance = null;

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

                                minecraftProcess = null;

                                runningInstance = null;

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