package dev.itsdropper.vanta;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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

    private LauncherProcessLifecycle() {
    }

    /**
     * Register resources that must be released when the launcher window closes.
     * Registration is safe before the main launcher view has finished starting.
     */
    public static void registerCleanup(Runnable cleanup) {
        if (cleanup == null) {
            return;
        }
        if (EXITING.get()) {
            runCleanup(cleanup);
            return;
        }

        CLEANUP_ACTIONS.add(cleanup);
        if (EXITING.get() && CLEANUP_ACTIONS.remove(cleanup)) {
            runCleanup(cleanup);
        }
    }

    /**
     * Runs best-effort cleanup and then terminates the launcher process.
     * Child game processes are intentionally not destroyed here.
     */
    public static void exit(Runnable... additionalCleanupActions) {
        if (!EXITING.compareAndSet(false, true)) {
            return;
        }

        for (Runnable cleanup : CLEANUP_ACTIONS) {
            runCleanup(cleanup);
        }
        CLEANUP_ACTIONS.clear();

        if (additionalCleanupActions != null) {
            for (Runnable cleanup : additionalCleanupActions) {
                runCleanup(cleanup);
            }
        }

        System.exit(0);
    }

    private static void runCleanup(Runnable cleanup) {
        if (cleanup == null) {
            return;
        }
        try {
            cleanup.run();
        } catch (Throwable failure) {
            System.err.println("[Vanta Shutdown] Cleanup failed: "
                    + failure.getClass().getSimpleName());
            failure.printStackTrace(System.err);
        }
    }
}
