# `RiftModMetadataCompatibility.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/instance/RiftModMetadataCompatibility.java`

## Overview

RiftModMetadataCompatibility is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.instance`
- Role: production implementation
- Top-level declaration: `public final class RiftModMetadataCompatibility`
- Source size: 173 lines

## API surface

- `public static int normalizeModsDirectory(Path modsDirectory) throws IOException {`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 16 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
