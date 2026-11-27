package dev.itsdropper.vanta.launcher.service;

import dev.itsdropper.vanta.launcher.LaunchData;
import dev.itsdropper.vanta.launcher.LaunchDataBuilder;
import dev.itsdropper.vanta.launcher.MinecraftLauncher;
import dev.itsdropper.vanta.launcher.account.Account;
import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.instance.MinecraftSecurityUpdateService;
import dev.itsdropper.vanta.launcher.instance.MinecraftSecurityException;
import dev.itsdropper.vanta.launcher.repair.FailureCategory;
import dev.itsdropper.vanta.launcher.repair.RepairDiagnosis;
import dev.itsdropper.vanta.launcher.repair.RepairDiagnosticEngine;
import dev.itsdropper.vanta.ui.views.RepairView;
import dev.itsdropper.vanta.telemetry.TelemetryService;
import dev.itsdropper.vanta.ui.LauncherSettings;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Consumer;

public class LaunchService {

    private static final Pattern SERVER_CONNECTION_PATTERN =
            Pattern.compile(
                    "(?:Connecting to|Connecting)\\s+(?:/)?([^,\\s:]+|\\[[^\\]]+\\])(?:,\\s*|:)(\\d+)"
            );

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

    private ServerTarget runningServerTarget;

    private Instance failedInstance;

    private LaunchState state =
            LaunchState.IDLE;

    private final StringBuilder outputBuffer =
            new StringBuilder();

    private LaunchFailure lastFailure;


    private long launchStartedAtMillis;

    private Set<String> crashReportsBeforeLaunch = Set.of();
    private final LaunchHistoryRepository launchHistoryRepository;
    private String launchHistoryId;

    public LaunchService(AccountService accountService) {
        this(accountService, new LaunchHistoryRepository());
    }

    LaunchService(AccountService accountService, LaunchHistoryRepository launchHistoryRepository) {
        this.accountService = accountService;
        this.launchHistoryRepository = java.util.Objects.requireNonNull(launchHistoryRepository);
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
        return launch(instance, null);
    }

    public synchronized Process launch(
            Instance instance,
            ServerTarget server
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

        beginHistory(instance);

        try {

            setState(
                    LaunchState.PREPARING
            );

            Account account = accountService.getCurrentAccount();

            if (account == null) {
                account = accountService.login();
            }
            String launchAccountId = account.getUuid();

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

            if (server != null) {
                ServerHistoryManager.recordConnection(
                        instance,
                        server.host(),
                        server.port()
                );
            }

            LaunchData launchData;

            try {
                // Validate mandatory mod metadata before creating a Minecraft
                // process, so repairable Fabric dependency failures never reach
                // the game's error screen.
                System.out.println("[Vanta Repair] Checking mandatory mod dependencies before launch...");
                List<dev.itsdropper.vanta.launcher.modrinth.InstalledModScanner.MissingDependency> repairedDependencies =
                        new ModrinthService().repairMissingRequiredDependencies(
                                instance,
                                message -> System.out.println("[Vanta Repair] " + message)
                        );
                if (!repairedDependencies.isEmpty()) {
                    System.out.println("[Vanta Repair] Installed " + repairedDependencies.size()
                            + " missing required mod dependency/dependencies before launch.");
                }

                // Refresh Mojang's version metadata and security-sensitive LWJGL
                // artifacts before constructing the classpath or starting Java.
                MinecraftSecurityUpdateService.refreshBeforeLaunch(instance);
                launchData = buildLaunchData(instance, server);
                logEnvironmentDiagnostics(instance, launchData);
            } catch (Exception buildFailure) {
                logEnvironmentDiagnostics(instance, null);
                /*
                 * A launch can fail before Minecraft even starts when
                 * an instance has a missing/corrupt core file. Repair
                 * only deterministic installation failures, then retry
                 * exactly once. User content such as mods, configs and
                 * saves is never touched by this repair path.
                 */
                if (!dev.itsdropper.vanta.launcher.instance.InstanceRepairService
                        .isRepairableInstallationFailure(buildFailure)) {
                    throw buildFailure;
                }

                System.out.println(
                        "[Vanta Repair] Core installation failure detected. "
                                + "Attempting automatic instance repair."
                );

                dev.itsdropper.vanta.launcher.instance.InstanceRepairService
                        .repair(instance);

                // Repair can reinstall version metadata and native files. Re-run
                // the security gate after repair so this retry cannot bypass it.
                MinecraftSecurityUpdateService.refreshBeforeLaunch(instance);
                launchData = buildLaunchData(instance, server);
                logEnvironmentDiagnostics(instance, launchData);

                System.out.println(
                        "[Vanta Repair] Instance repair completed successfully."
                );
            }

            setState(
                    LaunchState.STARTING
            );

            crashReportsBeforeLaunch = snapshotCrashReports(instance.getDirectory());
            launchStartedAtMillis = System.currentTimeMillis();

            // Refresh after instance preparation, immediately before
            // creating the process. This avoids using a token that expired
            // while assets, libraries or repairs were being prepared.
            account = accountService.ensureFreshSession(launchAccountId);

            Process process =
                    MinecraftLauncher.launch(
                            account,
                            launchData
                    );
            long processCreatedAtNanos = System.nanoTime();
            boolean optimizedLaunch = LauncherSettings.isExperimentalLaunchCacheEnabled();

            if (process == null) {

                throw new IllegalStateException(
                        "Minecraft process was not created."
                );
            }

            minecraftProcess =
                    process;

            runningInstance =
                    instance;

            runningServerTarget =
                    server;

            monitorOutput(
                    process,
                    instance,
                    optimizedLaunch,
                    processCreatedAtNanos
            );

            monitorProcess(
                    process
            );

            setState(
                    LaunchState.RUNNING
            );

            return process;

        } catch (Exception e) {

            // A failure in launcher-side monitoring/UI bookkeeping must never
            // turn a still-live Minecraft JVM into a reported game crash.
            // Once the child process exists, its actual lifetime is authoritative.
            Process activeProcess = minecraftProcess;
            if (activeProcess != null && activeProcess.isAlive()) {
                System.err.println("[Vanta Launch] A post-start launcher operation failed while "
                        + "Minecraft is still alive; retaining the running session. "
                        + e.getClass().getSimpleName());
                setState(LaunchState.RUNNING);
                return activeProcess;
            }

            minecraftProcess = null;

            runningInstance = null;

            runningServerTarget = null;

            failedInstance = instance;

            boolean securityBlocked = e instanceof MinecraftSecurityException;
            String failureTitle = securityBlocked
                    ? LaunchFailure.SECURITY_BLOCKED_TITLE
                    : "Minecraft could not be started";
            String failureDescription = securityBlocked
                    ? securityBlockDescription((MinecraftSecurityException) e)
                    : "Vanta was unable to start this Minecraft instance.";

            lastFailure = new LaunchFailure(
                    failureTitle,
                    failureDescription,
                    buildDetails(e)
            );

            setState(
                    LaunchState.ERROR
            );
            finishHistory(LaunchHistoryRepository.Outcome.FAILED,
                    lastFailure == null ? "Minecraft launch failed" : lastFailure.title(),
                    getRecentOutput().isBlank() ? buildDetails(e) : getRecentOutput(), null);

            throw e;
        }
    }

    private static String securityBlockDescription(MinecraftSecurityException failure) {
        return "Vanta blocked Minecraft " + failure.minecraftVersion()
                + " because Mojang's official metadata still selects "
                + failure.artifact() + ". The patched native font library is not available "
                + "for this version yet. This is an upstream security block, not a damaged "
                + "instance; reinstalling the instance will not resolve it. Retry after "
                + "Mojang updates this version's official metadata.";
    }

    private void logEnvironmentDiagnostics(Instance instance, LaunchData launchData) {
        try {
            dev.itsdropper.vanta.launcher.environment.EnvironmentDiagnosticService.Report report =
                    new dev.itsdropper.vanta.launcher.environment.EnvironmentDiagnosticService()
                            .inspect(instance, launchData);
            for (dev.itsdropper.vanta.launcher.environment.EnvironmentDiagnosticService.Diagnostic diagnostic
                    : report.diagnostics()) {
                if (diagnostic.severity()
                        == dev.itsdropper.vanta.launcher.environment.EnvironmentDiagnosticService.Severity.INFO) {
                    continue;
                }
                System.err.println("[Vanta Environment] " + diagnostic.severity()
                        + " [" + diagnostic.code() + "] " + diagnostic.message());
            }
        } catch (RuntimeException diagnosticFailure) {
            // Diagnostics must never replace the launch pipeline's authoritative validation or repair result.
            System.err.println("[Vanta Environment] Diagnostic pass failed: "
                    + diagnosticFailure.getClass().getSimpleName());
        }
    }

    // =============================================================
    // PROCESS OUTPUT
    // =============================================================

    private void monitorOutput(
            Process process,
            Instance instance,
            boolean optimizedLaunch,
            long processCreatedAtNanos
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
                        boolean firstOutputRecorded = false;

                        while (
                                (line = reader.readLine())
                                        != null
                        ) {
                            if (!firstOutputRecorded) {
                                firstOutputRecorded = true;
                                try {
                                    LaunchPerformanceTracker.recordProcessStartup(
                                            instance,
                                            optimizedLaunch,
                                            System.nanoTime() - processCreatedAtNanos
                                    );
                                } catch (RuntimeException metricsFailure) {
                                    System.err.println("[Vanta Launch] Could not record process startup metrics: "
                                            + metricsFailure.getClass().getSimpleName());
                                }
                            }

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

                            recordServerConnection(line);

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

    private void recordServerConnection(String line) {
        if (runningInstance == null || line == null) {
            return;
        }

        Matcher matcher = SERVER_CONNECTION_PATTERN.matcher(line);
        if (!matcher.find()) {
            return;
        }

        try {
            ServerHistoryManager.recordConnection(
                    runningInstance,
                    matcher.group(1),
                    Integer.parseInt(matcher.group(2))
            );
        } catch (Exception ignored) {
        }
    }

    /**
     * Combine process output with the instance's latest log and newest crash
     * report. This is read-only and bounded so huge logs cannot stall repair
     * detection or consume unbounded memory.
     */
    private String collectDiagnosticContext(String processOutput) {
        return collectLaunchEvidence(processOutput, true);
    }

    private String collectSuccessfulLaunchEvidence(String processOutput) {
        // Include the instance's latest.log as a useful comparison baseline, but never
        // attach old crash-report files to a successful or user-stopped session.
        return collectLaunchEvidence(processOutput, false);
    }

    private String collectLaunchEvidence(String processOutput, boolean includeCrashReports) {
        StringBuilder combined = new StringBuilder();
        appendDiagnosticText(combined, "PROCESS OUTPUT", processOutput, 120_000);

        Instance instance = runningInstance;
        if (instance == null || instance.getDirectory() == null) {
            return combined.toString();
        }

        Path directory = instance.getDirectory();
        appendDiagnosticFile(combined, "LATEST LOG", directory.resolve("logs").resolve("latest.log"), 180_000);
        if (!includeCrashReports) return combined.toString();

        Path crashDirectory = directory.resolve("crash-reports");
        if (!Files.isSymbolicLink(crashDirectory)
                && Files.isDirectory(crashDirectory, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            try (Stream<Path> files = Files.list(crashDirectory)) {
                files.filter(path -> !Files.isSymbolicLink(path)
                        && Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS))
                        .filter(p -> p.getFileName().toString().endsWith(".txt"))
                        .sorted(Comparator.comparingLong(this::lastModified).reversed())
                        .limit(2)
                        .forEach(p -> appendDiagnosticFile(combined, "CRASH REPORT", p, 100_000));
            } catch (IOException ex) {
                System.err.println("[Vanta Repair] Could not inspect crash reports: " + ex.getMessage());
            }
        }
        return combined.toString();
    }

    private long lastModified(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException ignored) { return 0L; }
    }

    private void appendDiagnosticFile(StringBuilder target, String label, Path path, int maxChars) {
        try {
            if (Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
            long size = Files.size(path);
            if (size <= 0) return;
            String text;
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                StringBuilder contents = new StringBuilder((int) Math.min(size, maxChars));
                char[] buffer = new char[8192];
                int read;
                while (contents.length() < maxChars && (read = reader.read(buffer, 0,
                        Math.min(buffer.length, maxChars - contents.length()))) >= 0) {
                    contents.append(buffer, 0, read);
                }
                text = contents.toString();
            }
            appendDiagnosticText(target, label + " (" + path.getFileName() + ")", text, maxChars);
        } catch (IOException ex) {
            System.err.println("[Vanta Repair] Could not read " + path + ": " + ex.getMessage());
        }
    }

    private void appendDiagnosticText(StringBuilder target, String label, String text, int maxChars) {
        if (text == null || text.isBlank() || target.length() >= 450_000) return;
        int remaining = Math.min(maxChars, 450_000 - target.length());
        target.append("\\n\\n===== ").append(label).append(" =====\\n");
        target.append(text, 0, Math.min(text.length(), remaining));
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
                    FailureCategory.MOD_MIXIN_FAILURE
                    || category ==
                    FailureCategory.MOD_CRASH
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
                    FailureCategory.AUTHENTICATION
                    || category ==
                    FailureCategory.GRAPHICS
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

    private LaunchData buildLaunchData(Instance instance, ServerTarget server) {
        long started = System.nanoTime();
        LaunchData data = LaunchDataBuilder.build(instance, server);
        try {
            LaunchPerformanceTracker.record(
                    instance,
                    LauncherSettings.isExperimentalLaunchCacheEnabled(),
                    System.nanoTime() - started
            );
        } catch (RuntimeException metricsFailure) {
            System.err.println("[Vanta Launch] Could not record experimental launch metrics: "
                    + metricsFailure.getClass().getSimpleName());
        }
        return data;
    }

    private Set<String> snapshotCrashReports(Path instanceDirectory) {
        if (instanceDirectory == null) {
            return Set.of();
        }

        Path crashDirectory = instanceDirectory.resolve("crash-reports");
        if (!Files.isDirectory(crashDirectory)) {
            return Set.of();
        }

        try (Stream<Path> files = Files.list(crashDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (IOException ignored) {
            return Set.of();
        }
    }

    private LaunchFailure classifyExitedFailure(int exitCode, String processOutput, String diagnosticContext) {
        // The process stream often contains only Fabric's short error-screen output.
        // The actionable dependency-resolution details are written to latest.log.
        // Always classify against the same combined evidence persisted in launch history.
        String diagnosticEvidence = diagnosticContext == null || diagnosticContext.isBlank()
                ? processOutput : diagnosticContext;
        List<RepairView.RepairIssue> specificIssues = LaunchFailureParser.parse(diagnosticEvidence);
        if (!specificIssues.isEmpty()) {
            RepairView.RepairIssue issue = specificIssues.get(0);
            String description = issue.details() == null || issue.details().isBlank()
                    ? issue.title() : issue.details();
            return new LaunchFailure(issue.title(), description, diagnosticEvidence);
        }

        RepairDiagnosis diagnosis = findActionableDiagnosis(RepairDiagnosticEngine.diagnose(diagnosticEvidence));
        if (diagnosis != null) {
            String description = diagnosis.description() == null || diagnosis.description().isBlank()
                    ? diagnosis.evidence() : diagnosis.description();
            return new LaunchFailure(diagnosis.title(), description,
                    diagnosis.evidence().isBlank() ? diagnosticEvidence : diagnosis.evidence());
        }

        return new LaunchFailure("Minecraft exited with code " + exitCode,
                "Minecraft terminated with a non-zero exit code (" + exitCode
                        + "). The available process output did not identify a specific cause.", processOutput);
    }

    private void monitorProcess(
            Process process
    ) {

        Thread monitor =
                new Thread(() -> {

                    try {

                        int exitCode =
                                process.waitFor();

                        Instance exitedInstance;
                        String processOutput;
                        long startedAt;
                        boolean intentionalClose;
                        Set<String> previousCrashReports;
                        String diagnosticContext;

                        synchronized (this) {

                            if (minecraftProcess != process) {
                                return;
                            }

                            exitedInstance = runningInstance;
                            processOutput = getRecentOutput();
                            startedAt = launchStartedAtMillis;
                            previousCrashReports = crashReportsBeforeLaunch;
                            intentionalClose = state == LaunchState.CLOSING;
                            diagnosticContext = !intentionalClose && exitCode != 0
                                    ? collectDiagnosticContext(processOutput)
                                    : collectSuccessfulLaunchEvidence(processOutput);

                            if (failedInstance == null) {
                                failedInstance = runningInstance;
                            }

                            minecraftProcess = null;
                            runningInstance = null;
                            runningServerTarget = null;

                            // Logs are evidence, never a reason to terminate a live game.
                            // Classification is permitted only after waitFor() and a non-zero exit.
                            if (exitCode != 0 && !intentionalClose) {
                                lastFailure = classifyExitedFailure(exitCode, processOutput, diagnosticContext);
                                setState(LaunchState.ERROR);
                            } else {
                                setState(LaunchState.IDLE);
                            }
                            LaunchHistoryRepository.Outcome outcome = intentionalClose
                                    ? LaunchHistoryRepository.Outcome.STOPPED
                                    : exitCode == 0
                                    ? LaunchHistoryRepository.Outcome.SUCCEEDED
                                    : LaunchHistoryRepository.Outcome.FAILED;
                            finishHistory(outcome,
                                    outcome == LaunchHistoryRepository.Outcome.FAILED
                                            ? (lastFailure == null ? "Minecraft exited unexpectedly" : lastFailure.title())
                                            : outcome == LaunchHistoryRepository.Outcome.STOPPED
                                            ? "Minecraft was closed by the user." : "Minecraft exited normally.",
                                    diagnosticContext, exitCode);
                        }

                        // Only a non-zero, non-user-initiated exit is eligible.
                        // TelemetryService uploads only a newly-created crash report
                        // or a throwable-shaped stack excerpt, never raw latest.log.
                        if (exitCode != 0 && !intentionalClose && exitedInstance != null) {
                            TelemetryService.reportMinecraftCrash(
                                    exitedInstance.getDirectory(),
                                    processOutput,
                                    startedAt,
                                    previousCrashReports
                            ).exceptionally(ignored -> null);
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
            Process finishedProcess = minecraftProcess;
            Integer exitCode = null;
            if (finishedProcess != null) {
                try {
                    exitCode = finishedProcess.exitValue();
                } catch (IllegalThreadStateException stillRunning) {
                    // The process can transition between isAlive() and exitValue().
                }
            }
            LaunchHistoryRepository.Outcome outcome = state == LaunchState.CLOSING
                    ? LaunchHistoryRepository.Outcome.STOPPED
                    : exitCode != null && exitCode != 0
                    ? LaunchHistoryRepository.Outcome.FAILED
                    : exitCode != null
                    ? LaunchHistoryRepository.Outcome.SUCCEEDED
                    : LaunchHistoryRepository.Outcome.STOPPED;
            finishHistory(outcome,
                    outcome == LaunchHistoryRepository.Outcome.FAILED
                            ? (lastFailure == null ? "Minecraft exited unexpectedly" : lastFailure.title())
                            : outcome == LaunchHistoryRepository.Outcome.SUCCEEDED
                            ? "Minecraft exited normally." : "Minecraft was closed by the user.",
                    getRecentOutput(), exitCode);

            minecraftProcess = null;
            runningInstance = null;
            runningServerTarget = null;
            setState(LaunchState.IDLE);
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

                                finishHistory(LaunchHistoryRepository.Outcome.STOPPED,
                                        "Minecraft was closed by the user.", getRecentOutput(), null);
                                minecraftProcess = null;

                                runningInstance = null;

                                runningServerTarget = null;

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

    public synchronized ServerTarget getRunningServerTarget() {
        return runningServerTarget;
    }

    public synchronized LaunchState getState() {

        return state;
    }

    private void beginHistory(Instance instance) {
        try {
            launchHistoryId = launchHistoryRepository.begin(instance);
        } catch (IOException | RuntimeException failure) {
            launchHistoryId = null;
            System.err.println("[Vanta Launch History] Could not start timeline record: "
                    + failure.getClass().getSimpleName());
        }
    }

    private void finishHistory(LaunchHistoryRepository.Outcome outcome, String message,
                               String diagnosticText, Integer exitCode) {
        String id = launchHistoryId;
        if (id == null) return;
        try {
            launchHistoryRepository.finish(id, outcome, message, diagnosticText, exitCode);
        } catch (IOException | RuntimeException failure) {
            System.err.println("[Vanta Launch History] Could not finalize timeline record: "
                    + failure.getClass().getSimpleName());
        } finally {
            launchHistoryId = null;
        }
    }

    private synchronized void setState(
            LaunchState newState
    ) {

        // Log signatures and advisory crash diagnoses are not process state.
        // Never publish ERROR while the exact Minecraft process is still alive.
        if (newState == LaunchState.ERROR
                && minecraftProcess != null
                && minecraftProcess.isAlive()) {
            System.err.println("[Vanta Launch] Ignoring an ERROR transition because Minecraft "
                    + "process " + minecraftProcess.pid() + " is still alive.");
            newState = LaunchState.RUNNING;
        }

        state =
                newState;

        if (launchHistoryId != null) {
            LaunchHistoryRepository.Outcome historyState = switch (newState) {
                case PREPARING -> null; // begin() already persists the initial launch-request event.
                case STARTING -> LaunchHistoryRepository.Outcome.STARTING;
                case RUNNING -> LaunchHistoryRepository.Outcome.RUNNING;
                case CLOSING -> LaunchHistoryRepository.Outcome.STOPPING;
                case IDLE, ERROR -> null;
            };
            if (historyState != null) {
                try {
                    launchHistoryRepository.transition(launchHistoryId, historyState,
                            switch (newState) {
                                case PREPARING -> "Preparing Minecraft instance";
                                case STARTING -> "Creating Minecraft process";
                                case RUNNING -> "Minecraft process started";
                                case CLOSING -> "User requested shutdown";
                                default -> "Launch state changed";
                            });
                } catch (IOException | RuntimeException failure) {
                    System.err.println("[Vanta Launch History] Could not record state transition: "
                            + failure.getClass().getSimpleName());
                }
            }
        }

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
