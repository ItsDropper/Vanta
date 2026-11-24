# `RuntimeSecurityPolicy.java`

**Source:** `src/main/java/dev/itsdropper/vanta/security/RuntimeSecurityPolicy.java`

## Overview

Signed, centrally applied runtime mitigations. Policy data cannot inject arbitrary JVM arguments.

## Package and type

- Package: `dev.itsdropper.vanta.security`
- Role: production implementation
- Top-level declaration: `public final class RuntimeSecurityPolicy`
- Source size: 138 lines

## API surface

- `public List<String> argumentsFor(int javaMajor,String architecture){`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 8 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.MinecraftLocator`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
