package dev.itsdropper.vanta;

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

    private LauncherProcessLifecycle() {
    }

    /**
     * Runs best-effort cleanup and then terminates the launcher process.
     * Child game processes are intentionally not destroyed here.
     */
    public static void exit(Runnable... cleanupActions) {
        if (!EXITING.compareAndSet(false, true)) {
            return;
        }

        if (cleanupActions != null) {
            for (Runnable cleanup : cleanupActions) {
                if (cleanup == null) {
                    continue;
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

        System.exit(0);
    }
}
