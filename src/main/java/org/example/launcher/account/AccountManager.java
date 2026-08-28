package org.example.launcher.account;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.step.java.session.StepFullJavaSession;
import org.example.launcher.security.WindowsCredentialManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AccountManager {


    private static final Path FILE = Path.of(
            System.getProperty("user.home"),
            ".dropperlauncher",
            "account.json"
    );

    public static void saveAccount(Account account) throws IOException {

        System.out.println("Saving Vanta account...");
        System.out.println("Account file: " + FILE);

        Files.createDirectories(FILE.getParent());

        JsonObject sessionJson =
                MinecraftAuth.JAVA_DEVICE_CODE_LOGIN
                        .toJson(account.getSession());

        System.out.println("Session serialized.");

        String encrypted =
                WindowsCredentialManager.encrypt(
                        sessionJson.toString()
                );

        System.out.println("Session encrypted.");
        System.out.println(
                "Encrypted data length: "
                        + encrypted.length()
        );

        JsonObject json = new JsonObject();

        json.addProperty("version", 1);
        json.addProperty(
                "encryptedSession",
                encrypted
        );

        Files.writeString(
                FILE,
                json.toString()
        );

        System.out.println("Account saved successfully.");
    }

    public static Account loadAccount() throws IOException {

        if (!Files.exists(FILE)) {

            System.out.println(
                    "No saved Vanta account found."
            );

            return null;
        }

        System.out.println(
                "Loading saved Vanta account..."
        );

        String content =
                Files.readString(FILE);

        JsonObject json =
                JsonParser.parseString(content)
                        .getAsJsonObject();

        if (!json.has("encryptedSession")) {

            throw new IOException(
                    "Account file is not encrypted"
            );
        }

        String encrypted =
                json.get("encryptedSession")
                        .getAsString();

        String decrypted;

        try {

            decrypted =
                    WindowsCredentialManager.decrypt(
                            encrypted
                    );

        } catch (Exception e) {

            throw new IOException(
                    "Could not decrypt saved Vanta account",
                    e
            );
        }

        JsonObject sessionJson =
                JsonParser.parseString(decrypted)
                        .getAsJsonObject();

        StepFullJavaSession.FullJavaSession session =
                MinecraftAuth.JAVA_DEVICE_CODE_LOGIN
                        .fromJson(sessionJson);

        System.out.println(
                "Saved Vanta account loaded successfully."
        );

        return new Account(session);
    }

    public static void deleteAccount() throws IOException {

        if (!Files.exists(FILE)) {

            System.out.println(
                    "No saved Vanta account to remove."
            );

            return;
        }

        Files.delete(FILE);

        System.out.println(
                "Saved Vanta account removed."
        );
    }

    public static void clearAccount() throws IOException {


        if (Files.exists(FILE)) {
            Files.delete(FILE);
        }


    }



}
