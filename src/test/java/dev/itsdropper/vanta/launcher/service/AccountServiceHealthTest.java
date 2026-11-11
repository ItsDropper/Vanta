package dev.itsdropper.vanta.launcher.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountServiceHealthTest {

    @Test
    void startsWithExplicitNoAccountState() {
        try (AccountService service = new AccountService()) {
            assertEquals(AccountService.SessionHealth.NO_ACCOUNT, service.getSessionHealth());
            assertEquals("No Minecraft account is selected.", service.getSessionHealthMessage());
        }
    }

    @Test
    void explicitRefreshWithoutAnAccountFailsWithoutChangingHealth() {
        try (AccountService service = new AccountService()) {
            assertThrows(IllegalStateException.class, service::refreshSessionNow);
            assertEquals(AccountService.SessionHealth.NO_ACCOUNT, service.getSessionHealth());
        }
    }

    @Test
    void closingServicePublishesClosedHealth() {
        AccountService service = new AccountService();
        service.close();
        assertEquals(AccountService.SessionHealth.CLOSED, service.getSessionHealth());
    }
}
