package org.example.launcher.fps;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;

public final class FpsHardwareDetector {

    private FpsHardwareDetector() {
    }

    public static FpsHardwareProfile detect() {
        String gpuName = detectGpuName();

        boolean nvidia =
                gpuName.toLowerCase().contains("nvidia")
                        || gpuName.toLowerCase().contains("geforce");

        boolean meshShaderCapable =
                nvidia && isTuringOrNewer(gpuName);

        return new FpsHardwareProfile(
                gpuName,
                nvidia,
                meshShaderCapable,
                Runtime.getRuntime().availableProcessors(),
                Runtime.getRuntime().maxMemory() / (1024L * 1024L)
        );
    }

    private static String detectGpuName() {
        try {
            GraphicsEnvironment environment =
                    GraphicsEnvironment.getLocalGraphicsEnvironment();

            GraphicsDevice[] devices =
                    environment.getScreenDevices();

            for (GraphicsDevice device : devices) {
                String id = device.getIDstring();

                if (id != null && !id.isBlank()) {
                    return id.trim();
                }
            }
        } catch (Throwable ignored) {
        }

        return "Unknown GPU";
    }

    /*
     * Nvidium requires an NVIDIA GPU with mesh-shader support.
     * NVIDIA's Turing generation starts with GTX 16xx and RTX 20xx.
     *
     * If Java cannot identify a model, we deliberately return false
     * instead of installing Nvidium speculatively.
     */
    private static boolean isTuringOrNewer(String gpuName) {
        String normalized =
                gpuName
                        .toLowerCase()
                        .replaceAll("\\s+", " ");

        if (normalized.contains("rtx")) {
            return true;
        }

        if (!normalized.contains("gtx")) {
            return false;
        }

        java.util.regex.Matcher matcher =
                java.util.regex.Pattern
                        .compile("gtx\\s*(\\d{4})")
                        .matcher(normalized);

        if (!matcher.find()) {
            return false;
        }

        try {
            return Integer.parseInt(matcher.group(1)) >= 1600;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
