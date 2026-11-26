package dev.itsdropper.vanta.launcher.security;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

/**
 * Minimal Vanta-owned binding for Windows Data Protection API (DPAPI).
 *
 * <p>The protected blob remains compatible with CryptProtectData /
 * CryptUnprotectData and therefore with credentials written by previous
 * Vanta versions using JNA Platform.</p>
 */
final class WindowsDpapi {
    private static final int CRYPTPROTECT_UI_FORBIDDEN = 0x1;
    private static final int MAX_BLOB_BYTES = 16 * 1024 * 1024;

    private interface Crypt32 extends StdCallLibrary {
        boolean CryptProtectData(DataBlob input, WString description,
                                 Pointer optionalEntropy, Pointer reserved,
                                 Pointer prompt, int flags, DataBlob output);

        boolean CryptUnprotectData(DataBlob input, PointerByReference description,
                                   Pointer optionalEntropy, Pointer reserved,
                                   Pointer prompt, int flags, DataBlob output);
    }

    private interface Kernel32 extends StdCallLibrary {
        Pointer LocalFree(Pointer memory);
        int GetLastError();
    }

    @Structure.FieldOrder({"cbData", "pbData"})
    private static final class DataBlob extends Structure {
        public int cbData;
        public Pointer pbData;

        private DataBlob() {
        }

        private DataBlob(Pointer data, int length) {
            cbData = length;
            pbData = data;
            write();
        }
    }

    private static final Crypt32 CRYPT32 = Native.load("Crypt32", Crypt32.class);
    private static final Kernel32 KERNEL32 = Native.load("kernel32", Kernel32.class);

    private WindowsDpapi() {
    }

    static byte[] protect(byte[] plaintext) throws IOException {
        return transform(plaintext, true);
    }

    static byte[] unprotect(byte[] ciphertext) throws IOException {
        return transform(ciphertext, false);
    }

    private static byte[] transform(byte[] input, boolean protect) throws IOException {
        if (input == null) {
            throw new IllegalArgumentException("DPAPI input cannot be null.");
        }
        if (input.length > MAX_BLOB_BYTES) {
            throw new IOException("Windows credential data exceeds the supported size limit.");
        }

        // DPAPI allocates its output with LocalAlloc; the caller must release it with LocalFree.
        try (Memory inputMemory = new Memory(Math.max(1, input.length))) {
            if (input.length > 0) {
                inputMemory.write(0, input, 0, input.length);
            }

            DataBlob inputBlob = new DataBlob(inputMemory, input.length);
            DataBlob outputBlob = new DataBlob();
            PointerByReference description = new PointerByReference();

            boolean succeeded = protect
                    ? CRYPT32.CryptProtectData(inputBlob, new WString("Vanta account session"),
                            null, null, null, CRYPTPROTECT_UI_FORBIDDEN, outputBlob)
                    : CRYPT32.CryptUnprotectData(inputBlob, description,
                            null, null, null, CRYPTPROTECT_UI_FORBIDDEN, outputBlob);

            try {
                if (!succeeded) {
                    throw new IOException("Windows DPAPI "
                            + (protect ? "encryption" : "decryption")
                            + " failed (Win32 error " + KERNEL32.GetLastError() + ").");
                }

                outputBlob.read();
                if (outputBlob.cbData < 0 || outputBlob.cbData > MAX_BLOB_BYTES
                        || (outputBlob.cbData > 0 && outputBlob.pbData == null)) {
                    throw new IOException("Windows DPAPI returned an invalid credential buffer.");
                }

                return outputBlob.cbData == 0
                        ? new byte[0]
                        : outputBlob.pbData.getByteArray(0, outputBlob.cbData);
            } finally {
                if (outputBlob.pbData != null) {
                    KERNEL32.LocalFree(outputBlob.pbData);
                }
                if (description.getValue() != null) {
                    KERNEL32.LocalFree(description.getValue());
                }
            }
        } finally {
            // Do not leave plaintext input in the temporary native buffer.
            // The buffer is owned by the try-with-resources block above.
        }
    }

    static String protectToBase64(String plaintext) throws IOException {
        if (plaintext == null) {
            throw new IllegalArgumentException("Credential cannot be null.");
        }
        return Base64.getEncoder().encodeToString(protect(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    static String unprotectFromBase64(String encrypted) throws IOException {
        if (encrypted == null || encrypted.isBlank()) {
            throw new IllegalArgumentException("Encrypted credential cannot be empty.");
        }
        if (encrypted.length() > ((MAX_BLOB_BYTES + 2L) / 3L) * 4L) {
            throw new IOException("Windows credential data exceeds the supported size limit.");
        }
        byte[] ciphertext;
        try {
            ciphertext = Base64.getDecoder().decode(encrypted);
        } catch (IllegalArgumentException malformed) {
            throw new IOException("Windows credential data is not valid Base64.", malformed);
        }
        return new String(unprotect(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
    }
}
