package dev.itsdropper.vanta.launcher.security;

import dev.itsdropper.vanta.launcher.Platform;

import java.io.IOException;

public final class WindowsCredentialManager {

    private WindowsCredentialManager() {
    }

    public static String encrypt(String plaintext) throws IOException {
        if (!isWindows()) {
            throw new UnsupportedOperationException(
                    "Windows DPAPI is only available on Windows"
            );
        }
        return WindowsDpapi.protectToBase64(plaintext);
    }

    public static String decrypt(String encrypted) throws IOException {
        if (!isWindows()) {
            throw new UnsupportedOperationException(
                    "Windows DPAPI is only available on Windows"
            );
        }
        return WindowsDpapi.unprotectFromBase64(encrypted);
    }

    private static boolean isWindows() {
        return Platform.operatingSystem() == Platform.OperatingSystem.WINDOWS;
    }
}
