package org.example.launcher.service;

import org.example.launcher.account.Account;
import org.example.launcher.account.AccountManager;
import org.example.launcher.auth.AuthManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class AccountService {

    private Account currentAccount;

    private final List<Account> accounts =
            new ArrayList<>();

    private final ReentrantLock accountLock =
            new ReentrantLock();

    private final List<AccountListener> listeners =
            new ArrayList<>();

    // =============================================================
    // ACCOUNT LOADING
    // =============================================================

    public Account loadAccount() throws Exception {

        accountLock.lock();

        try {

            accounts.clear();

            accounts.addAll(
                    AccountManager.loadAccounts()
            );

            if (accounts.isEmpty()) {

                setCurrentAccount(null);

                return null;
            }

            String activeId =
                    AccountManager.getActiveAccountId();

            Account activeAccount = null;

            if (activeId != null) {

                for (Account account : accounts) {

                    if (account.getUuid()
                            .equals(activeId)) {

                        activeAccount = account;
                        break;
                    }
                }
            }

            /*
             * If there is no valid active account,
             * use the first available account.
             */
            if (activeAccount == null) {

                activeAccount =
                        accounts.get(0);

                AccountManager.setActiveAccount(
                        activeAccount.getUuid()
                );
            }

            /*
             * Refresh only the active account.
             */
            Account refreshedAccount =
                    AuthManager.login(
                            activeAccount
                    );

            replaceAccount(
                    refreshedAccount
            );

            setCurrentAccount(
                    refreshedAccount
            );

            return refreshedAccount;

        } finally {

            accountLock.unlock();
        }
    }

    // =============================================================
    // LOGIN / ADD ACCOUNT
    // =============================================================

    public Account login() throws Exception {

        accountLock.lock();

        try {

            /*
             * AuthManager.login(null) starts
             * Microsoft device-code authentication.
             */
            Account account =
                    AuthManager.login(null);

            /*
             * Add the new account to the collection.
             */
            replaceAccount(account);

            /*
             * Make the newly-added account active.
             */
            AccountManager.setActiveAccount(
                    account.getUuid()
            );

            setCurrentAccount(account);

            return account;

        } finally {

            accountLock.unlock();
        }
    }

    // =============================================================
    // SWITCH ACCOUNT
    // =============================================================

    public void switchAccount(
            String uuid
    ) throws Exception {

        accountLock.lock();

        try {

            Account account =
                    findAccount(uuid);

            if (account == null) {

                throw new IllegalArgumentException(
                        "Account not found: "
                                + uuid
                );
            }

            /*
             * Authenticate first.
             *
             * Only make the account active after
             * authentication succeeds.
             */
            Account refreshedAccount =
                    AuthManager.login(
                            account
                    );

            replaceAccount(
                    refreshedAccount
            );

            AccountManager.setActiveAccount(
                    refreshedAccount.getUuid()
            );

            setCurrentAccount(
                    refreshedAccount
            );

        } finally {

            accountLock.unlock();
        }
    }

    // =============================================================
    // ACCOUNTS
    // =============================================================

    public List<Account> getAccounts() {

        accountLock.lock();

        try {

            return List.copyOf(
                    accounts
            );

        } finally {

            accountLock.unlock();
        }
    }

    public Account getCurrentAccount() {

        return currentAccount;
    }

    // =============================================================
    // CURRENT ACCOUNT
    // =============================================================

    private void setCurrentAccount(
            Account account
    ) {

        currentAccount = account;

        notifyListeners();
    }

    // =============================================================
    // LOGOUT / REMOVE ACCOUNT
    // =============================================================

    public void logout() throws IOException {

        accountLock.lock();

        try {

            if (currentAccount == null) {

                return;
            }

            String uuid =
                    currentAccount.getUuid();

            /*
             * Remove the current account completely.
             */
            AccountManager.deleteAccount(
                    uuid
            );

            accounts.removeIf(
                    account ->
                            account.getUuid()
                                    .equals(uuid)
            );

            /*
             * Choose another account if one exists.
             */
            if (!accounts.isEmpty()) {

                Account nextAccount =
                        accounts.get(0);

                AccountManager.setActiveAccount(
                        nextAccount.getUuid()
                );

                setCurrentAccount(
                        nextAccount
                );

            } else {

                setCurrentAccount(null);
            }

        } finally {

            accountLock.unlock();
        }
    }

    // =============================================================
    // INTERNAL ACCOUNT MANAGEMENT
    // =============================================================

    private Account findAccount(
            String uuid
    ) {

        for (Account account : accounts) {

            if (account.getUuid()
                    .equals(uuid)) {

                return account;
            }
        }

        return null;
    }

    private void replaceAccount(
            Account account
    ) {

        accounts.removeIf(
                existing ->
                        existing.getUuid()
                                .equals(account.getUuid())
        );

        accounts.add(account);
    }

    // =============================================================
    // LISTENERS
    // =============================================================

    public void addListener(
            AccountListener listener
    ) {

        listeners.add(listener);

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

        for (AccountListener listener :
                listeners) {

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