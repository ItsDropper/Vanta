package dev.itsdropper.vanta.launcher.update;

import dev.itsdropper.vanta.json.JsonNode;
import dev.itsdropper.vanta.json.ObjectMapper;
import dev.itsdropper.vanta.json.VantaJson;
import dev.itsdropper.vanta.security.Ed25519Signatures;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Verifies detached signatures over the exact release-manifest response bytes. */
final class ReleaseManifestSignatures {
    private static final int MAX_SIGNATURE_BYTES = 16 * 1024;
    private static final byte[] DOMAIN_PREFIX =
            "VANTA-RELEASE-MANIFEST-V1\n".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern KEY_ID = Pattern.compile("[a-z0-9][a-z0-9._-]{2,63}");
    private static final ObjectMapper JSON = VantaJson.strictMapper();

    private ReleaseManifestSignatures() {}

    static void verifyPinned(byte[] manifest, byte[] envelope) throws IOException {
        verify(manifest, envelope, loadPinnedKeys());
    }

    static void verify(byte[] manifest, byte[] envelope, Map<String, String> trustedKeys)
            throws IOException {
        if (manifest == null || manifest.length == 0 || manifest.length > 1024 * 1024) {
            throw new IOException("The signed release manifest has an invalid size.");
        }
        if (envelope == null || envelope.length == 0 || envelope.length > MAX_SIGNATURE_BYTES) {
            throw new IOException("The release manifest signature envelope has an invalid size.");
        }
        if (trustedKeys == null || trustedKeys.isEmpty()) {
            throw new IOException("This Vanta build has no pinned release-signing public keys.");
        }

        final JsonNode root;
        try {
            root = JSON.readTree(envelope);
        } catch (Exception malformed) {
            throw new IOException("The release manifest signature envelope is not valid JSON.", malformed);
        }
        requireExactFields(root, Set.of("schema_version", "algorithm", "key_id", "signature"));
        if (!root.path("schema_version").isIntegralNumber() || root.path("schema_version").asInt(-1) != 1
                || !"Ed25519".equals(root.path("algorithm").asText())) {
            throw new IOException("The release manifest signature envelope uses an unsupported format.");
        }

        String keyId = requiredText(root, "key_id");
        if (!KEY_ID.matcher(keyId).matches()) {
            throw new IOException("The release manifest signature contains an invalid key identifier.");
        }
        String encodedKey = trustedKeys.get(keyId);
        if (encodedKey == null) {
            throw new IOException("The release manifest was signed by an untrusted key: " + keyId);
        }

        byte[] signature = decodeCanonicalBase64(requiredText(root, "signature"), "signature");
        if (signature.length != 64) {
            throw new IOException("Ed25519 release signatures must contain exactly 64 bytes.");
        }

        try {
            PublicKey publicKey = Ed25519Signatures.decodePublicKey(encodedKey);
            ByteArrayOutputStream signed = new ByteArrayOutputStream(DOMAIN_PREFIX.length + manifest.length);
            signed.write(DOMAIN_PREFIX);
            signed.write(manifest);
            Ed25519Signatures.verify(signed.toByteArray(), signature, publicKey);
        } catch (GeneralSecurityException invalidSignature) {
            throw new IOException("The release manifest signature could not be verified.", invalidSignature);
        }
    }

    static Map<String, String> loadPinnedKeys() throws IOException {
        try (InputStream input = ReleaseManifestSignatures.class.getResourceAsStream(
                "/release-trusted-keys.json")) {
            if (input == null) {
                throw new IOException("The Vanta release-signing trust store is missing.");
            }
            byte[] bytes = input.readNBytes(MAX_SIGNATURE_BYTES + 1);
            if (bytes.length > MAX_SIGNATURE_BYTES) {
                throw new IOException("The Vanta release-signing trust store is too large.");
            }
            return parseKeyring(bytes);
        }
    }

    static Map<String, String> parseKeyring(byte[] bytes) throws IOException {
        final JsonNode root;
        try {
            root = JSON.readTree(bytes);
        } catch (Exception malformed) {
            throw new IOException("The Vanta release-signing trust store is invalid JSON.", malformed);
        }
        requireExactFields(root, Set.of("schema_version", "keys"));
        if (!root.path("schema_version").isIntegralNumber() || root.path("schema_version").asInt(-1) != 1
                || !root.path("keys").isArray()) {
            throw new IOException("The Vanta release-signing trust store has an unsupported schema.");
        }

        Map<String, String> keys = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode entry : root.path("keys")) {
            requireExactFields(entry, Set.of("key_id", "public_key"));
            String id = requiredText(entry, "key_id");
            if (!KEY_ID.matcher(id).matches() || !seen.add(id)) {
                throw new IOException("The Vanta release-signing trust store contains an invalid or duplicate key ID.");
            }
            String publicKey = requiredText(entry, "public_key");
            byte[] raw = decodeCanonicalBase64(publicKey, "public key");
            if (raw.length != 32) {
                throw new IOException("Pinned Ed25519 public keys must contain exactly 32 bytes.");
            }
            try {
                Ed25519Signatures.decodePublicKey(publicKey);
            } catch (GeneralSecurityException malformed) {
                throw new IOException("The Vanta release-signing trust store contains an invalid public key.", malformed);
            }
            keys.put(id, publicKey);
        }
        return Map.copyOf(keys);
    }

    private static byte[] decodeCanonicalBase64(String value, String description) throws IOException {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (!Base64.getEncoder().encodeToString(decoded).equals(value)) {
                throw new IOException("The release manifest " + description + " is not canonical Base64.");
            }
            return decoded;
        } catch (IllegalArgumentException malformed) {
            throw new IOException("The release manifest " + description + " is not valid Base64.", malformed);
        }
    }

    private static String requiredText(JsonNode node, String field) throws IOException {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()
                || !value.asText().equals(value.asText().trim())) {
            throw new IOException("The release manifest signature is missing a valid '" + field + "'.");
        }
        return value.asText();
    }

    private static void requireExactFields(JsonNode node, Set<String> expected) throws IOException {
        if (node == null || !node.isObject()) {
            throw new IOException("The release manifest signature metadata must be a JSON object.");
        }
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw new IOException("The release manifest signature metadata has missing or unknown fields.");
        }
    }
}
