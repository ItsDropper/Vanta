# `Ed25519Signatures.java`

**Source:** `src/main/java/dev/itsdropper/vanta/security/Ed25519Signatures.java`

## Overview

Shared Ed25519 primitives for authenticated, remotely distributed Vanta metadata. <p>Vanta pins raw 32-byte Ed25519 public keys in the application. Java's Ed25519 KeyFactory expects the corresponding X.509 SubjectPublicKeyInfo wrapper, which this class adds after validating the raw key length.</p>

## Package and type

- Package: `dev.itsdropper.vanta.security`
- Role: production implementation
- Top-level declaration: `class adds`
- Source size: 71 lines

## API surface

- `public static PublicKey decodePublicKey(String base64Key) throws GeneralSecurityException {`
- `public static void verify(byte[] payload, byte[] signature, PublicKey publicKey)`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 6 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
