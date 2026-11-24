# `WindowsCredentialManager.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/security/WindowsCredentialManager.java`

## Overview

WindowsCredentialManager is part of the security subsystem. Credential protection and platform security boundaries. Secrets must remain in OS credential stores rather than launcher config files. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.security`
- Role: production implementation
- Top-level declaration: `public final class WindowsCredentialManager`
- Source size: 54 lines

## API surface

- `public static String encrypt(String plaintext) {`
- `public static String decrypt(String encrypted) {`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 4 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.Platform`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
