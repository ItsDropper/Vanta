# `EnvironmentLockfile.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/environment/EnvironmentLockfile.java`

## Overview

EnvironmentLockfile is part of the environment subsystem. Read-only environment inspection and portable lockfile capture/comparison. These services must not mutate instance files. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.environment`
- Role: production implementation
- Top-level declaration: `public record EnvironmentLockfile`
- Source size: 64 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 1 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
