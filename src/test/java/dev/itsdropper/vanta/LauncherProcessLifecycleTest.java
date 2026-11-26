package dev.itsdropper.vanta;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherProcessLifecycleTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void closingLauncherRunsCleanupAndExitsDespiteNonDaemonThreads() throws Exception {
        Path marker = temporaryDirectory.resolve("cleanup-ran.txt");
        Path java = Path.of(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java");
        String classPath = String.join(File.pathSeparator,
                System.getProperty("java.class.path"),
                Path.of(LauncherProcessLifecycleTest.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()).toString(),
                Path.of(LauncherProcessLifecycle.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()).toString());

        Process child = new ProcessBuilder(java.toString(), "-cp", classPath,
                Child.class.getName(), marker.toString())
                .redirectErrorStream(true)
                .start();

        boolean exited = child.waitFor(5, TimeUnit.SECONDS);
        if (!exited) {
            child.destroyForcibly();
            child.waitFor();
        }

        assertTrue(exited,
                "Launcher JVM remained alive after its window-close shutdown path");
        assertEquals(0, child.exitValue());
        assertTrue(Files.isRegularFile(marker),
                "Shutdown cleanup must run before the launcher process exits");
    }

    @Test
    void closingLauncherReapsRegisteredHelperProcesses() throws Exception {
        Path pidFile = temporaryDirectory.resolve("helper-process.pid");
        Path java = Path.of(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java");
        String classPath = String.join(File.pathSeparator,
                System.getProperty("java.class.path"),
                Path.of(LauncherProcessLifecycleTest.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()).toString(),
                Path.of(LauncherProcessLifecycle.class.getProtectionDomain()
                        .getCodeSource().getLocation().toURI()).toString());

        Process child = new ProcessBuilder(java.toString(), "-cp", classPath,
                ProcessChild.class.getName(), pidFile.toString())
                .redirectErrorStream(true)
                .start();
        assertTrue(child.waitFor(5, TimeUnit.SECONDS),
                "Launcher JVM did not exit after helper-process cleanup");
        assertEquals(0, child.exitValue());
        assertTrue(Files.isRegularFile(pidFile), "Child did not publish helper PID");

        long helperPid = Long.parseLong(Files.readString(pidFile));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (ProcessHandle.of(helperPid).map(ProcessHandle::isAlive).orElse(false)
                && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        assertFalse(ProcessHandle.of(helperPid).map(ProcessHandle::isAlive).orElse(false),
                "A launcher-owned helper process survived launcher shutdown");
    }

    public static final class ProcessChild {
        private ProcessChild() {
        }

        public static void main(String[] args) throws Exception {
            Path java = Path.of(System.getProperty("java.home"), "bin",
                    isWindows() ? "java.exe" : "java");
            Process helper = new ProcessBuilder(java.toString(), "-cp",
                    System.getProperty("java.class.path"), HelperSleeper.class.getName())
                    .redirectErrorStream(true)
                    .start();
            Files.writeString(Path.of(args[0]), Long.toString(helper.pid()));
            LauncherProcessLifecycle.registerChildProcess(helper);
            LauncherProcessLifecycle.exit();
        }
    }

    public static final class HelperSleeper {
        private HelperSleeper() {
        }

        public static void main(String[] args) throws Exception {
            while (true) Thread.sleep(1_000);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    public static final class Child {
        private Child() {
        }

        public static void main(String[] args) {
            Thread backgroundWorker = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(1_000);
                    } catch (InterruptedException ignored) {
                        // Simulate a worker that does not cooperate with shutdown.
                    }
                }
            }, "vanta-test-background-worker");
            backgroundWorker.setDaemon(false);
            backgroundWorker.start();

            LauncherProcessLifecycle.registerCleanup(() -> {
                try {
                    Files.writeString(Path.of(args[0]), "cleaned");
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            });
            LauncherProcessLifecycle.exit();
        }
    }
}
