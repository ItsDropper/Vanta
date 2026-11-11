package dev.itsdropper.vanta.launcher.auth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class AuthManagerFailureClassificationTest {
    @Test
    void recognizesNestedTransientTransportFailures() {
        assertTrue(AuthManager.isTransientFailure(
                new RuntimeException("refresh failed", new ConnectException("offline"))));
        assertTrue(AuthManager.isTransientFailure(
                new RuntimeException("refresh failed", new UnknownHostException("dns"))));
        assertTrue(AuthManager.isTransientFailure(
                new HttpTimeoutException("timed out")));
    }

    @Test
    void doesNotTreatAuthenticationRejectionOrGenericIoAsNetworkOutage() {
        assertFalse(AuthManager.isTransientFailure(
                new IllegalStateException("invalid refresh token")));
        assertFalse(AuthManager.isTransientFailure(
                new IOException("malformed authentication response")));
    }
}
