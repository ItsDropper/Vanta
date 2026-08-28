package org.example.launcher.security;

import com.sun.jna.platform.win32.Crypt32Util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class WindowsCredentialManager {

    private WindowsCredentialManager() {
    }

    public static String encrypt(String plaintext) {

        if (!isWindows()) {
            throw new UnsupportedOperationException(
                    "Windows DPAPI is only available on Windows"
            );
        }

        byte[] encrypted = Crypt32Util.cryptProtectData(
                plaintext.getBytes(StandardCharsets.UTF_8)
        );

        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static String decrypt(String encrypted) {

        if (!isWindows()) {
            throw new UnsupportedOperationException(
                    "Windows DPAPI is only available on Windows"
            );
        }

        byte[] encryptedBytes =
                Base64.getDecoder().decode(encrypted);

        byte[] decrypted =
                Crypt32Util.cryptUnprotectData(
                        encryptedBytes
                );

        return new String(
                decrypted,
                StandardCharsets.UTF_8
        );
    }

    private static boolean isWindows() {
        return System.getProperty("os.name")
                .toLowerCase()
                .contains("win");
    }
}