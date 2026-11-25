# `MrpackInstaller.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/instance/MrpackInstaller.java`

## Overview

MrpackInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.instance`
- Role: production implementation
- Top-level declaration: `public class MrpackInstaller`
- Source size: 697 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

- `private static final ObjectMapper MAPPER =`
- `private static final String INDEX_FILE =`

## Dependencies

Imports: 12 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.model.Instance`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
