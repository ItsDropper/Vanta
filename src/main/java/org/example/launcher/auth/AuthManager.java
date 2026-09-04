package org.example.launcher.auth;

import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.step.AbstractStep;
import net.raphimc.minecraftauth.step.java.session.StepFullJavaSession;
import net.raphimc.minecraftauth.step.msa.StepMsaDeviceCode;

import net.lenni0451.commons.httpclient.HttpClient;

import org.example.launcher.account.Account;
import org.example.launcher.account.AccountManager;
import org.example.ui.LoginPopup;

public class AuthManager {

    private static final AbstractStep<?, StepFullJavaSession.FullJavaSession> LOGIN =
            MinecraftAuth.builder()
                    .withClientId("00000000402b5328")
                    .withScope("service::user.auth.xboxlive.com::MBI_SSL")
                    .deviceCode()
                    .withDeviceToken("Win32")
                    .sisuTitleAuthentication(
                            "rp://api.minecraftservices.com/"
                    )
                    .buildMinecraftJavaProfileStep(true);

    private static final HttpClient HTTP =
            MinecraftAuth.createHttpClient();

    // =============================================================
    // LOGIN
    // =============================================================

    public static Account login(
            Account savedAccount
    ) throws Exception {

        /*
         * Try to refresh the already-loaded account.
         */
        if (savedAccount != null) {

            System.out.println(
                    "Found saved Minecraft account."
            );

            System.out.println(
                    "Refreshing authentication..."
            );

            try {

                System.out.println(
                        "Starting authentication refresh request..."
                );

                StepFullJavaSession.FullJavaSession session =
                        LOGIN.refresh(
                                HTTP,
                                savedAccount.getSession()
                        );

                System.out.println(
                        "Authentication refresh request completed."
                );

                Account refreshedAccount =
                        new Account(session);

                AccountManager.saveAccount(
                        refreshedAccount
                );

                System.out.println(
                        "Logged in automatically as: "
                                + refreshedAccount.getUsername()
                );

                return refreshedAccount;

            } catch (Exception ex) {

                System.out.println(
                        "Saved authentication could not be refreshed."
                );

                System.out.println(
                        "Starting Microsoft login again."
                );

                ex.printStackTrace();
            }
        }

        // =========================================================
        // MICROSOFT LOGIN
        // =========================================================

        StepFullJavaSession.FullJavaSession session =
                LOGIN.getFromInput(
                        HTTP,
                        new StepMsaDeviceCode.MsaDeviceCodeCallback(
                                deviceCode -> {

                                    System.out.println(
                                            "Microsoft login code: "
                                                    + deviceCode.getUserCode()
                                    );

                                    System.out.println(
                                            "Verification URL: "
                                                    + deviceCode.getVerificationUri()
                                    );

                                    LoginPopup.show(
                                            deviceCode.getVerificationUri(),
                                            deviceCode.getUserCode()
                                    );
                                }
                        )
                );

        LoginPopup.close();

        Account account =
                new Account(session);

        AccountManager.saveAccount(
                account
        );

        System.out.println(
                "Logged in as: "
                        + account.getUsername()
        );

        return account;
    }
}