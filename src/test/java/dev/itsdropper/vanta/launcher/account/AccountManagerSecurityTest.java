package dev.itsdropper.vanta.launcher.account;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountManagerSecurityTest {
    @Test
    void acceptsCanonicalMinecraftUuidAndNormalizesCase() {
        assertEquals(
                "123e4567-e89b-12d3-a456-426614174000",
                AccountManager.normalizeAccountId("123E4567-E89B-12D3-A456-426614174000")
        );
    }

    @Test
    void rejectsPathTraversalAndNonCanonicalIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> AccountManager.normalizeAccountId("../../outside"));
        assertThrows(IllegalArgumentException.class,
                () -> AccountManager.normalizeAccountId("123e4567e89b12d3a456426614174000"));
        assertThrows(IllegalArgumentException.class,
                () -> AccountManager.normalizeAccountId(null));
    }

    @Test
    void activeAccountSetterRejectsInvalidIdentifierBeforeWriting() {
        assertThrows(IllegalArgumentException.class,
                () -> AccountManager.setActiveAccount("../account"));
    }
}
