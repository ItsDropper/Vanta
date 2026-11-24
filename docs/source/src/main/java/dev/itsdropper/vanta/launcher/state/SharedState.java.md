# `SharedState.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/state/SharedState.java`

## Overview

SharedState is part of the state subsystem. Read-only aggregate health snapshots and filesystem fingerprinting for instance/shared-resource status. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.state`
- Role: production implementation
- Top-level declaration: `public final class SharedState`
- Source size: 20 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

- `private final String name;`
- `private final Level level;`
- `private final int files;`
- `private final int broken;`
- `private final String summary;`

## Dependencies

No explicit imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
