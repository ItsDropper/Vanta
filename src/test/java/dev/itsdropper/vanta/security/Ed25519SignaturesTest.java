package dev.itsdropper.vanta.security;

import org.junit.jupiter.api.Test;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class Ed25519SignaturesTest {
    @Test
    void decodesRawPublicKeyAndVerifiesExactPayload() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] encoded = pair.getPublic().getEncoded();
        byte[] raw = java.util.Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length);
        var decoded = Ed25519Signatures.decodePublicKey(Base64.getEncoder().encodeToString(raw));
        byte[] payload = "vanta-platform-metadata-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        Ed25519Signatures.verify(payload, sign(payload, pair), decoded);
    }

    @Test
    void rejectsModifiedPayload() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] payload = "original".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] signature = sign(payload, pair);
        payload[0] ^= 1;

        assertThrows(GeneralSecurityException.class,
                () -> Ed25519Signatures.verify(payload, signature, pair.getPublic()));
    }

    @Test
    void rejectsMalformedPublicKeysAndSignatures() throws Exception {
        assertThrows(GeneralSecurityException.class, () -> Ed25519Signatures.decodePublicKey("AQID"));
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        assertThrows(GeneralSecurityException.class,
                () -> Ed25519Signatures.verify(new byte[]{1}, new byte[63], pair.getPublic()));
    }

    private static byte[] sign(byte[] payload, KeyPair pair) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(payload);
        return signer.sign();
    }
}
