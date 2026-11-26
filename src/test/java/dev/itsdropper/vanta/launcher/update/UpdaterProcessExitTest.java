package dev.itsdropper.vanta.launcher.update;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

        String classPath = String.join(
                File.pathSeparator,
                System.getProperty("java.class.path"),
                Path.of(
                        UpdaterProcessExitTest.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toURI()
                ).toString(),
                Path.of(
                        UpdaterLauncher.class
                                .getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toURI()
                ).toString()
        );

        Path helperPidFile = Files.createTempFile("vanta-updater-helper-", ".pid");
        Process child = new ProcessBuilder(
                java.toString(),
                "-cp",
                classPath,
                UpdaterProcessExitTest.Child.class.getName(),
                helperPidFile.toString()
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

        long helperPid = Long.parseLong(Files.readString(helperPidFile));
        long helperDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(EXIT_TIMEOUT_SECONDS);
        while (ProcessHandle.of(helperPid).map(ProcessHandle::isAlive).orElse(false)
                && System.nanoTime() < helperDeadline) {
            Thread.sleep(25);
        }
        assertFalse(ProcessHandle.of(helperPid).map(ProcessHandle::isAlive).orElse(false),
                "Updater handoff left a launcher-owned helper process running");
        Files.deleteIfExists(helperPidFile);
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

        public static void main(String[] args) throws Exception {
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

            Path java = Path.of(System.getProperty("java.home"), "bin",
                    isWindows() ? "java.exe" : "java");
            Process helper = new ProcessBuilder(java.toString(), "-cp",
                    System.getProperty("java.class.path"), HelperSleeper.class.getName())
                    .redirectErrorStream(true)
                    .start();
            Files.writeString(Path.of(args[0]), Long.toString(helper.pid()));
            dev.itsdropper.vanta.LauncherProcessLifecycle.registerChildProcess(helper);

            UpdaterLauncher.exitOriginalProcess();
        }
    }

    public static final class HelperSleeper {
        private HelperSleeper() {
        }

        public static void main(String[] args) throws Exception {
            while (true) Thread.sleep(1_000);
        }
    }
}
