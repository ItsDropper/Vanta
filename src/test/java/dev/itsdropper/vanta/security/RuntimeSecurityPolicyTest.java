package dev.itsdropper.vanta.security;

import org.junit.jupiter.api.Test;
import java.security.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeSecurityPolicyTest {
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"),ZoneOffset.UTC);

    @Test void verifiesEd25519SignatureAndParsesPolicy() throws Exception {
        byte[] p=manifest("2027-11-18T00:00:00Z","DISABLE_BASE64_INTRINSICS");
        var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var parsed=RuntimeSecurityPolicy.parseAndVerify(p,sign(p,pair.getPrivate()),clock,pair.getPublic());
        assertEquals(1,parsed.size());
        assertEquals(17,parsed.get(0).min());
        assertTrue(parsed.get(0).actions().contains(RuntimeSecurityPolicy.Action.DISABLE_BASE64_INTRINSICS));
    }
    @Test void rejectsTamperedSignedBytes() throws Exception {
        byte[] p=manifest("2027-11-18T00:00:00Z","DISABLE_BASE64_INTRINSICS");
        var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] sig=sign(p,pair.getPrivate());p[8]^=1;
        assertThrows(Exception.class,()->RuntimeSecurityPolicy.parseAndVerify(p,sig,clock,pair.getPublic()));
    }
    @Test void rejectsExpiredPolicy() throws Exception {assertRejected("2026-11-17T00:00:00Z","DISABLE_BASE64_INTRINSICS");}
    @Test void rejectsUnknownMitigation() throws Exception {assertRejected("2027-11-18T00:00:00Z","RUN_ARBITRARY_COMMAND");}
    @Test void normalizesSupportedArchitectureAliases(){
        assertEquals("x64",RuntimeSecurityPolicy.normalizeArchitecture("AMD64"));
        assertEquals("x64",RuntimeSecurityPolicy.normalizeArchitecture("x86-64"));
        assertEquals("aarch64",RuntimeSecurityPolicy.normalizeArchitecture("ARM64"));
        assertNull(RuntimeSecurityPolicy.normalizeArchitecture("riscv64"));
    }
    private void assertRejected(String expiry,String action)throws Exception{
        byte[] p=manifest(expiry,action);var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        assertThrows(Exception.class,()->RuntimeSecurityPolicy.parseAndVerify(p,sign(p,pair.getPrivate()),clock,pair.getPublic()));
    }
    private static byte[] sign(byte[] p,PrivateKey key)throws Exception{
        Signature s=Signature.getInstance("Ed25519");s.initSign(key);s.update(p);return s.sign();
    }
    private static byte[] manifest(String expiry,String action){
        return ("""
        {"schemaVersion":1,"sequence":1,"issuedAt":"2026-11-18T00:00:00Z","expiresAt":"%s",
        "rules":[{"id":"CVE-2026-114938","minimumJavaMajor":17,"maximumJavaMajor":999,
        "architectures":["x64"],"mitigations":["%s"]}]}
        """).formatted(expiry,action).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}