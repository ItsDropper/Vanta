package dev.itsdropper.vanta.launcher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MinecraftLocator {

    /**
     * Resolves Vanta's per-user data directory using the host operating system's
     * standard application-data location. The path is returned without creation.
     */
    public static Path getVantaDirectoryPath() {
        return resolveVantaDirectory(
                System.getProperty("os.name", ""),
                System.getProperty("user.home"),
                System.getenv("APPDATA"),
                System.getenv("XDG_DATA_HOME")
        );
    }

    static Path resolveVantaDirectory(
            String osName, String home, String appData, String xdgDataHome
    ) {
        Platform.OperatingSystem os = Platform.operatingSystem(osName);
        if (os == Platform.OperatingSystem.WINDOWS) {
            if (appData == null || appData.isBlank()) {
                throw new IllegalStateException("APPDATA is not available on Windows.");
            }
            Path appDataPath = Paths.get(appData);
            if (!appDataPath.isAbsolute()) {
                throw new IllegalStateException("APPDATA must be an absolute path on Windows.");
            }
            return appDataPath.resolve("Vanta").normalize();
        }

        if (home == null || home.isBlank()) {
            throw new IllegalStateException("The user home directory is not available.");
        }
        Path homePath = Paths.get(home);
        if (!homePath.isAbsolute()) {
            throw new IllegalStateException("The user home directory must be an absolute path.");
        }
        if (os == Platform.OperatingSystem.MACOS) {
            return homePath.resolve(Path.of("Library", "Application Support", "Vanta")).normalize();
        }
        if (os == Platform.OperatingSystem.LINUX) {
            Path dataHome = xdgDataHome != null && !xdgDataHome.isBlank()
                    && Paths.get(xdgDataHome).isAbsolute()
                    ? Paths.get(xdgDataHome)
                    : homePath.resolve(Path.of(".local", "share"));
            return dataHome.resolve("Vanta").normalize();
        }
        throw new UnsupportedOperationException("Unsupported operating system: " + osName);
    }

    public static Path getVantaDirectory() {
        Path directory = getVantaDirectoryPath();
        try {
            Files.createDirectories(directory);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create Vanta directory.", e);
        }
        return directory;
    }

    public static Path getInstancesDirectory() {
        Path directory = getVantaDirectory().resolve("instances");
        try {
            Files.createDirectories(directory);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create instances directory.", e);
        }
        return directory;
    }

    /** Resolves the shared libraries directory without creating it. */
    public static Path getLibrariesDirectoryPath() {
        return getVantaDirectoryPath().resolve("libraries");
    }

    public static Path getLibrariesDirectory() {
        Path directory = getLibrariesDirectoryPath();
        try {
            Files.createDirectories(directory);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create libraries directory.", e);
        }
        return directory;
    }
}
