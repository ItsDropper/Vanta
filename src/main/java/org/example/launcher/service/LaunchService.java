package org.example.launcher.service;

import org.example.launcher.LaunchData;
import org.example.launcher.LaunchDataBuilder;
import org.example.launcher.MinecraftLauncher;
import org.example.launcher.account.Account;
import org.example.launcher.model.Instance;
import org.example.launcher.repair.FailureCategory;
import org.example.launcher.repair.RepairDiagnosis;
import org.example.launcher.repair.RepairDiagnosticEngine;
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

            /*
             * ---------------------------------------------------------
             * FIRST: Look for specific Fabric/mod resolution errors.
             *
             * This parser must always get first access to the complete
             * Fabric Loader failure because the generic diagnostic engine
             * can incorrectly classify an early dependency line before
             * Fabric has printed the actual solution.
             * ---------------------------------------------------------
             */

            List<RepairView.RepairIssue> issues =
                    LaunchFailureParser.parse(
                            output
                    );

            if (!issues.isEmpty()) {

                RepairView.RepairIssue issue =
                        issues.get(0);

                failureHandled = true;

                System.out.println(
                        "[Vanta Repair] Specific failure detected: "
                                + issue.title()
                );

                System.out.println(
                        "[Vanta Repair] "
                                + issue.type()
                );

                if (issue.details() != null
                        && !issue.details().isBlank()) {

                    System.out.println(
                            "[Vanta Repair] Details: "
                                    + issue.details()
                    );
                }

                /*
                 * Preserve the failed instance before the
                 * process monitor clears runningInstance.
                 */
                failedInstance =
                        runningInstance;

                String description =
                        issue.details();

                if (description == null
                        || description.isBlank()) {

                    description =
                            issue.title();
                }

                lastFailure =
                        new LaunchFailure(
                                issue.title(),
                                description,
                                output
                        );

                /*
                 * Stop Minecraft immediately.
                 *
                 * Vanta already understands this failure, so there is
                 * no reason to leave the Fabric error screen open.
                 */
                process.destroyForcibly();

                setState(
                        LaunchState.ERROR
                );

                return;
            }

            /*
             * ---------------------------------------------------------
             * IMPORTANT:
             *
             * Fabric Loader prints mod-resolution errors over multiple
             * lines. The generic diagnostic engine can detect an early
             * line such as:
             *
             *   Fabric API requires Java 25
             *
             * before Fabric has printed the actual mod/Minecraft
             * incompatibility.
             *
             * Do NOT allow the generic engine to claim the failure while
             * Fabric is still producing its detailed resolution output.
             * ---------------------------------------------------------
             */

            boolean fabricModResolutionFailure =
                    output.contains(
                            "Mod resolution failed"
                    )
                            || output.contains(
                            "Incompatible mods found!"
                    );

            if (fabricModResolutionFailure) {
                return;
            }

            /*
             * ---------------------------------------------------------
             * SECOND: Fall back to the generic diagnostic engine.
             *
             * This handles failures that LaunchFailureParser does not
             * specifically understand.
             * ---------------------------------------------------------
             */

            List<RepairDiagnosis> diagnoses =
                    RepairDiagnosticEngine.diagnose(
                            output
                    );

            RepairDiagnosis diagnosis =
                    findActionableDiagnosis(
                            diagnoses
                    );

            if (diagnosis == null) {
                return;
            }

            failureHandled = true;

            System.out.println(
                    "[Vanta Repair] Known failure detected: "
                            + diagnosis.title()
            );

            System.out.println(
                    "[Vanta Repair] "
                            + diagnosis.category()
                            + " | "
                            + diagnosis.confidence()
            );

            if (!diagnosis.evidence().isBlank()) {

                System.out.println(
                        "[Vanta Repair] Evidence: "
                                + diagnosis.evidence()
                );
            }

            failedInstance =
                    runningInstance;

            String description =
                    diagnosis.description();

            if (description == null
                    || description.isBlank()) {

                description =
                        diagnosis.evidence();
            }

            lastFailure =
                    new LaunchFailure(
                            diagnosis.title(),
                            description,
                            diagnosis.evidence().isBlank()
                                    ? output
                                    : diagnosis.evidence()
                    );

            process.destroyForcibly();

            setState(
                    LaunchState.ERROR
            );
        }
    }

    private RepairDiagnosis findActionableDiagnosis(
            List<RepairDiagnosis> diagnoses
    ) {

        if (diagnoses == null
                || diagnoses.isEmpty()) {

            return null;
        }

        /*
         * Only use categories that represent an actual
         * failure. Generic LOADER and MINECRAFT_VERSION
         * matches are intentionally excluded here because
         * the current diagnostic engine can match normal
         * startup messages.
         */
        for (RepairDiagnosis diagnosis : diagnoses) {

            if (diagnosis == null) {
                continue;
            }

            FailureCategory category =
                    diagnosis.category();

            if (category ==
                    FailureCategory.MOD_DEPENDENCY
                    || category ==
                    FailureCategory.MOD_CONFLICT
                    || category ==
                    FailureCategory.DUPLICATE_MOD
                    || category ==
                    FailureCategory.MISSING_LIBRARY
                    || category ==
                    FailureCategory.CORRUPT_LIBRARY
                    || category ==
                    FailureCategory.MISSING_ASSET
                    || category ==
                    FailureCategory.JAVA_RUNTIME
                    || category ==
                    FailureCategory.JAVA_VERSION
                    || category ==
                    FailureCategory.JVM_ARGUMENTS
                    || category ==
                    FailureCategory.NATIVE
                    || category ==
                    FailureCategory.CLASSPATH
                    || category ==
                    FailureCategory.CONFIGURATION
                    || category ==
                    FailureCategory.INSTALLATION
                    || category ==
                    FailureCategory.DISK_SPACE
                    || category ==
                    FailureCategory.PERMISSION) {

                return diagnosis;
            }
        }

        return null;
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

                            if (failedInstance == null) {

                                failedInstance =
                                        runningInstance;
                            }

                            minecraftProcess =
                                    null;

                            runningInstance =
                                    null;

                            /*
                             * The output monitor already handled
                             * a known failure.
                             */
                            if (failureHandled) {

                                return;
                            }

                            if (exitCode != 0) {

                                if (lastFailure == null) {

                                    String output =
                                            getRecentOutput();

                                    lastFailure =
                                            new LaunchFailure(
                                                    "Minecraft stopped unexpectedly",
                                                    "Minecraft closed with an error that Vanta could not identify.",
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
