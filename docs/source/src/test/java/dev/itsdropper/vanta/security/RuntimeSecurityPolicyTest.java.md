# `RuntimeSecurityPolicyTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/security/RuntimeSecurityPolicyTest.java`

## Overview

Test class for RuntimeSecurityPolicy.

## Test cases

- `verifiesEd25519SignatureAndParsesPolicy`
- `rejectsTamperedSignedBytes`
- `rejectsExpiredPolicy`
- `rejectsUnknownMitigation`
- `normalizesSupportedArchitectureAliases`

## Dependencies

- `org.junit.jupiter.api.Test`

## Maintenance

Treat assertions and fixtures in this file as executable regression specifications. Update tests when observable behavior changes; do not weaken security or failure-path assertions merely to accommodate an implementation change.
