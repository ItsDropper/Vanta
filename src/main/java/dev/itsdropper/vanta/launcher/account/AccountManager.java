package dev.itsdropper.vanta.launcher.account;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.step.java.session.StepFullJavaSession;
import dev.itsdropper.vanta.launcher.MinecraftLocator;
import dev.itsdropper.vanta.launcher.security.CredentialManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

        ensureAccountsDirectory();

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
                CredentialManager.encrypt(
                        sessionJson.toString(),
                        uuid
                );

        System.out.println(
                "Session encrypted."
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

        ensureAccountsDirectory();

        List<Account> accounts =
                new ArrayList<>();

        try (var stream =
                     Files.list(
                             ACCOUNTS_DIRECTORY
                     )) {

            try {
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

                        } catch (RuntimeException | IOException failure) {
                            throw new AccountLoadFailure(path, failure);
                        }
                    });
            } catch (AccountLoadFailure failure) {
                throw new IOException(failure.getMessage(), failure.getCause());
            }
        }

        return accounts;
    }

    private static void ensureAccountsDirectory() throws IOException {
        Files.createDirectories(ACCOUNTS_DIRECTORY);
        if (Files.isSymbolicLink(ACCOUNTS_DIRECTORY)
                || !Files.isDirectory(ACCOUNTS_DIRECTORY, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Account storage path is not a real directory.");
        }
    }

    private static final class AccountLoadFailure extends RuntimeException {
        private AccountLoadFailure(Path path, Exception cause) {
            super("Could not load Vanta account file: " + path.getFileName(), cause);
        }
    }

    // =============================================================
    // ACTIVE ACCOUNT
    // =============================================================

    public static String getActiveAccountId()
            throws IOException {

        if (!Files.exists(ACTIVE_ACCOUNT_FILE, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        if (Files.isSymbolicLink(ACTIVE_ACCOUNT_FILE)
                || !Files.isRegularFile(ACTIVE_ACCOUNT_FILE, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Active-account storage is not a regular file.");
        }

        String uuid =
                Files.readString(
                        ACTIVE_ACCOUNT_FILE,
                        StandardCharsets.UTF_8
                ).trim();

        if (uuid.isEmpty()) {
            return null;
        }

        try {
            return normalizeAccountId(uuid);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("The active-account file contains an invalid account identifier.", invalid);
        }
    }

    public static void setActiveAccount(
            String uuid
    ) throws IOException {

        atomicWrite(
                ACTIVE_ACCOUNT_FILE,
                normalizeAccountId(uuid)
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

        String credentialReference = readCredentialReference(file);
        if (credentialReference != null) {
            CredentialManager.delete(credentialReference);
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

        if (Files.exists(ACCOUNTS_DIRECTORY, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(ACCOUNTS_DIRECTORY)
                    || !Files.isDirectory(ACCOUNTS_DIRECTORY, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Account storage directory is not a real directory.");
            }

            List<Path> accountFiles;
            try (var stream = Files.list(ACCOUNTS_DIRECTORY)) {
                accountFiles = stream
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .toList();
            }

            for (Path file : accountFiles) {
                String credentialReference = readCredentialReference(file);
                if (credentialReference != null) {
                    CredentialManager.delete(credentialReference);
                }
                Files.deleteIfExists(file);
            }
        }

        Files.deleteIfExists(ACTIVE_ACCOUNT_FILE);
    }

    private static String readCredentialReference(Path file) throws IOException {
        if (Files.isSymbolicLink(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Account storage entry is not a regular file: " + file.getFileName());
        }

        try {
            JsonObject stored = JsonParser.parseString(
                    Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!stored.has("encryptedSession") || !stored.get("encryptedSession").isJsonPrimitive()
                    || !stored.getAsJsonPrimitive("encryptedSession").isString()) {
                throw new IOException("Account file has no valid credential reference: " + file.getFileName());
            }
            return stored.get("encryptedSession").getAsString();
        } catch (com.google.gson.JsonParseException | IllegalStateException malformed) {
            throw new IOException("Account file is malformed: " + file.getFileName(), malformed);
        }
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
        if (Files.exists(newFile, LinkOption.NOFOLLOW_LINKS)) {
            // Never discard the legacy credential until the replacement can
            // actually be decrypted and parsed successfully.
            Account verification = loadAccount(uuid);
            if (verification == null) {
                throw new IOException("Existing account migration target could not be verified.");
            }
            Files.delete(LEGACY_FILE);
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

        if (Files.isSymbolicLink(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Account storage entry is not a regular file: " + file.getFileName());
        }
        if (Files.size(file) > 1024 * 1024) {
            throw new IOException("Account file exceeds the 1 MiB safety limit: " + file.getFileName());
        }

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
                    CredentialManager.decrypt(
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

    static String normalizeAccountId(String uuid) {
        if (uuid == null || !uuid.matches(
                "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Account identifier must be a canonical UUID.");
        }
        return UUID.fromString(uuid).toString();
    }

    private static Path getAccountFile(String uuid) {
        return ACCOUNTS_DIRECTORY.resolve(normalizeAccountId(uuid) + ".json");
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

        Path temp = Files.createTempFile(
                target.getParent(),
                target.getFileName().toString() + ".",
                ".tmp"
        );

        try {
            Files.writeString(
                    temp,
                    content,
                    StandardCharsets.UTF_8,
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
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(
                        temp,
                        target,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}