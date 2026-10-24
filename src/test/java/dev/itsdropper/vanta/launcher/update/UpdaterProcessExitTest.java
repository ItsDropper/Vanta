package dev.itsdropper.vanta.launcher.update;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdaterProcessExitTest {

    private static final long EXIT_TIMEOUT_SECONDS = 5;

    @Test
    void updaterHandoffTerminatesJvmEvenWhenNonDaemonThreadIsAlive()
            throws Exception {

        Path java = Path.of(
                System.getProperty("java.home"),
                "bin",
                isWindows() ? "java.exe" : "java"
        );

        Process child = new ProcessBuilder(
                java.toString(),
                "-cp",
                System.getProperty("java.class.path"),
                UpdaterProcessExitTest.Child.class.getName()
        )
                .redirectErrorStream(true)
                .start();

        boolean exited = child.waitFor(
                EXIT_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        );

        if (!exited) {
            child.destroyForcibly();
            child.waitFor();
        }

        assertTrue(
                exited,
                "Launcher JVM stayed alive after updater handoff; updater could time out waiting for its PID"
        );
        assertEquals(
                0,
                child.exitValue(),
                "Launcher JVM should exit successfully after updater startup"
        );
    }

    private static boolean isWindows() {
        return System.getProperty("os.name")
                .toLowerCase(java.util.Locale.ROOT)
                .contains("win");
    }

    /**
     * Runs in a separate JVM because System.exit must never terminate Gradle's
     * test worker. The non-daemon thread models a service thread that would
     * keep the original launcher alive if only JavaFX were shut down.
     */
    public static final class Child {
        private Child() {
        }

        public static void main(String[] args) {
            Thread nonDaemonServiceThread = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(1_000);
                    } catch (InterruptedException ignored) {
                        // Keep the simulated service thread alive until JVM exit.
                    }
                }
            }, "test-non-daemon-service");

            nonDaemonServiceThread.setDaemon(false);
            nonDaemonServiceThread.start();

            UpdaterLauncher.exitOriginalProcess();
        }
    }
}
