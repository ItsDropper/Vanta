# Runtime security policy

Vanta applies centrally managed runtime mitigations to every Minecraft instance. The
Ed25519 public key is embedded in the launcher; the detached signature, not HTTPS,
is the authenticity boundary. The signed manifest has a monotonically increasing
sequence and a bounded validity window. Rules can select only mitigation identifiers
compiled into Vanta and cannot inject arbitrary JVM arguments, commands, or URLs.

A verified cache loads before the UI; refresh runs asynchronously. Requests disable
redirects, have timeouts, and are size bounded. A persisted sequence/digest rejects
server-side rollback and sequence reuse with different bytes. This does not protect
against an attacker able to rewrite all local application data. The built-in Java4Shell
mitigation remains active offline and cannot be disabled by remote policy.

To publish a policy, increment sequence, set UTC issue/expiry timestamps (maximum
366 days), and commit security/runtime-policy.json. The signing workflow signs exact
manifest bytes and commits the detached signature. Configure repository Actions secret
VANTA_RUNTIME_POLICY_SIGNING_KEY with the PEM private key. Never commit the private key.
For offline signing, set VANTA_RUNTIME_POLICY_SIGNING_KEY_FILE=/secure/key.pem and run
scripts/sign-runtime-policy.sh. Key rotation requires a launcher release embedding the
new public key before replacing the signing secret.

The initial rule disables Base64 intrinsics on Java 17+ x64. Java 8, Java 11, and ARM64
do not receive this mitigation.
