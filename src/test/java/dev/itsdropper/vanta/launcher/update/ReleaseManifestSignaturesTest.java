package dev.itsdropper.vanta.launcher.update;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseManifestSignaturesTest {
    private static final byte[] DOMAIN =
            "VANTA-RELEASE-MANIFEST-V1\n".getBytes(StandardCharsets.US_ASCII);

    @Test
    void verifiesExactManifestBytesUsingPinnedKeyId() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] payload = "{\"version\":\"1.2.3\"}".getBytes(StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> ReleaseManifestSignatures.verify(payload,
                envelope("release-test-01", sign(payload, pair)),
                Map.of("release-test-01", rawPublicKey(pair))));
    }

    @Test
    void rejectsModifiedManifestBytes() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] payload = "original".getBytes(StandardCharsets.UTF_8);
        byte[] signature = sign(payload, pair);
        payload[0] ^= 1;
        assertThrows(IOException.class, () -> ReleaseManifestSignatures.verify(payload,
                envelope("release-test-01", signature), Map.of("release-test-01", rawPublicKey(pair))));
    }

    @Test
    void rejectsUnknownAndMalformedKeyIds() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] payload = "manifest".getBytes(StandardCharsets.UTF_8);
        byte[] signature = sign(payload, pair);
        assertThrows(IOException.class, () -> ReleaseManifestSignatures.verify(payload,
                envelope("release-test-01", signature), Map.of()));
        assertThrows(IOException.class, () -> ReleaseManifestSignatures.verify(payload,
                envelope("../attacker", signature), Map.of("../attacker", rawPublicKey(pair))));
    }

    @Test
    void rejectsDuplicateFieldsUnknownFieldsAndInvalidSignatureLengths() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] payload = "manifest".getBytes(StandardCharsets.UTF_8);
        String signature = Base64.getEncoder().encodeToString(sign(payload, pair));
        String duplicate = "{\"schema_version\":1,\"algorithm\":\"Ed25519\","
                + "\"key_id\":\"release-test-01\",\"key_id\":\"release-test-02\","
                + "\"signature\":\"" + signature + "\"}";
        assertThrows(IOException.class, () -> ReleaseManifestSignatures.verify(payload,
                duplicate.getBytes(StandardCharsets.UTF_8), Map.of("release-test-01", rawPublicKey(pair))));
        String extra = "{\"schema_version\":1,\"algorithm\":\"Ed25519\","
                + "\"key_id\":\"release-test-01\",\"signature\":\""
                + signature + "\",\"extra\":true}";
        assertThrows(IOException.class, () -> ReleaseManifestSignatures.verify(payload,
                extra.getBytes(StandardCharsets.UTF_8), Map.of("release-test-01", rawPublicKey(pair))));
        assertThrows(IOException.class, () -> ReleaseManifestSignatures.verify(payload,
                envelope("release-test-01", new byte[63]), Map.of("release-test-01", rawPublicKey(pair))));
    }

    @Test
    void parsesKeyringAndRejectsDuplicateIds() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String key = rawPublicKey(pair);
        String entry = "{\"key_id\":\"release-test-01\",\"public_key\":\"" + key + "\"}";
        byte[] valid = ("{\"schema_version\":1,\"keys\":[" + entry + "]}")
                .getBytes(StandardCharsets.UTF_8);
        assertEquals(Map.of("release-test-01", key), ReleaseManifestSignatures.parseKeyring(valid));
        byte[] duplicate = ("{\"schema_version\":1,\"keys\":[" + entry + "," + entry + "]}")
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> ReleaseManifestSignatures.parseKeyring(duplicate));
    }

    private static byte[] envelope(String keyId, byte[] signature) {
        String json = "{\"schema_version\":1,\"algorithm\":\"Ed25519\",\"key_id\":\""
                + keyId + "\",\"signature\":\"" + Base64.getEncoder().encodeToString(signature) + "\"}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] sign(byte[] payload, KeyPair pair) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(DOMAIN);
        signer.update(payload);
        return signer.sign();
    }

    private static String rawPublicKey(KeyPair pair) {
        byte[] encoded = pair.getPublic().getEncoded();
        return Base64.getEncoder().encodeToString(
                java.util.Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length));
    }
}
