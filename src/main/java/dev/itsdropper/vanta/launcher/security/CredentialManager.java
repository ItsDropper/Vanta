package dev.itsdropper.vanta.launcher.security;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import dev.itsdropper.vanta.launcher.Platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Stores account sessions in the host operating system's credential vault.
 * Linux requires Secret Service and the secret-tool command (libsecret-tools).
 */
public final class CredentialManager {
    private static final String SERVICE = "dev.itsdropper.vanta";
    private static final String MARKER = "vanta-os-secret:v1:";

    private interface MacSecurity extends Library {
        int SecKeychainAddGenericPassword(Pointer keychain, int serviceLength, String service,
                int accountLength, String account, int passwordLength, byte[] password,
                PointerByReference item);
        int SecKeychainFindGenericPassword(Pointer keychain, int serviceLength, String service,
                int accountLength, String account, IntByReference passwordLength,
                PointerByReference passwordData, PointerByReference item);
        int SecKeychainItemModifyAttributesAndData(Pointer item, Pointer attributes,
                int passwordLength, byte[] password);
        int SecKeychainItemFreeContent(Pointer attributes, Pointer data);
    }

    private CredentialManager() {}

    public static String encrypt(String plaintext, String accountId) throws IOException {
        if (plaintext == null || accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Credential and account identifier are required.");
        }
        return switch (Platform.operatingSystem()) {
            case WINDOWS -> WindowsCredentialManager.encrypt(plaintext);
            case MACOS -> {
                storeMacCredential(accountId, plaintext);
                yield MARKER + accountId;
            }
            case LINUX -> {
                storeLinuxCredential(accountId, plaintext);
                yield MARKER + accountId;
            }
            case UNKNOWN -> throw new IOException("Secure account storage is unavailable on this operating system.");
        };
    }

    public static String decrypt(String storedValue) throws IOException {
        if (storedValue == null || storedValue.isBlank()) {
            throw new IOException("Stored account credential is empty.");
        }
        if (!storedValue.startsWith(MARKER)) {
            try {
                return WindowsCredentialManager.decrypt(storedValue);
            } catch (RuntimeException | UnsupportedOperationException failure) {
                throw new IOException("This account uses Windows DPAPI and cannot be decrypted on this platform.", failure);
            }
        }
        String accountId = storedValue.substring(MARKER.length());
        if (!accountId.matches("[A-Za-z0-9-]{1,128}")) {
            throw new IOException("Stored credential reference is invalid.");
        }
        return switch (Platform.operatingSystem()) {
            case WINDOWS -> throw new IOException("This account credential belongs to another operating system's vault.");
            case MACOS -> loadMacCredential(accountId);
            case LINUX -> loadLinuxCredential(accountId);
            case UNKNOWN -> throw new IOException("Secure account storage is unavailable on this operating system.");
        };
    }

    public static void delete(String storedValue) throws IOException {
        if (storedValue == null || !storedValue.startsWith(MARKER)) return;
        String accountId = storedValue.substring(MARKER.length());
        if (!accountId.matches("[A-Za-z0-9-]{1,128}")) return;
        switch (Platform.operatingSystem()) {
            case MACOS -> deleteMacCredential(accountId);
            case LINUX -> runSecretTool(new String[]{"clear", "service", SERVICE, "account", accountId}, null);
            default -> { }
        }
    }

    private static MacSecurity macSecurity() {
        return Native.load("Security", MacSecurity.class);
    }

    private static void storeMacCredential(String accountId, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        MacSecurity security = macSecurity();
        PointerByReference existing = new PointerByReference();
        IntByReference existingLength = new IntByReference();
        PointerByReference existingData = new PointerByReference();
        int status = security.SecKeychainFindGenericPassword(null, SERVICE.length(), SERVICE,
                accountId.length(), accountId, existingLength, existingData, existing);
        try {
            if (status == 0) {
                int update = security.SecKeychainItemModifyAttributesAndData(
                        existing.getValue(), null, bytes.length, bytes);
                if (update != 0) throw new IOException("macOS Keychain update failed (status " + update + ").");
                return;
            }
            if (status != -25300) {
                throw new IOException("macOS Keychain lookup failed (status " + status + ").");
            }
            int added = security.SecKeychainAddGenericPassword(null, SERVICE.length(), SERVICE,
                    accountId.length(), accountId, bytes.length, bytes, new PointerByReference());
            if (added != 0) throw new IOException("macOS Keychain write failed (status " + added + ").");
        } catch (UnsatisfiedLinkError | RuntimeException failure) {
            throw new IOException("Could not access the macOS Keychain.", failure);
        } finally {
            if (existingData.getValue() != null) {
                security.SecKeychainItemFreeContent(null, existingData.getValue());
            }
        }
    }

    private static String loadMacCredential(String accountId) throws IOException {
        MacSecurity security = macSecurity();
        IntByReference length = new IntByReference();
        PointerByReference data = new PointerByReference();
        int status = security.SecKeychainFindGenericPassword(null, SERVICE.length(), SERVICE,
                accountId.length(), accountId, length, data, new PointerByReference());
        if (status != 0 || data.getValue() == null) {
            throw new IOException("The account credential was not found in macOS Keychain (status " + status + ").");
        }
        try {
            return new String(data.getValue().getByteArray(0, length.getValue()), StandardCharsets.UTF_8);
        } finally {
            security.SecKeychainItemFreeContent(null, data.getValue());
        }
    }

    private static void deleteMacCredential(String accountId) throws IOException {
        MacSecurity security = macSecurity();
        IntByReference length = new IntByReference();
        PointerByReference data = new PointerByReference();
        PointerByReference item = new PointerByReference();
        int status = security.SecKeychainFindGenericPassword(null, SERVICE.length(), SERVICE,
                accountId.length(), accountId, length, data, item);
        if (data.getValue() != null) security.SecKeychainItemFreeContent(null, data.getValue());
        if (status == -25300) return;
        if (status != 0) throw new IOException("macOS Keychain lookup failed (status " + status + ").");
        // Delete is performed by updating the item to an empty secret; the account file is removed separately.
        int update = security.SecKeychainItemModifyAttributesAndData(item.getValue(), null, 0, new byte[0]);
        if (update != 0) throw new IOException("macOS Keychain cleanup failed (status " + update + ").");
    }

    private static void storeLinuxCredential(String accountId, String value) throws IOException {
        runSecretTool(new String[]{"store", "--label=Vanta account session",
                "service", SERVICE, "account", accountId}, value);
    }

    private static String loadLinuxCredential(String accountId) throws IOException {
        return runSecretTool(new String[]{"lookup", "service", SERVICE, "account", accountId}, null);
    }

    private static String runSecretTool(String[] arguments, String stdin) throws IOException {
        String[] command = new String[arguments.length + 1];
        command[0] = "secret-tool";
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        final Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException unavailable) {
            throw new IOException("Linux secure storage requires Secret Service and the 'secret-tool' command.", unavailable);
        }
        if (stdin != null) {
            try (var output = process.getOutputStream()) {
                output.write(stdin.getBytes(StandardCharsets.UTF_8));
                output.write('\n');
            }
        } else {
            process.getOutputStream().close();
        }
        byte[] output;
        try (var input = process.getInputStream()) {
            output = input.readAllBytes();
        }
        try {
            if (process.waitFor() != 0) {
                throw new IOException("Linux Secret Service operation failed: "
                        + new String(output, StandardCharsets.UTF_8).trim());
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while accessing Linux Secret Service.", interrupted);
        }
        return new String(output, StandardCharsets.UTF_8).stripTrailing();
    }
}
