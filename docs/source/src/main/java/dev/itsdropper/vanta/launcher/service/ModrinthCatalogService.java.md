# `ModrinthCatalogService.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/service/ModrinthCatalogService.java`

## Overview

ModrinthCatalogService is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.service`
- Role: production implementation
- Top-level declaration: `public final class ModrinthCatalogService`
- Source size: 418 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

- `private final ModrinthClient client;`
- `private final InstalledModScanner installedModScanner;`

## Dependencies

Imports: 2 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.model.Instance`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
