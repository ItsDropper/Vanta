package dev.itsdropper.vanta.launcher.security;

import dev.itsdropper.vanta.launcher.Platform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WindowsCredentialManagerTest {

    @Test
    void rejectsCallsOnNonWindowsHosts() {
        assumeTrue(Platform.operatingSystem() != Platform.OperatingSystem.WINDOWS);

        assertThrows(UnsupportedOperationException.class,
                () -> WindowsCredentialManager.encrypt("credential"));
        assertThrows(UnsupportedOperationException.class,
                () -> WindowsCredentialManager.decrypt("Y3JlZGVudGlhbA=="));
    }

    @Test
    void protectsAndRestoresUnicodeCredentialsWithCurrentWindowsUser() throws Exception {
        assumeTrue(Platform.operatingSystem() == Platform.OperatingSystem.WINDOWS);

        String original = "Vanta session — ääkköset 🔐";
        String encrypted = WindowsCredentialManager.encrypt(original);

        org.junit.jupiter.api.Assertions.assertNotEquals(original, encrypted);
        assertEquals(original, WindowsCredentialManager.decrypt(encrypted));
    }

    @Test
    void protectsAndRestoresEmptyCredentials() throws Exception {
        assumeTrue(Platform.operatingSystem() == Platform.OperatingSystem.WINDOWS);

        assertEquals("", WindowsCredentialManager.decrypt(
                WindowsCredentialManager.encrypt("")));
    }
}
