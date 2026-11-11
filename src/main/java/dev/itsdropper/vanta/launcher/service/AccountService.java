package dev.itsdropper.vanta.launcher.service;

import dev.itsdropper.vanta.launcher.account.Account;
import dev.itsdropper.vanta.launcher.account.AccountManager;
import dev.itsdropper.vanta.launcher.auth.AuthManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class AccountService {

    private volatile Account currentAccount;

    private final List<Account> accounts =
            new ArrayList<>();

    private final ReentrantLock accountLock =
            new ReentrantLock();

    private final List<AccountListener> listeners =
            new CopyOnWriteArrayList<>();

    private final ScheduledExecutorService sessionMaintenance =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "Vanta-Session-Maintenance");
                thread.setDaemon(true);
                return thread;
            });

    private ScheduledFuture<?> maintenanceTask;
    private String maintenancePausedAccountId;
    private volatile boolean closed;

    // =============================================================
    // ACCOUNT LOADING
    // =============================================================

    public Account loadAccount() throws Exception {

        accountLock.lock();

        try {

            List<Account> loadedAccounts = AccountManager.loadAccounts();
            accounts.clear();
            accounts.addAll(loadedAccounts);

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
            startSessionMaintenance();

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
             * Persist the active selection before publishing it in memory.
             */
            AccountManager.setActiveAccount(
                    account.getUuid()
            );

            replaceAccount(account);
            maintenancePausedAccountId = null;
            setCurrentAccount(account);
            startSessionMaintenance();

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

            AccountManager.setActiveAccount(
                    refreshedAccount.getUuid()
            );

            replaceAccount(refreshedAccount);
            maintenancePausedAccountId = null;
            setCurrentAccount(
                    refreshedAccount
            );
            startSessionMaintenance();

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
        accountLock.lock();
        try {
            return currentAccount;
        } finally {
            accountLock.unlock();
        }
    }

    /**
     * Refreshes the selected session immediately before launching Minecraft.
     * A valid refresh is silent. Only Microsoft's explicit invalid_grant
     * response falls back to the device-code sign-in flow.
     */
    public Account refreshCurrentAccount() throws Exception {
        accountLock.lock();
        try {
            if (currentAccount == null) {
                return null;
            }
            Account account = currentAccount;
            maintenancePausedAccountId = null;
            try {
                Account refreshed = AuthManager.login(account);
                replaceAccount(refreshed);
                setCurrentAccount(refreshed);
                return refreshed;
            } catch (Exception failure) {
                maintenancePausedAccountId = account.getUuid();
                throw failure;
            }
        } finally {
            accountLock.unlock();
        }
    }

    private void startSessionMaintenance() {
        if (closed || (maintenanceTask != null && !maintenanceTask.isDone() && !maintenanceTask.isCancelled())) {
            return;
        }
        maintenanceTask = sessionMaintenance.scheduleWithFixedDelay(
                this::refreshSessionIfExpired, 1, 1, TimeUnit.MINUTES
        );
    }

    private void refreshSessionIfExpired() {
        if (closed || !accountLock.tryLock()) {
            return;
        }
        try {
            Account account = currentAccount;
            if (account == null
                    || account.getUuid().equals(maintenancePausedAccountId)
                    || !isSessionRefreshDue(account)) {
                return;
            }
            try {
                Account refreshed = AuthManager.refresh(account);
                replaceAccount(refreshed);
                maintenancePausedAccountId = null;
                setCurrentAccount(refreshed);
                System.out.println("[Vanta Auth] Active Minecraft session refreshed.");
            } catch (Exception failure) {
                // Avoid repeated requests after a rejected refresh. A launch
                // or explicit refresh is the next retry point.
                maintenancePausedAccountId = account.getUuid();
                System.err.println("[Vanta Auth] Background session refresh failed; "
                        + "automatic retries paused until the next explicit refresh.");
            }
        } finally {
            accountLock.unlock();
        }
    }

    static boolean isSessionRefreshDue(Account account) {
        return account != null
                && account.getSession() != null
                && account.getSession().isExpired();
    }

    public void close() {
        closed = true;
        if (maintenanceTask != null) {
            maintenanceTask.cancel(true);
        }
        sessionMaintenance.shutdownNow();
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

        if (listener == null) {
            throw new IllegalArgumentException("Account listener cannot be null.");
        }
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