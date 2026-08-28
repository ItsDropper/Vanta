package org.example.launcher.service;

import org.example.launcher.MinecraftLauncher;
import org.example.launcher.account.Account;

public class LaunchService {

    private final AccountService accountService;

    private Process minecraftProcess;

    public LaunchService(AccountService accountService) {

        this.accountService = accountService;
    }

    public synchronized Process launch() throws Exception {

        if (isRunning()) {
            return minecraftProcess;
        }

        Account account =
                accountService.getCurrentAccount();

        if (account == null) {
            account =
                    accountService.login();
        }

        minecraftProcess =
                MinecraftLauncher.launch(account);

        return minecraftProcess;
    }

    public synchronized boolean isRunning() {

        return minecraftProcess != null
                && minecraftProcess.isAlive();
    }

    public synchronized void close() {

        if (!isRunning()) {
            minecraftProcess = null;
            return;
        }

        minecraftProcess.destroy();

        minecraftProcess = null;
    }

    public synchronized Process getProcess() {

        return minecraftProcess;
    }
}

