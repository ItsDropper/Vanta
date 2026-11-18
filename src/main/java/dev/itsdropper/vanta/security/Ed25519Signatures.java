package dev.itsdropper.vanta.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Shared Ed25519 primitives for authenticated, remotely distributed Vanta metadata.
 *
 * <p>Vanta pins raw 32-byte Ed25519 public keys in the application. Java's
 * Ed25519 KeyFactory expects the corresponding X.509 SubjectPublicKeyInfo
 * wrapper, which this class adds after validating the raw key length.</p>
 */
public final class Ed25519Signatures {
    private static final byte[] ED25519_X509_PREFIX = {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };
    private static final int RAW_PUBLIC_KEY_BYTES = 32;
    private static final int SIGNATURE_BYTES = 64;

    private Ed25519Signatures() {
    }

    /** Decodes a Base64-encoded, raw 32-byte Ed25519 public key. */
    public static PublicKey decodePublicKey(String base64Key) throws GeneralSecurityException {
        if (base64Key == null || base64Key.isBlank()) {
            throw new GeneralSecurityException("Ed25519 public key is missing.");
        }

        final byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException malformed) {
            throw new GeneralSecurityException("Ed25519 public key is not valid Base64.", malformed);
        }
        if (raw.length != RAW_PUBLIC_KEY_BYTES) {
            throw new GeneralSecurityException("Ed25519 public keys must contain exactly 32 bytes.");
        }

        byte[] encoded = new byte[ED25519_X509_PREFIX.length + raw.length];
        System.arraycopy(ED25519_X509_PREFIX, 0, encoded, 0, ED25519_X509_PREFIX.length);
        System.arraycopy(raw, 0, encoded, ED25519_X509_PREFIX.length, raw.length);
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
    }

    /** Verifies the exact payload bytes; callers must not parse or reserialize before verification. */
    public static void verify(byte[] payload, byte[] signature, PublicKey publicKey)
            throws GeneralSecurityException {
        if (payload == null || payload.length == 0) {
            throw new GeneralSecurityException("Signed metadata payload is empty.");
        }
        if (signature == null || signature.length != SIGNATURE_BYTES) {
            throw new GeneralSecurityException("Ed25519 signatures must contain exactly 64 bytes.");
        }
        if (publicKey == null || !"EdDSA".equalsIgnoreCase(publicKey.getAlgorithm())
                && !"Ed25519".equalsIgnoreCase(publicKey.getAlgorithm())) {
            throw new GeneralSecurityException("An Ed25519 public key is required.");
        }

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(payload);
        if (!verifier.verify(signature)) {
            throw new GeneralSecurityException("Ed25519 signature verification failed.");
        }
    }
}
