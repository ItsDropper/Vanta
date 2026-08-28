package org.example.launcher.auth;

import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.step.java.session.StepFullJavaSession;
import net.raphimc.minecraftauth.step.AbstractStep;
import net.raphimc.minecraftauth.step.msa.StepMsaDeviceCode;
import net.raphimc.minecraftauth.util.logging.ILogger;
import org.example.launcher.account.AccountManager;
import org.example.ui.LoginPopup;

public class AuthManager {

    public static Account login() throws Exception {


        StepFullJavaSession.FullJavaSession session =
                MinecraftAuth.JAVA_DEVICE_CODE_LOGIN.getFromInput(
                        MinecraftAuth.createHttpClient(),
                        new StepMsaDeviceCode.MsaDeviceCodeCallback(deviceCode -> {
                            LoginPopup.show(
                                    deviceCode.getVerificationUri(),
                                    deviceCode.getUserCode()
                            );
                        })
                );

        LoginPopup.close();

        System.out.println("Logged in as: " + session.getMcProfile().getName());

        Account account = new Account(
                session.getMcProfile().getName(),
                session.getMcProfile().getId().toString(),
                session.getMcProfile().getMcToken().getAccessToken()
        );

        AccountManager.saveAccount(account);

        return account;

    }
}