package org.example.launcher.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.launcher.auth.Account;

import java.io.File;
import java.io.IOException;

public class AccountManager {

    private static final File FILE = new File(
            System.getProperty("user.home"),
            ".dropperlauncher/account.json"
    );

    private static final ObjectMapper mapper = new ObjectMapper();


    public static void saveAccount(Account account) throws IOException {

        FILE.getParentFile().mkdirs();

        mapper.writeValue(FILE, account);
    }


    public static Account loadAccount() throws IOException {

        if (!FILE.exists()) {
            return null;
        }

        return mapper.readValue(FILE, Account.class);
    }
}