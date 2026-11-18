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
