package org.example.launcher.fps;

public record FpsHardwareProfile(
        String gpuName,
        boolean nvidia,
        boolean nvidiaMeshShaderCapable,
        int logicalProcessors,
        long memoryMb
) {
    public boolean supportsNvidium() {
        return nvidia && nvidiaMeshShaderCapable;
    }
}
