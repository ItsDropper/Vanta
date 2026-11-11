package dev.itsdropper.vanta.launcher.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountSessionMaintenanceTest {
    @Test
    void doesNotRefreshWhileMinecraftTokenAndCertificatesAreValid() {
        assertFalse(AccountService.isSessionRefreshDue(
                20_000L,
                30_000L,
                10_000L
        ));
    }

    @Test
    void refreshesWhenMinecraftTokenExpires() {
        assertTrue(AccountService.isSessionRefreshDue(
                9_999L,
                30_000L,
                10_000L
        ));
    }

    @Test
    void refreshesWhenPlayerCertificatesExpireFirst() {
        assertTrue(AccountService.isSessionRefreshDue(
                30_000L,
                9_999L,
                10_000L
        ));
    }

    @Test
    void expiryAtCurrentTimeIsDue() {
        assertTrue(AccountService.isSessionRefreshDue(
                10_000L,
                20_000L,
                10_000L
        ));
    }
}
