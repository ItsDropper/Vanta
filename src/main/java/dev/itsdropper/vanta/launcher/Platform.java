package dev.itsdropper.vanta.launcher;

import java.util.Locale;

/** Centralized host platform detection for native paths and distribution logic. */
public final class Platform {
    public enum OperatingSystem {
        WINDOWS, LINUX, MACOS, UNKNOWN
    }

    private Platform() {}

    public static OperatingSystem operatingSystem() {
        return operatingSystem(System.getProperty("os.name", ""));
    }

    static OperatingSystem operatingSystem(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        // "Darwin" contains the substring "win", so detect Apple platforms first.
        if (normalized.contains("mac") || normalized.contains("darwin")) return OperatingSystem.MACOS;
        if (normalized.contains("windows")) return OperatingSystem.WINDOWS;
        if (normalized.contains("linux") || normalized.contains("unix") || normalized.contains("bsd")) {
            return OperatingSystem.LINUX;
        }
        return OperatingSystem.UNKNOWN;
    }

    public static String javaExecutableName() {
        return javaExecutableName(System.getProperty("os.name", ""));
    }

    static String javaExecutableName(String osName) {
        return operatingSystem(osName) == OperatingSystem.WINDOWS ? "java.exe" : "java";
    }

    public static String adoptiumOs() {
        return adoptiumOs(System.getProperty("os.name", ""));
    }

    static String adoptiumOsForTest(String osName) {
        return adoptiumOs(osName);
    }

    private static String adoptiumOs(String osName) {
        return switch (operatingSystem(osName)) {
            case WINDOWS -> "windows";
            case LINUX -> "linux";
            case MACOS -> "mac";
            case UNKNOWN -> throw new UnsupportedOperationException("Unsupported operating system: "
                    + osName);
        };
    }

    public static String adoptiumArchitecture() {
        return adoptiumArchitecture(System.getProperty("os.arch", ""));
    }

    static String adoptiumArchitecture(String architecture) {
        String arch = architecture == null ? "" : architecture.toLowerCase(Locale.ROOT);
        return switch (arch) {
            case "amd64", "x86_64", "x86-64", "x64" -> "x64";
            case "aarch64", "arm64" -> "aarch64";
            default -> throw new UnsupportedOperationException("Unsupported CPU architecture: " + arch);
        };
    }

    public static boolean supportsDesktop() {
        if (operatingSystem() == OperatingSystem.UNKNOWN) return false;
        try {
            String architecture = adoptiumArchitecture();
            return architecture.equals("x64") || architecture.equals("aarch64");
        } catch (UnsupportedOperationException unsupported) {
            return false;
        }
    }
}
