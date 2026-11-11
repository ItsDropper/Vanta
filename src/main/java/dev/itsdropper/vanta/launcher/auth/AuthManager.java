package dev.itsdropper.vanta.launcher.auth;

import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.step.AbstractStep;
import net.raphimc.minecraftauth.step.java.session.StepFullJavaSession;
import net.raphimc.minecraftauth.step.msa.StepMsaDeviceCode;
import net.lenni0451.commons.httpclient.HttpClient;

import dev.itsdropper.vanta.launcher.account.Account;
import dev.itsdropper.vanta.launcher.account.AccountManager;
import dev.itsdropper.vanta.ui.LoginPopup;

import java.io.IOException;
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

    public static Account login(Account savedAccount) throws Exception {
        if (savedAccount != null) {
            final StepFullJavaSession.FullJavaSession refreshedSession;
            try {
                refreshedSession = LOGIN.refresh(HTTP, savedAccount.getSession());
            } catch (Exception refreshFailure) {
                if (isTransientFailure(refreshFailure)) {
                    // Do not turn a temporary outage into a device-code login loop.
                    // Keep the saved credential untouched so the user can retry later.
                    throw new IOException(
                            "Microsoft authentication is temporarily unavailable. "
                                    + "Check the connection and retry; the saved account was preserved.",
                            refreshFailure
                    );
                }
                // A non-transient refresh rejection (for example, revoked credentials)
                // requires the normal interactive sign-in flow below.
                System.err.println("Saved Minecraft authentication was rejected; interactive sign-in is required.");
                refreshedSession = null;
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

    private static boolean isTransientFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof IOException
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
