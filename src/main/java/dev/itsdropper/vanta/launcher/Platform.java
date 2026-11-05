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
        if (normalized.contains("win")) return OperatingSystem.WINDOWS;
        if (normalized.contains("mac") || normalized.contains("darwin")) return OperatingSystem.MACOS;
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
        return switch (operatingSystem()) {
            case WINDOWS -> "windows";
            case LINUX -> "linux";
            case MACOS -> "mac";
            case UNKNOWN -> throw new UnsupportedOperationException("Unsupported operating system: "
                    + System.getProperty("os.name", "unknown"));
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
            case "x86", "i386", "i486", "i586", "i686" -> "x86";
            default -> throw new UnsupportedOperationException("Unsupported CPU architecture: " + arch);
        };
    }

    public static boolean supportsDesktop() {
        return operatingSystem() != OperatingSystem.UNKNOWN
                && (adoptiumArchitecture().equals("x64") || adoptiumArchitecture().equals("aarch64"));
    }
}
