package org.example.ui;

import org.example.launcher.account.Account;

public final class DebugAccess {

    private static final String OWNER_UUID =
            "8c4e7ff3-4a08-4d14-bc39-27b7b0045611";

    private DebugAccess() {
    }

    public static boolean isOwner(Account account) {
        return account != null
                && OWNER_UUID.equalsIgnoreCase(account.getUuid());
    }
}
