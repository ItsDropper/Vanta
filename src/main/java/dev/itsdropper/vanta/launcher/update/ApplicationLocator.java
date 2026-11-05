package dev.itsdropper.vanta.launcher.update;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Locates and describes the three jpackage application-image layouts. */
public final class ApplicationLocator {
    private ApplicationLocator() {}

    public static Path getApplicationDirectory() {
        Path fromRuntime = findFromJavaHome();
        if (fromRuntime != null) return fromRuntime;

        Path fromProcess = findFromProcessCommand();
        if (fromProcess != null) return fromProcess;

        Path fromCodeSource = findFromCodeSource();
        if (fromCodeSource != null) return fromCodeSource;

        Path fromWorkingDirectory = findInstallationRoot(
                Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize());
        if (fromWorkingDirectory != null) return fromWorkingDirectory;

        throw new IllegalStateException(
                "Could not locate the Vanta application image. Self-update requires a packaged installation.");
    }

    /** Returns the private runtime directory inside a jpackage image. */
    public static Path getRuntimeDirectory(Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        Path[] candidates = {
                normalized.resolve("runtime"),
                normalized.resolve("lib/runtime"),
                normalized.resolve("Contents/runtime/Contents/Home"),
                normalized.resolve("Contents/runtime")
        };
        for (Path candidate : candidates) {
            if (isRuntimeImage(candidate)) return candidate;
        }
        throw new IllegalStateException("Could not locate the bundled Java runtime in " + normalized);
    }

    /** Returns the directory containing the application JAR and configuration. */
    public static Path getApplicationPayloadDirectory(Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        Path[] candidates = {
                normalized.resolve("app"),
                normalized.resolve("lib/app"),
                normalized.resolve("Contents/app")
        };
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) return candidate;
        }
        throw new IllegalStateException("Could not locate the Vanta application payload in " + normalized);
    }

    /** Returns the native jpackage launcher executable for the image layout. */
    public static Path getLauncherExecutable(Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        if (!isSupportedInstallation(normalized)) {
            throw new IllegalStateException("Not a supported Vanta application image: " + normalized);
        }
        Path[] candidates = {
                normalized.resolve("Vanta.exe"),
                normalized.resolve("bin/Vanta"),
                normalized.resolve("Contents/MacOS/Vanta")
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("Could not locate the Vanta launcher executable in " + normalized);
    }

    static boolean isTranslocatedApplication(Path root) {
        if (root == null) return false;
        for (Path component : root.toAbsolutePath().normalize()) {
            if ("AppTranslocation".equalsIgnoreCase(component.toString())) return true;
        }
        return false;
    }

    /** True only when the complete image signature for a supported OS exists. */
    public static boolean isSupportedInstallation(Path root) {
        return root != null && detectLayout(root.toAbsolutePath().normalize()) != Layout.UNKNOWN;
    }

    private static Path findFromJavaHome() {
        try {
            return findFromJavaHome(Path.of(System.getProperty("java.home")));
        } catch (RuntimeException ignored) {
            // Try the other process-location strategies.
            return null;
        }
    }

    static Path findFromJavaHome(Path javaHomePath) {
        try {
            Path javaHome = javaHomePath.toAbsolutePath().normalize();
            Path current = javaHome;
            for (int depth = 0; current != null && depth < 4; depth++, current = current.getParent()) {
                Path root = current;
                if (current.getFileName() != null
                        && "runtime".equalsIgnoreCase(current.getFileName().toString())) {
                    root = current.getParent();
                    if (root != null && root.getFileName() != null
                            && "lib".equalsIgnoreCase(root.getFileName().toString())) {
                        root = root.getParent();
                    } else if (root != null && root.getFileName() != null
                            && "Contents".equalsIgnoreCase(root.getFileName().toString())) {
                        root = root.getParent();
                    }
                }
                if (root != null && isSupportedInstallation(root)) return root;
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static Path findFromProcessCommand() {
        try {
            Optional<String> command = ProcessHandle.current().info().command();
            if (command.isEmpty()) return null;
            return findInstallationRoot(Path.of(command.get()).toAbsolutePath().normalize().getParent());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Path findFromCodeSource() {
        try {
            Path location = Path.of(ApplicationLocator.class.getProtectionDomain()
                            .getCodeSource().getLocation().toURI())
                    .toAbsolutePath().normalize();
            return findInstallationRoot(Files.isRegularFile(location) ? location.getParent() : location);
        } catch (URISyntaxException | RuntimeException ignored) {
            return null;
        }
    }

    private static Path findInstallationRoot(Path start) {
        Path current = start;
        for (int depth = 0; current != null && depth < 16; depth++, current = current.getParent()) {
            if (isSupportedInstallation(current)) return current;
        }
        return null;
    }

    private static boolean isRuntimeImage(Path runtime) {
        return Files.isRegularFile(runtime.resolve("bin/java"))
                || Files.isRegularFile(runtime.resolve("bin/java.exe"))
                || Files.isRegularFile(runtime.resolve("bin/javaw.exe"))
                || Files.isRegularFile(runtime.resolve("Contents/Home/bin/java"))
                || Files.isRegularFile(runtime.resolve("lib/modules"))
                || Files.isRegularFile(runtime.resolve("Contents/Home/lib/modules"));
    }

    private static Layout detectLayout(Path root) {
        if (!Files.isDirectory(root)) return Layout.UNKNOWN;

        if (Files.isRegularFile(root.resolve("Vanta.exe"))
                && Files.isDirectory(root.resolve("app"))
                && isRuntimeImage(root.resolve("runtime"))) {
            return Layout.WINDOWS;
        }

        if (Files.isRegularFile(root.resolve("bin/Vanta"))
                && Files.isDirectory(root.resolve("lib/app"))
                && isRuntimeImage(root.resolve("lib/runtime"))) {
            return Layout.LINUX;
        }

        Path contents = root.resolve("Contents");
        if (Files.isRegularFile(contents.resolve("MacOS/Vanta"))
                && Files.isDirectory(contents.resolve("app"))
                && isRuntimeImage(contents.resolve("runtime"))) {
            return Layout.MACOS;
        }
        return Layout.UNKNOWN;
    }

    private enum Layout { WINDOWS, LINUX, MACOS, UNKNOWN }
}
