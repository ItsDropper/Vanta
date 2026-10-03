package org.example.launcher.fps;

import com.sun.management.OperatingSystemMXBean;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FpsHardwareDetector {

    private static final Pattern GPU_NAME =
            Pattern.compile("(?i)(NVIDIA[^\\r\\n]*|GeForce[^\\r\\n]*)");

    private FpsHardwareDetector() {
    }

    public static FpsHardwareProfile detect() {
        String gpuName = detectGpuName();

        boolean nvidia =
                gpuName.toLowerCase(Locale.ROOT).contains("nvidia")
                        || gpuName.toLowerCase(Locale.ROOT).contains("geforce");

        boolean meshShaderCapable =
                nvidia && isTuringOrNewer(gpuName);

        int logicalProcessors =
                Math.max(1, Runtime.getRuntime().availableProcessors());

        long memoryMb = detectPhysicalMemoryMb();

        return new FpsHardwareProfile(
                gpuName,
                nvidia,
                meshShaderCapable,
                logicalProcessors,
                memoryMb
        );
    }

    private static String detectGpuName() {
        if (isWindows()) {
            String windowsGpu = detectWindowsGpuName();
            if (!windowsGpu.isBlank()) {
                return windowsGpu;
            }
        }

        return detectAwtGpuName();
    }

    private static String detectWindowsGpuName() {
        try {
            Process process =
                    new ProcessBuilder(
                            "powershell.exe",
                            "-NoProfile",
                            "-NonInteractive",
                            "-ExecutionPolicy",
                            "Bypass",
                            "-Command",
                            "Get-CimInstance Win32_VideoController | "
                                    + "Select-Object -ExpandProperty Name"
                    )
                            .redirectErrorStream(true)
                            .start();

            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }

            String output =
                    new String(
                            process.getInputStream().readAllBytes(),
                            StandardCharsets.UTF_8
                    );

            List<String> adapters = new ArrayList<>();

            for (String line : output.split("\R")) {
                String value = line.trim();
                if (!value.isBlank()) {
                    adapters.add(value);
                }
            }

            for (String adapter : adapters) {
                String normalized =
                        adapter.toLowerCase(Locale.ROOT);

                if (normalized.contains("nvidia")
                        || normalized.contains("geforce")) {
                    return adapter;
                }
            }

            return adapters.isEmpty()
                    ? ""
                    : adapters.get(0);

        } catch (Exception ignored) {
            return "";
        }
    }

    private static String detectAwtGpuName() {
        try {
            GraphicsEnvironment environment =
                    GraphicsEnvironment.getLocalGraphicsEnvironment();

            for (GraphicsDevice device :
                    environment.getScreenDevices()) {

                String id = device.getIDstring();

                if (id != null && !id.isBlank()
                        && !id.startsWith("\\\\Display")) {
                    return id.trim();
                }
            }
        } catch (Throwable ignored) {
        }

        return "Unknown GPU";
    }

    private static long detectPhysicalMemoryMb() {
        try {
            OperatingSystemMXBean os =
                    (OperatingSystemMXBean)
                            ManagementFactory.getOperatingSystemMXBean();

            long bytes =
                    os.getTotalMemorySize();

            if (bytes > 0) {
                return bytes / (1024L * 1024L);
            }
        } catch (Throwable ignored) {
        }

        return Runtime.getRuntime().maxMemory()
                / (1024L * 1024L);
    }

    private static boolean isWindows() {
        return System.getProperty(
                "os.name",
                ""
        ).toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean isTuringOrNewer(String gpuName) {
        String normalized =
                gpuName
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("\\s+", " ");

        if (normalized.contains("rtx")) {
            return true;
        }

        Matcher matcher =
                Pattern.compile(
                        "gtx\\s*(\\d{4})"
                ).matcher(normalized);

        if (!matcher.find()) {
            return false;
        }

        try {
            return Integer.parseInt(
                    matcher.group(1)
            ) >= 1600;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
