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
    void onlyExplicitInvalidGrantRequiresInteractiveSignIn() {
        assertTrue(AuthManager.isRefreshTokenRejectedError("invalid_grant"));
        assertTrue(AuthManager.isRefreshTokenRejectedError("INVALID_GRANT"));
        assertFalse(AuthManager.isRefreshTokenRejectedError("invalid_client"));
        assertFalse(AuthManager.isRefreshTokenRejectedError("temporarily_unavailable"));
        assertFalse(AuthManager.isRefreshTokenRejectedError(null));
    }

    @Test
    void recognizesInterruptedDeviceCodeLoginAsCancellation() {
        assertTrue(AuthManager.isInterruptedFailure(new InterruptedException("user closed login")));
        assertTrue(AuthManager.isInterruptedFailure(
                new RuntimeException("polling stopped", new InterruptedException("cancelled"))));
        assertFalse(AuthManager.isInterruptedFailure(new IOException("network unavailable")));
    }

    @Test
    void doesNotTreatAuthenticationRejectionOrGenericIoAsNetworkOutage() {
        assertFalse(AuthManager.isTransientFailure(
                new IllegalStateException("invalid refresh token")));
        assertFalse(AuthManager.isTransientFailure(
                new IOException("malformed authentication response")));
    }
}
