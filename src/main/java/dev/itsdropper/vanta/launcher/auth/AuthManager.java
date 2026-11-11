package dev.itsdropper.vanta.launcher.auth;

import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.step.AbstractStep;
import net.raphimc.minecraftauth.step.java.session.StepFullJavaSession;
import net.raphimc.minecraftauth.step.msa.StepMsaDeviceCode;
import net.raphimc.minecraftauth.responsehandler.exception.MsaRequestException;
import net.lenni0451.commons.httpclient.HttpClient;

import dev.itsdropper.vanta.launcher.account.Account;
import dev.itsdropper.vanta.launcher.account.AccountManager;
import dev.itsdropper.vanta.ui.LoginPopup;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

public class AuthManager {

    private static final AbstractStep<?, StepFullJavaSession.FullJavaSession> LOGIN =
            MinecraftAuth.builder()
                    .withClientId("00000000402b5328")
                    .withScope("service::user.auth.xboxlive.com::MBI_SSL")
                    .deviceCode()
                    .withDeviceToken("Win32")
                    .sisuTitleAuthentication("rp://api.minecraftservices.com/")
                    .buildMinecraftJavaProfileStep(true);

    private static final HttpClient HTTP = MinecraftAuth.createHttpClient();

    private AuthManager() {
    }

    /**
     * Refreshes an existing session without opening an interactive login flow.
     * MinecraftAuth refreshes only expired token-chain steps; the Minecraft
     * profile is revalidated as part of its full-session refresh.
     */
    public static Account refresh(Account account) throws Exception {
        if (account == null || account.getSession() == null) {
            throw new IllegalArgumentException("A saved Minecraft session is required to refresh.");
        }

        StepFullJavaSession.FullJavaSession session =
                LOGIN.refresh(HTTP, account.getSession());
        Account refreshedAccount = new Account(session);
        AccountManager.saveAccount(refreshedAccount);
        return refreshedAccount;
    }

    public static Account login(Account savedAccount) throws Exception {
        if (savedAccount != null) {
            StepFullJavaSession.FullJavaSession refreshedSession;
            try {
                refreshedSession = LOGIN.refresh(HTTP, savedAccount.getSession());
            } catch (Exception refreshFailure) {
                if (isRefreshTokenRejected(refreshFailure)) {
                    // Microsoft explicitly rejected the refresh token. Only this
                    // known OAuth outcome should start a new interactive sign-in.
                    System.err.println("Saved Minecraft refresh token was rejected; interactive sign-in is required.");
                    refreshedSession = null;
                } else if (isTransientFailure(refreshFailure)) {
                    // Keep the saved credential untouched so the user can retry.
                    throw new IOException(
                            "Microsoft authentication is temporarily unavailable. "
                                    + "Check the connection and retry; the saved account was preserved.",
                            refreshFailure
                    );
                } else {
                    // Unknown HTTP/API failures are not proof that the user's
                    // refresh token is invalid. Never prompt for login blindly.
                    throw new IOException(
                            "Vanta could not refresh the saved Minecraft session. "
                                    + "The account was preserved; retry before signing in again.",
                            refreshFailure
                    );
                }
            }

            if (refreshedSession != null) {
                Account refreshedAccount = new Account(refreshedSession);
                // Persistence errors are not authentication failures and must never
                // trigger a second interactive login.
                AccountManager.saveAccount(refreshedAccount);
                System.out.println("Authenticated Minecraft account: " + refreshedAccount.getUsername());
                return refreshedAccount;
            }
        }

        StepFullJavaSession.FullJavaSession session;
        try {
            session = LOGIN.getFromInput(
                    HTTP,
                    new StepMsaDeviceCode.MsaDeviceCodeCallback(deviceCode -> {
                        LoginPopup.show(
                                deviceCode.getVerificationUri(),
                                deviceCode.getUserCode()
                        );
                    })
            );
        } finally {
            // Also close the popup when the user cancels or authentication fails.
            LoginPopup.close();
        }

        Account account = new Account(session);
        AccountManager.saveAccount(account);
        System.out.println("Authenticated Minecraft account: " + account.getUsername());
        return account;
    }

    static boolean isRefreshTokenRejected(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof MsaRequestException msaFailure
                    && isRefreshTokenRejectedError(msaFailure.getError())) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    static boolean isRefreshTokenRejectedError(String error) {
        return "invalid_grant".equalsIgnoreCase(error);
    }

    /**
     * Refreshes a saved session without ever starting an interactive login.
     * Used by the session keeper and immediately before launching Minecraft.
     */
    public static Account refreshSavedSession(Account savedAccount) throws Exception {
        if (savedAccount == null || savedAccount.getSession() == null) {
            throw new IllegalArgumentException("A saved Minecraft session is required.");
        }
        StepFullJavaSession.FullJavaSession refreshedSession =
                LOGIN.refresh(HTTP, savedAccount.getSession());
        Account refreshedAccount = new Account(refreshedSession);
        // Persist the rotated refresh token before publishing the new session.
        AccountManager.saveAccount(refreshedAccount);
        return refreshedAccount;
    }

    static boolean isTransientFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof InterruptedIOException
                    || current instanceof ConnectException
                    || current instanceof SocketException
                    || current instanceof UnknownHostException
                    || current instanceof HttpTimeoutException
                    || current instanceof TimeoutException) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }
}
