# `LaunchFailureParser.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/service/LaunchFailureParser.java`

## Overview

LaunchFailureParser is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.service`
- Role: production implementation
- Top-level declaration: `public class LaunchFailureParser`
- Source size: 432 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

- `private static final Pattern MISSING_DEPENDENCY =`
- `private static final Pattern WRONG_VERSION =`
- `private static final Pattern INCOMPATIBLE_MODS =`
- `private static final Pattern MINECRAFT_VERSION_CONFLICT =`
- `private static final Pattern MOD_REQUIRES_MINECRAFT_VERSION =`
- `private static final Pattern MOD_MINECRAFT_VERSION_CONFLICT =`
- `private static final Pattern MISSING_DEPENDENCY_ANY_VERSION =`

## Dependencies

Imports: 6 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.ui.views.RepairView`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
