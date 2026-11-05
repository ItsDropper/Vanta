package dev.itsdropper.vanta.launcher.security;

import dev.itsdropper.vanta.launcher.Platform;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CredentialManagerPlatformTest {
    @Test
    void windowsDpapiStoresAndRetrievesAccountSession() throws Exception {
        assumeTrue(Platform.operatingSystem() == Platform.OperatingSystem.WINDOWS);
        String secret = "vanta-dpapi-test-" + UUID.randomUUID();
        String reference = CredentialManager.encrypt(secret, UUID.randomUUID().toString());
        assertEquals(secret, CredentialManager.decrypt(reference));
    }

    @Test
    void macOSKeychainStoresAndRetrievesAccountSession() throws Exception {
        assumeTrue(Platform.operatingSystem() == Platform.OperatingSystem.MACOS);
        String accountId = UUID.randomUUID().toString();
        String secret = "vanta-keychain-test-" + UUID.randomUUID();

        String reference = CredentialManager.encrypt(secret, accountId);
        try {
            assertEquals(secret, CredentialManager.decrypt(reference));
        } finally {
            CredentialManager.delete(reference);
        }
    }
}
