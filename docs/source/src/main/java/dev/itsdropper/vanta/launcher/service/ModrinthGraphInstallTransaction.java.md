# `ModrinthGraphInstallTransaction.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/service/ModrinthGraphInstallTransaction.java`

## Overview

ModrinthGraphInstallTransaction is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.service`
- Role: production implementation
- Top-level declaration: `final class ModrinthGraphInstallTransaction`
- Source size: 534 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

- `private final Path instanceRoot;`
- `private final Path modsDirectory;`
- `private final Path directory;`
- `private final Properties journal;`
- `private int users;`

## Dependencies

Imports: 21 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.instance.InstalledModManager`
- `dev.itsdropper.vanta.launcher.instance.InstalledModRecord`
- `dev.itsdropper.vanta.launcher.model.Instance`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
