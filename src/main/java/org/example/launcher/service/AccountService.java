package org.example.launcher.service;

import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.step.java.session.StepFullJavaSession;

import org.example.launcher.account.Account;
import org.example.launcher.account.AccountManager;
import org.example.launcher.auth.AuthManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AccountService {


    private Account currentAccount;

    private final List<AccountListener> listeners =
            new ArrayList<>();

// =============================================================
// ACCOUNT LOADING
// =============================================================

    public Account loadAccount() throws Exception {

        Account account =
                AccountManager.loadAccount();

        if (account == null) {

            setCurrentAccount(null);

            return null;
        }

        StepFullJavaSession.FullJavaSession session =
                account.getSession();

        session =
                MinecraftAuth.JAVA_DEVICE_CODE_LOGIN.refresh(
                        MinecraftAuth.createHttpClient(),
                        session
                );

        account =
                new Account(session);

        AccountManager.saveAccount(account);

        setCurrentAccount(account);

        return account;
    }

// =============================================================
// LOGIN
// =============================================================

    public Account login() throws Exception {

        Account account =
                AuthManager.login();

        setCurrentAccount(account);

        return account;
    }

// =============================================================
// CURRENT ACCOUNT
// =============================================================

    public Account getCurrentAccount() {

        return currentAccount;
    }

    private void setCurrentAccount(
            Account account
    ) {

        currentAccount = account;

        notifyListeners();
    }

// =============================================================
// LOGOUT
// =============================================================

    public void logout() throws IOException {

        AccountManager.clearAccount();

        setCurrentAccount(null);
    }

// =============================================================
// LISTENERS
// =============================================================

    public void addListener(
            AccountListener listener
    ) {

        listeners.add(listener);

        // Immediately give the listener
        // the current state.
        listener.onAccountChanged(
                currentAccount
        );
    }

    public void removeListener(
            AccountListener listener
    ) {

        listeners.remove(listener);
    }

    private void notifyListeners() {

        for (AccountListener listener : listeners) {

            listener.onAccountChanged(
                    currentAccount
            );
        }
    }

// =============================================================
// LISTENER INTERFACE
// =============================================================

    public interface AccountListener {

        void onAccountChanged(
                Account account
        );
    }


}
