package dev.itsdropper.vanta;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the final process boundary for normal launcher shutdown.
 *
 * JavaFX can be configured to stay alive while its only window is hidden
 * during Minecraft gameplay. Closing that window must therefore explicitly
 * terminate the launcher JVM instead of relying on implicit JavaFX exit.
 */
public final class LauncherProcessLifecycle {
    private static final AtomicBoolean EXITING = new AtomicBoolean();
    private static final List<Runnable> CLEANUP_ACTIONS = new CopyOnWriteArrayList<>();
    private static final Set<Process> CHILD_PROCESSES = ConcurrentHashMap.newKeySet();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(
                LauncherProcessLifecycle::terminateChildProcesses,
                "Vanta-Child-Process-Reaper"));
    }

    private LauncherProcessLifecycle() {
    }

    /**
     * Register a short-lived helper process owned by the launcher.
     * Do not register Minecraft, managed game servers, or the isolated updater:
     * those processes have explicit lifetimes independent of the launcher UI.
     */
    public static void registerChildProcess(Process process) {
        if (process == null) return;
        if (EXITING.get()) {
            terminateChildProcess(process);
            return;
        }
        CHILD_PROCESSES.add(process);
        process.onExit().thenRun(() -> CHILD_PROCESSES.remove(process));
        if (EXITING.get() && CHILD_PROCESSES.remove(process)) {
            terminateChildProcess(process);
        }
    }

    public static void registerCleanup(Runnable cleanup) {
        if (cleanup == null) return;
        if (EXITING.get()) {
            runCleanup(cleanup);
            return;
        }
        CLEANUP_ACTIONS.add(cleanup);
        if (EXITING.get() && CLEANUP_ACTIONS.remove(cleanup)) runCleanup(cleanup);
    }

    /**
     * Runs cleanup, reaps every registered helper process, then terminates Vanta.
     * Minecraft and explicitly detached services are deliberately not registered.
     */
    public static void exit(Runnable... additionalCleanupActions) {
        if (!EXITING.compareAndSet(false, true)) return;

        for (Runnable cleanup : CLEANUP_ACTIONS) runCleanup(cleanup);
        CLEANUP_ACTIONS.clear();

        if (additionalCleanupActions != null) {
            for (Runnable cleanup : additionalCleanupActions) runCleanup(cleanup);
        }

        terminateChildProcesses();
        System.exit(0);
    }

    private static void terminateChildProcesses() {
        for (Process process : CHILD_PROCESSES) {
            if (CHILD_PROCESSES.remove(process)) terminateChildProcess(process);
        }
    }

    private static void terminateChildProcess(Process process) {
        if (process == null) return;

        List<ProcessHandle> descendants;
        try {
            descendants = process.descendants().toList();
        } catch (RuntimeException unavailable) {
            descendants = List.of();
        }
        descendants.forEach(ProcessHandle::destroyForcibly);

        if (process.isAlive()) process.destroy();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        try {
            if (process.isAlive() && !process.waitFor(500, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
            }
            for (ProcessHandle descendant : descendants) {
                if (!descendant.isAlive()) continue;
                long remaining = deadline - System.nanoTime();
                if (remaining > 0) {
                    try {
                        descendant.onExit().get(remaining, TimeUnit.NANOSECONDS);
                    } catch (java.util.concurrent.ExecutionException
                             | java.util.concurrent.TimeoutException ignored) {
                        descendant.destroyForcibly();
                    }
                } else {
                    descendant.destroyForcibly();
                }
            }
            if (process.isAlive()) process.waitFor(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            descendants.forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private static void runCleanup(Runnable cleanup) {
        if (cleanup == null) return;
        try {
            cleanup.run();
        } catch (Throwable failure) {
            System.err.println("[Vanta Shutdown] Cleanup failed: "
                    + failure.getClass().getSimpleName());
            failure.printStackTrace(System.err);
        }
    }
}
