package org.example.launcher.account;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.step.java.session.StepFullJavaSession;
import org.example.launcher.MinecraftLocator;
import org.example.launcher.security.WindowsCredentialManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class AccountManager {

    /*
     * New multi-account storage.
     */
    private static final Path ACCOUNTS_DIRECTORY =
            MinecraftLocator.getVantaDirectory()
                    .resolve("accounts");

    private static final Path ACTIVE_ACCOUNT_FILE =
            MinecraftLocator.getVantaDirectory()
                    .resolve("active-account.txt");

    /*
     * Old single-account storage.
     *
     * This is only used for migration.
     */
    private static final Path LEGACY_FILE =
            Path.of(
                    System.getProperty("user.home"),
                    ".dropperlauncher",
                    "account.json"
            );

    // =============================================================
    // SAVE ACCOUNT
    // =============================================================

    public static void saveAccount(
            Account account
    ) throws IOException {

        Files.createDirectories(
                ACCOUNTS_DIRECTORY
        );

        String uuid =
                account.getUuid();

        Path file =
                getAccountFile(uuid);

        System.out.println(
                "Saving Vanta account: "
                        + account.getUsername()
        );

        System.out.println(
                "Account file: "
                        + file
        );

        JsonObject sessionJson =
                MinecraftAuth.JAVA_DEVICE_CODE_LOGIN
                        .toJson(
                                account.getSession()
                        );

        System.out.println(
                "Session serialized."
        );

        String encrypted =
                WindowsCredentialManager.encrypt(
                        sessionJson.toString()
                );

        System.out.println(
                "Session encrypted."
        );

        System.out.println(
                "Encrypted data length: "
                        + encrypted.length()
        );

        JsonObject json =
                new JsonObject();

        json.addProperty(
                "version",
                1
        );

        json.addProperty(
                "encryptedSession",
                encrypted
        );

        atomicWrite(
                file,
                json.toString()
        );

        System.out.println(
                "Account saved successfully."
        );
    }

    // =============================================================
    // LOAD ONE ACCOUNT
    // =============================================================

    public static Account loadAccount(
            String uuid
    ) throws IOException {

        Path file =
                getAccountFile(uuid);

        if (!Files.exists(file)) {

            return null;
        }

        return loadAccountFile(file);
    }

    // =============================================================
    // LOAD ALL ACCOUNTS
    // =============================================================

    public static List<Account> loadAccounts()
            throws IOException {

        migrateLegacyAccount();

        Files.createDirectories(
                ACCOUNTS_DIRECTORY
        );

        List<Account> accounts =
                new ArrayList<>();

        try (var stream =
                     Files.list(
                             ACCOUNTS_DIRECTORY
                     )) {

            stream
                    .filter(path ->
                            path.getFileName()
                                    .toString()
                                    .endsWith(".json")
                    )
                    .forEach(path -> {

                        try {

                            Account account =
                                    loadAccountFile(path);

                            if (account != null) {

                                accounts.add(account);
                            }

                        } catch (IOException e) {

                            System.err.println(
                                    "Failed to load account: "
                                            + path
                            );

                            e.printStackTrace();
                        }
                    });
        }

        return accounts;
    }

    // =============================================================
    // ACTIVE ACCOUNT
    // =============================================================

    public static String getActiveAccountId()
            throws IOException {

        if (!Files.exists(
                ACTIVE_ACCOUNT_FILE
        )) {

            return null;
        }

        String uuid =
                Files.readString(
                        ACTIVE_ACCOUNT_FILE,
                        StandardCharsets.UTF_8
                ).trim();

        if (uuid.isEmpty()) {

            return null;
        }

        return uuid;
    }

    public static void setActiveAccount(
            String uuid
    ) throws IOException {

        if (uuid == null ||
                uuid.isBlank()) {

            throw new IllegalArgumentException(
                    "Account UUID cannot be empty."
            );
        }

        atomicWrite(
                ACTIVE_ACCOUNT_FILE,
                uuid
        );
    }

    // =============================================================
    // DELETE ACCOUNT
    // =============================================================

    public static void deleteAccount(
            String uuid
    ) throws IOException {

        Path file =
                getAccountFile(uuid);

        if (!Files.exists(file)) {

            return;
        }

        Files.delete(file);

        /*
         * If the deleted account was active,
         * clear the active-account file.
         */
        String active =
                getActiveAccountId();

        if (uuid.equals(active)) {

            Files.deleteIfExists(
                    ACTIVE_ACCOUNT_FILE
            );
        }

        System.out.println(
                "Account removed: "
                        + uuid
        );
    }

    // =============================================================
    // CLEAR ALL ACCOUNTS
    // =============================================================

    public static void clearAccounts()
            throws IOException {

        if (Files.exists(
                ACCOUNTS_DIRECTORY
        )) {

            try (var stream =
                         Files.list(
                                 ACCOUNTS_DIRECTORY
                         )) {

                stream
                        .filter(path ->
                                path.getFileName()
                                        .toString()
                                        .endsWith(".json")
                        )
                        .forEach(path -> {

                            try {

                                Files.deleteIfExists(
                                        path
                                );

                            } catch (IOException e) {

                                throw new RuntimeException(
                                        e
                                );
                            }
                        });
            }
        }

        Files.deleteIfExists(
                ACTIVE_ACCOUNT_FILE
        );
    }

    // =============================================================
    // LEGACY MIGRATION
    // =============================================================

    private static void migrateLegacyAccount()
            throws IOException {

        if (!Files.exists(
                LEGACY_FILE
        )) {

            return;
        }

        System.out.println(
                "Found legacy Vanta account."
        );

        Account account =
                loadAccountFile(
                        LEGACY_FILE
                );

        if (account == null) {

            throw new IOException(
                    "Legacy account could not be loaded."
            );
        }

        String uuid =
                account.getUuid();

        Path newFile =
                getAccountFile(uuid);

        /*
         * If the account already exists in the new
         * storage, there is nothing to migrate.
         */
        if (Files.exists(newFile)) {

            System.out.println(
                    "Legacy account already exists "
                            + "in new account storage."
            );

            Files.delete(
                    LEGACY_FILE
            );

            return;
        }

        /*
         * Save the account into the new storage.
         */
        saveAccount(account);

        /*
         * Verify that the newly-created account
         * can actually be loaded.
         */
        Account verification =
                loadAccount(uuid);

        if (verification == null) {

            throw new IOException(
                    "Account migration verification failed."
            );
        }

        /*
         * Only delete the old file AFTER successful
         * migration and verification.
         */
        Files.delete(
                LEGACY_FILE
        );

        System.out.println(
                "Legacy account migrated successfully."
        );
    }

    // =============================================================
    // INTERNAL LOADING
    // =============================================================

    private static Account loadAccountFile(
            Path file
    ) throws IOException {

        String content =
                Files.readString(
                        file,
                        StandardCharsets.UTF_8
                );

        JsonObject json =
                JsonParser.parseString(
                        content
                ).getAsJsonObject();

        if (!json.has(
                "encryptedSession"
        )) {

            throw new IOException(
                    "Account file is not encrypted: "
                            + file
            );
        }

        String encrypted =
                json.get(
                        "encryptedSession"
                ).getAsString();

        String decrypted;

        try {

            decrypted =
                    WindowsCredentialManager.decrypt(
                            encrypted
                    );

        } catch (Exception e) {

            throw new IOException(
                    "Could not decrypt account: "
                            + file,
                    e
            );
        }

        JsonObject sessionJson =
                JsonParser.parseString(
                        decrypted
                ).getAsJsonObject();

        StepFullJavaSession.FullJavaSession session =
                MinecraftAuth.JAVA_DEVICE_CODE_LOGIN
                        .fromJson(
                                sessionJson
                        );

        return new Account(
                session
        );
    }

    // =============================================================
    // PATH
    // =============================================================

    private static Path getAccountFile(
            String uuid
    ) {

        return ACCOUNTS_DIRECTORY
                .resolve(
                        uuid + ".json"
                );
    }

    // =============================================================
    // ATOMIC WRITE
    // =============================================================

    private static void atomicWrite(
            Path target,
            String content
    ) throws IOException {

        Files.createDirectories(
                target.getParent()
        );

        Path temp =
                target.resolveSibling(
                        target.getFileName()
                                + ".tmp"
                );

        Files.writeString(
                temp,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );

        try {

            Files.move(
                    temp,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );

        } catch (
                AtomicMoveNotSupportedException ex
        ) {

            Files.move(
                    temp,
                    target,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }
}